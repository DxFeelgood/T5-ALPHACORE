package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Servizio per la risoluzione e catalogazione dei libri tramite Web Scraping:
 * 1. Amazon.it (Apify Amazon Scraper & Scraper Diretto Amazon.it)
 * 2. Libraccio.it (Catalogo online & JSON-LD metadata)
 * 3. IBS.it (Catalogo IBS & dataLayer)
 */
class BookLookupService(
    private val okHttpClient: OkHttpClient = NetworkClient.okHttpClient,
    private val searchService: BookSearchService = BookSearchService(okHttpClient)
) {

    private val tag = "BookLookupService"

    /**
     * Esegue la ricerca a cascata basata su ISBN:
     * 1. IBS.it
     * 2. Libraccio.it
     */
    suspend fun lookupBookCascade(
        isbn: String,
        publisherFilter: String = "",
        yearFilter: String = "",
        onStepUpdate: (CascadeStepStatus) -> Unit = {}
    ): LookupResult? = withContext(Dispatchers.IO) {
        val cleanIsbn = isbn.replace("-", "").replace(" ", "").trim()
        if (cleanIsbn.isBlank()) {
            return@withContext null
        }

        // --- 1. IBS.it ---
        try {
            val ibsResult = queryIbsByIsbn(cleanIsbn, publisherFilter, yearFilter)
            if (ibsResult != null && BookSearchService.isLikelyBook(ibsResult)) {
                val enriched = enrichMetadata(ibsResult)
                if (matchesFilters(enriched, publisherFilter, yearFilter)) {
                    onStepUpdate(
                        CascadeStepStatus(
                            source = ApiSource.IBS_IT,
                            success = true,
                            message = "Trovato su IBS.it"
                        )
                    )
                    return@withContext enriched
                }
            }
            onStepUpdate(
                CascadeStepStatus(
                    source = ApiSource.IBS_IT,
                    success = false,
                    message = "Passo a Libraccio.it..."
                )
            )
        } catch (e: Exception) {
            Log.w(tag, "Errore IBS.it ISBN: ${e.message}")
        }

        // --- 2. Libraccio.it ---
        try {
            val libraccioResult = queryLibraccioByIsbn(cleanIsbn, publisherFilter, yearFilter)
            if (libraccioResult != null && BookSearchService.isLikelyBook(libraccioResult)) {
                val enriched = enrichMetadata(libraccioResult)
                if (matchesFilters(enriched, publisherFilter, yearFilter)) {
                    onStepUpdate(
                        CascadeStepStatus(
                            source = ApiSource.LIBRACCIO_IT,
                            success = true,
                            message = "Trovato su Libraccio.it"
                        )
                    )
                    return@withContext enriched
                }
            }
            onStepUpdate(
                CascadeStepStatus(
                    source = ApiSource.LIBRACCIO_IT,
                    success = false,
                    message = "Libro non trovato su IBS.it né Libraccio.it"
                )
            )
        } catch (e: Exception) {
            Log.w(tag, "Errore Libraccio.it ISBN: ${e.message}")
        }

        null
    }

    /**
     * Cerca le edizioni di un libro per Titolo ed Autore tramite il BookSearchService dedicato
     */
    suspend fun searchEditionsByTitleAndAuthor(
        title: String,
        author: String
    ): List<LookupResult> {
        return searchService.searchBooksByTitleAndAuthor(title, author)
    }

    /**
     * Ricerca tramite Google Books API per ISBN
     */
    private suspend fun queryGoogleBooksByIsbn(isbn: String): LookupResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return@withContext null
                if (items.length() == 0) return@withContext null

                val volInfo = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return@withContext null
                return@withContext parseGoogleBooksVolumeInfo(isbn, volInfo)
            }
        } catch (e: Exception) {
            Log.d(tag, "Google Books ISBN error: ${e.message}")
        }
        null
    }

    /**
     * Ricerca lista edizioni su Google Books API
     */
    private suspend fun queryGoogleBooksList(query: String): List<LookupResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LookupResult>()
        try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=${java.net.URLEncoder.encode(query, "UTF-8")}&maxResults=5"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return@withContext emptyList()

                for (i in 0 until items.length()) {
                    val itemObj = items.getJSONObject(i)
                    val volInfo = itemObj.optJSONObject("volumeInfo") ?: continue
                    val idStr = itemObj.optString("id").ifBlank { "GB-$i" }
                    val res = parseGoogleBooksVolumeInfo(idStr, volInfo)
                    if (res != null) results.add(res)
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Google Books List error: ${e.message}")
        }
        results
    }

    private fun parseGoogleBooksVolumeInfo(isbn: String, volInfo: JSONObject): LookupResult? {
        val rawTitle = volInfo.optString("title")
        val title = cleanTitle(rawTitle)
        if (title.isBlank()) return null

        var author = ""
        val authorsArr = volInfo.optJSONArray("authors")
        if (authorsArr != null && authorsArr.length() > 0) {
            val list = mutableListOf<String>()
            for (i in 0 until authorsArr.length()) {
                list.add(authorsArr.getString(i))
            }
            author = list.joinToString(", ")
        }
        author = cleanAuthor(author)

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

        return LookupResult(
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
    }

    /**
     * Ricerca su Open Library per ISBN
     */
    private suspend fun queryOpenLibraryByIsbn(isbn: String): LookupResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                if (!body.contains("ISBN:")) return@withContext null

                val json = JSONObject(body)
                val bookObj = json.optJSONObject("ISBN:$isbn") ?: return@withContext null

                val title = cleanTitle(bookObj.optString("title"))
                if (title.isBlank()) return@withContext null

                var author = ""
                val authors = bookObj.optJSONArray("authors")
                if (authors != null && authors.length() > 0) {
                    author = authors.getJSONObject(0).optString("name")
                }

                var publisher = ""
                val publishers = bookObj.optJSONArray("publishers")
                if (publishers != null && publishers.length() > 0) {
                    publisher = cleanPublisher(publishers.getJSONObject(0).optString("name"))
                }

                val year = extractYearFromText(bookObj.optString("publish_date"))
                val pageCount = bookObj.optInt("number_of_pages", 0)

                var coverUrl: String? = null
                val coverObj = bookObj.optJSONObject("cover")
                if (coverObj != null) {
                    coverUrl = coverObj.optString("large").ifBlank { coverObj.optString("medium") }
                }

                return@withContext LookupResult(
                    isbn = isbn,
                    title = title,
                    author = cleanAuthor(author),
                    publisher = publisher,
                    publishedYear = year,
                    description = "Trovato su Open Library",
                    coverUrl = if (coverUrl.isNullOrBlank()) null else coverUrl,
                    pageCount = pageCount,
                    genre = "Narrativa / Saggistica",
                    sourceApi = "Open Library"
                )
            }
        } catch (e: Exception) {
            Log.d(tag, "Open Library ISBN error: ${e.message}")
        }
        null
    }

    /**
     * Ricerca lista edizioni su Open Library per parole chiave
     */
    private suspend fun queryOpenLibraryList(query: String): List<LookupResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LookupResult>()
        try {
            val url = "https://openlibrary.org/search.json?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=5"
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

                    var publisher = ""
                    val publishers = doc.optJSONArray("publisher")
                    if (publishers != null && publishers.length() > 0) {
                        publisher = publishers.getString(0)
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
                    val isbnStr = if (isbns != null && isbns.length() > 0) isbns.getString(0) else "OL-$i"

                    results.add(
                        LookupResult(
                            isbn = isbnStr,
                            title = title,
                            author = cleanAuthor(author),
                            publisher = cleanPublisher(publisher),
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
            Log.d(tag, "Open Library List error: ${e.message}")
        }
        results
    }

    /**
     * Ricerca tramite Apify Amazon Product Scraper API per ISBN
     */
    private suspend fun queryApifyAmazonScraper(
        isbn: String,
        publisherFilter: String,
        yearFilter: String
    ): LookupResult? = withContext(Dispatchers.IO) {
        val apifyToken = System.getenv("APIFY_API_TOKEN") ?: ""
        if (apifyToken.isBlank()) return@withContext null

        val baseUrl = "https://api.apify.com/v2/acts/apify~amazon-product-scraper/run-sync-get-dataset-items"
        val url = "$baseUrl?token=$apifyToken"

        val jsonBody = JSONObject().apply {
            put("searchKeywords", isbn)
            put("maxItems", 1)
            put("amazonDomain", "amazon.it")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBodyStr = response.body?.string() ?: return@withContext null
                if (responseBodyStr.isBlank()) return@withContext null

                val jsonArray = JSONArray(responseBodyStr)
                if (jsonArray.length() == 0) return@withContext null

                val item = jsonArray.getJSONObject(0)

                val rawTitle = item.optString("title").ifBlank { item.optString("name") }
                val title = cleanTitle(rawTitle)
                if (title.isBlank()) return@withContext null

                var rawAuthor = item.optString("author")
                if (rawAuthor.isBlank()) {
                    val authorsArr = item.optJSONArray("authors")
                    if (authorsArr != null && authorsArr.length() > 0) {
                        rawAuthor = authorsArr.getString(0)
                    }
                }
                val author = cleanAuthor(rawAuthor)

                val publisher = extractApifyPublisher(item)
                val year = extractApifyYear(item)
                val pageCount = extractApifyPages(item)

                val description = item.optString("description").ifBlank { item.optString("productDescription") }
                val coverUrl = item.optString("image").ifBlank { item.optString("imageUrl").ifBlank { item.optString("thumbnail") } }
                val genre = item.optString("genre").ifBlank { "Narrativa / Saggistica" }

                return@withContext LookupResult(
                    isbn = isbn,
                    title = title,
                    author = author,
                    publisher = publisher,
                    publishedYear = year,
                    description = description.ifBlank { "Estratto da Amazon.it" },
                    coverUrl = if (coverUrl.isBlank()) null else coverUrl,
                    pageCount = pageCount,
                    genre = genre,
                    sourceApi = ApiSource.AMAZON_IT.displayName
                )
            }
        } catch (e: Exception) {
            Log.d(tag, "Eccezione durante Apify API: ${e.message}")
        }
        null
    }

    /**
     * Ricerca diretta Web Scraping su Amazon.it
     */
    private suspend fun queryAmazonDirectScraper(
        isbn: String,
        publisherFilter: String,
        yearFilter: String
    ): LookupResult? = withContext(Dispatchers.IO) {
        val urlsToTry = listOf(
            "https://www.amazon.it/dp/$isbn",
            "https://www.amazon.it/s?k=$isbn&i=stripbooks",
            "https://www.amazon.com/dp/$isbn"
        )

        for (url in urlsToTry) {
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

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val html = response.body?.string() ?: return@use
                    if (html.isBlank() || html.lowercase().contains("robot check") || html.lowercase().contains("captcha")) return@use

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

                    if (title.isBlank()) return@use

                    var author = ""
                    val authorMatcher = Pattern.compile("(?:id=\"bylineInfo\"|class=\"[^\"]*author[^\"]*\")[^>]*>.*?<a[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                    if (authorMatcher.find()) {
                        author = cleanAuthor(authorMatcher.group(1) ?: "")
                    }

                    var publisher = ""
                    var year = ""
                    var pageCount = 0

                    val pubPattern = Pattern.compile("(?:Editore|Publisher)\\s*:\\s*</span>\\s*<span>\\s*([^;<|\n\r<]+)", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (pubPattern.find()) {
                        publisher = cleanPublisher(pubPattern.group(1) ?: "")
                    }

                    val datePattern = Pattern.compile("(?:Data di pubblicazione|Editore|Publication date)\\s*:\\s*</span>\\s*<span>\\s*(.*?)</span", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (datePattern.find()) {
                        year = extractYearFromText(datePattern.group(1) ?: "")
                    }

                    val pageMatcher = Pattern.compile("(?:Copertina flessibile|Copertina rigida|Pagine|Length|Print length)\\s*:\\s*</span>\\s*<span>\\s*(\\d+)\\s*pagine", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (pageMatcher.find()) {
                        pageCount = pageMatcher.group(1)?.toIntOrNull() ?: 0
                    }

                    var coverUrl: String? = null
                    val ogImgMatcher = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"(.*?)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (ogImgMatcher.find()) {
                        coverUrl = ogImgMatcher.group(1)?.trim()
                    }

                    return@withContext LookupResult(
                        isbn = isbn,
                        title = title,
                        author = author,
                        publisher = publisher,
                        publishedYear = year,
                        description = "Catalogo edizioni Amazon.it",
                        coverUrl = coverUrl,
                        pageCount = pageCount,
                        genre = "Narrativa / Saggistica",
                        sourceApi = ApiSource.AMAZON_IT.displayName
                    )
                }
            } catch (e: Exception) {
                Log.d(tag, "Scraper Direct Amazon su $url fallito: ${e.message}")
            }
        }
        null
    }

    /**
     * Ricerca su Amazon.it (Apify + Direct) per ISBN
     */
    suspend fun queryAmazonItByIsbn(
        isbn: String,
        publisherFilter: String = "",
        yearFilter: String = ""
    ): LookupResult? = withContext(Dispatchers.IO) {
        try {
            val apifyResult = queryApifyAmazonScraper(isbn, publisherFilter, yearFilter)
            if (apifyResult != null && BookSearchService.isLikelyBook(apifyResult)) {
                return@withContext apifyResult.copy(sourceApi = ApiSource.AMAZON_IT.displayName)
            }
        } catch (e: Exception) {
            Log.d(tag, "Apify Scraper fallito: ${e.message}")
        }

        try {
            val directResult = queryAmazonDirectScraper(isbn, publisherFilter, yearFilter)
            if (directResult != null && BookSearchService.isLikelyBook(directResult)) {
                return@withContext directResult.copy(sourceApi = ApiSource.AMAZON_IT.displayName)
            }
        } catch (e: Exception) {
            Log.d(tag, "Amazon Direct fallito: ${e.message}")
        }

        // Fallback robusto su Google Books in caso di blocco di Amazon.it
        try {
            val googleResult = queryGoogleBooksByIsbn(isbn)
            if (googleResult != null && BookSearchService.isLikelyBook(googleResult)) {
                return@withContext googleResult.copy(
                    sourceApi = ApiSource.AMAZON_IT.displayName,
                    description = googleResult.description.ifBlank { "Recuperato tramite archivio globale di riserva (Amazon.it)" }
                )
            }
        } catch (e: Exception) {
            Log.d(tag, "Google Books Fallback fallito per Amazon.it: ${e.message}")
        }

        // Fallback robusto su Open Library in caso di blocco di Amazon.it
        try {
            val olResult = queryOpenLibraryByIsbn(isbn)
            if (olResult != null && BookSearchService.isLikelyBook(olResult)) {
                return@withContext olResult.copy(
                    sourceApi = ApiSource.AMAZON_IT.displayName,
                    description = olResult.description.ifBlank { "Recuperato tramite archivio globale di riserva (Amazon.it)" }
                )
            }
        } catch (e: Exception) {
            Log.d(tag, "Open Library Fallback fallito per Amazon.it: ${e.message}")
        }

        null
    }

    /**
     * Ricerca Web Scraping su Libraccio.it tramite JSON-LD, dataLayer e tabella attributi
     */
    suspend fun queryLibraccioByIsbn(
        isbn: String,
        publisherFilter: String = "",
        yearFilter: String = ""
    ): LookupResult? = withContext(Dispatchers.IO) {
        val url = "https://www.libraccio.it/libro/$isbn/"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string() ?: return@use null
                if (html.isBlank()) return@use null

                var title = ""
                var author = ""
                var publisher = ""
                var publishedYear = ""
                var pageCount = 0
                var description = ""
                var coverUrl: String? = null
                var genre = "Narrativa / Saggistica"

                // 1. Parse JSON-LD Schema
                val jsonLdMatcher = Pattern.compile("<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                while (jsonLdMatcher.find()) {
                    try {
                        val jsonStr = jsonLdMatcher.group(1)?.trim() ?: continue
                        val jsonObj = JSONObject(jsonStr)
                        val typeObj = jsonObj.opt("@type")
                        val isBookOrProduct = typeObj?.toString()?.contains("Book", ignoreCase = true) == true ||
                                typeObj?.toString()?.contains("Product", ignoreCase = true) == true
                        if (isBookOrProduct) {
                            if (title.isBlank()) title = jsonObj.optString("name")
                            if (description.isBlank()) description = jsonObj.optString("description")
                            if (coverUrl.isNullOrBlank()) {
                                var img = jsonObj.optString("image").ifBlank { null }
                                if (img != null) {
                                    if (img.startsWith("/")) {
                                        img = "https://img.libraccio.it$img"
                                    } else if (img.contains("www.libraccio.it/images/")) {
                                        img = img.replace("www.libraccio.it/images/", "img.libraccio.it/images/")
                                    }
                                    coverUrl = img
                                }
                            }

                            val authorObj = jsonObj.opt("author")
                            if (author.isBlank() && authorObj is JSONObject) {
                                author = authorObj.optString("name")
                            } else if (author.isBlank() && authorObj is JSONArray) {
                                val names = mutableListOf<String>()
                                for (i in 0 until authorObj.length()) {
                                    val item = authorObj.optJSONObject(i)
                                    val n = item?.optString("name")
                                    if (!n.isNullOrBlank()) names.add(n)
                                }
                                if (names.isNotEmpty()) author = names.joinToString(", ")
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(tag, "Libraccio JSON-LD parse error: ${e.message}")
                    }
                }

                // 2. Parse dataLayer
                val dlMatcher = Pattern.compile("products'\\s*:\\s*\\[\\s*\\{(.*?)\\}\\s*\\]", Pattern.DOTALL).matcher(html)
                if (dlMatcher.find()) {
                    val dlContent = dlMatcher.group(1) ?: ""
                    val kvMatcher = Pattern.compile("'([^']+)'\\s*:\\s*'([^']*)'").matcher(dlContent)
                    val map = mutableMapOf<String, String>()
                    while (kvMatcher.find()) {
                        map[kvMatcher.group(1) ?: ""] = kvMatcher.group(2) ?: ""
                    }
                    if (title.isBlank()) title = map["name"] ?: ""
                    if (author.isBlank()) author = map["author"] ?: ""
                    if (publisher.isBlank()) publisher = map["publisher"] ?: ""
                    if (publishedYear.isBlank()) publishedYear = map["yearEdition"] ?: ""
                    if (genre.isBlank() || genre == "Narrativa / Saggistica") {
                        val cat = map["category"] ?: ""
                        if (cat.isNotBlank()) genre = cat.replace("Libri/", "")
                    }
                }

                // 3. Parse Attributes Table
                val tableRowMatcher = Pattern.compile("<td[^>]*class=[\"']titleColumn[\"'][^>]*>(.*?)</td>\\s*<td[^>]*class=[\"']valueColumn[\"'][^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                while (tableRowMatcher.find()) {
                    val key = tableRowMatcher.group(1)?.replace(Regex("<.*?>"), "")?.replace(":", "")?.trim() ?: ""
                    val value = tableRowMatcher.group(2)?.replace(Regex("<.*?>"), "")?.trim() ?: ""
                    when {
                        key.contains("Editore", ignoreCase = true) && publisher.isBlank() -> publisher = value
                        (key.contains("Anno", ignoreCase = true) || key.contains("Data", ignoreCase = true)) && publishedYear.isBlank() -> {
                            publishedYear = extractYearFromText(value)
                        }
                        key.contains("Dati", ignoreCase = true) && pageCount == 0 -> {
                            val pMatcher = Pattern.compile("(\\d+)\\s*p\\.", Pattern.CASE_INSENSITIVE).matcher(value)
                            if (pMatcher.find()) {
                                pageCount = pMatcher.group(1)?.toIntOrNull() ?: 0
                            }
                        }
                    }
                }

                if (title.isBlank()) {
                    val h1Matcher = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                    if (h1Matcher.find()) {
                        title = h1Matcher.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: ""
                    }
                }

                title = cleanTitle(title)
                if (title.isBlank()) return@use null

                if (coverUrl.isNullOrBlank()) {
                    coverUrl = "https://img.libraccio.it/images/${isbn}_0_170_0_75.jpg"
                } else if (coverUrl!!.contains("www.libraccio.it/images/")) {
                    coverUrl = coverUrl!!.replace("www.libraccio.it/images/", "img.libraccio.it/images/")
                }

                description = description.replace(Regex("<.*?>"), "").trim()
                if (description.isBlank()) {
                    description = "Trovato nel catalogo Libraccio.it"
                }

                LookupResult(
                    isbn = isbn,
                    title = title,
                    author = cleanAuthor(author),
                    publisher = cleanPublisher(publisher),
                    publishedYear = publishedYear,
                    description = description,
                    coverUrl = coverUrl,
                    pageCount = pageCount,
                    genre = genre.ifBlank { "Narrativa / Saggistica" },
                    sourceApi = ApiSource.LIBRACCIO_IT.displayName
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Errore Libraccio per ISBN $isbn: ${e.message}")
            null
        }
    }

    /**
     * Ricerca Web Scraping su IBS.it per ISBN
     */
    suspend fun queryIbsByIsbn(
        isbn: String,
        publisherFilter: String = "",
        yearFilter: String = ""
    ): LookupResult? = withContext(Dispatchers.IO) {
        val searchUrl = "https://www.ibs.it/search/?ts=as&query=$isbn"
        try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string() ?: return@use null
                if (html.isBlank()) return@use null

                var title = ""
                var author = ""
                var publisher = ""
                var publishedYear = ""
                var pageCount = 0
                var description = ""
                var coverUrl: String? = null
                var genre = "Narrativa / Saggistica"

                // 1. Link matching e parsing slug
                var prodPath = ""
                val linkMatcher = Pattern.compile("href=[\"'](/[^\"']+/e/$isbn[^\"']*)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (linkMatcher.find()) {
                    prodPath = linkMatcher.group(1) ?: ""
                    val rawTitleFromLink = linkMatcher.group(2)?.replace(Regex("<.*?>"), "")?.trim() ?: ""
                    if (rawTitleFromLink.isNotBlank()) title = rawTitleFromLink

                    val slug = prodPath.substringBefore("/e/").trim('/')
                    if (slug.contains("-libro-")) {
                        val parts = slug.split("-libro-")
                        if (parts.size >= 2) {
                            val authorSlug = parts[1].replace("-", " ")
                            author = authorSlug.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                        }
                    }
                }

                // 2. dataLayer dalla pagina di ricerca
                val dlMatcher = Pattern.compile("dataLayer\\.push\\((.*?)\\);", Pattern.DOTALL).matcher(html)
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
                                    if (itemId.contains(isbn)) {
                                        if (title.isBlank()) title = itObj.optString("item_name")
                                        if (author.isBlank()) {
                                            val itAuthor = itObj.optString("item_author")
                                            if (itAuthor.isNotBlank()) author = itAuthor
                                        }
                                        if (publisher.isBlank()) publisher = itObj.optString("item_brand")
                                        if (publishedYear.isBlank()) {
                                            val itYear = itObj.optString("year_edition")
                                            if (itYear.isNotBlank()) publishedYear = itYear
                                        }
                                        val cat2 = itObj.optString("item_category2")
                                        if (cat2.isNotBlank()) genre = cat2
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(tag, "IBS dataLayer parse error: ${e.message}")
                    }
                }

                // 3. Se abbiamo il percorso della scheda prodotto su IBS, la scarichiamo per estrarre la Sinossi completa e le proprietà del catalogo
                if (prodPath.isNotBlank()) {
                    try {
                        val productUrl = if (prodPath.startsWith("http")) prodPath else "https://www.ibs.it$prodPath"
                        val prodRequest = Request.Builder()
                            .url(productUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .header("Referer", searchUrl)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                            .build()
                        okHttpClient.newCall(prodRequest).execute().use { prodResp ->
                            if (prodResp.isSuccessful) {
                                val prodHtml = prodResp.body?.string() ?: ""
                                if (prodHtml.isNotBlank()) {
                                    // A. Estrazione Sinossi da sezione #pdp-descrizione
                                    val pdpDescMatcher = Pattern.compile(
                                        "<div[^>]*id=[\"']pdp-descrizione[\"'][^>]*>.*?<div[^>]*class=[\"'][^\"']*cc-content-text[^\"']*[\"'][^>]*>(.*?)</div>",
                                        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
                                    ).matcher(prodHtml)
                                    if (pdpDescMatcher.find()) {
                                        description = cleanHtmlDescription(pdpDescMatcher.group(1))
                                    }

                                    // Fallback sinossi da cc-clamp
                                    if (description.isBlank()) {
                                        val clampMatcher = Pattern.compile(
                                            "<div[^>]*class=[\"'][^\"']*cc-content-text[^\"']*cc-clamp[^\"']*[\"'][^>]*>(.*?)</div>",
                                            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
                                        ).matcher(prodHtml)
                                        if (clampMatcher.find()) {
                                            description = cleanHtmlDescription(clampMatcher.group(1))
                                        }
                                    }

                                    // Fallback da og:description
                                    if (description.isBlank()) {
                                        val ogDescMatcher = Pattern.compile(
                                            "<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"'](.*?)[\"']",
                                            Pattern.CASE_INSENSITIVE
                                        ).matcher(prodHtml)
                                        if (ogDescMatcher.find()) {
                                            description = cleanHtmlDescription(ogDescMatcher.group(1))
                                        }
                                    }

                                    // B. Estrazione proprietà da JSON-LD nella pagina prodotto (Pagine, Editore, Anno)
                                    val ldMatcher = Pattern.compile(
                                        "<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
                                        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
                                    ).matcher(prodHtml)
                                    while (ldMatcher.find()) {
                                        try {
                                            val ldStr = ldMatcher.group(1)?.trim() ?: continue
                                            val ldObj = JSONObject(ldStr)
                                            val graph = ldObj.optJSONArray("@graph")
                                            val candidateNodes = mutableListOf<JSONObject>()
                                            if (graph != null) {
                                                for (idx in 0 until graph.length()) {
                                                    val node = graph.optJSONObject(idx) ?: continue
                                                    candidateNodes.add(node)
                                                }
                                            } else {
                                                candidateNodes.add(ldObj)
                                            }

                                            for (node in candidateNodes) {
                                                val type = node.optString("@type")
                                                if (type.equals("Product", ignoreCase = true) || type.equals("Book", ignoreCase = true)) {
                                                    if (title.isBlank()) title = node.optString("name")
                                                    if (description.isBlank()) {
                                                        val d = node.optString("description")
                                                        if (d.isNotBlank()) description = cleanHtmlDescription(d)
                                                    }
                                                    val props = node.optJSONArray("additionalProperty")
                                                    if (props != null) {
                                                        for (p in 0 until props.length()) {
                                                            val prop = props.optJSONObject(p) ?: continue
                                                            val pName = prop.optString("name")
                                                            val pVal = prop.optString("value")
                                                            when {
                                                                pName.contains("Pagine", ignoreCase = true) && pageCount == 0 -> {
                                                                    val digits = pVal.replace(Regex("[^0-9]"), "")
                                                                    pageCount = digits.toIntOrNull() ?: 0
                                                                }
                                                                pName.contains("Editore", ignoreCase = true) && publisher.isBlank() -> {
                                                                    publisher = cleanPublisher(pVal)
                                                                }
                                                                pName.contains("Anno", ignoreCase = true) && publishedYear.isBlank() -> {
                                                                    publishedYear = extractYearFromText(pVal)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.d(tag, "Product JSON-LD parse error: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(tag, "Fetch IBS product detail page error: ${e.message}")
                    }
                }

                // 4. Fallback title & cover
                if (title.isBlank()) {
                    val h1Matcher = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                    if (h1Matcher.find()) {
                        title = h1Matcher.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: ""
                    }
                }

                // Immagine di copertina ad alta risoluzione:
                // Libraccio e IBS condividono l'infrastruttura di catalogazione e le immagini per ISBN.
                // Il CDN img.libraccio.it ospita le copertine senza protezione anti-bot / 429 di Akamai.
                coverUrl = "https://img.libraccio.it/images/${isbn}_0_500_0_75.jpg"

                title = cleanTitle(title)
                if (title.isBlank()) return@use null

                LookupResult(
                    isbn = isbn,
                    title = title,
                    author = cleanAuthor(author),
                    publisher = cleanPublisher(publisher),
                    publishedYear = publishedYear,
                    description = description.ifBlank { "Trovato nel catalogo IBS.it" },
                    coverUrl = coverUrl,
                    pageCount = pageCount,
                    genre = genre,
                    sourceApi = ApiSource.IBS_IT.displayName
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Errore IBS.it per ISBN $isbn: ${e.message}")
            null
        }
    }

    private suspend fun queryApifyAmazonScraperList(query: String): List<LookupResult> = withContext(Dispatchers.IO) {
        val apifyToken = System.getenv("APIFY_API_TOKEN") ?: ""
        if (apifyToken.isBlank()) return@withContext emptyList()

        val baseUrl = "https://api.apify.com/v2/acts/apify~amazon-product-scraper/run-sync-get-dataset-items"
        val url = "$baseUrl?token=$apifyToken"

        val jsonBody = JSONObject().apply {
            put("searchKeywords", query)
            put("maxItems", 5)
            put("amazonDomain", "amazon.it")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .build()

        val results = mutableListOf<LookupResult>()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseBodyStr = response.body?.string() ?: return@withContext emptyList()
                if (responseBodyStr.isBlank()) return@withContext emptyList()

                val jsonArray = JSONArray(responseBodyStr)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val rawTitle = item.optString("title").ifBlank { item.optString("name") }
                    val title = cleanTitle(rawTitle)
                    if (title.isBlank()) continue

                    var rawAuthor = item.optString("author")
                    if (rawAuthor.isBlank()) {
                        val authorsArr = item.optJSONArray("authors")
                        if (authorsArr != null && authorsArr.length() > 0) {
                            rawAuthor = authorsArr.getString(0)
                        }
                    }
                    val author = cleanAuthor(rawAuthor)

                    val publisher = extractApifyPublisher(item)
                    val year = extractApifyYear(item)
                    val pageCount = extractApifyPages(item)

                    val description = item.optString("description").ifBlank { item.optString("productDescription") }
                    val coverUrl = item.optString("image").ifBlank { item.optString("imageUrl").ifBlank { item.optString("thumbnail") } }
                    val genre = item.optString("genre").ifBlank { "Narrativa / Saggistica" }
                    val itemIsbn = item.optString("isbn").ifBlank { item.optString("asin").ifBlank { "AMZ-$i" } }

                    results.add(
                        LookupResult(
                            isbn = itemIsbn,
                            title = title,
                            author = author,
                            publisher = publisher,
                            publishedYear = year,
                            description = description.ifBlank { "Trovato su Amazon via Apify" },
                            coverUrl = if (coverUrl.isBlank()) null else coverUrl,
                            pageCount = pageCount,
                            genre = genre,
                            sourceApi = ApiSource.AMAZON_IT.displayName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Eccezione durante Apify List API: ${e.message}")
        }
        results
    }

    private suspend fun queryAmazonDirectSearchResults(query: String): List<LookupResult> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.amazon.it/s?k=$encodedQuery&i=stripbooks"
        val results = mutableListOf<LookupResult>()

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

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val html = response.body?.string() ?: return@use
                if (html.isBlank() || html.lowercase().contains("robot check")) return@use

                val itemPattern = Pattern.compile(
                    "data-asin=\"([A-Z0-9]{10})\"[^>]*>.*?<h2[^>]*>.*?<span[^>]*>(.*?)</span>.*?<img[^>]*class=\"s-image\"[^>]*src=\"(.*?)\"",
                    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                )
                val matcher = itemPattern.matcher(html)

                var count = 0
                while (matcher.find() && count < 5) {
                    val asin = matcher.group(1) ?: continue
                    val rawTitle = matcher.group(2) ?: continue
                    val coverUrl = matcher.group(3)?.trim()

                    val title = cleanTitle(rawTitle)
                    if (title.isBlank()) continue

                    results.add(
                        LookupResult(
                            isbn = asin,
                            title = title,
                            author = "",
                            publisher = "",
                            publishedYear = "",
                            description = "Risultato ricerca Web Scraping Amazon.it",
                            coverUrl = coverUrl,
                            pageCount = 0,
                            genre = "Narrativa / Saggistica",
                            sourceApi = ApiSource.AMAZON_IT.displayName
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Errore durante Amazon Direct Search Results: ${e.message}")
        }
        results
    }

    /**
     * Arricchisce il risultato con Editore, Anno, Pagine e Copertina se mancanti, consultando Google Books / Open Library
     */
    suspend fun enrichMetadata(result: LookupResult): LookupResult = withContext(Dispatchers.IO) {
        val hasGenericDesc = isGenericDescription(result.description)
        if (result.author.isNotBlank() && result.publisher.isNotBlank() && result.publishedYear.isNotBlank() && result.pageCount > 0 && !result.coverUrl.isNullOrBlank() && !hasGenericDesc) {
            return@withContext result
        }

        var updatedAuthor = result.author
        var updatedPublisher = result.publisher
        var updatedYear = result.publishedYear
        var updatedPages = result.pageCount
        var updatedGenre = result.genre
        var updatedDescription = result.description
        var updatedCover = result.coverUrl

        // 1. Consulta Google Books API per completare i campi mancanti o la sinossi
        try {
            val query = if (result.isbn.isNotBlank() && !result.isbn.startsWith("AMZ-") && !result.isbn.startsWith("GB-") && !result.isbn.startsWith("OL-")) {
                "isbn:${result.isbn}"
            } else {
                "${result.title} ${result.author}".trim()
            }
            val gUrl = "https://www.googleapis.com/books/v1/volumes?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder().url(gUrl).build()
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
                                    val authorsArr = volInfo.optJSONArray("authors")
                                    if (authorsArr != null && authorsArr.length() > 0) {
                                        updatedAuthor = cleanAuthor(authorsArr.getString(0))
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
                                if (updatedCover.isNullOrBlank()) {
                                    val imageLinks = volInfo.optJSONObject("imageLinks")
                                    if (imageLinks != null) {
                                        val t = imageLinks.optString("thumbnail").ifBlank { imageLinks.optString("smallThumbnail") }
                                        if (t.isNotBlank()) {
                                            updatedCover = if (t.startsWith("http:")) t.replace("http:", "https:") else t
                                        }
                                    }
                                }
                                if (updatedGenre.isBlank() || updatedGenre == "Narrativa / Saggistica") {
                                    val categories = volInfo.optJSONArray("categories")
                                    if (categories != null && categories.length() > 0) {
                                        updatedGenre = categories.getString(0)
                                    }
                                }
                                if (updatedDescription.isBlank() || isGenericDescription(updatedDescription)) {
                                    val desc = volInfo.optString("description")
                                    if (desc.isNotBlank()) updatedDescription = cleanHtmlDescription(desc)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Enrich Google Books error: ${e.message}")
        }

        // 2. Se ancora manca editore, anno, copertina o sinossi, consulta Open Library
        if (updatedAuthor.isBlank() || updatedPublisher.isBlank() || updatedYear.isBlank() || updatedPages == 0 || updatedCover.isNullOrBlank() || isGenericDescription(updatedDescription)) {
            try {
                val olUrl = if (result.isbn.isNotBlank() && !result.isbn.startsWith("AMZ-") && !result.isbn.startsWith("GB-") && !result.isbn.startsWith("OL-")) {
                    "https://openlibrary.org/api/books?bibkeys=ISBN:${result.isbn}&format=json&jscmd=data"
                } else {
                    "https://openlibrary.org/search.json?q=${java.net.URLEncoder.encode("${result.title} $updatedAuthor", "UTF-8")}&limit=1"
                }
                val request = Request.Builder().url(olUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            if (body.startsWith("{") && body.contains("ISBN:")) {
                                val json = JSONObject(body)
                                val key = json.keys().asSequence().firstOrNull()
                                if (key != null) {
                                    val bookObj = json.optJSONObject(key)
                                    if (bookObj != null) {
                                        if (updatedAuthor.isBlank()) {
                                            val authors = bookObj.optJSONArray("authors")
                                            if (authors != null && authors.length() > 0) {
                                                updatedAuthor = cleanAuthor(authors.getJSONObject(0).optString("name"))
                                            }
                                        }
                                        if (updatedPublisher.isBlank()) {
                                            val publishers = bookObj.optJSONArray("publishers")
                                            if (publishers != null && publishers.length() > 0) {
                                                updatedPublisher = cleanPublisher(publishers.getJSONObject(0).optString("name"))
                                            }
                                        }
                                        if (updatedYear.isBlank()) {
                                            updatedYear = extractYearFromText(bookObj.optString("publish_date"))
                                        }
                                        if (updatedPages == 0) {
                                            updatedPages = bookObj.optInt("number_of_pages", 0)
                                        }
                                        if (updatedCover.isNullOrBlank()) {
                                            val coverObj = bookObj.optJSONObject("cover")
                                            val covM = coverObj?.optString("medium") ?: coverObj?.optString("small") ?: ""
                                            if (covM.isNotBlank()) updatedCover = covM
                                        }
                                        if (updatedDescription.isBlank() || isGenericDescription(updatedDescription)) {
                                            val olDesc = bookObj.opt("description")
                                            if (olDesc is String && olDesc.isNotBlank()) {
                                                updatedDescription = cleanHtmlDescription(olDesc)
                                            } else if (olDesc is JSONObject) {
                                                val v = olDesc.optString("value")
                                                if (v.isNotBlank()) updatedDescription = cleanHtmlDescription(v)
                                            }
                                        }
                                    }
                                }
                            } else if (body.startsWith("{")) {
                                val json = JSONObject(body)
                                val docs = json.optJSONArray("docs")
                                if (docs != null && docs.length() > 0) {
                                    val doc = docs.getJSONObject(0)
                                    if (updatedAuthor.isBlank()) {
                                        val authorNames = doc.optJSONArray("author_name")
                                        if (authorNames != null && authorNames.length() > 0) {
                                            updatedAuthor = cleanAuthor(authorNames.getString(0))
                                        }
                                    }
                                    if (updatedPublisher.isBlank()) {
                                        val pubs = doc.optJSONArray("publisher")
                                        if (pubs != null && pubs.length() > 0) {
                                            updatedPublisher = cleanPublisher(pubs.getString(0))
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
                                    if (updatedCover.isNullOrBlank()) {
                                        val coverI = doc.optInt("cover_i", 0)
                                        if (coverI > 0) {
                                            updatedCover = "https://covers.openlibrary.org/b/id/$coverI-M.jpg"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "Enrich Open Library error: ${e.message}")
            }
        }

        result.copy(
            author = updatedAuthor,
            publisher = updatedPublisher,
            publishedYear = updatedYear,
            pageCount = updatedPages,
            genre = updatedGenre,
            description = updatedDescription,
            coverUrl = updatedCover
        )
    }

    private fun extractApifyPublisher(item: JSONObject): String {
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

    private fun extractApifyYear(item: JSONObject): String {
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

    private fun extractApifyPages(item: JSONObject): Int {
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

    private fun matchesFilters(
        result: LookupResult,
        publisherFilter: String,
        yearFilter: String
    ): Boolean {
        if (publisherFilter.isNotBlank() && !result.publisher.contains(publisherFilter.trim(), ignoreCase = true)) {
            return false
        }
        if (yearFilter.isNotBlank() && !result.publishedYear.contains(yearFilter.trim(), ignoreCase = true)) {
            return false
        }
        return true
    }

    private fun cleanTitle(rawTitle: String, fallbackQuery: String = ""): String {
        if (rawTitle.isBlank()) return if (fallbackQuery.isNotBlank() && fallbackQuery != rawTitle) cleanTitle(fallbackQuery) else ""
        
        var title = rawTitle
            .replace(Regex("(?i):\\s*Copertina\\s+(?:flessibile|rigida)"), "")
            .replace(Regex("(?i)\\[Copertina\\s+(?:flessibile|rigida)\\]"), "")
            .replace(Regex("(?i)\\(Copertina\\s+(?:flessibile|rigida)\\)"), "")
            .replace(Regex("(?i)\\bCopertina\\s+(?:flessibile|rigida)\\b"), "")
            .replace(Regex("(?i)\\bFormato\\s+Kindle\\b"), "")
            .replace(Regex("(?i):\\s*Amazon\\.it:\\s*Libri"), "")
            .replace(Regex("(?i):\\s*Amazon\\.com:\\s*Books"), "")
            .replace(Regex("(?i)Amazon\\.it"), "")
            .replace(Regex("(?i)Amazon\\.com"), "")
            .replace(Regex("<.*?>"), "")
            .replace(Regex("^[:\\s\\-\\–\\—]+"), "")
            .replace(Regex("[:\\s\\-\\–\\—]+$"), "")
            .trim()

        if (title.isBlank() || 
            title.equals("Copertina flessibile", ignoreCase = true) || 
            title.equals("Copertina rigida", ignoreCase = true) || 
            title.equals("Libri", ignoreCase = true) || 
            title.equals("Books", ignoreCase = true)) {
            if (fallbackQuery.isNotBlank() && !fallbackQuery.equals(rawTitle, ignoreCase = true)) {
                return cleanTitle(fallbackQuery)
            }
            return ""
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
            .replace(Regex("(?i)\\s*\\(Illustratore\\)"), "")
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
        
        val datePattern = Pattern.compile("(?:\\b|\\()([12]\\d{3})(?:\\b|\\))")
        val matcher = datePattern.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group(1) ?: continue
            val y = candidate.toIntOrNull() ?: continue
            if (y in 1800..2026 && y != 2000) {
                return y.toString()
            }
        }

        val y2000Pattern = Pattern.compile("\\b(2000)\\b")
        val m2000 = y2000Pattern.matcher(text)
        if (m2000.find() && (text.contains("pubblicazione", ignoreCase = true) || text.contains("editore", ignoreCase = true))) {
            return "2000"
        }

        return ""
    }

    fun cleanHtmlDescription(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutTags = raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)<p[^>]*>"), "\n\n")
            .replace(Regex("(?i)</p>"), "")
            .replace(Regex("<.*?>"), " ")
        val unescaped = android.text.Html.fromHtml(
            withoutTags,
            android.text.Html.FROM_HTML_MODE_LEGACY
        ).toString()
        return unescaped
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun isGenericDescription(desc: String?): Boolean {
        if (desc.isNullOrBlank()) return true
        val lower = desc.lowercase().trim()
        return lower.startsWith("trovato nel catalogo") ||
                lower.startsWith("trovato su") ||
                lower.startsWith("recuperato tramite") ||
                lower.contains("web scraping") ||
                lower == "descrizione non disponibile" ||
                lower.length < 30
    }
}
