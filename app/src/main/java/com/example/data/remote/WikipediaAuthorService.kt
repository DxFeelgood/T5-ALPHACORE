package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Modello dati contenente le informazioni biografiche di un autore estratte da Wikipedia.
 */
data class WikipediaAuthorInfo(
    val queryName: String,
    val canonicalTitle: String,
    val description: String?,
    val extract: String,
    val photoUrl: String?,
    val wikipediaUrl: String,
    val birthDeathInfo: String? = null,
    val pageLanguage: String = "it"
)

/**
 * Servizio per interrogare le API ufficiali REST di Wikipedia (in italiano, con fallback in inglese)
 * e ottenere le informazioni biografiche, la descrizione, le date di vita e la foto dell'autore.
 */
class WikipediaAuthorService(
    private val okHttpClient: OkHttpClient = NetworkClient.okHttpClient
) {
    private val tag = "WikipediaAuthorService"

    /**
     * Recupera le informazioni biografiche per un determinato autore.
     */
    suspend fun getAuthorBiography(rawAuthorName: String): Result<WikipediaAuthorInfo> = withContext(Dispatchers.IO) {
        val cleanName = cleanAuthorName(rawAuthorName)
        if (cleanName.isBlank() || isGenericAuthor(cleanName)) {
            return@withContext Result.failure(
                IllegalArgumentException("Nome autore non specificato o generico.")
            )
        }

        // 1. Prova prima su Wikipedia in Italiano (it.wikipedia.org)
        val italianResult = queryWikipedia(cleanName, "it")
        if (italianResult != null) {
            return@withContext Result.success(italianResult)
        }

        // 2. Fallback su Wikipedia in Inglese (en.wikipedia.org) per autori internazionali
        val englishResult = queryWikipedia(cleanName, "en")
        if (englishResult != null) {
            return@withContext Result.success(englishResult)
        }

        Result.failure(
            NoSuchElementException("Nessuna voce biografica trovata su Wikipedia per \"$cleanName\".")
        )
    }

    /**
     * Esegue la query su una specifica lingua di Wikipedia (it o en).
     */
    private fun queryWikipedia(authorName: String, lang: String): WikipediaAuthorInfo? {
        val domain = "$lang.wikipedia.org"

        // Step A: Tentativo diretto con il nome formattato a slug (es: "Italo_Calvino")
        val directSlug = authorName.trim().replace("\\s+".toRegex(), "_")
        val directSummary = fetchPageSummary(directSlug, domain, lang, authorName)
        if (directSummary != null && directSummary.extract.isNotBlank() && !isDisambiguation(directSummary)) {
            return directSummary
        }

        // Step B: Ricerca tramite Search API di Wikipedia per individuare il titolo esatto della pagina
        val searchResultTitle = searchWikipediaPageTitle(authorName, domain)
        if (!searchResultTitle.isNullOrBlank()) {
            val searchedSlug = searchResultTitle.replace("\\s+".toRegex(), "_")
            val searchedSummary = fetchPageSummary(searchedSlug, domain, lang, authorName)
            if (searchedSummary != null && searchedSummary.extract.isNotBlank()) {
                return searchedSummary
            }
        }

        return null
    }

    /**
     * Interroga l'endpoint REST /page/summary/{title} di Wikipedia
     */
    private fun fetchPageSummary(
        titleSlug: String,
        domain: String,
        lang: String,
        queryName: String
    ): WikipediaAuthorInfo? {
        return try {
            val encodedTitle = URLEncoder.encode(titleSlug, StandardCharsets.UTF_8.name())
            val url = "https://$domain/api/rest_v1/page/summary/$encodedTitle"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ArchivioLibriApp/1.0 (Android; IT; BiografiaAutore)")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseWikipediaSummaryJson(body, domain, lang, queryName)
            }
        } catch (e: Exception) {
            Log.w(tag, "Errore fetch summary da $domain per $titleSlug: ${e.message}")
            null
        }
    }

    /**
     * Cerca il titolo canonico della pagina tramite l'API query search
     */
    private fun searchWikipediaPageTitle(query: String, domain: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://$domain/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json&utf8=1&srlimit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ArchivioLibriApp/1.0 (Android; IT; BiografiaAutore)")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val queryObj = json.optJSONObject("query") ?: return null
                val searchArr = queryObj.optJSONArray("search") ?: return null
                if (searchArr.length() > 0) {
                    val firstItem = searchArr.getJSONObject(0)
                    firstItem.optString("title").takeIf { it.isNotBlank() }
                } else null
            }
        } catch (e: Exception) {
            Log.w(tag, "Errore ricerca Wikipedia su $domain per $query: ${e.message}")
            null
        }
    }

    /**
     * Effettua il parsing del payload JSON restituito dall'API REST di Wikipedia
     */
    private fun parseWikipediaSummaryJson(
        jsonString: String,
        domain: String,
        lang: String,
        queryName: String
    ): WikipediaAuthorInfo? {
        return try {
            val json = JSONObject(jsonString)

            val type = json.optString("type", "")
            if (type == "disambiguation") return null

            val title = json.optString("title", queryName)
            val description = json.optString("description").takeIf { it.isNotBlank() }
            val extract = json.optString("extract").trim()
            if (extract.isBlank()) return null

            // Foto / Immagine di copertina / Ritratto
            val thumbnailObj = json.optJSONObject("thumbnail")
            val originalImageObj = json.optJSONObject("originalimage")
            val photoUrl = originalImageObj?.optString("source")?.takeIf { it.isNotBlank() }
                ?: thumbnailObj?.optString("source")?.takeIf { it.isNotBlank() }

            // URL della pagina desktop
            val contentUrls = json.optJSONObject("content_urls")
            val desktopUrls = contentUrls?.optJSONObject("desktop")
            val defaultUrl = "https://$domain/wiki/${URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8.name())}"
            val wikipediaUrl = desktopUrls?.optString("page", defaultUrl) ?: defaultUrl

            // Se possibile, arricchisce l'estratto con la versione completa del testo introduttivo
            val fullIntro = fetchFullIntroExtract(title, domain)
            val finalExtract = if (!fullIntro.isNullOrBlank() && fullIntro.length > extract.length) {
                fullIntro
            } else {
                extract
            }

            // Estrazione anni e luoghi di nascita e morte dal testo di apertura
            // es: (Santiago de Las Vegas de La Habana, 15 ottobre 1923 – Siena, 19 settembre 1985)
            val birthDeathInfo = extractBirthDeathInfo(finalExtract)

            WikipediaAuthorInfo(
                queryName = queryName,
                canonicalTitle = title,
                description = description,
                extract = finalExtract,
                photoUrl = photoUrl,
                wikipediaUrl = wikipediaUrl,
                birthDeathInfo = birthDeathInfo,
                pageLanguage = lang
            )
        } catch (e: Exception) {
            Log.e(tag, "Errore nel parsing del JSON di Wikipedia: ${e.message}", e)
            null
        }
    }

    /**
     * Recupera l'intero estratto introduttivo della voce (testo piano completo) tramite l'API query di Wikipedia
     */
    private fun fetchFullIntroExtract(title: String, domain: String): String? {
        return try {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
            val url = "https://$domain/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&titles=$encodedTitle&format=json&utf8=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ArchivioLibriApp/1.0 (Android; IT; BiografiaAutore)")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val queryObj = json.optJSONObject("query") ?: return null
                val pagesObj = queryObj.optJSONObject("pages") ?: return null
                val keys = pagesObj.keys()
                if (keys.hasNext()) {
                    val page = pagesObj.getJSONObject(keys.next())
                    val text = page.optString("extract", "").trim()
                    if (text.isNotBlank()) text else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Estrae le coordinate biografiche (nascita, morte, luoghi) racchiuse tra parentesi all'inizio dell'estratto.
     */
    private fun extractBirthDeathInfo(extract: String): String? {
        // Cerca parentesi che contengono date numeriche a 4 cifre
        val regex = Regex("""\(([^)]*(?:1[0-9]{3}|20[0-9]{2})[^)]*)\)""")
        val match = regex.find(extract.take(300))
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Verifica se il summary rappresenta una pagina di disambiguazione
     */
    private fun isDisambiguation(info: WikipediaAuthorInfo): Boolean {
        val desc = info.description.orEmpty().lowercase()
        val extract = info.extract.lowercase()
        return desc.contains("disambigua") ||
                desc.contains("disambiguation") ||
                extract.startsWith("questa voce o sezione sull'argomento") ||
                extract.startsWith("le voci seguenti hanno un titolo simile")
    }

    /**
     * Pulisce e normalizza il nome dell'autore (inverte "Cognome, Nome", rimuove coautori multipli)
     */
    fun cleanAuthorName(raw: String): String {
        var name = raw.trim()

        // Rimuove prefissi o suffissi frequenti come "di ", "by ", ecc.
        if (name.startsWith("di ", ignoreCase = true)) {
            name = name.substring(3).trim()
        }

        // Se nel formato "Calvino, Italo", inverte in "Italo Calvino"
        if (name.contains(",") && !name.contains(" e ", ignoreCase = true) && !name.contains("&")) {
            val parts = name.split(",").map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                name = "${parts[1]} ${parts[0]}"
            }
        }

        // Se sono presenti più autori separati da separatori standard, prende il primo autore
        val separators = listOf(";", "/", " & ", " and ", " ed ", " e ")
        for (sep in separators) {
            if (name.contains(sep, ignoreCase = true)) {
                name = name.split(Regex(Regex.escape(sep), RegexOption.IGNORE_CASE))[0].trim()
                break
            }
        }

        return name.trim()
    }

    /**
     * Verifica se l'autore inserito è un placeholder o dicitura generica
     */
    fun isGenericAuthor(name: String): Boolean {
        val lower = name.trim().lowercase()
        return lower in listOf(
            "aa.vv.", "aavv", "aa. vv.", "autori vari", "autore non specificato",
            "autore sconosciuto", "anonimo", "anonymous", "sconosciuto", "vari",
            "various", "non specificato", "n/a", "nessuno", "none"
        )
    }
}
