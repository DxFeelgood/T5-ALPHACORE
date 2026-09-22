package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Modulo per la configurazione centralizzata del client HTTP OkHttp e
 * delle istanze Retrofit per Google Books, Open Library e altri servizi,
 * dotato di interceptor diagnostico e meccanismo di retry esponenziale
 * per gestire timeout temporanei e fallimenti transitori delle API.
 */
object NetworkClient {

    private const val TAG = "NetworkDiagnostic"
    private const val RETRY_TAG = "NetworkRetry"
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val WRITE_TIMEOUT_SECONDS = 15L
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val INITIAL_BACKOFF_MS = 500L
    private const val BACKOFF_MULTIPLIER = 2.0
    private const val MAX_BACKOFF_MS = 4000L
    private const val USER_AGENT = "ArchivioLibriApp/1.0 (Android; IT)"

    /**
     * Interceptor per impostare intestazioni HTTP comuni (User-Agent, Accept).
     */
    private val headerInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
        if (originalRequest.header("User-Agent") == null) {
            builder.header("User-Agent", USER_AGENT)
        }
        if (originalRequest.header("Accept") == null) {
            builder.header("Accept", "application/json, text/plain, */*")
        }
        chain.proceed(builder.build())
    }

    /**
     * Interceptor con algoritmo di Exponential Backoff:
     * Riprova automaticamente le richieste verso API esterne (Google Books, Open Library, ecc.)
     * in caso di timeout temporanei (SocketTimeoutException, ConnectException), errori I/O transitori
     * o codici di stato HTTP riprovabili (429 Too Many Requests, 500, 502, 503, 504).
     */
    val exponentialRetryInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        var attempt = 0
        var currentBackoff = INITIAL_BACKOFF_MS
        var lastException: IOException? = null
        var lastResponse: Response? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++
            try {
                // Esecuzione richiesta
                val response = chain.proceed(request)

                // Verifica se la risposta indica un errore temporaneo del server o rate limiting
                val statusCode = response.code
                val isTransientHttpError = statusCode in listOf(429, 500, 502, 503, 504)

                if (!isTransientHttpError || attempt >= MAX_RETRY_ATTEMPTS) {
                    return@Interceptor response
                }

                // Chiudiamo il body della risposta precedente prima del retry per evitare leak
                val retryAfterHeader = response.header("Retry-After")
                response.close()

                val backoffDelayMs = calculateBackoffDelay(currentBackoff, retryAfterHeader)
                Log.w(
                    RETRY_TAG,
                    "[HTTP $statusCode RETRY #$attempt/$MAX_RETRY_ATTEMPTS] Attesa di $backoffDelayMs ms prima di riprovare $url"
                )

                try {
                    Thread.sleep(backoffDelayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry interrotto per $url", ie)
                }

                currentBackoff = (currentBackoff * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_BACKOFF_MS)

            } catch (e: IOException) {
                lastException = e
                val isRetryableException = isRetryableNetworkError(e)

                if (!isRetryableException || attempt >= MAX_RETRY_ATTEMPTS) {
                    Log.e(
                        RETRY_TAG,
                        "[FATAL EXCEPTION #$attempt/$MAX_RETRY_ATTEMPTS] Errore non riprovabile o tentativi esauriti per $url: ${e.javaClass.simpleName} - ${e.message}"
                    )
                    throw e
                }

                val backoffDelayMs = calculateBackoffDelay(currentBackoff, null)
                Log.w(
                    RETRY_TAG,
                    "[TIMEOUT/IO ERROR RETRY #$attempt/$MAX_RETRY_ATTEMPTS] ${e.javaClass.simpleName} (${e.message}). Attesa di $backoffDelayMs ms prima del nuovo tentativo per $url"
                )

                try {
                    Thread.sleep(backoffDelayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry interrotto durante attesa per $url", ie)
                }

                currentBackoff = (currentBackoff * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_BACKOFF_MS)
            }
        }

        lastResponse ?: throw (lastException ?: IOException("Tentativi di retry esauriti ($MAX_RETRY_ATTEMPTS) per $url"))
    }

    /**
     * Calcola il ritardo di backoff aggiungendo un jitter casuale e rispettando l'header Retry-After
     */
    private fun calculateBackoffDelay(currentBackoff: Long, retryAfterSecondsHeader: String?): Long {
        if (!retryAfterSecondsHeader.isNullOrBlank()) {
            val seconds = retryAfterSecondsHeader.toLongOrNull()
            if (seconds != null && seconds in 1..10) {
                return seconds * 1000L
            }
        }
        // Jitter +/- 15% per evitare thundering herd
        val jitter = (Math.random() * 0.3 - 0.15) * currentBackoff
        return (currentBackoff + jitter).toLong().coerceIn(100L, MAX_BACKOFF_MS)
    }

    /**
     * Determina se l'eccezione di rete è causata da un timeout o da un problema transitorio riprovabile
     */
    private fun isRetryableNetworkError(e: IOException): Boolean {
        return when (e) {
            is java.net.SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.net.UnknownHostException -> true
            is java.io.InterruptedIOException -> !e.message.orEmpty().contains("canceled", ignoreCase = true)
            else -> {
                val msg = e.message.orEmpty().lowercase()
                msg.contains("timeout") ||
                        msg.contains("connection reset") ||
                        msg.contains("broken pipe") ||
                        msg.contains("unexpected end of stream") ||
                        msg.contains("software caused connection abort")
            }
        }
    }

    /**
     * Interceptor diagnostico di rete:
     * Registra l'esatto URL di richiesta, tutti gli headers e il corpo completo
     * delle risposte fallite (HTTP non-2xx o errori), fondamentale per diagnosticare
     * le chiamate a Google Books, Open Library e altri endpoint durante la ricerca ISBN.
     */
    private val isbnDiagnosticInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val isIsbnQuery = url.contains("isbn", ignoreCase = true) ||
                url.contains("googleapis.com/books", ignoreCase = true) ||
                url.contains("openlibrary.org", ignoreCase = true) ||
                url.contains("isbnsearch.org", ignoreCase = true)

        val startTime = System.currentTimeMillis()

        // Registrazione dettagliata della richiesta
        val requestHeadersSummary = request.headers.joinToString(separator = "\n  ") { (name, value) -> "$name: $value" }
        val reqLog = buildString {
            appendLine("--> HTTP ${request.method} $url")
            if (requestHeadersSummary.isNotBlank()) {
                appendLine("  Headers:\n  $requestHeadersSummary")
            }
        }
        if (isIsbnQuery) {
            Log.d(TAG, "[ISBN SEARCH REQUEST]\n$reqLog")
        } else {
            Log.d(TAG, reqLog.trimEnd())
        }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            val durationMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "[NETWORK EXCEPTION] ${request.method} $url ($durationMs ms) -> Exception: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }

        val durationMs = System.currentTimeMillis() - startTime
        val statusCode = response.code
        val isSuccessful = response.isSuccessful

        // Se la richiesta è fallita (es. 4xx, 5xx), registriamo l'intero corpo della risposta per la diagnosi
        if (!isSuccessful) {
            val errorBody = try {
                // Utilizza peekBody per leggere il corpo completo senza consumare lo stream per il chiamante
                response.peekBody(1024 * 1024).string()
            } catch (e: Exception) {
                "<Impossibile leggere il body della risposta: ${e.message}>"
            }

            val responseHeadersSummary = response.headers.joinToString(separator = "\n  ") { (name, value) -> "$name: $value" }

            val failureLog = buildString {
                appendLine("<-- HTTP FAILED: $statusCode ${response.message} ($durationMs ms)")
                appendLine("  URL: $url")
                appendLine("  Request Method: ${request.method}")
                if (requestHeadersSummary.isNotBlank()) {
                    appendLine("  Request Headers:\n  $requestHeadersSummary")
                }
                if (responseHeadersSummary.isNotBlank()) {
                    appendLine("  Response Headers:\n  $responseHeadersSummary")
                }
                appendLine("  FULL RESPONSE BODY:")
                appendLine(errorBody.ifBlank { "<EMPTY BODY>" })
                append("--------------------------------------------------")
            }

            Log.e(TAG, "[ISBN SEARCH HTTP FAILURE]\n$failureLog")
        } else {
            // Risposta HTTP 200 OK: verifica se ci sono indicatori di contenuto vuoto per query ISBN
            if (isIsbnQuery) {
                val bodyPreview = try {
                    response.peekBody(1024 * 512).string()
                } catch (e: Exception) {
                    ""
                }
                val isEmptyResult = bodyPreview.contains("\"totalItems\": 0") ||
                        bodyPreview.contains("\"items\": null") ||
                        bodyPreview.contains("\"docs\":[]") ||
                        bodyPreview.contains("\"numFound\":0") ||
                        bodyPreview.contains("Page Not Found") ||
                        bodyPreview.contains("does not exist")

                if (isEmptyResult) {
                    Log.w(
                        TAG,
                        "[ISBN SEARCH EMPTY RESULT] HTTP $statusCode from $url ($durationMs ms)\nBody Preview:\n${bodyPreview.take(500)}"
                    )
                } else {
                    Log.d(TAG, "<-- HTTP $statusCode ${response.message} for $url ($durationMs ms)")
                }
            } else {
                Log.d(TAG, "<-- HTTP $statusCode ${response.message} for $url ($durationMs ms)")
            }
        }

        response
    }

    /**
     * Interceptor di logging standard per HttpLoggingInterceptor.
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * Istanza condivisa del client OkHttp configurato con timeout, interceptors e retry.
     */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(exponentialRetryInterceptor)
            .addInterceptor(isbnDiagnosticInterceptor)
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Istanza Moshi con supporto a Kotlin reflection/codegen.
     */
    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Crea un'istanza Retrofit configurata con il client OkHttp diagnostico e il parser Moshi.
     */
    fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Client Retrofit per Google Books API
     */
    val googleBooksRetrofit: Retrofit by lazy {
        createRetrofit("https://www.googleapis.com/")
    }

    /**
     * Client Retrofit per Open Library API
     */
    val openLibraryRetrofit: Retrofit by lazy {
        createRetrofit("https://openlibrary.org/")
    }
}
