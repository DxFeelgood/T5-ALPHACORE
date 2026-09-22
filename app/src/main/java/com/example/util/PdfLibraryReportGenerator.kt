package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.local.BookEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opzioni di configurazione per la generazione del report PDF della libreria.
 */
data class PdfExportOptions(
    val title: String = "Catalogo Biblioteca Personale",
    val includeNotes: Boolean = true,
    val includeSummaryStats: Boolean = true,
    val sortBy: PdfSortOption = PdfSortOption.AUTHOR_ASC
)

enum class PdfSortOption(val displayName: String) {
    AUTHOR_ASC("Autore (A-Z) e Titolo"),
    TITLE_ASC("Titolo (A-Z)"),
    READING_STATUS("Stato di Lettura"),
    RATING_DESC("Valutazione (5-1 Stelle)"),
    RECENT_FIRST("Aggiunti di Recente")
}

/**
 * Risultato della generazione del documento PDF.
 */
data class GeneratedPdfResult(
    val file: File,
    val uri: Uri,
    val pageCount: Int,
    val bookCount: Int,
    val fileSizeBytes: Long
)

/**
 * Generatore nativo per il riepilogo PDF dell'intera collezione di libri.
 * Organizzato con sezioni intestate per autore, ordinamento alfabetico e miniature compresse.
 */
object PdfLibraryReportGenerator {

    private const val PAGE_WIDTH = 595 // Standard A4 (punti tipografici a 72 DPI)
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val MARGIN_TOP = 36f
    private const val MARGIN_BOTTOM = 806f
    private const val USABLE_WIDTH = MARGIN_RIGHT - MARGIN_LEFT // 523f

