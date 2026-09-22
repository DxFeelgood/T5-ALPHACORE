package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Servizio dedicato per la ricerca unificata dei libri e il parsing esplicito dei dati
 * (Titolo, Autore, Editore, Anno, Pagine, Copertina, ISBN).
 * Supporta Apify Amazon Scraper, Amazon Direct Web Scraping, Google Books API e Open Library API.
 */
class BookSearchService(
    private val okHttpClient: OkHttpClient = NetworkClient.okHttpClient
) {
    private val tag = "BookSearchService"

    private val scrapingOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Esegue la ricerca online unificata per Titolo ed Autore.
     * Mappa esplicitamente i dati restituiti dai risultati di ricerca in un oggetto LookupResult affidabile.
     */
    suspend fun searchBooksByTitleAndAuthor(
        title: String,
        author: String
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val cleanTitleQuery = title.trim()
        val cleanAuthorQuery = author.trim()
        val query = "$cleanTitleQuery $cleanAuthorQuery".trim()

        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<LookupResult>()

        // 1. Eseguiamo la ricerca in parallelo su IBS.it e Libraccio.it
        val ibsDeferred = async {
            try {
                withTimeoutOrNull(10000) { queryIbsSearch(query, cleanTitleQuery, cleanAuthorQuery) } ?: emptyList()
            } catch (e: Exception) {
                Log.d(tag, "IBS.it search failed: ${e.message}")
                emptyList()
            }
        }

        val libraccioDeferred = async {
            try {
                withTimeoutOrNull(10000) { queryLibraccioSearch(query, cleanTitleQuery, cleanAuthorQuery) } ?: emptyList()
            } catch (e: Exception) {
                Log.d(tag, "Libraccio.it search failed: ${e.message}")
                emptyList()
            }
        }

        // Raccogliamo tutti i risultati dalle sorgenti (IBS.it e Libraccio.it)
        val ibsResults = ibsDeferred.await()
        results.addAll(ibsResults)

        val libraccioResults = libraccioDeferred.await()
        results.addAll(libraccioResults)

        // Filtro preliminare di rilevanza: accetta solo risultati che corrispondono rigorosamente al titolo cercato
        val relevantResults = results.filter { item ->
            val isBook = isLikelyBook(item)
            val titleMatches = if (cleanTitleQuery.isNotBlank()) isTitleMatch(cleanTitleQuery, item.title, cleanAuthorQuery, item.author) else true
            val authorMatches = if (cleanAuthorQuery.isNotBlank()) isAuthorMatch(cleanAuthorQuery, item.author) else true
            isBook && titleMatches && authorMatches
        }

        // Deduplicazione iniziale per ISBN normalizzato per evitare duplicati esatti tra sorgenti
        val uniqueSearchMap = LinkedHashMap<String, LookupResult>()
        for (item in relevantResults) {
            val normIsbn = item.isbn.replace("-", "").replace(" ", "").trim()
            val key = if (normIsbn.length >= 10) "isbn_$normIsbn" else "${normalizeText(item.title)}_${normalizeText(item.publisher)}_${item.publishedYear}_${item.sourceApi}"
            if (!uniqueSearchMap.containsKey(key)) {
                uniqueSearchMap[key] = item
            }
        }
        val uniqueSearchResults = uniqueSearchMap.values.toList()

        // 2. Arricchimento dei dettagli (Editore, Anno, Copertina) per i risultati con informazioni parziali
        val itemsToEnrich = uniqueSearchResults.filter { it.publisher.isBlank() || it.publishedYear.isBlank() }.take(6)
        val enrichMap = mutableMapOf<String, LookupResult>()

        if (itemsToEnrich.isNotEmpty()) {
            val enrichedList = itemsToEnrich.map { item ->
                async {
                    try {
                        withTimeoutOrNull(4000) { enrichBookMetadata(item, cleanAuthorQuery) } ?: item
                    } catch (e: Exception) {
                        item
                    }
                }
            }.awaitAll()

            for (enriched in enrichedList) {
                val normIsbn = enriched.isbn.replace("-", "").replace(" ", "").trim()
                val k = if (normIsbn.length >= 10) "isbn_$normIsbn" else "${normalizeText(enriched.title)}_${enriched.sourceApi}"
                enrichMap[k] = enriched
            }
        }

        val mergedResults = uniqueSearchResults.map { item ->
            val normIsbn = item.isbn.replace("-", "").replace(" ", "").trim()
            val k = if (normIsbn.length >= 10) "isbn_$normIsbn" else "${normalizeText(item.title)}_${item.sourceApi}"
            enrichMap[k] ?: item
        }

        // Deduplicazione finale intelligente che preserva edizioni differenti (diverso editore, diverso anno o diverso ISBN)
        val seenKeys = mutableSetOf<String>()
        val uniqueList = mutableListOf<LookupResult>()
        for (item in mergedResults) {
            if (!isLikelyBook(item)) continue
            if (cleanTitleQuery.isNotBlank() && !isTitleMatch(cleanTitleQuery, item.title, cleanAuthorQuery, item.author)) continue
            if (cleanAuthorQuery.isNotBlank() && !isAuthorMatch(cleanAuthorQuery, item.author)) continue

            val normIsbn = item.isbn.replace("-", "").replace(" ", "").trim()
            val normTitle = normalizeText(item.title)
            val normPub = normalizeText(item.publisher)
            val normYear = item.publishedYear.trim()

            val key = when {
                normIsbn.length >= 10 -> "isbn_$normIsbn"
                normPub.isNotBlank() && normYear.isNotBlank() -> "t_p_y_${normTitle}_${normPub}_${normYear}"
                normPub.isNotBlank() -> "t_p_${normTitle}_${normPub}"
                normYear.isNotBlank() -> "t_y_${normTitle}_${normYear}"
                !item.coverUrl.isNullOrBlank() -> "cov_${normTitle}_${item.coverUrl.hashCode()}"
                else -> "raw_${normTitle}_${uniqueList.size}"
            }

            if (seenKeys.add(key)) {
                uniqueList.add(item)
            }
        }

        // Ordinamento: prima la fonte (ibs.it -> libraccio.it), poi corrispondenza titolo, poi copertina, poi completezza dati
        val sortedList = uniqueList.sortedWith(
            compareByDescending<LookupResult> {
                when (it.sourceApi) {
                    "IBS.it" -> 2
                    "Libraccio.it" -> 1
                    else -> 0
                }
            }.thenByDescending {
                if (cleanTitleQuery.isNotBlank()) {
                    normalizeText(it.title) == normalizeText(cleanTitleQuery)
                } else false
            }.thenByDescending {
                !it.coverUrl.isNullOrBlank()
            }.thenByDescending {
                (if (it.publisher.isNotBlank()) 1 else 0) + (if (it.publishedYear.isNotBlank() && it.publishedYear != "2000") 1 else 0)
            }.thenByDescending {
                it.isbn.replace("-", "").trim().length >= 10
            }
        )

        sortedList
    }

    /**
     * Ricerca tramite testo su IBS.it e parsing intelligente dei risultati
     */
    suspend fun queryIbsSearch(
        query: String,
        fallbackTitle: String = "",
        fallbackAuthor: String = ""
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.ibs.it/search/?ts=as&query=$encodedQuery"
        Log.d(tag, "IBS.it text search started: $searchUrl")
        try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            scrapingOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val html = response.body?.string() ?: return@use emptyList()
                if (html.isBlank()) return@use emptyList()

                // Check if redirected to a single book page
                val actualUrl = response.request.url.toString()
                val isDetailPage = actualUrl.contains("/e/")
                if (isDetailPage) {
                    val isbnMatcher = Pattern.compile("/e/(\\d{10,13})").matcher(actualUrl)
                    if (isbnMatcher.find()) {
                        val foundIsbn = isbnMatcher.group(1) ?: ""
                        val bookLookup = BookLookupService()
                        val res = bookLookup.queryIbsByIsbn(foundIsbn)
                        if (res != null) return@use listOf(res)
                    }
                }

                // Parser of the search list
                val itemPattern = Pattern.compile("href=[\"'](/([^\"'/]+)/e/(\\d{10,13})[^\"']*)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                val matcher = itemPattern.matcher(html)
                val uniqueIsbns = mutableSetOf<String>()
                val searchResults = mutableListOf<LookupResult>()
                while (matcher.find()) {
                    val path = matcher.group(1) ?: ""
                    val slug = matcher.group(2) ?: ""
                    val isbn = matcher.group(3) ?: ""
                    val linkText = matcher.group(4)?.replace(Regex("<.*?>"), "")?.trim() ?: ""
                    if (isbn.length >= 10 && uniqueIsbns.add(isbn) && searchResults.size < 6) {
                        var author = ""
                        if (slug.contains("-libro-")) {
                            val parts = slug.split("-libro-")
                            if (parts.size >= 2) {
                                author = parts[1].replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                            }
                        }
                        val title = if (linkText.isNotBlank()) linkText else slug.replace("-", " ")
                        val coverUrl = "https://www.ibs.it/images/${isbn}_0_0_250_0_75.jpg"
                        
                        searchResults.add(LookupResult(
                            isbn = isbn,
                            title = cleanTitle(title),
                            author = cleanAuthor(author),
                            publisher = "",
                            publishedYear = "",
                            description = "Trovato su IBS.it",
                            coverUrl = coverUrl,
                            pageCount = 0,
                            genre = "Narrativa / Saggistica",
                            sourceApi = "IBS.it"
                        ))
                    }
                }

                // Local dataLayer enrichment
                val dlMatcher = Pattern.compile("dataLayer\\.push\\((.*?)\\);", Pattern.DOTALL).matcher(html)
                val dlItems = mutableMapOf<String, JSONObject>()
                while (dlMatcher.find()) {
                    try {
                        val jsonStr = dlMatcher.group(1) ?: continue
                        val jsonObj = JSONObject(jsonStr)
                        val ecom = jsonObj.optJSONObject("ecommerce")
                        if (ecom != null) {
                            val items = ecom.optJSONArray("items") ?: (ecom.optJSONArray("impression")?.optJSONArray(0))
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val itObj = items.optJSONObject(i) ?: continue
                                    val itemId = itObj.optString("item_id").ifBlank { itObj.optString("id") }
                                    val cleanId = itemId.replace("-", "").trim()
                                    if (cleanId.isNotBlank()) {
                                        dlItems[cleanId] = itObj
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(tag, "IBS list dataLayer parse error: ${e.message}")
                    }
                }

                return@use searchResults.map { book ->
                    val dlItem = dlItems[book.isbn]
                    if (dlItem != null) {
                        val publisher = cleanPublisher(dlItem.optString("item_brand"))
                        val cat2 = dlItem.optString("item_category2")
                        book.copy(
                            publisher = publisher.ifBlank { book.publisher },
                            genre = cat2.ifBlank { book.genre }
                        )
                    } else {
                        book
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "IBS text search error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Ricerca tramite testo su Libraccio.it e parsing intelligente dei risultati
     */
    suspend fun queryLibraccioSearch(
        query: String,
        fallbackTitle: String = "",
        fallbackAuthor: String = ""
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.libraccio.it/ricerca?q=$encodedQuery"
        Log.d(tag, "Libraccio.it text search started: $searchUrl")
        try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            scrapingOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val html = response.body?.string() ?: return@use emptyList()
                if (html.isBlank()) return@use emptyList()

                // Check if redirected to detail page
                val actualUrl = response.request.url.toString()
                val isDetailPage = actualUrl.contains("/libro/")
                if (isDetailPage) {
                    val isbnMatcher = Pattern.compile("/libro/(\\d{10,13})").matcher(actualUrl)
                    if (isbnMatcher.find()) {
                        val foundIsbn = isbnMatcher.group(1) ?: ""
                        val bookLookup = BookLookupService()
                        val res = bookLookup.queryLibraccioByIsbn(foundIsbn)
                        if (res != null) return@use listOf(res)
                    }
                }

                val itemPattern = Pattern.compile("href=[\"'](/libro/(\\d{10,13})/[^\"']*)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                val matcher = itemPattern.matcher(html)
                val uniqueIsbns = mutableSetOf<String>()
                val searchResults = mutableListOf<LookupResult>()
                while (matcher.find()) {
                    val path = matcher.group(1) ?: ""
                    val isbn = matcher.group(2) ?: ""
                    val linkText = matcher.group(3)?.replace(Regex("<.*?>"), "")?.trim() ?: ""
                    if (isbn.length >= 10 && uniqueIsbns.add(isbn) && searchResults.size < 6) {
                        val title = if (linkText.isNotBlank()) linkText else "Libro $isbn"
                        val coverUrl = "https://img.libraccio.it/images/${isbn}_0_170_0_75.jpg"
                        
                        searchResults.add(LookupResult(
                            isbn = isbn,
                            title = cleanTitle(title),
                            author = "",
                            publisher = "",
                            publishedYear = "",
                            description = "Trovato su Libraccio.it",
                            coverUrl = coverUrl,
                            pageCount = 0,
                            genre = "Narrativa / Saggistica",
                            sourceApi = "Libraccio.it"
                        ))
                    }
                }

                // Local dataLayer enrichment for Libraccio if available
                val dlMatcher = Pattern.compile("products'\\s*:\\s*\\[\\s*\\{(.*?)\\}\\s*\\]", Pattern.DOTALL).matcher(html)
                val dlItems = mutableMapOf<String, Map<String, String>>()
                while (dlMatcher.find()) {
                    val dlContent = dlMatcher.group(1) ?: ""
                    val kvMatcher = Pattern.compile("'([^']+)'\\s*:\\s*'([^']*)'").matcher(dlContent)
                    val map = mutableMapOf<String, String>()
                    while (kvMatcher.find()) {
                        map[kvMatcher.group(1) ?: ""] = kvMatcher.group(2) ?: ""
                    }
                    val id = map["id"] ?: ""
                    if (id.isNotBlank()) {
                        dlItems[id] = map
                    }
                }

                return@use searchResults.map { book ->
                    val dlItem = dlItems[book.isbn]
                    if (dlItem != null) {
                        val author = cleanAuthor(dlItem["author"] ?: "")
                        val publisher = cleanPublisher(dlItem["publisher"] ?: "")
                        val yearEdition = dlItem["yearEdition"] ?: ""
                        val category = dlItem["category"] ?: ""
                        val genre = if (category.isNotBlank()) category.replace("Libri/", "") else book.genre
                        book.copy(
                            author = author.ifBlank { book.author },
                            publisher = publisher.ifBlank { book.publisher },
                            publishedYear = yearEdition.ifBlank { book.publishedYear },
                            genre = genre.ifBlank { book.genre }
                        )
                    } else {
                        book
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Libraccio text search error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Ricerca diretta su ISBNSearch.org dato un ISBN specifico (supporta pagina singola e lista)
     */
    suspend fun queryIsbnSearchOrgByIsbn(isbn: String): LookupResult? = withContext(Dispatchers.IO) {
        val cleanIsbn = isbn.replace("-", "").replace(" ", "").trim()
        if (cleanIsbn.isBlank()) return@withContext null

        val urlsToTry = listOf(
            "https://isbnsearch.org/isbn/$cleanIsbn",
            "https://isbnsearch.org/search?s=$cleanIsbn"
        )

        for (searchUrl in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .build()

                scrapingOkHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val html = response.body?.string() ?: return@use
                    if (html.isBlank() || html.lowercase().contains("robot check") || html.lowercase().contains("captcha")) {
                        return@use
                    }

                    val parsedItems = parseIsbnSearchOrgHtml(html, fallbackAuthor = "", targetIsbn = cleanIsbn)
                    if (parsedItems.isNotEmpty()) {
                        val exactMatch = parsedItems.firstOrNull {
                            it.isbn.replace("-", "").replace(" ", "").trim() == cleanIsbn
                        } ?: parsedItems.first()
                        if (isLikelyBook(exactMatch)) {
                            Log.d(tag, "ISBNSearch.org trovato con successo per ISBN $cleanIsbn: ${exactMatch.title}")
                            return@withContext exactMatch
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "ISBNSearch.org lookup error for $searchUrl: ${e.message}")
            }
        }
        null
    }

    /**
     * Parsing completo dell'HTML restituito da ISBNSearch.org:
     * gestisce sia la pagina di dettaglio singola (<div class="bookinfo"> / <div id="book"> con <h1>)
     * sia la pagina con elenco di risultati (<li>...<h2>...</li>)
     */
    fun parseIsbnSearchOrgHtml(
        html: String,
        fallbackAuthor: String = "",
        targetIsbn: String = "",
        fallbackTitle: String = ""
    ): List<LookupResult> {
        val results = mutableListOf<LookupResult>()

        // 1. Controllo pagina dettaglio singolo libro (reindirizzamento diretto su /isbn/...)
        val hasSingleBook = html.contains("id=\"book\"") || html.contains("class=\"bookinfo\"")
        if (hasSingleBook) {
            var title = ""
            val h1Matcher = Pattern.compile("<h1>\\s*(?:<a[^>]*>)?\\s*(.*?)\\s*(?:</a>)?\\s*</h1>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
            if (h1Matcher.find()) {
                val rawH1 = h1Matcher.group(1) ?: ""
                if (!rawH1.contains("ISBN Search", ignoreCase = true) && !rawH1.contains("Find a book", ignoreCase = true)) {
                    title = cleanTitle(rawH1)
                }
            }

            if (title.isNotBlank()) {
                var isbn13 = ""
                val isbn13Matcher = Pattern.compile("ISBN-13:</strong>\\s*(?:<a[^>]*>)?\\s*([^<\\s]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                if (isbn13Matcher.find()) {
                    isbn13 = isbn13Matcher.group(1)?.replace("-", "")?.replace(" ", "")?.trim() ?: ""
                }

                var isbn10 = ""
                val isbn10Matcher = Pattern.compile("ISBN-10:</strong>\\s*(?:<a[^>]*>)?\\s*([^<\\s]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                if (isbn10Matcher.find()) {
                    isbn10 = isbn10Matcher.group(1)?.replace("-", "")?.replace(" ", "")?.trim() ?: ""
                }

                var author = ""
                val authorMatcher = Pattern.compile("(?:Author|Authors):</strong>\\s*(.*?)\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (authorMatcher.find()) {
                    val rawAuthorHtml = authorMatcher.group(1) ?: ""
                    author = cleanAuthor(rawAuthorHtml.replace(Regex("<.*?>"), "").trim())
                }
                if (author.isBlank()) {
                    author = fallbackAuthor
                }

                var publisher = ""
                val publisherMatcher = Pattern.compile("(?:Publisher|Publishers):</strong>\\s*(.*?)\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (publisherMatcher.find()) {
                    val rawPubHtml = publisherMatcher.group(1) ?: ""
                    publisher = cleanPublisher(rawPubHtml.replace(Regex("<.*?>"), "").trim())
                }

                var publishedDateText = ""
                val publishedMatcher = Pattern.compile("Published:</strong>\\s*([^<]+?)\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (publishedMatcher.find()) {
                    publishedDateText = publishedMatcher.group(1) ?: ""
                }
                val publishedYear = extractYearFromText(publishedDateText)

                var format = ""
                val formatMatcher = Pattern.compile("(?:Binding|Format):</strong>\\s*([^<]+?)\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (formatMatcher.find()) {
                    format = formatMatcher.group(1)?.trim() ?: ""
                }

                var coverUrl: String? = null
                val imgMatcher = Pattern.compile("<div[^>]+class=[\"']image[\"'][^>]*>\\s*<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                if (imgMatcher.find()) {
                    val imgUrl = imgMatcher.group(1) ?: ""
                    if (imgUrl.isNotBlank()) {
                        coverUrl = if (imgUrl.startsWith("http")) imgUrl else "https://isbnsearch.org$imgUrl"
                    }
                }

                var pageCount = 0
                val pageCountMatcher = Pattern.compile("(\\d+)\\s*(?:pages|pagine)", Pattern.CASE_INSENSITIVE).matcher(format)
                if (pageCountMatcher.find()) {
                    pageCount = pageCountMatcher.group(1)?.toIntOrNull() ?: 0
                }

                val cleanFormat = if (pageCount > 0) format.replace(Pattern.compile(",?\\s*\\d+\\s*(?:pages|pagine)", Pattern.CASE_INSENSITIVE).toRegex(), "").trim() else format
                val finalIsbn = isbn13.ifBlank { isbn10.ifBlank { targetIsbn } }

                val titleOk = if (fallbackTitle.isNotBlank()) isTitleMatch(fallbackTitle, title, fallbackAuthor, author) else true
                val authorOk = if (fallbackAuthor.isNotBlank() && author.isNotBlank()) isAuthorMatch(fallbackAuthor, author) else true

                if (titleOk && authorOk) {
                    results.add(
                        LookupResult(
                            isbn = finalIsbn,
                            title = title,
                            author = author,
                            publisher = publisher,
                            publishedYear = publishedYear,
                            description = "Formato: $cleanFormat. Estratto da ISBNSearch.org",
                            coverUrl = coverUrl,
                            pageCount = pageCount,
                            genre = "Narrativa / Saggistica",
                            sourceApi = "ISBNSearch.org"
                        )
                    )
                }
            }
        }

        // 2. Controllo elenco risultati con tag <li>
        val liPattern = Pattern.compile("<li>\\s*<div[^>]+class=\"thumbnail\"[^>]*>.*?<div[^>]+class=\"bookinfo\"[^>]*>.*?</li>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val liMatcher = liPattern.matcher(html)
        var count = 0
        while (liMatcher.find() && count < 20) {
            val block = liMatcher.group()

            var title = ""
            val titleMatcher = Pattern.compile("<h2>\\s*(?:<a[^>]*>)?\\s*(.*?)\\s*(?:</a>)?\\s*</h2>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(block)
            if (titleMatcher.find()) {
                title = cleanTitle(titleMatcher.group(1) ?: "")
            }

            if (title.isBlank()) continue

            var isbn13 = ""
            val isbn13Matcher = Pattern.compile("ISBN-13:</strong>\\s*([^<\\s]+)", Pattern.CASE_INSENSITIVE).matcher(block)
            if (isbn13Matcher.find()) {
                isbn13 = isbn13Matcher.group(1)?.replace("-", "")?.replace(" ", "")?.trim() ?: ""
            }

            var isbn10 = ""
            val isbn10Matcher = Pattern.compile("ISBN-10:</strong>\\s*([^<\\s]+)", Pattern.CASE_INSENSITIVE).matcher(block)
            if (isbn10Matcher.find()) {
                isbn10 = isbn10Matcher.group(1)?.replace("-", "")?.replace(" ", "")?.trim() ?: ""
            }

            var author = ""
            val authorMatcher = Pattern.compile("Author:</strong>\\s*(?:<a[^>]*>)?\\s*([^<]+?)\\s*(?:</a>)?\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(block)
            if (authorMatcher.find()) {
                author = cleanAuthor(authorMatcher.group(1) ?: "")
            }
            if (author.isBlank()) {
                author = fallbackAuthor
            }

            if (fallbackTitle.isNotBlank() && !isTitleMatch(fallbackTitle, title, fallbackAuthor, author)) continue
            if (fallbackAuthor.isNotBlank() && author.isNotBlank() && !isAuthorMatch(fallbackAuthor, author)) continue

            var publisher = ""
            val publisherMatcher = Pattern.compile("Publisher:</strong>\\s*([^<]+?)\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(block)
            if (publisherMatcher.find()) {
                publisher = cleanPublisher(publisherMatcher.group(1) ?: "")
            }

            var publishedDateText = ""
            val publishedMatcher = Pattern.compile("Published:</strong>\\s*([^<]+?)\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(block)
            if (publishedMatcher.find()) {
                publishedDateText = publishedMatcher.group(1) ?: ""
            }
            val publishedYear = extractYearFromText(publishedDateText)

            var format = ""
            val formatMatcher = Pattern.compile("(?:Binding|Format):</strong>\\s*([^<]+?)\\s*</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(block)
            if (formatMatcher.find()) {
                format = formatMatcher.group(1)?.trim() ?: ""
            }

            var coverUrl: String? = null
            val imgMatcher = Pattern.compile("<img[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(block)
            if (imgMatcher.find()) {
                val imgUrl = imgMatcher.group(1) ?: ""
                if (imgUrl.isNotBlank()) {
                    coverUrl = if (imgUrl.startsWith("http")) imgUrl else "https://isbnsearch.org$imgUrl"
                }
            }

            var pageCount = 0
            val pageCountMatcher = Pattern.compile("(\\d+)\\s*(?:pages|pagine)", Pattern.CASE_INSENSITIVE).matcher(format)
            if (pageCountMatcher.find()) {
                pageCount = pageCountMatcher.group(1)?.toIntOrNull() ?: 0
            }

            val cleanFormat = if (pageCount > 0) format.replace(Pattern.compile(",?\\s*\\d+\\s*(?:pages|pagine)", Pattern.CASE_INSENSITIVE).toRegex(), "").trim() else format
            val finalIsbn = isbn13.ifBlank { isbn10 }

            results.add(
                LookupResult(
                    isbn = finalIsbn,
                    title = title,
                    author = author,
                    publisher = publisher,
                    publishedYear = publishedYear,
                    description = "Formato: $cleanFormat. Estratto da ISBNSearch.org",
                    coverUrl = coverUrl,
                    pageCount = pageCount,
                    genre = "Narrativa / Saggistica",
                    sourceApi = "ISBNSearch.org"
                )
            )
            count++
        }

        return results
    }

    /**
     * Ricerca generica tramite ISBNSearch.org
     */
    suspend fun queryIsbnSearchOrg(
        query: String,
        fallbackTitle: String = "",
        fallbackAuthor: String = ""
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://isbnsearch.org/search?s=$encodedQuery"

        Log.d(tag, "ISBNSearch.org started: $searchUrl")

        try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            scrapingOkHttpClient.newCall(request).execute().use { response ->
                Log.d(tag, "ISBNSearch.org HTTP Status Code: ${response.code}")

                if (!response.isSuccessful) {
                    Log.d(tag, "ISBNSearch.org HTTP error: ${response.code}")
                    return@use emptyList()
                }

                val html = response.body?.string() ?: return@use emptyList()
                if (html.isBlank() || html.lowercase().contains("robot check") || html.lowercase().contains("captcha")) {
                    Log.d(tag, "ISBNSearch.org captcha or empty response")
                    return@use emptyList()
                }

                return@use parseIsbnSearchOrgHtml(html, fallbackAuthor = fallbackAuthor, targetIsbn = "", fallbackTitle = fallbackTitle)
            }
        } catch (e: Exception) {
            Log.d(tag, "ISBNSearch.org error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Scrape diretto dei dettagli di un libro da Amazon.it usando il suo ASIN / ISBN
     */
    private suspend fun scrapeAmazonDetailPage(asin: String): LookupResult? = withContext(Dispatchers.IO) {
        val url = "https://www.amazon.it/dp/$asin"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .build()

            scrapingOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null
                if (html.isBlank() || html.lowercase().contains("robot check") || html.lowercase().contains("captcha")) return@withContext null

                var title = ""
                val productTitleMatcher = Pattern.compile("<span\\s+id=\"productTitle\"[^>]*>\\s*(.*?)\\s*</span>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (productTitleMatcher.find()) {
                    title = cleanTitle(productTitleMatcher.group(1) ?: "")
                }
                if (title.isBlank()) {
                    val ogTitleMatcher = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"(.*?)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (ogTitleMatcher.find()) {
                        title = cleanTitle(ogTitleMatcher.group(1) ?: "")
                    }
                }

                if (title.isBlank()) return@withContext null

                var author = ""
                val authorMatcher = Pattern.compile("(?:id=\"bylineInfo\"|class=\"[^\"]*author[^\"]*\")[^>]*>.*?<a[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (authorMatcher.find()) {
                    author = cleanAuthor(authorMatcher.group(1) ?: "")
                }

                var publisher = ""
                var year = ""
                var pageCount = 0
                var isbn13 = asin

                // Editore
                val pubPattern = Pattern.compile("(?:Editore|Publisher)\\s*:\\s*</span>\\s*<span>\\s*([^;<|\n\r<]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                if (pubPattern.find()) {
                    publisher = cleanPublisher(pubPattern.group(1) ?: "")
                } else {
                    val pubPatternAlt = Pattern.compile("<li>\\s*<b>\\s*(?:Editore|Publisher)\\s*:\\s*</b>\\s*([^;<\\(]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (pubPatternAlt.find()) {
                        publisher = cleanPublisher(pubPatternAlt.group(1) ?: "")
                    }
                }

                // Data di pubblicazione
                val datePattern = Pattern.compile("(?:Data di pubblicazione|Editore|Publication date)\\s*:\\s*</span>\\s*<span>\\s*(.*?)</span", Pattern.CASE_INSENSITIVE).matcher(html)
                if (datePattern.find()) {
                    year = extractYearFromText(datePattern.group(1) ?: "")
                } else {
                    val datePatternAlt = Pattern.compile("(?:Data di pubblicazione|Editore|Publication date)\\s*:\\s*</b>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (datePatternAlt.find()) {
                        year = extractYearFromText(datePatternAlt.group(1) ?: "")
                    }
                }

                // Pagine
                val pageMatcher = Pattern.compile("(?:Copertina flessibile|Copertina rigida|Pagine|Length|Print length|N. pagine)\\s*:\\s*</span>\\s*<span>\\s*(\\d+)\\s*(?:pagine|pages)", Pattern.CASE_INSENSITIVE).matcher(html)
                if (pageMatcher.find()) {
                    pageCount = pageMatcher.group(1)?.toIntOrNull() ?: 0
                } else {
                    val pageMatcherAlt = Pattern.compile("<li>\\s*<b>\\s*(?:Copertina flessibile|Copertina rigida|Pagine|Length|Print length)\\s*:\\s*</b>\\s*(\\d+)\\s*(?:pagine|pages)", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (pageMatcherAlt.find()) {
                        pageCount = pageMatcherAlt.group(1)?.toIntOrNull() ?: 0
                    }
                }

                // ISBN-13
                val isbn13Matcher = Pattern.compile("ISBN-13\\s*:\\s*</span>\\s*<span>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                if (isbn13Matcher.find()) {
                    isbn13 = isbn13Matcher.group(1)?.replace("-", "")?.replace(" ", "")?.trim() ?: asin
                } else {
                    val isbn13Fallback = Pattern.compile("ISBN-13[^<]*</b>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (isbn13Fallback.find()) {
                        isbn13 = isbn13Fallback.group(1)?.replace("-", "")?.replace(" ", "")?.trim() ?: asin
                    }
                }

                var coverUrl: String? = null
                val ogImgMatcher = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"(.*?)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogImgMatcher.find()) {
                    coverUrl = ogImgMatcher.group(1)?.trim()
                }

                return@withContext LookupResult(
                    isbn = isbn13,
                    title = title,
                    author = author,
                    publisher = publisher,
                    publishedYear = year,
                    description = "Dettagli estratti da Amazon.it",
                    coverUrl = coverUrl,
                    pageCount = pageCount,
                    genre = "Narrativa / Saggistica",
                    sourceApi = "Amazon.it"
                )
            }
        } catch (e: Exception) {
            Log.d(tag, "Amazon Direct Detail Scraper error for $asin: ${e.message}")
        }
        null
    }

    /**
     * Arricchimento veloce che interroga SOLO Google Books (evitando il lentissimo Open Library)
     */
    private suspend fun enrichBookMetadataFast(
        result: LookupResult,
        fallbackAuthor: String = ""
    ): LookupResult = withContext(Dispatchers.IO) {
        var updatedAuthor = result.author.ifBlank { fallbackAuthor }
        var updatedPublisher = result.publisher
        var updatedYear = result.publishedYear
        var updatedPages = result.pageCount
        var updatedGenre = result.genre
        var updatedDescription = result.description
        var updatedIsbn = result.isbn

        if (updatedAuthor.isNotBlank() && updatedPublisher.isNotBlank() && updatedYear.isNotBlank() && updatedPages > 0) {
            return@withContext result.copy(author = updatedAuthor)
        }

        try {
            val q = if (updatedIsbn.isNotBlank()) "isbn:$updatedIsbn" else "${result.title} $updatedAuthor".trim()
            val url = "https://www.googleapis.com/books/v1/volumes?q=${java.net.URLEncoder.encode(q, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items")
                        if (items != null && items.length() > 0) {
                            val volInfo = items.getJSONObject(0).optJSONObject("volumeInfo")
                            if (volInfo != null) {
                                if (updatedAuthor.isBlank()) {
                                    val arr = volInfo.optJSONArray("authors")
                                    if (arr != null && arr.length() > 0) {
                                        updatedAuthor = cleanAuthor(arr.getString(0), fallbackAuthor)
                                    }
                                }
                                if (updatedPublisher.isBlank()) {
                                    updatedPublisher = cleanPublisher(volInfo.optString("publisher"))
                                }
                                if (updatedYear.isBlank()) {
                                    updatedYear = extractYearFromText(volInfo.optString("publishedDate"))
                                }
                                if (updatedPages == 0) {
                                    updatedPages = volInfo.optInt("pageCount", 0)
                                }
                                if (updatedGenre.isBlank() || updatedGenre == "Narrativa / Saggistica") {
                                    val cats = volInfo.optJSONArray("categories")
                                    if (cats != null && cats.length() > 0) {
                                        updatedGenre = cats.getString(0)
                                    }
                                }
                                if (updatedDescription.isBlank() || updatedDescription.startsWith("Scraping")) {
                                    val d = volInfo.optString("description")
                                    if (d.isNotBlank()) updatedDescription = d
                                }
                                if (updatedIsbn.isBlank()) {
                                    val indIds = volInfo.optJSONArray("industryIdentifiers")
                                    if (indIds != null) {
                                        for (idx in 0 until indIds.length()) {
                                            val obj = indIds.getJSONObject(idx)
                                            if (obj.optString("type").startsWith("ISBN")) {
                                                updatedIsbn = obj.optString("identifier")
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Fast enrichment Google Books error: ${e.message}")
        }

        result.copy(
            isbn = updatedIsbn,
            author = updatedAuthor,
            publisher = updatedPublisher,
            publishedYear = updatedYear,
            pageCount = updatedPages,
            genre = updatedGenre,
            description = updatedDescription
        )
    }

    /**
     * Ricerca tramite Apify Amazon Product Scraper API e parsing esplicito
     */
    private suspend fun queryApifyAmazon(
        query: String,
        fallbackTitle: String = "",
        fallbackAuthor: String = ""
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val apifyToken = System.getenv("APIFY_API_TOKEN") ?: ""
        if (apifyToken.isBlank()) return@withContext emptyList()

        // Added timeout=10 to the Apify run-sync endpoint to enforce a maximum wait time on the server-side
        val url = "https://api.apify.com/v2/acts/apify~amazon-product-scraper/run-sync-get-dataset-items?token=$apifyToken&timeout=10"
        val jsonBody = JSONObject().apply {
            put("searchKeywords", query)
            put("maxItems", 6)
            put("amazonDomain", "amazon.it")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .build()

        val list = mutableListOf<LookupResult>()
        try {
            // Using dedicated scraping client with independent, short connection/read timeouts
            scrapingOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr.isBlank()) return@withContext emptyList()

                val array = JSONArray(bodyStr)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val book = parseApifyItemToLookupResult(item, fallbackAuthor)
                    if (book != null) {
                        if (fallbackTitle.isNotBlank() && !isTitleMatch(fallbackTitle, book.title, fallbackAuthor, book.author)) continue
                        if (fallbackAuthor.isNotBlank() && book.author.isNotBlank() && !isAuthorMatch(fallbackAuthor, book.author)) continue
                        list.add(book)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Apify search error: ${e.message}")
        }
        list
    }

    /**
     * Parsing esplicito dell'oggetto JSON Apify in un LookupResult
     */
    private fun parseApifyItemToLookupResult(item: JSONObject, fallbackAuthor: String): LookupResult? {
        val rawTitle = item.optString("title").ifBlank { item.optString("name") }
        val title = cleanTitle(rawTitle)
        if (title.isBlank()) return null

        var rawAuthor = item.optString("author")
        if (rawAuthor.isBlank()) {
            val authorsArr = item.optJSONArray("authors")
            if (authorsArr != null && authorsArr.length() > 0) {
                rawAuthor = authorsArr.getString(0)
            }
        }
        val author = cleanAuthor(rawAuthor, fallbackAuthor)

        val publisher = extractPublisherFromJson(item)
        val publishedYear = extractYearFromJson(item)
        val pageCount = extractPageCountFromJson(item)

        val description = item.optString("description").ifBlank { item.optString("productDescription") }
        val coverUrl = item.optString("image").ifBlank { item.optString("imageUrl").ifBlank { item.optString("thumbnail") } }
        val genre = item.optString("genre").ifBlank { "Narrativa / Saggistica" }
        val isbn = item.optString("isbn").ifBlank { item.optString("asin").ifBlank { "" } }

        return LookupResult(
            isbn = isbn,
            title = title,
            author = author,
            publisher = publisher,
            publishedYear = publishedYear,
            description = description.ifBlank { "Estratto da Apify Amazon Scraper" },
            coverUrl = if (coverUrl.isBlank()) null else coverUrl,
            pageCount = pageCount,
            genre = genre,
            sourceApi = "Amazon.it"
        )
    }

    /**
     * Ricerca diretta HTML Web Scraping su Amazon.it
     */
    private suspend fun queryAmazonDirectSearch(
        query: String,
        fallbackTitle: String,
        fallbackAuthor: String
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.amazon.it/s?k=$encodedQuery&i=stripbooks"
        val results = mutableListOf<LookupResult>()

        Log.d(tag, "Amazon Direct Search started: $searchUrl")

        try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .build()

            // Using dedicated scraping OkHttpClient with customized timeouts (5s)
            scrapingOkHttpClient.newCall(request).execute().use { response ->
                Log.d(tag, "Amazon Direct Search HTTP Status Code: ${response.code}")
                Log.d(tag, "Amazon Direct Search Response Headers: ${response.headers}")

                if (!response.isSuccessful) {
                    Log.d(tag, "Amazon Direct Search HTTP error: Request was not successful")
                    return@use
                }

                val html = response.body?.string() ?: return@use
                Log.d(tag, "Amazon Direct Search HTML Body Length: ${html.length}")

                // Log raw HTML for debugging purposes (first 2500 characters)
                val debugHtml = if (html.length > 2500) html.substring(0, 2500) else html
                Log.d(tag, "Amazon Direct Search RAW HTML SAMPLE:\n$debugHtml")

                if (html.isBlank()) {
                    Log.d(tag, "Amazon Direct Search error: Received empty HTML response")
                    return@use
                }

                if (html.contains("robot check", ignoreCase = true) || html.contains("captcha", ignoreCase = true)) {
                    Log.d(tag, "Amazon Direct Search warning: Request was blocked by Amazon's bot detection captcha!")
                    return@use
                }

                // Split HTML by product card elements to parse details (title, author, publisher, cover) independently.
                // This is extremely robust against Amazon's modern DOM where the image appears BEFORE the title in the DOM source.
                val cards = html.split("data-asin=\"")
                Log.d(tag, "Amazon Direct Search: isolated ${cards.size - 1} product cards in HTML response")

                var count = 0
                for (idx in 1 until cards.size) {
                    if (count >= 12) break
                    val cardHtml = cards[idx]
                    
                    if (cardHtml.length < 10) continue
                    val asin = cardHtml.substring(0, 10)
                    if (!asin.matches(Regex("[A-Z0-9]{10}"))) continue
                    
                    // Limit the search of properties to the local card's block (first 4000 chars) to prevent bleeding into other cards
                    val cardContent = if (cardHtml.length > 4000) cardHtml.substring(0, 4000) else cardHtml
                    
                    // 1. Title Extraction: Target span containing title within search results cards
                    var title = ""
                    val titlePattern = Pattern.compile(
                        "<span[^>]*class=\"[^\"]*a-size-(?:medium|base-plus)[^\"]*a-text-normal\"[^>]*>(.*?)</span>",
                        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                    )
                    val titleMatcher = titlePattern.matcher(cardContent)
                    if (titleMatcher.find()) {
                        title = titleMatcher.group(1)?.trim() ?: ""
                    }
                    
                    if (title.isBlank()) {
                        val fallbackTitlePattern = Pattern.compile("<h2[^>]*>.*?<span[^>]*>(.*?)</span>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                        val fallbackTitleMatcher = fallbackTitlePattern.matcher(cardContent)
                        if (fallbackTitleMatcher.find()) {
                            title = fallbackTitleMatcher.group(1)?.trim() ?: ""
                        }
                    }
                    
                    title = cleanTitle(title)
                    if (title.isBlank()) continue
                    
                    // 2. Cover Image Extraction: Target img with class s-image
                    var coverUrl: String? = null
                    val imgPattern = Pattern.compile(
                        "<img[^>]*class=\"[^\"]*s-image[^\"]*\"[^>]*src=\"([^\"]+)\"",
                        Pattern.CASE_INSENSITIVE
                    )
                    val imgMatcher = imgPattern.matcher(cardContent)
                    if (imgMatcher.find()) {
                        coverUrl = imgMatcher.group(1)?.trim()
                    }
                    
                    // 3. Author Extraction: Parse any standard Amazon author hyperlink or plain text credit
                    var author = fallbackAuthor
                    val authorPattern = Pattern.compile(
                        "href=\"/[^\"]*/e/B[A-Z0-9]{9}[^\"]*\"[^>]*>([^<]+)</a>",
                        Pattern.CASE_INSENSITIVE
                    )
                    val authorMatcher = authorPattern.matcher(cardContent)
                    if (authorMatcher.find()) {
                        author = cleanAuthor(authorMatcher.group(1)?.trim() ?: "", fallbackAuthor)
                    } else {
                        val authorTextPattern = Pattern.compile(
                            "(?:di|by)\\s+<a[^>]*class=\"[^\"]*a-size-base[^\"]*\"[^>]*>([^<]+)</a>",
                            Pattern.CASE_INSENSITIVE
                        )
                        val authorTextMatcher = authorTextPattern.matcher(cardContent)
                        if (authorTextMatcher.find()) {
                            author = cleanAuthor(authorTextMatcher.group(1)?.trim() ?: "", fallbackAuthor)
                        }
                    }

                    if (fallbackTitle.isNotBlank() && !isTitleMatch(fallbackTitle, title, fallbackAuthor, author)) {
                        continue
                    }
                    if (fallbackAuthor.isNotBlank() && author.isNotBlank() && !isAuthorMatch(fallbackAuthor, author)) {
                        continue
                    }
                    
                    var amazonYear = ""
                    val yearMatcher = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(cardContent)
                    if (yearMatcher.find()) {
                        amazonYear = yearMatcher.group(1) ?: ""
                    }

                    var amazonPublisher = ""
                    val pubMatcher = Pattern.compile("(?:Editore|Publisher):?\\s*(?:<[^>]+>)*\\s*([^<|•\n\r]+)", Pattern.CASE_INSENSITIVE).matcher(cardContent)
                    if (pubMatcher.find()) {
                        amazonPublisher = cleanPublisher(pubMatcher.group(1) ?: "")
                    }

                    Log.d(tag, "Amazon Direct Search Match found: ASIN=$asin, Title=$title, Author=$author, Publisher=$amazonPublisher, Year=$amazonYear, Cover=$coverUrl")

                    results.add(
                        LookupResult(
                            isbn = asin,
                            title = title,
                            author = author,
                            publisher = amazonPublisher,
                            publishedYear = amazonYear,
                            description = "Scraping Amazon.it Web",
                            coverUrl = coverUrl,
                            pageCount = 0,
                            genre = "Narrativa / Saggistica",
                            sourceApi = "Amazon.it"
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Amazon Direct Search error: ${e.message}")
        }
        results
    }

    /**
     * Ricerca tramite Google Books API
     */
    private suspend fun queryGoogleBooksSearch(
        query: String,
        fallbackTitle: String = "",
        fallbackAuthor: String = ""
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LookupResult>()
        try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=${java.net.URLEncoder.encode(query, "UTF-8")}&maxResults=20"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return@withContext emptyList()

                for (i in 0 until items.length()) {
                    val volInfo = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
                    val rawTitle = volInfo.optString("title")
                    val title = cleanTitle(rawTitle)
                    if (title.isBlank()) continue

                    var author = ""
                    val authorsArr = volInfo.optJSONArray("authors")
                    if (authorsArr != null && authorsArr.length() > 0) {
                        val authorList = mutableListOf<String>()
                        for (aIdx in 0 until authorsArr.length()) {
                            authorList.add(authorsArr.getString(aIdx))
                        }
                        author = authorList.joinToString(", ")
                    }
                    author = cleanAuthor(author, fallbackAuthor)

                    if (fallbackTitle.isNotBlank() && !isTitleMatch(fallbackTitle, title, fallbackAuthor, author)) continue
                    if (fallbackAuthor.isNotBlank() && author.isNotBlank() && !isAuthorMatch(fallbackAuthor, author)) continue

                    val publisher = cleanPublisher(volInfo.optString("publisher"))
                    val publishedYear = extractYearFromText(volInfo.optString("publishedDate"))
                    val pageCount = volInfo.optInt("pageCount", 0)
                    val description = volInfo.optString("description")

                    var coverUrl: String? = null
                    val imageLinks = volInfo.optJSONObject("imageLinks")
                    if (imageLinks != null) {
                        coverUrl = imageLinks.optString("thumbnail").ifBlank { imageLinks.optString("smallThumbnail") }
                        if (coverUrl.isNotBlank() && coverUrl.startsWith("http:")) {
                            coverUrl = coverUrl.replace("http:", "https:")
                        }
                    }

                    var genre = "Narrativa / Saggistica"
                    val categories = volInfo.optJSONArray("categories")
                    if (categories != null && categories.length() > 0) {
                        genre = categories.getString(0)
                    }

                    var isbn = ""
                    val industryIds = volInfo.optJSONArray("industryIdentifiers")
                    if (industryIds != null) {
                        for (j in 0 until industryIds.length()) {
                            val idObj = industryIds.getJSONObject(j)
                            val type = idObj.optString("type")
                            if (type == "ISBN_13" || type == "ISBN_10") {
                                isbn = idObj.optString("identifier")
                                break
                            }
                        }
                    }

                    list.add(
                        LookupResult(
                            isbn = isbn,
                            title = title,
                            author = author,
                            publisher = publisher,
                            publishedYear = publishedYear,
                            description = description.ifBlank { "Trovato su Google Books" },
                            coverUrl = if (coverUrl.isNullOrBlank()) null else coverUrl,
                            pageCount = pageCount,
                            genre = genre,
                            sourceApi = "Google Books"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Google Books Search error: ${e.message}")
        }
        list
    }

    /**
     * Ricerca tramite Open Library API
     */
    private suspend fun queryOpenLibrarySearch(
        query: String,
        fallbackTitle: String = "",
        fallbackAuthor: String = ""
    ): List<LookupResult> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LookupResult>()
        try {
            val url = "https://openlibrary.org/search.json?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=15"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val docs = json.optJSONArray("docs") ?: return@withContext emptyList()

                for (i in 0 until docs.length()) {
                    val doc = docs.getJSONObject(i)
                    val rawTitle = doc.optString("title")
                    val title = cleanTitle(rawTitle)
                    if (title.isBlank()) continue

                    var author = ""
                    val authorNames = doc.optJSONArray("author_name")
                    if (authorNames != null && authorNames.length() > 0) {
                        author = authorNames.getString(0)
                    }
                    author = cleanAuthor(author, fallbackAuthor)

                    if (fallbackTitle.isNotBlank() && !isTitleMatch(fallbackTitle, title, fallbackAuthor, author)) continue
                    if (fallbackAuthor.isNotBlank() && author.isNotBlank() && !isAuthorMatch(fallbackAuthor, author)) continue

                    var publisher = ""
                    val publishers = doc.optJSONArray("publisher")
                    if (publishers != null && publishers.length() > 0) {
                        publisher = cleanPublisher(publishers.getString(0))
                    }

                    var publishedYear = ""
                    val publishDates = doc.optJSONArray("publish_date")
                    if (publishDates != null && publishDates.length() > 0) {
                        publishedYear = extractYearFromText(publishDates.getString(0))
                    }
                    if (publishedYear.isBlank()) {
                        val firstY = doc.optInt("first_publish_year", 0)
                        if (firstY in 1800..2026) publishedYear = firstY.toString()
                    }

                    val pageCount = doc.optInt("number_of_pages_median", 0)
                    val coverI = doc.optInt("cover_i", 0)
                    val coverUrl = if (coverI > 0) "https://covers.openlibrary.org/b/id/$coverI-M.jpg" else null

                    val isbns = doc.optJSONArray("isbn")
                    val isbnStr = if (isbns != null && isbns.length() > 0) isbns.getString(0) else ""

                    list.add(
                        LookupResult(
                            isbn = isbnStr,
                            title = title,
                            author = author,
                            publisher = publisher,
                            publishedYear = publishedYear,
                            description = "Trovato su Open Library",
                            coverUrl = coverUrl,
                            pageCount = pageCount,
                            genre = "Narrativa / Saggistica",
                            sourceApi = "Open Library"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Open Library Search error: ${e.message}")
        }
        list
    }

    /**
     * Arricchisce e mappa i campi mancanti del libro (Autore, Editore, Anno e Pagine) interrogando Google Books & Open Library
     */
    suspend fun enrichBookMetadata(
        result: LookupResult,
        fallbackAuthor: String = ""
    ): LookupResult = withContext(Dispatchers.IO) {
        var updatedAuthor = result.author.ifBlank { fallbackAuthor }
        var updatedPublisher = result.publisher
        var updatedYear = result.publishedYear
        var updatedPages = result.pageCount
        var updatedGenre = result.genre
        var updatedDescription = result.description
        var updatedIsbn = result.isbn

        if (updatedAuthor.isNotBlank() && updatedPublisher.isNotBlank() && updatedYear.isNotBlank() && updatedPages > 0) {
            return@withContext result.copy(author = updatedAuthor)
        }

        // Interroga Google Books per completare i campi
        try {
            val q = if (updatedIsbn.isNotBlank()) "isbn:$updatedIsbn" else "${result.title} $updatedAuthor".trim()
            val url = "https://www.googleapis.com/books/v1/volumes?q=${java.net.URLEncoder.encode(q, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items")
                        if (items != null && items.length() > 0) {
                            val volInfo = items.getJSONObject(0).optJSONObject("volumeInfo")
                            if (volInfo != null) {
                                if (updatedAuthor.isBlank()) {
                                    val arr = volInfo.optJSONArray("authors")
                                    if (arr != null && arr.length() > 0) {
                                        updatedAuthor = cleanAuthor(arr.getString(0), fallbackAuthor)
                                    }
                                }
                                if (updatedPublisher.isBlank()) {
                                    updatedPublisher = cleanPublisher(volInfo.optString("publisher"))
                                }
                                if (updatedYear.isBlank()) {
                                    updatedYear = extractYearFromText(volInfo.optString("publishedDate"))
                                }
                                if (updatedPages == 0) {
                                    updatedPages = volInfo.optInt("pageCount", 0)
                                }
                                if (updatedGenre.isBlank() || updatedGenre == "Narrativa / Saggistica") {
                                    val cats = volInfo.optJSONArray("categories")
                                    if (cats != null && cats.length() > 0) {
                                        updatedGenre = cats.getString(0)
                                    }
                                }
                                if (updatedDescription.isBlank() || updatedDescription.startsWith("Scraping")) {
                                    val d = volInfo.optString("description")
                                    if (d.isNotBlank()) updatedDescription = d
                                }
                                if (updatedIsbn.isBlank()) {
                                    val indIds = volInfo.optJSONArray("industryIdentifiers")
                                    if (indIds != null) {
                                        for (idx in 0 until indIds.length()) {
                                            val obj = indIds.getJSONObject(idx)
                                            if (obj.optString("type").startsWith("ISBN")) {
                                                updatedIsbn = obj.optString("identifier")
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Enrichment Google Books error: ${e.message}")
        }

        // Interroga Open Library se mancano ancora editore, anno o pagine
        if (updatedAuthor.isBlank() || updatedPublisher.isBlank() || updatedYear.isBlank() || updatedPages == 0) {
            try {
                val olUrl = "https://openlibrary.org/search.json?q=${java.net.URLEncoder.encode("${result.title} $updatedAuthor", "UTF-8")}&limit=1"
                val request = Request.Builder().url(olUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.startsWith("{")) {
                            val json = JSONObject(body)
                            val docs = json.optJSONArray("docs")
                            if (docs != null && docs.length() > 0) {
                                val doc = docs.getJSONObject(0)
                                if (updatedAuthor.isBlank()) {
                                    val aArr = doc.optJSONArray("author_name")
                                    if (aArr != null && aArr.length() > 0) {
                                        updatedAuthor = cleanAuthor(aArr.getString(0), fallbackAuthor)
                                    }
                                }
                                if (updatedPublisher.isBlank()) {
                                    val pArr = doc.optJSONArray("publisher")
                                    if (pArr != null && pArr.length() > 0) {
                                        updatedPublisher = cleanPublisher(pArr.getString(0))
                                    }
                                }
                                if (updatedYear.isBlank()) {
                                    val pDates = doc.optJSONArray("publish_date")
                                    if (pDates != null && pDates.length() > 0) {
                                        updatedYear = extractYearFromText(pDates.getString(0))
                                    } else {
                                        val firstY = doc.optInt("first_publish_year", 0)
                                        if (firstY in 1800..2026) updatedYear = firstY.toString()
                                    }
                                }
                                if (updatedPages == 0) {
                                    updatedPages = doc.optInt("number_of_pages_median", 0)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "Enrichment Open Library error: ${e.message}")
            }
        }

        result.copy(
            isbn = updatedIsbn,
            author = updatedAuthor,
            publisher = updatedPublisher,
            publishedYear = updatedYear,
            pageCount = updatedPages,
            genre = updatedGenre,
            description = updatedDescription
        )
    }

    private fun extractPublisherFromJson(item: JSONObject): String {
        val pub = item.optString("publisher")
        if (pub.isNotBlank()) return cleanPublisher(pub)

        val pdArr = item.optJSONArray("productDetails")
        if (pdArr != null) {
            for (i in 0 until pdArr.length()) {
                val obj = pdArr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val value = obj.optString("value")
                if (name.contains("Editore", ignoreCase = true) || name.contains("Publisher", ignoreCase = true)) {
                    return cleanPublisher(value)
                }
            }
        }

        val specs = item.optJSONArray("specifications")
        if (specs != null) {
            for (i in 0 until specs.length()) {
                val obj = specs.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val value = obj.optString("value")
                if (name.contains("Editore", ignoreCase = true) || name.contains("Publisher", ignoreCase = true)) {
                    return cleanPublisher(value)
                }
            }
        }
        return ""
    }

    private fun extractYearFromJson(item: JSONObject): String {
        val dateStr = item.optString("publishedDate")
            .ifBlank { item.optString("publicationDate") }
            .ifBlank { item.optString("releaseDate") }

        var year = extractYearFromText(dateStr)
        if (year.isNotBlank()) return year

        val pdArr = item.optJSONArray("productDetails")
        if (pdArr != null) {
            for (i in 0 until pdArr.length()) {
                val obj = pdArr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val value = obj.optString("value")
                if (name.contains("pubblicazione", ignoreCase = true) || name.contains("Editore", ignoreCase = true)) {
                    year = extractYearFromText(value)
                    if (year.isNotBlank()) return year
                }
            }
        }
        return ""
    }

    private fun extractPageCountFromJson(item: JSONObject): Int {
        val p = item.optInt("pageCount", item.optInt("pages", item.optInt("numberOfPages", 0)))
        if (p > 0) return p

        val pdArr = item.optJSONArray("productDetails")
        if (pdArr != null) {
            for (i in 0 until pdArr.length()) {
                val obj = pdArr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val value = obj.optString("value")
                if (name.contains("pagine", ignoreCase = true) || value.contains("pagine", ignoreCase = true) || name.contains("Copertina", ignoreCase = true)) {
                    val match = Pattern.compile("(\\d+)\\s*pagine", Pattern.CASE_INSENSITIVE).matcher("$name $value")
                    if (match.find()) {
                        return match.group(1)?.toIntOrNull() ?: 0
                    }
                }
            }
        }
        return 0
    }

    private fun cleanTitle(rawTitle: String, fallback: String = ""): String {
        if (rawTitle.isBlank()) return fallback
        var title = rawTitle
            .replace(Regex("(?i):\\s*Copertina\\s+(?:flessibile|rigida)"), "")
            .replace(Regex("(?i)\\[Copertina\\s+(?:flessibile|rigida)\\]"), "")
            .replace(Regex("(?i)\\bCopertina\\s+(?:flessibile|rigida)\\b"), "")
            .replace(Regex("(?i)\\bFormato\\s+Kindle\\b"), "")
            .replace(Regex("(?i):\\s*Amazon\\.it:\\s*Libri"), "")
            .replace(Regex("(?i)Amazon\\.it"), "")
            .replace(Regex("<.*?>"), "")
            .replace(Regex("^[:\\s\\-\\–\\—]+"), "")
            .replace(Regex("[:\\s\\-\\–\\—]+$"), "")
            .trim()

        if (title.isBlank() || title.equals("Copertina flessibile", ignoreCase = true) || title.equals("Copertina rigida", ignoreCase = true)) {
            return fallback
        }
        return title
    }

    private fun cleanAuthor(rawAuthor: String, fallbackAuthor: String = ""): String {
        if (rawAuthor.isBlank()) return fallbackAuthor.trim()
        var author = rawAuthor
            .replace(Regex("<.*?>"), "")
            .replace(Regex("(?i)^di\\s+"), "")
            .replace(Regex("(?i)^by\\s+"), "")
            .replace(Regex("(?i)\\s*\\(Autore\\)"), "")
            .replace(Regex("(?i)\\s*\\(A cura di\\)"), "")
            .replace(Regex("(?i)\\bCopertina\\s+(?:flessibile|rigida)\\b"), "")
            .trim()

        if (author.isBlank() ||
            author.equals("Autore Amazon", ignoreCase = true) ||
            author.equals("Amazon", ignoreCase = true) ||
            author.equals("Edizioni Amazon", ignoreCase = true)) {
            return fallbackAuthor.trim()
        }
        return author
    }

    private fun cleanPublisher(rawPublisher: String): String {
        if (rawPublisher.isBlank()) return ""
        var publisher = rawPublisher
            .replace(Regex("<.*?>"), "")
            .replace(Regex("(?i)^Editore\\s*:\\s*"), "")
            .replace(Regex("(?i)\\s*;.*$"), "")
            .replace(Regex("\\s*\\(\\d{1,2}\\s+[a-zA-Zà-ùÀ-Ù]+\\s+\\d{4}\\).*$"), "")
            .replace(Regex("\\s*\\(\\d{4}\\).*$"), "")
            .trim()

        if (publisher.equals("Edizioni Amazon", ignoreCase = true) ||
            publisher.equals("Amazon", ignoreCase = true) ||
            publisher.equals("Edizione Commerciale Amazon", ignoreCase = true)) {
            return ""
        }
        return publisher
    }

    private fun extractYearFromText(text: String): String {
        if (text.isBlank()) return ""
        val pattern = Pattern.compile("(?:\\b|\\()([12]\\d{3})(?:\\b|\\))")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group(1) ?: continue
            val y = candidate.toIntOrNull() ?: continue
            if (y in 1800..2026 && y != 2000) {
                return y.toString()
            }
        }
        return ""
    }

    companion object {
        val NON_BOOK_KEYWORDS = listOf(
            "disco freno", "freno a disco", "brembo", "pastiglie freno", "pastiglia freno",
            "filtro olio", "filtro aria", "ammortizzatore", "tergicristallo", "lampadina",
            "pneumatico", "cinghia distribuzione", "batteria auto", "candela accensione",
            "pinza freno", "ricambi auto", "pompa acqua", "radiatore", "bullone ruota",
            "sensore abs", "dischi freno"
        )

        fun isLikelyBook(result: LookupResult): Boolean {
            val titleLower = result.title.lowercase()
            for (kw in NON_BOOK_KEYWORDS) {
                if (titleLower.contains(kw)) return false
            }
            return true
        }

        private val STOP_WORDS = setOf(
            "il", "lo", "la", "i", "gli", "le", "l",
            "un", "uno", "una", "un'",
            "the", "a", "an",
            "di", "del", "della", "dello", "dei", "degli", "delle", "d",
            "of", "in", "con", "su", "per", "tra", "fra", "da", "dal", "dalla", "dallo", "dai", "dagli", "dalle",
            "e", "ed", "and", "to", "at", "by", "for", "with", "about",
            "o", "od", "or"
        )

        private val EDITION_WORDS = setOf(
            "edizione", "ediz", "edizioni", "edition", "editions", "ed", "eds",
            "nuova", "nuove", "nuovo", "nuovi", "new",
            "speciale", "speciali", "special",
            "integrale", "integrali", "integral",
            "illustrata", "illustrate", "illustrati", "illustrato", "illustrated",
            "annotata", "annotato", "annotated",
            "commentata", "commentato", "commentary",
            "completa", "completo", "complete",
            "tascabile", "tascabili", "pocket",
            "oscar", "classici", "classico", "classic", "classics", "grandi",
            "collana", "bompiani", "mondadori", "feltrinelli", "adelphi", "einaudi", "newton", "compton", "rizzoli", "bur", "sellerio", "garzanti", "laterza", "tea", "giunti", "salani", "fabbri", "harpercollins", "penguin", "vintage",
            "vol", "volume", "volumi", "tomo", "tomi", "libro", "libri", "book", "books",
            "copertina", "flessibile", "rigida", "hardcover", "paperback", "kindle", "ebook", "audiobook",
            "anniversario", "anniversary",
            "version", "versione", "versioni",
            "serie", "saga", "collection", "collezione",
            "bestseller", "romanzo", "novel", "novels", "racconti", "stories",
            "testo", "fronte", "italiano", "italiana", "english"
        )

        fun normalizeText(text: String): String {
            if (text.isBlank()) return ""
            val temp = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            val withoutDiacritics = Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "")
            return withoutDiacritics
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun extractTokens(text: String, filterStopWords: Boolean = true): List<String> {
            val normalized = normalizeText(text)
            if (normalized.isBlank()) return emptyList()
            val allTokens = normalized.split(" ").filter { it.isNotBlank() }
            if (!filterStopWords) return allTokens
            val significant = allTokens.filter { it !in STOP_WORDS }
            return if (significant.isNotEmpty()) significant else allTokens
        }

        fun levenshteinDistance(s1: String, s2: String): Int {
            if (s1 == s2) return 0
            if (s1.isEmpty()) return s2.length
            if (s2.isEmpty()) return s1.length

            val d = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) d[i][0] = i
            for (j in 0..s2.length) d[0][j] = j

            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    d[i][j] = minOf(
                        d[i - 1][j] + 1,
                        d[i][j - 1] + 1,
                        d[i - 1][j - 1] + cost
                    )
                }
            }
            return d[s1.length][s2.length]
        }

        fun wordMatches(w1: String, w2: String): Boolean {
            if (w1 == w2) return true
            if (w1.length >= 4 && w2.length >= 4 && (w1.startsWith(w2) || w2.startsWith(w1))) return true
            val maxLen = maxOf(w1.length, w2.length)
            val maxDist = when {
                maxLen <= 3 -> 0
                maxLen <= 6 -> 1
                else -> 2
            }
            return levenshteinDistance(w1, w2) <= maxDist
        }

        /**
         * Verifica rigorosa e intelligente che il titolo del candidato corrisponda al titolo cercato.
         * Esclude edizioni o libri con titoli differenti (es. altri libri dello stesso autore).
         */
        fun isTitleMatch(
            searchedTitle: String,
            candidateTitle: String,
            searchedAuthor: String = "",
            candidateAuthor: String = ""
        ): Boolean {
            val cleanSearched = searchedTitle.trim()
            if (cleanSearched.isBlank()) return true // Nessun titolo specificato nella ricerca
            
            val cleanCandidate = candidateTitle.trim()
            if (cleanCandidate.isBlank()) return false

            val normSearched = normalizeText(cleanSearched)
            val normCandidate = normalizeText(cleanCandidate)

            // 1. Controllo corrispondenza diretta normalizzata esatta
            if (normCandidate == normSearched) return true

            val searchedTokens = extractTokens(cleanSearched, filterStopWords = true)
            if (searchedTokens.isEmpty()) return true

            val candidateAllTokens = extractTokens(cleanCandidate, filterStopWords = false)

            // 2. Tutti i token significativi del titolo cercato devono essere presenti nel candidato
            var matchedSearchedCount = 0
            for (sToken in searchedTokens) {
                val found = candidateAllTokens.any { cToken -> wordMatches(sToken, cToken) }
                if (found) {
                    matchedSearchedCount++
                }
            }

            // Tolleranza: se il titolo cercato ha molte parole (>= 4), permettiamo al massimo 1 parola secondaria mancante
            val requiredMatches = if (searchedTokens.size >= 4) searchedTokens.size - 1 else searchedTokens.size
            if (matchedSearchedCount < requiredMatches) {
                return false
            }

            // 3. Estrazione del titolo principale prima di sottotitoli o note di collana (:, -, (, [, ., /)
            val mainPart = cleanCandidate.split(Regex("[:\\-\\–\\—\\(\\[\\./]"))[0].trim()
            val mainTokens = extractTokens(mainPart, filterStopWords = true)

            // Token associati all'autore cercato o al candidato (nel caso il titolo includa "di Autore")
            val authorTokens = (extractTokens(searchedAuthor, filterStopWords = true) +
                                extractTokens(candidateAuthor, filterStopWords = true)).toSet()

            // 4. Se il titolo principale contiene parole extra prima del sottotitolo,
            // verifichiamo che siano termini di edizione, numeri o parte dell'autore (e NON un libro differente)
            var unexpectedExtraWords = 0
            for (mToken in mainTokens) {
                val matchesSearched = searchedTokens.any { sToken -> wordMatches(mToken, sToken) }
                val isEditionWord = mToken in EDITION_WORDS || mToken.toIntOrNull() != null
                val isAuthorWord = authorTokens.any { aToken -> wordMatches(mToken, aToken) }

                if (!matchesSearched && !isEditionWord && !isAuthorWord) {
                    unexpectedExtraWords++
                }
            }

            // Se ci sono parole non collegate nel titolo principale, è un libro diverso (es. "I figli di Dune" per "Dune")
            if (unexpectedExtraWords > 1 || (searchedTokens.size <= 2 && unexpectedExtraWords > 0)) {
                return false
            }

            return true
        }

        /**
         * Verifica che l'autore del candidato sia compatibile con l'autore cercato.
         */
        fun isAuthorMatch(searchedAuthor: String, candidateAuthor: String): Boolean {
            val cleanSearched = searchedAuthor.trim()
            val cleanCandidate = candidateAuthor.trim()
            if (cleanSearched.isBlank() || cleanCandidate.isBlank()) return true

            val sTokens = extractTokens(cleanSearched, filterStopWords = true)
            val cTokens = extractTokens(cleanCandidate, filterStopWords = true)
            if (sTokens.isEmpty() || cTokens.isEmpty()) return true

            // Almeno una parola significativa dell'autore (es. cognome) deve corrispondere
            return sTokens.any { sToken ->
                cTokens.any { cToken -> wordMatches(sToken, cToken) }
            }
        }
    }
}
