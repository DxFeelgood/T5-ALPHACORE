package com.example.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.EAN_13,
                BarcodeFormat.UPC_A,
                BarcodeFormat.QR_CODE
            ),
            DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE
        )
        setHints(hints)
    }

    private var isScanning = true

    fun resume() {
        isScanning = true
    }

    fun pause() {
        isScanning = false
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        try {
            val plane = imageProxy.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = imageProxy.width
            val height = imageProxy.height

            // Estrai il canale Y (grayscale) rispettando rowStride e pixelStride in modo ultra-sicuro
            val data = ByteArray(width * height)
            val rowBuffer = ByteArray(rowStride)
            buffer.rewind()
            for (y in 0 until height) {
                val remaining = buffer.remaining()
                if (remaining <= 0) break
                val bytesToRead = minOf(rowStride, remaining)
                buffer.get(rowBuffer, 0, bytesToRead)
                for (x in 0 until width) {
                    val index = x * pixelStride
                    if (index < bytesToRead) {
                        data[y * width + x] = rowBuffer[index]
                    }
                }
            }

            val rotation = imageProxy.imageInfo.rotationDegrees

            // Ruota se in portrait (90 o 270 gradi)
            val (rotatedData, finalWidth, finalHeight) = when (rotation) {
                90 -> Triple(rotate90(data, width, height), height, width)
                270 -> Triple(rotate270(data, width, height), height, width)
                180 -> Triple(rotate180(data, width, height), width, height)
                else -> Triple(data, width, height)
            }

            var result: com.google.zxing.Result? = null

            // Primo tentativo: scansione ritagliata al centro (corrispondente al mirino)
            // Se fallisce per qualsiasi motivo o lancia out of bounds, viene catturata autonomamente senza bloccare la pipeline
            try {
                val cropLeft = (finalWidth * 0.1).toInt()
                val cropTop = (finalHeight * 0.25).toInt()
                val cropWidth = (finalWidth * 0.8).toInt()
                val cropHeight = (finalHeight * 0.4).toInt()

                if (cropWidth > 0 && cropHeight > 0 && cropLeft >= 0 && cropTop >= 0 && (cropLeft + cropWidth) <= finalWidth && (cropTop + cropHeight) <= finalHeight) {
                    val sourceCropped = PlanarYUVLuminanceSource(
                        rotatedData,
                        finalWidth,
                        finalHeight,
                        cropLeft,
                        cropTop,
                        cropWidth,
                        cropHeight,
                        false
                    )
                    result = tryDecode(sourceCropped)
                }
            } catch (e: Exception) {
                android.util.Log.d("BarcodeAnalyzer", "Crop scan failed, falling back to full-screen: ${e.message}")
            }

            // Secondo tentativo: se la scansione ritagliata fallisce, prova a tutto schermo ruotato
            if (result == null) {
                try {
                    val sourceRotatedFull = PlanarYUVLuminanceSource(
                        rotatedData,
                        finalWidth,
                        finalHeight,
                        0,
                        0,
                        finalWidth,
                        finalHeight,
                        false
                    )
                    result = tryDecode(sourceRotatedFull)
                } catch (e: Exception) {
                    android.util.Log.d("BarcodeAnalyzer", "Rotated full-screen scan failed: ${e.message}")
                }
            }

            // Terzo tentativo: se fallisce ancora, prova con l'immagine originale non ruotata a tutto schermo
            if (result == null && (rotation == 90 || rotation == 270 || rotation == 180)) {
                try {
                    val sourceDirectFull = PlanarYUVLuminanceSource(
                        data,
                        width,
                        height,
                        0,
                        0,
                        width,
                        height,
                        false
                    )
                    result = tryDecode(sourceDirectFull)
                } catch (e: Exception) {
                    android.util.Log.d("BarcodeAnalyzer", "Direct full-screen scan failed: ${e.message}")
                }
            }

            if (result != null && result.text.isNotBlank()) {
                val rawCode = result.text.trim()
                val cleanCode = rawCode.replace("-", "").replace(" ", "").trim()
                val isNumeric = cleanCode.all { it.isDigit() || it == 'X' || it == 'x' }
                val isQr = result.barcodeFormat == BarcodeFormat.QR_CODE

                if (isQr) {
                    isScanning = false
                    onBarcodeDetected(cleanCode)
                } else if (isNumeric) {
                    // Accetta solo codici a 10 cifre (ISBN-10) o a 13 cifre che iniziano con i prefissi editoriali ufficiali 978/979
                    val isValidIsbn = (cleanCode.length == 13 && (cleanCode.startsWith("978") || cleanCode.startsWith("979"))) ||
                                      (cleanCode.length == 10)
                    
                    if (isValidIsbn) {
                        isScanning = false
                        onBarcodeDetected(cleanCode)
                    } else {
                        android.util.Log.d("BarcodeAnalyzer", "Ignored non-book barcode: '$cleanCode'")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BarcodeAnalyzer", "Analysis error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun tryDecode(source: PlanarYUVLuminanceSource): com.google.zxing.Result? {
        // 1. Prova con HybridBinarizer
        var bitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            return reader.decodeWithState(bitmap)
        } catch (e: Exception) {
            // ignore
        } finally {
            reader.reset()
        }

        // 2. Prova con GlobalHistogramBinarizer (molto più sensibile per i codici a barre 1D ad alto contrasto)
        bitmap = BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source))
        try {
            return reader.decodeWithState(bitmap)
        } catch (e: Exception) {
            // ignore
        } finally {
            reader.reset()
        }

        return null
    }

    private fun rotate90(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var k = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                rotated[k++] = data[y * width + x]
            }
        }
        return rotated
    }

    private fun rotate270(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var k = 0
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                rotated[k++] = data[y * width + x]
            }
        }
        return rotated
    }

    private fun rotate180(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(width * height)
        for (i in 0 until width * height) {
            rotated[i] = data[width * height - 1 - i]
        }
        return rotated
    }
}