    suspend fun generateLibraryPdf(
        context: Context,
        books: List<BookEntity>,
        options: PdfExportOptions = PdfExportOptions()
    ): Result<GeneratedPdfResult> {
        return try {
            if (books.isEmpty()) {
                return Result.failure(IllegalStateException("Nessun libro presente nel catalogo da esportare in PDF."))
            }

            // Grouping by author and sorting books within author section
            val authorGroups = books
                .groupBy { it.author.trim().ifBlank { "Autore Sconosciuto" } }
                .mapValues { (_, authorBooks) ->
                    when (options.sortBy) {
                        PdfSortOption.AUTHOR_ASC, PdfSortOption.TITLE_ASC -> authorBooks.sortedBy { it.title.lowercase() }
                        PdfSortOption.READING_STATUS -> authorBooks.sortedWith(compareBy({
                            when (it.readingStatus.uppercase()) {
                                "LETTO" -> 1
                                "IN_LETTURA" -> 2
                                else -> 3
                            }
                        }, { it.title.lowercase() }))
                        PdfSortOption.RATING_DESC -> authorBooks.sortedWith(compareByDescending<BookEntity> { it.rating }.thenBy { it.title.lowercase() })
                        PdfSortOption.RECENT_FIRST -> authorBooks.sortedByDescending { it.dateAdded }
                    }
                }
                .toList()
                .sortedBy { (authorName, _) -> authorName.lowercase() }

            // Pre-caricamento e compressione miniature copertina (leggerezza PDF garantita)
            val thumbnailMap = mutableMapOf<Long, Bitmap?>()
            for (book in books) {
                if (!book.coverUrl.isNullOrBlank()) {
                    val compressedBmp = loadCompressedThumbnail(context, book.coverUrl)
                    if (compressedBmp != null) {
                        thumbnailMap[book.id] = compressedBmp
                    }
                }
            }

            val pdfDocument = PdfDocument()
            var pageNumber = 1

            // Setup Paints
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59) // Slate 800
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139) // Slate 500
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105) // Slate 600
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                color = Color.rgb(226, 232, 240) // Slate 200
            }

            // Statistiche complessive
            val totalBooks = books.size
            val readCount = books.count { it.readingStatus.equals("LETTO", ignoreCase = true) }
            val inProgressCount = books.count { it.readingStatus.equals("IN_LETTURA", ignoreCase = true) }
            val unreadCount = books.count { it.readingStatus.equals("DA_LEGGERE", ignoreCase = true) }
            val totalPagesSum = books.sumOf { it.pageCount }
            val ratedBooks = books.filter { it.rating > 0 }
            val avgRating = if (ratedBooks.isNotEmpty()) ratedBooks.map { it.rating }.average() else 0.0

            val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.ITALIAN)
            val currentDateStr = dateFormat.format(Date())

            var currentPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDocument.startPage(currentPageInfo)
            var canvas = currentPage.canvas
            var currentY = MARGIN_TOP

            // Disegna Intestazione Prima Pagina
            currentY = drawCoverHeader(
                canvas = canvas,
                options = options,
                currentDateStr = currentDateStr,
                totalBooks = totalBooks,
                readCount = readCount,
                inProgressCount = inProgressCount,
                unreadCount = unreadCount,
                totalPagesSum = totalPagesSum,
                avgRating = avgRating,
                startY = currentY
            )

            var globalIndex = 1

            // Itera sulle sezioni Autore e disegna intestazione sezione e libri
            for ((authorName, authorBooks) in authorGroups) {
                // Verifico se occorre spazio per l'intestazione autore + almeno 1 libro
                if (currentY + 28f + 42f > MARGIN_BOTTOM - 20f) {
                    drawFooter(canvas, pageNumber)
                    pdfDocument.finishPage(currentPage)

                    pageNumber++
                    currentPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    currentPage = pdfDocument.startPage(currentPageInfo)
                    canvas = currentPage.canvas
                    currentY = MARGIN_TOP

                    currentY = drawRunningHeader(canvas, options.title, currentY)
                }

                // Disegna Sezione Titolo Autore
                currentY = drawAuthorHeader(
                    canvas = canvas,
                    authorName = authorName,
                    bookCount = authorBooks.size,
                    startY = currentY
                )

                // Disegna i libri dell'autore
                for (book in authorBooks) {
                    val hasNotes = options.includeNotes && book.userNotes.isNotBlank()
                    val estimatedHeight = if (hasNotes) 52f else 38f

                    // Controllo cambio pagina per libro
                    if (currentY + estimatedHeight > MARGIN_BOTTOM - 20f) {
                        drawFooter(canvas, pageNumber)
                        pdfDocument.finishPage(currentPage)

                        pageNumber++
                        currentPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        currentPage = pdfDocument.startPage(currentPageInfo)
                        canvas = currentPage.canvas
                        currentY = MARGIN_TOP

                        currentY = drawRunningHeader(canvas, "${options.title} — $authorName", currentY)
                    }

                    // Disegna singolo libro con miniatura
                    currentY = drawBookItem(
                        canvas = canvas,
                        book = book,
                        globalIndex = globalIndex,
                        coverBitmap = thumbnailMap[book.id],
                        hasNotes = hasNotes,
                        startY = currentY,
                        textPaint = textPaint,
                        boldPaint = boldPaint,
                        subPaint = subPaint,
                        notePaint = notePaint,
                        bgPaint = bgPaint,
                        strokePaint = strokePaint
                    )

                    globalIndex++
                }

                currentY += 6f // Spaziatura tra sezioni autore
            }

            // Disegna footer sull'ultima pagina
            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(currentPage)

            // Salva su file nella cache dell'app
            val pdfDir = File(context.cacheDir, "pdf_reports").apply { if (!exists()) mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val pdfFile = File(pdfDir, "Catalogo_Libri_$timeStamp.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            Result.success(
                GeneratedPdfResult(
                    file = pdfFile,
                    uri = uri,
                    pageCount = pageNumber,
                    bookCount = totalBooks,
                    fileSizeBytes = pdfFile.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Carica e comprime la copertina ad una miniatura JPEG ultraleggera.
     */
    private suspend fun loadCompressedThumbnail(context: Context, coverUrl: String?): Bitmap? {
        if (coverUrl.isNullOrBlank()) return null
        return try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(72, 105) // Richiede il ridimensionamento diretto da Coil
                .allowHardware(false) // Fondamentale per il Canvas del PdfDocument!
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    val orig = drawable.bitmap
                    val stream = ByteArrayOutputStream()
                    orig.compress(Bitmap.CompressFormat.JPEG, 65, stream)
                    val compressedBytes = stream.toByteArray()
                    BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun drawCoverHeader(
        canvas: Canvas,
        options: PdfExportOptions,
        currentDateStr: String,
        totalBooks: Int,
        readCount: Int,
        inProgressCount: Int,
        unreadCount: Int,
        totalPagesSum: Int,
        avgRating: Double,
        startY: Float
    ): Float {
        var y = startY

        // Banner superiore Slate Navy
        val headerRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 64f)
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // Slate 900
        }
        canvas.drawRoundRect(headerRect, 8f, 8f, bannerPaint)

        // Titolo Banner
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(options.title.uppercase(), MARGIN_LEFT + 14f, y + 24f, titlePaint)

        // Sottotitolo e Data
        val subBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184) // Slate 400
            textSize = 8.5f
        }
        canvas.drawText("Inventario Biblioteca Personale · $currentDateStr", MARGIN_LEFT + 14f, y + 40f, subBannerPaint)
        canvas.drawText("Organizzazione: Sezioni Autore (A-Z) & Titolo · Miniature Compresse", MARGIN_LEFT + 14f, y + 53f, subBannerPaint)

        y += 72f

        // Scheda Statistiche Sintetiche
        if (options.includeSummaryStats) {
            val statsRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 44f)
            val statsBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 250, 252) // Slate 50
            }
            val statsStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
                color = Color.rgb(226, 232, 240) // Slate 200
            }
            canvas.drawRoundRect(statsRect, 6f, 6f, statsBgPaint)
            canvas.drawRoundRect(statsRect, 6f, 6f, statsStrokePaint)

            val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val statValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val colWidth = USABLE_WIDTH / 4f

            // Colonna 1: Totale
            canvas.drawText("TOTALE VOLUMI", MARGIN_LEFT + 10f, y + 16f, statLabelPaint)
            canvas.drawText("$totalBooks libri", MARGIN_LEFT + 10f, y + 32f, statValPaint)

            // Colonna 2: Letti / In Lettura
            canvas.drawText("STATO LETTURA", MARGIN_LEFT + colWidth + 5f, y + 16f, statLabelPaint)
            canvas.drawText("$readCount L · $inProgressCount Prog · $unreadCount Da L", MARGIN_LEFT + colWidth + 5f, y + 32f, statValPaint.apply { textSize = 9f })

            // Colonna 3: Pagine
            canvas.drawText("PAGINE CUMULATE", MARGIN_LEFT + colWidth * 2f + 5f, y + 16f, statLabelPaint)
            val pagesText = if (totalPagesSum > 0) "$totalPagesSum pag." else "N/D"
            canvas.drawText(pagesText, MARGIN_LEFT + colWidth * 2f + 5f, y + 32f, statValPaint.apply { textSize = 10.5f })

            // Colonna 4: Media Voto
            canvas.drawText("VALUTAZIONE MEDIA", MARGIN_LEFT + colWidth * 3f + 5f, y + 16f, statLabelPaint)
            val ratingText = if (avgRating > 0) String.format(Locale.US, "%.1f ★", avgRating) else "—"
            canvas.drawText(ratingText, MARGIN_LEFT + colWidth * 3f + 5f, y + 32f, statValPaint.apply { textSize = 10.5f })

            y += 52f
        }

        // Intestazione tabella elenco
        val tableHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("#", MARGIN_LEFT + 2f, y + 8f, tableHeadPaint)
        canvas.drawText("COPERTINA & TITOLO", MARGIN_LEFT + 18f, y + 8f, tableHeadPaint)
        canvas.drawText("DETTAGLI & GENERE", MARGIN_LEFT + 270f, y + 8f, tableHeadPaint)
        canvas.drawText("STATO & VOTO", MARGIN_RIGHT - 75f, y + 8f, tableHeadPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN_LEFT, y + 14f, MARGIN_RIGHT, y + 14f, linePaint)

        return y + 18f
    }

    private fun drawRunningHeader(canvas: Canvas, title: String, startY: Float): Float {
        var y = startY
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(title, MARGIN_LEFT, y + 10f, headPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.75f
        }
        canvas.drawLine(MARGIN_LEFT, y + 16f, MARGIN_RIGHT, y + 16f, linePaint)

        y += 22f

        val tableHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("#", MARGIN_LEFT + 2f, y + 6f, tableHeadPaint)
        canvas.drawText("COPERTINA & TITOLO", MARGIN_LEFT + 18f, y + 6f, tableHeadPaint)
        canvas.drawText("DETTAGLI & GENERE", MARGIN_LEFT + 270f, y + 6f, tableHeadPaint)
        canvas.drawText("STATO & VOTO", MARGIN_RIGHT - 75f, y + 6f, tableHeadPaint)
        canvas.drawLine(MARGIN_LEFT, y + 12f, MARGIN_RIGHT, y + 12f, linePaint)

        return y + 16f
    }

    /**
     * Disegna la barra di intestazione della sezione Autore.
     */
    private fun drawAuthorHeader(
        canvas: Canvas,
        authorName: String,
        bookCount: Int,
        startY: Float
    ): Float {
        val y = startY

        // Sfondo barra sezione
        val headerRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 22f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 245, 249) // Slate 100
        }
        canvas.drawRoundRect(headerRect, 4f, 4f, bgPaint)

        // Barretta d'accento laterale
        val accentRect = RectF(MARGIN_LEFT, y, MARGIN_LEFT + 4f, y + 22f)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // Slate 900
        }
        canvas.drawRoundRect(accentRect, 2f, 2f, accentPaint)

        // Titolo Autore
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleStr = "AUTORE: ${authorName.uppercase()}"
        canvas.drawText(truncateText(titleStr, 55), MARGIN_LEFT + 12f, y + 15f, authorPaint)

        // Conteggio volumi a destra
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val countStr = "$bookCount ${if (bookCount == 1) "volume" else "volumi"}"
        canvas.drawText(countStr, MARGIN_RIGHT - 10f, y + 15f, countPaint)

        return y + 26f
    }

    private fun drawBookItem(
        canvas: Canvas,
        book: BookEntity,
        globalIndex: Int,
        coverBitmap: Bitmap?,
        hasNotes: Boolean,
        startY: Float,
        textPaint: Paint,
        boldPaint: Paint,
        subPaint: Paint,
        notePaint: Paint,
        bgPaint: Paint,
        strokePaint: Paint
    ): Float {
        var y = startY

        // Sfondo alternato per facilitare la lettura
        if (globalIndex % 2 == 0) {
            val itemHeight = if (hasNotes) 52f else 38f
            val itemRect = RectF(MARGIN_LEFT, y - 1f, MARGIN_RIGHT, y + itemHeight - 2f)
            bgPaint.color = Color.rgb(248, 250, 252) // Slate 50
            canvas.drawRoundRect(itemRect, 4f, 4f, bgPaint)
        }

        // Numero progressivo
        val numPaint = Paint(subPaint).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("$globalIndex", MARGIN_LEFT + 2f, y + 12f, numPaint)

        // Miniature Copertina compressa (24f x 34f)
        val thumbRect = RectF(MARGIN_LEFT + 18f, y + 2f, MARGIN_LEFT + 42f, y + 36f)
        if (coverBitmap != null && !coverBitmap.isRecycled) {
            val srcRect = Rect(0, 0, coverBitmap.width, coverBitmap.height)
            canvas.drawBitmap(coverBitmap, srcRect, thumbRect, null)
            val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                color = Color.rgb(203, 213, 225) // Slate 300
            }
            canvas.drawRoundRect(thumbRect, 2f, 2f, thumbBorderPaint)
        } else {
            // Placeholder per copertina non presente
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(241, 245, 249) // Slate 100
            }
            canvas.drawRoundRect(thumbRect, 2f, 2f, placeholderPaint)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                color = Color.rgb(203, 213, 225)
            }
            canvas.drawRoundRect(thumbRect, 2f, 2f, borderPaint)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 9f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("📖", thumbRect.centerX(), thumbRect.centerY() + 3f, iconPaint)
        }

        // Titolo Libro
        val titleText = truncateText(book.title, 38)
        canvas.drawText(titleText, MARGIN_LEFT + 48f, y + 12f, boldPaint)

        // Dettagli sotto titolo
        val subLine = buildString {
            if (book.publishedYear.isNotBlank()) append("(${book.publishedYear}) ")
            if (book.publisher.isNotBlank()) append("${book.publisher} ")
            if (book.pageCount > 0) append("· ${book.pageCount} pag.")
        }
        canvas.drawText(truncateText(subLine.ifBlank { "—" }, 42), MARGIN_LEFT + 48f, y + 24f, subPaint)

        // Colonna Centrale: Dettagli Extra, Genere, Scaffale
        val metaSecondLine = buildString {
            if (book.genre.isNotBlank()) append(book.genre)
            if (book.shelfLocation.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append("📍 ${book.shelfLocation}")
            } else if (book.isbn.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append("ISBN: ${book.isbn}")
            }
        }
        canvas.drawText(truncateText(metaSecondLine.ifBlank { "—" }, 32), MARGIN_LEFT + 270f, y + 12f, textPaint)

        if (book.customTags.isNotBlank()) {
            canvas.drawText(truncateText("🏷️ ${book.customTags}", 32), MARGIN_LEFT + 270f, y + 24f, subPaint)
        }

        // Stato di Lettura
        val statusText = when (book.readingStatus.uppercase()) {
            "LETTO" -> "LETTO"
            "IN_LETTURA" -> "IN LETTURA"
            else -> "DA LEGGERE"
        }
        val statusColor = when (book.readingStatus.uppercase()) {
            "LETTO" -> Color.rgb(22, 101, 52)
            "IN_LETTURA" -> Color.rgb(30, 64, 175)
            else -> Color.rgb(180, 83, 9)
        }
        val statusPaint = Paint(boldPaint).apply {
            color = statusColor
            textSize = 8.5f
        }
        canvas.drawText(statusText, MARGIN_RIGHT - 75f, y + 12f, statusPaint)

        // Valutazione a Stelle
        if (book.rating > 0) {
            val starString = buildString {
                val fullStars = book.rating.toInt().coerceIn(0, 5)
                for (s in 1..fullStars) append("★")
                for (s in (fullStars + 1)..5) append("☆")
            }
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(202, 138, 4)
                textSize = 8.5f
            }
            canvas.drawText(starString, MARGIN_RIGHT - 75f, y + 24f, starPaint)
        }

        y += 28f

        // Note personali se opzione attiva
        if (hasNotes) {
            val noteRect = RectF(MARGIN_LEFT + 48f, y, MARGIN_LEFT + 50f, y + 10f)
            bgPaint.color = Color.rgb(148, 163, 184)
            canvas.drawRoundRect(noteRect, 1f, 1f, bgPaint)

            val cleanNote = book.userNotes.replace("\n", " ")
            canvas.drawText("Note: ${truncateText(cleanNote, 75)}", MARGIN_LEFT + 56f, y + 8f, notePaint)
            y += 14f
        }

        // Linea divisoria sottile
        canvas.drawLine(MARGIN_LEFT, y + 2f, MARGIN_RIGHT, y + 2f, strokePaint)

        return y + 4f
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val y = MARGIN_BOTTOM + 14f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN_LEFT, y - 6f, MARGIN_RIGHT, y - 6f, linePaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8f
        }
        canvas.drawText("Generato con Archivio Libri · Catalogo Personale", MARGIN_LEFT, y + 4f, footerPaint)

        val pageText = "Pagina $pageNumber"
        val pageTextWidth = footerPaint.measureText(pageText)
        canvas.drawText(pageText, MARGIN_RIGHT - pageTextWidth, y + 4f, footerPaint)
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.substring(0, (maxLength - 3).coerceAtLeast(0)) + "..."
        } else {
            text
        }
    }

    /**
     * Apre il visualizzatore PDF predefinito del sistema.
     */
    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Visualizza Catalogo PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Nessuna app di lettura PDF trovata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Condivide o invia il PDF via email, WhatsApp, Google Drive o altre app.
     */
    fun sharePdf(context: Context, uri: Uri, bookCount: Int) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Catalogo Biblioteca Personale ($bookCount volumi)")
                putExtra(Intent.EXTRA_TEXT, "In allegato il riepilogo in formato PDF della mia collezione di libri ($bookCount volumi).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Condividi o Salva PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Errore nella condivisione del PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Invia il documento al servizio di stampa di Android.
     */
    fun printPdf(context: Context, file: File, jobName: String = "Catalogo Libri") {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val printAdapter = object : android.print.PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: android.os.Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val info = android.print.PrintDocumentInfo.Builder("$jobName.pdf")
                            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build()
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out android.print.PageRange>?,
                        destination: android.os.ParcelFileDescriptor?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        try {
                            val input = java.io.FileInputStream(file)
                            val output = java.io.FileOutputStream(destination?.fileDescriptor)
                            val buf = ByteArray(16384)
                            var bytesRead: Int
                            while (input.read(buf).also { bytesRead = it } >= 0) {
                                if (cancellationSignal?.isCanceled == true) {
                                    callback?.onWriteCancelled()
                                    input.close()
                                    output.close()
                                    return
                                }
                                output.write(buf, 0, bytesRead)
                            }
                            input.close()
                            output.close()
                            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                        }
                    }
                }
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            } else {
                Toast.makeText(context, "Servizio di stampa non disponibile su questo dispositivo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Errore durante l'invio alla stampante: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
