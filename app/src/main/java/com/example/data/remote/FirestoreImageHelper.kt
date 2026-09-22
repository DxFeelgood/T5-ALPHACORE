package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Base64
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Helper per la codifica e decodifica delle immagini di copertina dei libri
 * destinate al backup, alla sincronizzazione e al ripristino su Cloud Firestore.
 *
 * Le immagini vengono ottimizzate e compresse (JPEG ~80% a max 480px di risoluzione)
 * per garantire dimensioni contenute (20-50 KB per volume), perfettamente compatibili
 * con il limite massimo di 1 MiB per documento di Google Cloud Firestore.
 */
object FirestoreImageHelper {

    private const val TAG = "FirestoreImageHelper"
    private const val MAX_IMAGE_DIMENSION = 480
    private const val JPEG_QUALITY = 80
    private const val MAX_BYTE_SIZE = 600 * 1024 // 600 KB ceiling

    /**
     * Estrae e codifica in stringa Base64 l'immagine di copertina indicata da [coverUrl].
     * Supporta:
     * - File locali salvati nella memoria interna (es. scatti fotocamera o immagini da galleria)
     * - Indirizzi URI locali (file:// o content://)
     * - Stringhe già in formato data URI Base64 (data:image/...)
     * - URL remoti Web (http:// o https://) scaricati o caricati dalla cache Coil
     *
     * Restituisce null se la copertina è assente, vuota o non decodificabile.
     */
    suspend fun getBase64ImageForCover(context: Context?, coverUrl: String?): String? = withContext(Dispatchers.IO) {
        if (coverUrl.isNullOrBlank() || context == null) return@withContext null

        val trimmed = coverUrl.trim()

        // 1. Caso Base64 diretto
        if (trimmed.startsWith("data:image", ignoreCase = true)) {
            val base64Part = trimmed.substringAfter("base64,")
            return@withContext if (base64Part.isNotBlank()) base64Part.trim() else null
        }

        // 2. Caso File locale (percorso assoluto o file://)
        val localFilePath = when {
            trimmed.startsWith("file://", ignoreCase = true) -> trimmed.removePrefix("file://")
            trimmed.startsWith("/") -> trimmed
            else -> null
        }

        if (localFilePath != null) {
            val localFile = File(localFilePath)
            if (localFile.exists() && localFile.isFile && localFile.length() > 0) {
                val decoded = decodeSampledBitmapFromFile(localFile.absolutePath, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                if (decoded != null) {
                    val encoded = compressAndEncodeBitmap(decoded)
                    if (encoded != null) return@withContext encoded
                }
            }
        }

        // 3. Caso content:// URI
        if (trimmed.startsWith("content://", ignoreCase = true)) {
            try {
                val uri = Uri.parse(trimmed)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val encoded = compressAndEncodeBitmap(bmp)
                        if (encoded != null) return@withContext encoded
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Impossibile leggere content URI: ${e.message}")
            }
        }

        // 4. Caso Web URL (http:// o https://)
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            // Tentativo prioritario tramite Coil (sfrutta eventuale cache disco/memoria)
            val coilBitmap = withTimeoutOrNull(4500L) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(trimmed)
                        .allowHardware(false)
                        .size(MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        (result.drawable as? BitmapDrawable)?.bitmap
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            if (coilBitmap != null) {
                val encoded = compressAndEncodeBitmap(coilBitmap)
                if (encoded != null) return@withContext encoded
            }

            // Fallback con HttpURLConnection diretta con timeout rapido
            val directBitmap = withTimeoutOrNull(3500L) {
                try {
                    val url = java.net.URL(trimmed)
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 3000
                        readTimeout = 3000
                        instanceFollowRedirects = true
                    }
                    conn.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (directBitmap != null) {
                val encoded = compressAndEncodeBitmap(directBitmap)
                if (encoded != null) return@withContext encoded
            }
        }

        null
    }

    /**
     * Decodifica una stringa Base64 salvando i byte in un file locale JPEG all'interno
     * della directory privata dell'applicazione (files/covers/).
     *
     * Restituisce il percorso assoluto del file salvato sul dispositivo, oppure null se la stringa è vuota o corrotta.
     */
    fun saveBase64ToLocalStorage(context: Context?, base64String: String?, identifier: String): String? {
        if (base64String.isNullOrBlank() || context == null) return null

        return try {
            val cleanBase64 = if (base64String.contains("base64,")) {
                base64String.substringAfter("base64,")
            } else {
                base64String
            }.trim()

            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (bytes == null || bytes.isEmpty()) return null

            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }

            val safeId = identifier.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(24)
            val fileName = "cover_restored_${safeId}_${System.currentTimeMillis()}.jpg"
            val targetFile = File(coversDir, fileName)

            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }

            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il salvataggio locale della copertina ripristinata: ${e.message}")
            null
        }
    }

    /**
     * Comprime, scala se necessario e converte in Base64 il [bitmap].
     */
    private fun compressAndEncodeBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                val ratio = width.toFloat() / height.toFloat()
                val (targetW, targetH) = if (ratio > 1f) {
                    MAX_IMAGE_DIMENSION to (MAX_IMAGE_DIMENSION / ratio).toInt().coerceAtLeast(1)
                } else {
                    (MAX_IMAGE_DIMENSION * ratio).toInt().coerceAtLeast(1) to MAX_IMAGE_DIMENSION
                }
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            var bytes = baos.toByteArray()

            // Se l'immagine supera il limite di sicurezza (600 KB), applica un'ulteriore compressione a qualità 60
            if (bytes.size > MAX_BYTE_SIZE) {
                baos.reset()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                bytes = baos.toByteArray()
            }

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la compressione dell'immagine per Firestore: ${e.message}")
            null
        }
    }

    /**
     * Decodifica campionata da file per evitare consumo eccessivo di memoria.
     */
    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Riduce l'occupazione di memoria RAM
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Errore decodifica campionata bitmap: ${e.message}")
            null
        }
    }
}
