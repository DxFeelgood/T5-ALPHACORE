package com.example.data.repository

import android.content.Context
import com.example.data.local.BookDao
import com.example.data.local.BookEntity
import com.example.data.remote.BookLookupService
import com.example.data.remote.CascadeStepStatus
import com.example.data.remote.DirectExportResult
import com.example.data.remote.DirectImportResult
import com.example.data.remote.FirebaseHelper
import com.example.data.remote.FirestoreImageHelper
import com.example.data.remote.LookupResult
import com.example.data.remote.SyncReport
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class BookRepository(
    private val bookDao: BookDao,
    private val lookupService: BookLookupService = BookLookupService(),
    private val context: Context? = null
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val totalCount: Flow<Int> = bookDao.getTotalCount()
    val readCount: Flow<Int> = bookDao.getReadCount()
    val readBooks: Flow<List<BookEntity>> = bookDao.getReadBooks()

    fun searchBooks(query: String): Flow<List<BookEntity>> = bookDao.searchBooks(query)

    fun filterByStatus(status: String): Flow<List<BookEntity>> = bookDao.filterByStatus(status)

    fun getBookById(id: Long): Flow<BookEntity?> = bookDao.getBookById(id)

    suspend fun getBookByIdDirect(id: Long): BookEntity? = bookDao.getBookByIdDirect(id)

    suspend fun findBookByIsbn(isbn: String): BookEntity? = bookDao.findBookByIsbn(isbn)

    suspend fun findBookByTitleAndAuthor(title: String, author: String): BookEntity? = bookDao.findBookByTitleAndAuthor(title, author)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun insertBooks(books: List<BookEntity>): List<Long> = bookDao.insertBooks(books)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)

    suspend fun deleteBookById(id: Long) = bookDao.deleteBookById(id)

    /**
     * Salva o aggiorna un libro nella cache locale Room impostando il flag di sincronizzazione a false.
     */
    suspend fun cacheBookOffline(book: BookEntity): Long {
        return bookDao.insertBook(book.copy(isSynced = false))
    }

    /**
     * Recupera tutti i libri memorizzati nella cache locale non ancora sincronizzati con Firestore.
     */
    suspend fun getUnsyncedBooks(): List<BookEntity> = bookDao.getUnsyncedBooks()

    /**
     * Recupera i libri con stato 'LETTO' salvati offline in attesa di sincronizzazione.
     */
    suspend fun getUnsyncedReadBooks(): List<BookEntity> = bookDao.getUnsyncedReadBooks()

    /**
     * Sincronizza i libri letti offline verso la collezione 'books' di Firestore
     * e aggiorna lo stato di sincronizzazione locale in Room.
     */
    suspend fun syncReadBooksToFirestore(): Result<Int> {
        val unsyncedRead = bookDao.getUnsyncedReadBooks()
        if (unsyncedRead.isEmpty()) {
            return Result.success(0)
        }

        var successCount = 0
        val syncedIds = mutableListOf<Long>()

        for (book in unsyncedRead) {
            val result = addBookToFirestore(book)
            if (result.isSuccess) {
                successCount++
                if (book.id > 0) syncedIds.add(book.id)
            }
        }

        if (syncedIds.isNotEmpty()) {
            bookDao.markBooksAsSynced(syncedIds, System.currentTimeMillis())
        }

        return Result.success(successCount)
    }

    /**
     * Sincronizza tutti i libri offline verso Firestore e aggiorna Room.
     */
    suspend fun syncAllUnsyncedBooksToFirestore(): Result<Int> {
        val unsynced = bookDao.getUnsyncedBooks()
        if (unsynced.isEmpty()) {
            return Result.success(0)
        }

        var successCount = 0
        val syncedIds = mutableListOf<Long>()

        for (book in unsynced) {
            val result = addBookToFirestore(book)
            if (result.isSuccess) {
                successCount++
                if (book.id > 0) syncedIds.add(book.id)
            }
        }

        if (syncedIds.isNotEmpty()) {
            bookDao.markBooksAsSynced(syncedIds, System.currentTimeMillis())
        }

        return Result.success(successCount)
    }

    suspend fun lookupBookCascade(
        isbn: String,
        publisherFilter: String = "",
        yearFilter: String = "",
        onStepUpdate: (CascadeStepStatus) -> Unit = {}
    ): LookupResult? {
        return lookupService.lookupBookCascade(isbn, publisherFilter, yearFilter, onStepUpdate)
    }

    suspend fun searchEditions(
        title: String,
        author: String
    ): List<LookupResult> {
        return lookupService.searchEditionsByTitleAndAuthor(title, author)
    }

    suspend fun enrichEditionMetadata(edition: LookupResult): LookupResult {
        return lookupService.enrichMetadata(edition)
    }

    /**
     * Salva i metadati di un libro come documento nella collezione 'books' di Firestore.
     * Restituisce un Result con l'identificativo del documento salvato.
     */
    suspend fun addBookToFirestore(book: BookEntity): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseHelper.getFirestore()
            val docRef = if (book.id > 0) {
                db.collection("books").document(book.id.toString())
            } else if (book.isbn.isNotBlank()) {
                db.collection("books").document(book.isbn.trim())
            } else {
                db.collection("books").document()
            }

            val coverBase64 = FirestoreImageHelper.getBase64ImageForCover(context, book.coverUrl)

            val bookData = hashMapOf<String, Any?>(
                "id" to book.id,
                "isbn" to book.isbn,
                "title" to book.title,
                "author" to book.author,
                "publisher" to book.publisher,
                "publishedYear" to book.publishedYear,
                "description" to book.description,
                "coverUrl" to book.coverUrl,
                "coverImageBase64" to coverBase64,
                "hasCoverImage" to (coverBase64 != null),
                "pageCount" to book.pageCount,
                "genre" to book.genre,
                "customTags" to book.customTags,
                "readingStatus" to book.readingStatus,
                "rating" to book.rating,
                "userNotes" to book.userNotes,
                "shelfLocation" to book.shelfLocation,
                "sourceApi" to book.sourceApi,
                "dateAdded" to book.dateAdded
            )

            Tasks.await(docRef.set(bookData), 10, java.util.concurrent.TimeUnit.SECONDS)
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Esegue l'esportazione DIRETTA di tutti i libri presenti nel database Room
     * direttamente sulla collezione Cloud Firestore ('books'), SENZA generazione o manipolazione di stringhe JSON.
     * Utilizza le credenziali e la configurazione fornite da google-services.json.
     */
    suspend fun directExportAllBooksToFirestore(onLog: (String) -> Unit = {}): Result<DirectExportResult> = withContext(Dispatchers.IO) {
        try {
            onLog("Inizializzazione esportazione diretta...")
            val db = FirebaseHelper.getFirestore()
            onLog("Lettura libri locali da Room...")
            val localBooks = bookDao.getAllBooksDirect()
            if (localBooks.isEmpty()) {
                onLog("Nessun volume trovato in locale.")
                return@withContext Result.success(
                    DirectExportResult(
                        exportedCount = 0,
                        totalBooks = 0,
                        message = "Nessun volume trovato nel database locale Room da esportare."
                    )
                )
            }

            onLog("Trovati ${localBooks.size} libri da esportare. Preparazione dei batch (dati e immagini)...")
            var successCount = 0
            var errorCount = 0
            var totalImagesExported = 0
            val now = System.currentTimeMillis()
            val syncedIds = mutableListOf<Long>()

            // Batch scrittura diretta su Firestore (ottimizzato a blocchi di 25 documenti per gestire in sicurezza i payload delle immagini)
            val chunkSize = 25
            val chunks = localBooks.chunked(chunkSize)

            for ((index, chunk) in chunks.withIndex()) {
                onLog("Elaborazione blocco ${index + 1}/${chunks.size} (${chunk.size} libri)... Codifica immagini in corso...")
                val batch = db.batch()
                val batchIds = mutableListOf<Long>()
                var chunkImages = 0

                for (book in chunk) {
                    val docRef = if (book.id > 0) {
                        db.collection("books").document(book.id.toString())
                    } else if (book.isbn.isNotBlank()) {
                        db.collection("books").document(book.isbn.trim())
                    } else {
                        db.collection("books").document()
                    }

                    // Estrazione e codifica dell'immagine copertina per il salvataggio su Firestore
                    val coverBase64 = FirestoreImageHelper.getBase64ImageForCover(context, book.coverUrl)
                    if (coverBase64 != null) {
                        chunkImages++
                    }

                    val bookMap = hashMapOf<String, Any?>(
                        "id" to book.id,
                        "isbn" to book.isbn,
                        "title" to book.title,
                        "author" to book.author,
                        "publisher" to book.publisher,
                        "publishedYear" to book.publishedYear,
                        "description" to book.description,
                        "coverUrl" to book.coverUrl,
                        "coverImageBase64" to coverBase64,
                        "hasCoverImage" to (coverBase64 != null),
                        "pageCount" to book.pageCount,
                        "genre" to book.genre,
                        "customTags" to book.customTags,
                        "readingStatus" to book.readingStatus,
                        "rating" to book.rating,
                        "userNotes" to book.userNotes,
                        "shelfLocation" to book.shelfLocation,
                        "sourceApi" to book.sourceApi,
                        "dateAdded" to book.dateAdded,
                        "isSynced" to true,
                        "lastSyncedAt" to now,
                        "directExportedAt" to now
                    )
                    batch.set(docRef, bookMap)
                    if (book.id > 0) batchIds.add(book.id)
                }

                try {
                    onLog("Invio blocco ${index + 1} a Firestore (${chunk.size} libri, $chunkImages immagini incluse)...")
                    Tasks.await(batch.commit(), 15, java.util.concurrent.TimeUnit.SECONDS)
                    successCount += chunk.size
                    totalImagesExported += chunkImages
                    syncedIds.addAll(batchIds)
                    onLog("Blocco ${index + 1} completato con successo ($chunkImages immagini caricate)!")
                } catch (e: Exception) {
                    errorCount += chunk.size
                    val formattedErr = FirebaseHelper.formatFirestoreError(e)
                    onLog("ERRORE blocco ${index + 1}: $formattedErr")
                }
            }

            if (syncedIds.isNotEmpty()) {
                onLog("Salvataggio stato di sincronizzazione locale...")
                bookDao.markBooksAsSynced(syncedIds, now)
            }

            if (successCount == 0 && errorCount > 0) {
                val errorSummary = "Esportazione Firestore non riuscita ($errorCount errori su ${localBooks.size} libri). Progetto '${FirebaseHelper.getProjectId()}'. Verifica regole di sicurezza o google-services.json."
                onLog("RIEPILOGO: $errorSummary")
                val result = DirectExportResult(
                    exportedCount = 0,
                    failedCount = errorCount,
                    totalBooks = localBooks.size,
                    imagesCount = 0,
                    collectionName = "books",
                    timestamp = now,
                    message = errorSummary
                )
                return@withContext Result.failure(Exception(errorSummary))
            }

            val msg = if (errorCount > 0) {
                "Esportazione parziale completata: $successCount libri e $totalImagesExported immagini caricati, $errorCount non riusciti su ${localBooks.size} libri."
            } else {
                "Esportazione diretta su Cloud Firestore completata! ($successCount/${localBooks.size} volumi e $totalImagesExported immagini di copertina salvati con successo sul cloud)"
            }
            onLog(msg)

            val result = DirectExportResult(
                exportedCount = successCount,
                failedCount = errorCount,
                totalBooks = localBooks.size,
                imagesCount = totalImagesExported,
                collectionName = "books",
                timestamp = now,
                message = msg
            )
            Result.success(result)
        } catch (e: Exception) {
            val formatted = FirebaseHelper.formatFirestoreError(e)
            onLog("ERRORE CRITICO: $formatted")
            Result.failure(Exception(formatted, e))
        }
    }
    /**
      * Forza l'upload dei libri memorizzati in locale su Room verso Firestore,
      * gestendo i conflitti di versione tra locale e remoto.
      */
    suspend fun forceSyncToFirestoreWithConflictResolution(onLog: (String) -> Unit = {}): Result<SyncReport> = withContext(Dispatchers.IO) {
        try {
            onLog("Inizio sincronizzazione bidirezionale con risoluzione conflitti...")
            val db = FirebaseHelper.getFirestore()
            onLog("Lettura libri locali da Room...")
            val localBooks = bookDao.getAllBooksDirect()
            if (localBooks.isEmpty()) {
                onLog("Nessun libro presente nel database locale Room.")
                return@withContext Result.success(SyncReport(totalProcessed = 0, message = "Nessun libro presente nel database locale Room."))
            }

            var uploaded = 0
            var conflictsResolved = 0
            var upToDate = 0
            var errors = 0
            var syncedImagesCount = 0
            val now = System.currentTimeMillis()

            onLog("Elaborazione di ${localBooks.size} libri...")
            for ((index, localBook) in localBooks.withIndex()) {
                try {
                    onLog("Controllo libro ${index + 1}/${localBooks.size}: '${localBook.title}'...")
                    val docRef = if (localBook.id > 0) {
                        db.collection("books").document(localBook.id.toString())
                    } else if (localBook.isbn.isNotBlank()) {
                        db.collection("books").document(localBook.isbn.trim())
                    } else {
                        db.collection("books").document()
                    }

                    // Verifica se il documento esiste su Firestore
                    val snapshot = Tasks.await(docRef.get(), 10, java.util.concurrent.TimeUnit.SECONDS)

                    if (!snapshot.exists()) {
                        // Nuovo documento: upload diretto con immagine
                        onLog("-> Nuovo libro su Cloud. Codifica copertina ed upload...")
                        val coverBase64 = FirestoreImageHelper.getBase64ImageForCover(context, localBook.coverUrl)
                        if (coverBase64 != null) syncedImagesCount++

                        val bookData = hashMapOf<String, Any?>(
                            "id" to localBook.id,
                            "isbn" to localBook.isbn,
                            "title" to localBook.title,
                            "author" to localBook.author,
                            "publisher" to localBook.publisher,
                            "publishedYear" to localBook.publishedYear,
                            "description" to localBook.description,
                            "coverUrl" to localBook.coverUrl,
                            "coverImageBase64" to coverBase64,
                            "hasCoverImage" to (coverBase64 != null),
                            "pageCount" to localBook.pageCount,
                            "genre" to localBook.genre,
                            "customTags" to localBook.customTags,
                            "readingStatus" to localBook.readingStatus,
                            "rating" to localBook.rating,
                            "userNotes" to localBook.userNotes,
                            "shelfLocation" to localBook.shelfLocation,
                            "sourceApi" to localBook.sourceApi,
                            "dateAdded" to localBook.dateAdded,
                            "isSynced" to true,
                            "lastSyncedAt" to now
                        )
                        Tasks.await(docRef.set(bookData), 10, java.util.concurrent.TimeUnit.SECONDS)
                        bookDao.updateBook(localBook.copy(isSynced = true, lastSyncedAt = now))
                        uploaded++
                        onLog("-> Upload completato con successo!")
                    } else {
                        // Documento già esistente: verifica e risoluzione dei conflitti di versione
                        val remoteLastSynced = snapshot.getLong("lastSyncedAt") ?: 0L
                        val remoteStatus = snapshot.getString("readingStatus") ?: localBook.readingStatus
                        val remoteRating = (snapshot.getDouble("rating") ?: localBook.rating.toDouble()).toFloat()
                        val remoteNotes = snapshot.getString("userNotes") ?: ""
                        val remoteShelf = snapshot.getString("shelfLocation") ?: ""
                        val remoteCoverBase64 = snapshot.getString("coverImageBase64") ?: snapshot.getString("coverImage")

                        val hasConflict = (remoteStatus != localBook.readingStatus) ||
                                (remoteRating != localBook.rating) ||
                                (remoteNotes != localBook.userNotes && remoteNotes.isNotBlank() && localBook.userNotes.isNotBlank())

                        if (hasConflict) {
                            onLog("-> Trovato conflitto di versione. Eseguo il merge...")
                            val mergedNotes = if (localBook.userNotes.isBlank()) {
                                remoteNotes
                            } else if (remoteNotes.isBlank() || localBook.userNotes == remoteNotes) {
                                localBook.userNotes
                            } else {
                                "${localBook.userNotes}\n[Cloud]: $remoteNotes"
                            }

                            val mergedRating = if (localBook.rating > 0f) localBook.rating else remoteRating
                            val mergedShelf = if (localBook.shelfLocation.isNotBlank()) localBook.shelfLocation else remoteShelf
                            val mergedStatus = if (localBook.lastSyncedAt >= remoteLastSynced) localBook.readingStatus else remoteStatus

                            // Ripristino o codifica dell'immagine copertina
                            val resolvedCoverUrl = if (localBook.coverUrl.isNullOrBlank() && !remoteCoverBase64.isNullOrBlank() && context != null) {
                                FirestoreImageHelper.saveBase64ToLocalStorage(context, remoteCoverBase64, localBook.isbn.ifBlank { localBook.id.toString() }) ?: localBook.coverUrl
                            } else {
                                localBook.coverUrl
                            }

                            val coverBase64ToSave = FirestoreImageHelper.getBase64ImageForCover(context, resolvedCoverUrl) ?: remoteCoverBase64
                            if (coverBase64ToSave != null) syncedImagesCount++

                            val resolvedBook = localBook.copy(
                                coverUrl = resolvedCoverUrl,
                                readingStatus = mergedStatus,
                                rating = mergedRating,
                                userNotes = mergedNotes,
                                shelfLocation = mergedShelf,
                                isSynced = true,
                                lastSyncedAt = now
                            )

                            val bookData = hashMapOf<String, Any?>(
                                "id" to resolvedBook.id,
                                "isbn" to resolvedBook.isbn,
                                "title" to resolvedBook.title,
                                "author" to resolvedBook.author,
                                "publisher" to resolvedBook.publisher,
                                "publishedYear" to resolvedBook.publishedYear,
                                "description" to resolvedBook.description,
                                "coverUrl" to resolvedBook.coverUrl,
                                "coverImageBase64" to coverBase64ToSave,
                                "hasCoverImage" to (coverBase64ToSave != null),
                                "pageCount" to resolvedBook.pageCount,
                                "genre" to resolvedBook.genre,
                                "customTags" to resolvedBook.customTags,
                                "readingStatus" to resolvedBook.readingStatus,
                                "rating" to resolvedBook.rating,
                                "userNotes" to resolvedBook.userNotes,
                                "shelfLocation" to resolvedBook.shelfLocation,
                                "sourceApi" to resolvedBook.sourceApi,
                                "dateAdded" to resolvedBook.dateAdded,
                                "isSynced" to true,
                                "lastSyncedAt" to now
                            )
                            Tasks.await(docRef.set(bookData), 10, java.util.concurrent.TimeUnit.SECONDS)
                            bookDao.updateBook(resolvedBook)
                            conflictsResolved++
                            onLog("-> Merge e sincronizzazione completati!")
                        } else {
                            onLog("-> Già sincronizzato e allineato con il Cloud.")
                            val coverBase64 = FirestoreImageHelper.getBase64ImageForCover(context, localBook.coverUrl) ?: remoteCoverBase64
                            if (coverBase64 != null) syncedImagesCount++

                            val bookData = hashMapOf<String, Any?>(
                                "id" to localBook.id,
                                "isbn" to localBook.isbn,
                                "title" to localBook.title,
                                "author" to localBook.author,
                                "publisher" to localBook.publisher,
                                "publishedYear" to localBook.publishedYear,
                                "description" to localBook.description,
                                "coverUrl" to localBook.coverUrl,
                                "coverImageBase64" to coverBase64,
                                "hasCoverImage" to (coverBase64 != null),
                                "pageCount" to localBook.pageCount,
                                "genre" to localBook.genre,
                                "customTags" to localBook.customTags,
                                "readingStatus" to localBook.readingStatus,
                                "rating" to localBook.rating,
                                "userNotes" to localBook.userNotes,
                                "shelfLocation" to localBook.shelfLocation,
                                "sourceApi" to localBook.sourceApi,
                                "dateAdded" to localBook.dateAdded,
                                "isSynced" to true,
                                "lastSyncedAt" to now
                            )
                            Tasks.await(docRef.set(bookData), 10, java.util.concurrent.TimeUnit.SECONDS)
                            bookDao.updateBook(localBook.copy(isSynced = true, lastSyncedAt = now))
                            upToDate++
                        }
                    }
                } catch (e: Exception) {
                    errors++
                    val formattedErr = FirebaseHelper.formatFirestoreError(e)
                    onLog("-> ERRORE sul libro '${localBook.title}': $formattedErr")
                }
            }

            val reportMsg = if (errors > 0 && uploaded == 0 && conflictsResolved == 0 && upToDate == 0) {
                "Sincronizzazione non riuscita ($errors errori su ${localBooks.size} libri). Progetto '${FirebaseHelper.getProjectId()}'. Verifica regole Firestore o credenziali."
            } else {
                "Sincronizzazione completata: $uploaded caricati, $conflictsResolved conflitti risolti, $upToDate già allineati ($syncedImagesCount immagini gestite), $errors errori."
            }
            onLog(reportMsg)

            val report = SyncReport(
                totalProcessed = localBooks.size,
                uploadedCount = uploaded,
                resolvedConflictsCount = conflictsResolved,
                upToDateCount = upToDate,
                errorCount = errors,
                imagesCount = syncedImagesCount,
                message = reportMsg
            )

            if (errors > 0 && uploaded == 0 && conflictsResolved == 0 && upToDate == 0) {
                Result.failure(Exception(reportMsg))
            } else {
                Result.success(report)
            }
        } catch (e: Exception) {
            val formatted = FirebaseHelper.formatFirestoreError(e)
            onLog("ERRORE CRITICO GENERALE: $formatted")
            Result.failure(Exception(formatted, e))
        }
    }

    /**
     * Importa direttamente tutti i documenti presenti nella collezione 'books' di Cloud Firestore
     * salvandoli nel database locale Room.
     * Ripristina in locale le immagini di copertina codificate in Base64 salvandole nello storage dell'app.
     * Se un libro è già presente (per ISBN o per Titolo+Autore), ne aggiorna i dettagli e i metadati.
     */
    suspend fun directImportAllBooksFromFirestore(onLog: (String) -> Unit = {}): Result<DirectImportResult> = withContext(Dispatchers.IO) {
        try {
            onLog("Inizializzazione importazione da Cloud Firestore...")
            val db = FirebaseHelper.getFirestore()
            onLog("Interrogazione collezione 'books' (Progetto '${FirebaseHelper.getProjectId()}')...")

            val querySnapshot = Tasks.await(
                db.collection("books").get(),
                15,
                java.util.concurrent.TimeUnit.SECONDS
            )

            if (querySnapshot == null || querySnapshot.isEmpty) {
                val msg = "Nessun documento trovato nella collezione 'books' di Firestore (Progetto '${FirebaseHelper.getProjectId()}')."
                onLog(msg)
                return@withContext Result.success(
                    DirectImportResult(
                        totalRemote = 0,
                        message = msg
                    )
                )
            }

            val totalDocs = querySnapshot.size()
            onLog("Trovati $totalDocs volumi su Firestore. Inizio importazione e ripristino copertine nel database locale...")

            var importedCount = 0
            var updatedCount = 0
            var failedCount = 0
            var imagesRestoredCount = 0
            val now = System.currentTimeMillis()

            for ((index, doc) in querySnapshot.documents.withIndex()) {
                try {
                    val rawTitle = doc.getString("title") ?: doc.getString("titolo") ?: ""
                    val rawAuthor = doc.getString("author") ?: doc.getString("autore") ?: ""
                    val rawIsbn = doc.getString("isbn") ?: ""

                    if (rawTitle.isBlank() && rawIsbn.isBlank()) {
                        onLog("-> [${index + 1}/$totalDocs] Documento [${doc.id}] saltato: titolo e ISBN vuoti.")
                        failedCount++
                        continue
                    }

                    val publisher = doc.getString("publisher") ?: doc.getString("editore") ?: ""
                    val publishedYear = doc.getString("publishedYear") ?: doc.getString("anno") ?: ""
                    val description = doc.getString("description") ?: doc.getString("descrizione") ?: ""
                    val rawCoverUrl = doc.getString("coverUrl") ?: doc.getString("copertina")
                    val coverImageBase64 = doc.getString("coverImageBase64")
                        ?: doc.getString("coverImage")
                        ?: doc.getString("coverBase64")

                    // Se è presente l'immagine di copertina salvata nel cloud, ripristiniamola su file locale
                    val resolvedCoverUrl = if (!coverImageBase64.isNullOrBlank() && context != null) {
                        val localPath = FirestoreImageHelper.saveBase64ToLocalStorage(
                            context = context,
                            base64String = coverImageBase64,
                            identifier = if (rawIsbn.isNotBlank()) rawIsbn else doc.id
                        )
                        if (localPath != null) {
                            imagesRestoredCount++
                            localPath
                        } else {
                            rawCoverUrl
                        }
                    } else {
                        rawCoverUrl
                    }

                    val pageCount = doc.getLong("pageCount")?.toInt() ?: doc.getLong("pagine")?.toInt() ?: 0
                    val genre = doc.getString("genre") ?: doc.getString("genere") ?: ""
                    val customTags = doc.getString("customTags") ?: doc.getString("tag") ?: ""
                    val readingStatus = doc.getString("readingStatus") ?: doc.getString("statoLettura") ?: "DA_LEGGERE"
                    val rating = (doc.getDouble("rating") ?: doc.getDouble("valutazione") ?: 0.0).toFloat()
                    val userNotes = doc.getString("userNotes") ?: doc.getString("note") ?: ""
                    val shelfLocation = doc.getString("shelfLocation") ?: doc.getString("scaffale") ?: ""
                    val sourceApi = doc.getString("sourceApi") ?: "Firestore Import"
                    val dateAdded = doc.getLong("dateAdded") ?: now

                    // Cerca se esiste già in locale
                    val existingBook = when {
                        rawIsbn.isNotBlank() -> bookDao.findBookByIsbn(rawIsbn.trim())
                        rawTitle.isNotBlank() && rawAuthor.isNotBlank() -> bookDao.findBookByTitleAndAuthor(rawTitle.trim(), rawAuthor.trim())
                        else -> null
                    }

                    val hasImageTag = if (!coverImageBase64.isNullOrBlank()) " [copertina ripristinata]" else ""

                    if (existingBook != null) {
                        // Aggiorna il libro esistente mantenendo l'ID locale
                        val updated = existingBook.copy(
                            isbn = if (rawIsbn.isNotBlank()) rawIsbn.trim() else existingBook.isbn,
                            title = if (rawTitle.isNotBlank()) rawTitle.trim() else existingBook.title,
                            author = if (rawAuthor.isNotBlank()) rawAuthor.trim() else existingBook.author,
                            publisher = if (publisher.isNotBlank()) publisher else existingBook.publisher,
                            publishedYear = if (publishedYear.isNotBlank()) publishedYear else existingBook.publishedYear,
                            description = if (description.isNotBlank()) description else existingBook.description,
                            coverUrl = resolvedCoverUrl ?: existingBook.coverUrl,
                            pageCount = if (pageCount > 0) pageCount else existingBook.pageCount,
                            genre = if (genre.isNotBlank()) genre else existingBook.genre,
                            customTags = if (customTags.isNotBlank()) customTags else existingBook.customTags,
                            readingStatus = if (readingStatus.isNotBlank()) readingStatus else existingBook.readingStatus,
                            rating = if (rating > 0f) rating else existingBook.rating,
                            userNotes = if (userNotes.isNotBlank()) userNotes else existingBook.userNotes,
                            shelfLocation = if (shelfLocation.isNotBlank()) shelfLocation else existingBook.shelfLocation,
                            isSynced = true,
                            lastSyncedAt = now
                        )
                        bookDao.updateBook(updated)
                        updatedCount++
                        onLog("-> [${index + 1}/$totalDocs] Aggiornato: '${updated.title}' (${updated.author})$hasImageTag")
                    } else {
                        // Inserisci come nuovo libro
                        val newBook = BookEntity(
                            id = 0, // Auto-generato da Room per evitare collisioni di ID
                            isbn = rawIsbn.trim(),
                            title = rawTitle.trim(),
                            author = rawAuthor.trim(),
                            publisher = publisher,
                            publishedYear = publishedYear,
                            description = description,
                            coverUrl = resolvedCoverUrl,
                            pageCount = pageCount,
                            genre = genre,
                            customTags = customTags,
                            readingStatus = readingStatus,
                            rating = rating,
                            userNotes = userNotes,
                            shelfLocation = shelfLocation,
                            sourceApi = sourceApi,
                            dateAdded = dateAdded,
                            isSynced = true,
                            lastSyncedAt = now
                        )
                        bookDao.insertBook(newBook)
                        importedCount++
                        onLog("-> [${index + 1}/$totalDocs] Importato nuovo: '${newBook.title}' (${newBook.author})$hasImageTag")
                    }
                } catch (e: Exception) {
                    failedCount++
                    onLog("-> ERRORE sul documento [${doc.id}]: ${e.localizedMessage}")
                }
            }

            val summaryMsg = "Importazione da Firestore completata! ($importedCount nuovi aggiunti, $updatedCount aggiornati, $imagesRestoredCount immagini ripristinate, $failedCount errori su $totalDocs volumi trovati)."
            onLog(summaryMsg)

            val result = DirectImportResult(
                importedCount = importedCount,
                updatedCount = updatedCount,
                failedCount = failedCount,
                imagesRestoredCount = imagesRestoredCount,
                totalRemote = totalDocs,
                collectionName = "books",
                timestamp = now,
                message = summaryMsg
            )
            Result.success(result)
        } catch (e: Exception) {
            val formatted = FirebaseHelper.formatFirestoreError(e)
            onLog("ERRORE CRITICO DURANTE L'IMPORTAZIONE: $formatted")
            Result.failure(Exception(formatted, e))
        }
    }
}
