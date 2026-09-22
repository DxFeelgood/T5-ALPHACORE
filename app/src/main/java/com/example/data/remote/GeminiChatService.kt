package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

enum class MessageSender {
    USER,
    ASSISTANT
}

class GeminiChatService(
    private val context: Context,
    private val okHttpClient: OkHttpClient = NetworkClient.okHttpClient
) {
    private val tag = "GeminiChatService"
    private val prefs = context.getSharedPreferences("gemini_chat_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_CUSTOM_API_KEY = "custom_gemini_api_key"
        private const val MODEL_NAME = "gemini-3.5-flash"
    }

    /**
     * Salva una chiave API inserita dall'utente nelle preferenze locali.
     */
    fun saveCustomApiKey(key: String) {
        prefs.edit().putString(PREF_CUSTOM_API_KEY, key.trim()).apply()
    }

    /**
     * Restituisce la chiave API memorizzata o la chiave di default presente in BuildConfig.
     */
    fun getEffectiveApiKey(): String {
        val customKey = prefs.getString(PREF_CUSTOM_API_KEY, "")?.trim() ?: ""
        if (customKey.isNotEmpty()) {
            return customKey
        }
        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun hasValidApiKey(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    fun getCustomApiKey(): String {
        return prefs.getString(PREF_CUSTOM_API_KEY, "") ?: ""
    }

    fun clearCustomApiKey() {
        prefs.edit().remove(PREF_CUSTOM_API_KEY).apply()
    }

    /**
     * Invia un messaggio al chatbot letterario Gemini con cronologia e contesto della libreria.
     */
    suspend fun sendChatMessage(
        userMessage: String,
        history: List<ChatMessage>,
        libraryBooks: List<BookEntity>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Chiave API Gemini non configurata. Inserisci la tua API Key nelle impostazioni della chat per iniziare a conversare con l'esperto letterario.")
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        try {
            val rootJson = JSONObject()

            // Istruzione di sistema per l'esperto di letteratura
            val systemInstructionJson = JSONObject()
            val systemPartsArray = JSONArray()
            val systemPart = JSONObject()

            val systemPrompt = buildString {
                appendLine("Sei un illustre critico letterario, bibliotecario ed esperto appassionato di letteratura universale, con profonda conoscenza di classici, narrativa contemporanea, saggi, generi letterari e storia del libro.")
                appendLine("Rispondi sempre in italiano con tono colto, accogliente, stimolante ed empatico.")
                appendLine("IMPORTANTE: Fornisci sempre risposte esaustive, complete, ben articolate e ricche di dettagli. Non abbreviare o troncare mai le tue spiegazioni. Organizza i tuoi pensieri con paragrafi chiari, elenchi ben definiti e analisi approfondite.")
                appendLine("Puoi consigliare nuovi libri, analizzare stili di scrittura, spiegare contesti storici, approfondire trame senza fare spoiler non richiesti, o proporre percorsi di lettura a tema.")
                if (!libraryBooks.isNullOrEmpty()) {
                    appendLine("\n[CONTESTO LIBRERIA PERSONALE DELL'UTENTE]")
                    appendLine("L'utente ha attualmente i seguenti volumi archiviati nella propria libreria:")
                    libraryBooks.take(40).forEachIndexed { index, book ->
                        appendLine("- ${book.title} (Autore: ${book.author}, Genere: ${book.genre.ifBlank { "N/D" }}, Stato: ${book.readingStatus})")
                    }
                    appendLine("Fai riferimento ai suoi libri e autori se pertinente alla domanda o per consigliare titoli affini!")
                }
            }

            systemPart.put("text", systemPrompt)
            systemPartsArray.put(systemPart)
            systemInstructionJson.put("parts", systemPartsArray)
            rootJson.put("systemInstruction", systemInstructionJson)

            // Contenuti della conversazione (History + Nuovo Messaggio senza duplicati)
            val contentsArray = JSONArray()

            // Filtra la cronologia escludendo i messaggi di errore e l'ultimo messaggio se è già uguale a userMessage
            val previousMessages = history.filter { !it.isError }
                .let { list ->
                    if (list.isNotEmpty() && list.last().sender == MessageSender.USER && list.last().text == userMessage) {
                        list.dropLast(1)
                    } else {
                        list
                    }
                }
                .takeLast(10)

            for (msg in previousMessages) {
                val msgJson = JSONObject()
                msgJson.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", msg.text))
                msgJson.put("parts", parts)
                contentsArray.put(msgJson)
            }

            // Aggiungi il nuovo messaggio dell'utente
            val userMsgJson = JSONObject()
            userMsgJson.put("role", "user")
            val userParts = JSONArray()
            userParts.put(JSONObject().put("text", userMessage))
            userMsgJson.put("parts", userParts)
            contentsArray.put(userMsgJson)

            rootJson.put("contents", contentsArray)

            // Configurazione generazione con limite token capiente per evitare troncamenti
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.7)
            genConfig.put("maxOutputTokens", 4096)
            rootJson.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errJson = JSONObject(responseBody)
                        errJson.optJSONObject("error")?.optString("message") ?: "Errore HTTP ${response.code}"
                    } catch (e: Exception) {
                        "Errore HTTP ${response.code}: $responseBody"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val replyTextBuilder = StringBuilder()
                        for (p in 0 until parts.length()) {
                            val partObj = parts.getJSONObject(p)
                            if (partObj.has("text")) {
                                replyTextBuilder.append(partObj.getString("text"))
                            }
                        }
                        val replyText = replyTextBuilder.toString().trim()
                        if (replyText.isNotEmpty()) {
                            return@withContext Result.success(replyText)
                        }
                    }
                }
                return@withContext Result.failure(Exception("Nessuna risposta generata da Gemini."))
            }
        } catch (e: Exception) {
            Log.e(tag, "Errore chiamata Gemini: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Invia un messaggio a Gemini interamente focalizzato e contestualizzato su uno specifico libro.
     */
    suspend fun sendBookSpecificChatMessage(
        book: BookEntity,
        userMessage: String,
        history: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Chiave API Gemini non configurata. Inserisci la tua API Key per iniziare a dialogare sull'opera.")
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        try {
            val rootJson = JSONObject()

            // Istruzione di sistema focalizzata sul singolo volume
            val systemInstructionJson = JSONObject()
            val systemPartsArray = JSONArray()
            val systemPart = JSONObject()

            val systemPrompt = buildString {
                appendLine("Sei un illustre critico ed esperto letterario. Stai dialogando con l'utente specificamente e approfonditamente sul seguente libro:")
                appendLine("• Titolo: ${book.title}")
                appendLine("• Autore: ${book.author}")
                if (book.publisher.isNotBlank()) appendLine("• Editore: ${book.publisher}")
                if (book.publishedYear.isNotBlank()) appendLine("• Anno di pubblicazione: ${book.publishedYear}")
                if (book.genre.isNotBlank()) appendLine("• Genere / Categoria: ${book.genre}")
                if (book.isbn.isNotBlank()) appendLine("• ISBN: ${book.isbn}")
                if (book.pageCount > 0) appendLine("• Pagine: ${book.pageCount}")
                if (book.customTags.isNotBlank()) appendLine("• Tag assegnati: ${book.customTags}")
                if (book.readingStatus.isNotBlank()) appendLine("• Stato di lettura nella libreria dell'utente: ${book.readingStatus}")
                if (book.rating > 0) appendLine("• Valutazione del lettore: ${book.rating} / 5 stelle")
                if (book.userNotes.isNotBlank()) appendLine("• Note personali del lettore: ${book.userNotes}")
                if (book.description.isNotBlank()) {
                    appendLine("• Descrizione / Sinossi:")
                    appendLine(book.description)
                }

                appendLine("\n[DIRETTIVE]")
                appendLine("1. Rispondi sempre in italiano con tono stimolante, colto, chiaro, accogliente ed empatico.")
                appendLine("2. Tutte le tue risposte devono essere focalizzate e pertinenti a questo libro (\"${book.title}\" di ${book.author}), al suo autore, ai suoi temi, ai personaggi, alla contestualizzazione storica/stilistica e a collegamenti letterari.")
                appendLine("3. Se l'utente chiede un riassunto o spiegazioni generali, EVITA spoiler clamorosi a meno che non chieda esplicitamente spiegazioni sul finale o colpi di scena.")
                appendLine("4. Struttura le risposte in modo leggibile ed esaustivo con paragrafi ben definiti ed elenchi puntati dove appropriato.")
            }

            systemPart.put("text", systemPrompt)
            systemPartsArray.put(systemPart)
            systemInstructionJson.put("parts", systemPartsArray)
            rootJson.put("systemInstruction", systemInstructionJson)

            // Cronologia + Nuovo Messaggio
            val contentsArray = JSONArray()

            val previousMessages = history.filter { !it.isError }
                .let { list ->
                    if (list.isNotEmpty() && list.last().sender == MessageSender.USER && list.last().text == userMessage) {
                        list.dropLast(1)
                    } else {
                        list
                    }
                }
                .takeLast(10)

            for (msg in previousMessages) {
                val msgJson = JSONObject()
                msgJson.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", msg.text))
                msgJson.put("parts", parts)
                contentsArray.put(msgJson)
            }

            val userMsgJson = JSONObject()
            userMsgJson.put("role", "user")
            val userParts = JSONArray()
            userParts.put(JSONObject().put("text", userMessage))
            userMsgJson.put("parts", userParts)
            contentsArray.put(userMsgJson)

            rootJson.put("contents", contentsArray)

            val genConfig = JSONObject()
            genConfig.put("temperature", 0.7)
            genConfig.put("maxOutputTokens", 4096)
            rootJson.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errJson = JSONObject(responseBody)
                        errJson.optJSONObject("error")?.optString("message") ?: "Errore HTTP ${response.code}"
                    } catch (e: Exception) {
                        "Errore HTTP ${response.code}: $responseBody"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val replyTextBuilder = StringBuilder()
                        for (p in 0 until parts.length()) {
                            val partObj = parts.getJSONObject(p)
                            if (partObj.has("text")) {
                                replyTextBuilder.append(partObj.getString("text"))
                            }
                        }
                        val replyText = replyTextBuilder.toString().trim()
                        if (replyText.isNotEmpty()) {
                            return@withContext Result.success(replyText)
                        }
                    }
                }
                return@withContext Result.failure(Exception("Nessuna risposta generata da Gemini."))
            }
        } catch (e: Exception) {
            Log.e(tag, "Errore chiamata Gemini mirata sul libro: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }
}
