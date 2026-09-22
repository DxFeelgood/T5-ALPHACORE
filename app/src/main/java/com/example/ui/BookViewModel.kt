package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.remote.ApiSource
import com.example.data.remote.CascadeStepStatus
import com.example.data.remote.DirectExportResult
import com.example.data.remote.DirectImportResult
import com.example.data.remote.FirestoreImageHelper
import com.example.data.remote.LookupResult
import com.example.data.remote.SyncReport
import com.example.data.remote.WikipediaAuthorService
import com.example.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class AppThemeMode(val label: String) {
    SYSTEM("Sistema (Automatico)"),
    LIGHT("Skin Chiara"),
    DARK("Skin Scura")
}

sealed interface LookupUiState {
    object Idle : LookupUiState
    data class Searching(
        val currentSource: ApiSource,
        val isbn: String,
        val logs: List<CascadeStepStatus>
    ) : LookupUiState
    data class Success(
        val result: LookupResult,
        val logs: List<CascadeStepStatus>,
        val alreadyExistsInDb: Boolean
    ) : LookupUiState
    data class NotFound(
        val isbn: String,
        val logs: List<CascadeStepStatus>
    ) : LookupUiState
    data class Error(val message: String) : LookupUiState
}

enum class ReadingFilter(val label: String, val statusKey: String?) {
    ALL("Tutti", null),
    TO_READ("Da Leggere", "DA_LEGGERE"),
    READING("In Lettura", "IN_LETTURA"),
    COMPLETED("Letti", "LETTO")
}

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository
    init {
        val db = AppDatabase.getDatabase(application)
        repository = BookRepository(db.bookDao(), context = application)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ReadingFilter.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _advancedFilter = MutableStateFlow(AdvancedFilterCriteria())
    val advancedFilter = _advancedFilter.asStateFlow()

    private val _lookupUiState = MutableStateFlow<LookupUiState>(LookupUiState.Idle)
    val lookupUiState: StateFlow<LookupUiState> = _lookupUiState.asStateFlow()

    private val _selectedBookForDetail = MutableStateFlow<BookEntity?>(null)
    val selectedBookForDetail = _selectedBookForDetail.asStateFlow()

    private val _showManualAddDialog = MutableStateFlow(false)
    val showManualAddDialog = _showManualAddDialog.asStateFlow()

    private val _manualAddInitialIsbn = MutableStateFlow("")
    val manualAddInitialIsbn = _manualAddInitialIsbn.asStateFlow()

    private val _editionSearchResults = MutableStateFlow<List<LookupResult>>(emptyList())
    val editionSearchResults = _editionSearchResults.asStateFlow()

    private val _isSearchingEditions = MutableStateFlow(false)
    val isSearchingEditions = _isSearchingEditions.asStateFlow()

    private val _searchEditionsError = MutableStateFlow<String?>(null)
    val searchEditionsError = _searchEditionsError.asStateFlow()

    private val _showAdvancedSearchDialog = MutableStateFlow(false)
    val showAdvancedSearchDialog = _showAdvancedSearchDialog.asStateFlow()

    // Servizio Wikipedia per le schede biografiche degli autori
    private val wikipediaAuthorService = WikipediaAuthorService()

    private val _selectedAuthorForWikipedia = MutableStateFlow<String?>(null)
    val selectedAuthorForWikipedia = _selectedAuthorForWikipedia.asStateFlow()

    private val _authorWikipediaUiState = MutableStateFlow<AuthorWikipediaUiState>(AuthorWikipediaUiState.Idle)
    val authorWikipediaUiState = _authorWikipediaUiState.asStateFlow()

    fun openAuthorWikipedia(authorName: String) {
        val clean = authorName.trim()
        if (clean.isBlank()) return
        _selectedAuthorForWikipedia.value = clean
        fetchAuthorWikipedia(clean)
    }

    fun closeAuthorWikipedia() {
        _selectedAuthorForWikipedia.value = null
        _authorWikipediaUiState.value = AuthorWikipediaUiState.Idle
    }

    fun fetchAuthorWikipedia(authorName: String) {
        viewModelScope.launch {
            _authorWikipediaUiState.value = AuthorWikipediaUiState.Loading(authorName)
            val result = wikipediaAuthorService.getAuthorBiography(authorName)
            result.fold(
                onSuccess = { info ->
                    _authorWikipediaUiState.value = AuthorWikipediaUiState.Success(info)
                },
                onFailure = { error ->
                    if (error is NoSuchElementException || error is IllegalArgumentException) {
                        _authorWikipediaUiState.value = AuthorWikipediaUiState.NotFound(
                            authorName = authorName,
                            message = error.message ?: "Voce biografica non disponibile per questo autore su Wikipedia."
                        )
                    } else {
                        _authorWikipediaUiState.value = AuthorWikipediaUiState.Error(
                            authorName = authorName,
                            message = error.message ?: "Impossibile recuperare i dati da Wikipedia. Verifica la connessione a Internet."
                        )
                    }
                }
            )
        }
    }

    // Tone & Vibrator per scansione riuscita
    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 75)
    } catch (_: Exception) {
        null
    }

    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val readCount: StateFlow<Int> = repository.readCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val books: StateFlow<List<BookEntity>> = combine(
        repository.allBooks,
        _searchQuery,
        _selectedFilter,
        _advancedFilter
    ) { allList, query, filter, adv ->
        allList.filter { book ->
            // 1. Filtro stato di lettura (da filtri rapidi o avanzati)
            val effectiveStatus = adv.status ?: filter.statusKey
            if (effectiveStatus != null && book.readingStatus != effectiveStatus) {
                return@filter false
            }

            // 2. Ricerca globale
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val match = book.title.lowercase().contains(q) ||
                        book.author.lowercase().contains(q) ||
                        book.publisher.lowercase().contains(q) ||
                        book.isbn.lowercase().contains(q) ||
                        book.genre.lowercase().contains(q) ||
                        book.publishedYear.lowercase().contains(q) ||
                        book.customTags.lowercase().contains(q) ||
                        book.userNotes.lowercase().contains(q)
                if (!match) return@filter false
            }

            // 3. Criteri di ricerca avanzata
            if (adv.title.isNotBlank() && !book.title.contains(adv.title.trim(), ignoreCase = true)) {
                return@filter false
            }
            if (adv.author.isNotBlank() && !book.author.contains(adv.author.trim(), ignoreCase = true)) {
                return@filter false
            }
            if (adv.publisher.isNotBlank() && !book.publisher.contains(adv.publisher.trim(), ignoreCase = true)) {
                return@filter false
            }
            if (adv.year.isNotBlank() && !book.publishedYear.contains(adv.year.trim(), ignoreCase = true)) {
                return@filter false
            }
            if (adv.category.isNotBlank() && !book.genre.contains(adv.category.trim(), ignoreCase = true)) {
                return@filter false
            }
            if (adv.tag.isNotBlank()) {
                val tagQuery = adv.tag.trim().lowercase()
                val hasMatchingTag = book.getTagList().any { it.lowercase().contains(tagQuery) } ||
                        book.customTags.lowercase().contains(tagQuery)
                if (!hasMatchingTag) return@filter false
            }

            true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: ReadingFilter) {
        _selectedFilter.value = filter
    }

    fun setAdvancedFilter(criteria: AdvancedFilterCriteria) {
        _advancedFilter.value = criteria
    }

    fun resetAdvancedFilter() {
        _advancedFilter.value = AdvancedFilterCriteria()
    }

    fun openAdvancedSearch() {
        _showAdvancedSearchDialog.value = true
    }

    fun closeAdvancedSearch() {
        _showAdvancedSearchDialog.value = false
    }

    fun toggleQuickCategoryFilter(category: String) {
        val current = _advancedFilter.value
        if (current.category.equals(category, ignoreCase = true)) {
            _advancedFilter.value = current.copy(category = "")
        } else {
            _advancedFilter.value = current.copy(category = category)
        }
    }

    fun toggleQuickTagFilter(tag: String) {
        val current = _advancedFilter.value
        if (current.tag.equals(tag, ignoreCase = true)) {
            _advancedFilter.value = current.copy(tag = "")
        } else {
            _advancedFilter.value = current.copy(tag = tag)
        }
    }

    fun selectBook(book: BookEntity?) {
        _selectedBookForDetail.value = book
    }

    fun openManualAdd(isbn: String = "") {
        _manualAddInitialIsbn.value = isbn
        _editionSearchResults.value = emptyList()
        _isSearchingEditions.value = false
        _searchEditionsError.value = null
        _showManualAddDialog.value = true
    }

    fun closeManualAdd() {
        _showManualAddDialog.value = false
        _manualAddInitialIsbn.value = ""
        _editionSearchResults.value = emptyList()
        _isSearchingEditions.value = false
        _searchEditionsError.value = null
    }

    fun searchEditions(title: String, author: String) {
        val cleanTitle = title.trim()
        val cleanAuthor = author.trim()
        if (cleanTitle.length < 2 && cleanAuthor.length < 2) {
            _editionSearchResults.value = emptyList()
            _isSearchingEditions.value = false
            _searchEditionsError.value = "Inserisci almeno 2 caratteri per la ricerca."
            return
        }

        viewModelScope.launch {
            _isSearchingEditions.value = true
            _searchEditionsError.value = null
            try {
                val results = repository.searchEditions(cleanTitle, cleanAuthor)
                _editionSearchResults.value = results
                if (results.isEmpty()) {
                    _searchEditionsError.value = "Nessuna edizione trovata online per i criteri inseriti."
                }
            } catch (e: Exception) {
                _editionSearchResults.value = emptyList()
                _searchEditionsError.value = "Errore durante la ricerca online: ${e.localizedMessage ?: "connessione fallita"}"
            } finally {
                _isSearchingEditions.value = false
            }
        }
    }

    fun clearEditionResults() {
        _editionSearchResults.value = emptyList()
        _isSearchingEditions.value = false
        _searchEditionsError.value = null
    }

    suspend fun enrichEditionMetadata(edition: LookupResult): LookupResult {
        return repository.enrichEditionMetadata(edition)
    }

    fun dismissLookup() {
        _lookupUiState.value = LookupUiState.Idle
    }

    /**
     * Esegue la ricerca a cascata con aggiornamenti in tempo reale di ogni step
     */
    fun onBarcodeDetected(scannedCode: String) {
        val cleanIsbn = scannedCode.replace("-", "").replace(" ", "").trim()
        if (cleanIsbn.isBlank()) return

        android.util.Log.d("BarcodeScan", "Barcode detected successfully: '$cleanIsbn'")

        // Feedback sonoro e vibrazione
        playBeepAndVibrate()

        startCascadeLookup(cleanIsbn)
    }

    fun startCascadeLookup(
        isbn: String,
        publisherFilter: String = "",
        yearFilter: String = ""
    ) {
        val cleanIsbn = isbn.replace("-", "").replace(" ", "").trim()
        val stepLogs = mutableListOf<CascadeStepStatus>()

        android.util.Log.d("BookLookup", "Starting cascade lookup for ISBN: '$cleanIsbn'")

        _lookupUiState.value = LookupUiState.Searching(
            currentSource = ApiSource.IBS_IT,
            isbn = cleanIsbn,
            logs = emptyList()
        )

        viewModelScope.launch {
            val existingInDb = repository.findBookByIsbn(cleanIsbn) != null

            val result = repository.lookupBookCascade(cleanIsbn, publisherFilter, yearFilter) { stepStatus ->
                stepLogs.add(stepStatus)
                val nextSource = when (stepStatus.source) {
                    ApiSource.IBS_IT -> if (stepStatus.success) ApiSource.IBS_IT else ApiSource.LIBRACCIO_IT
                    ApiSource.LIBRACCIO_IT -> ApiSource.LIBRACCIO_IT
                    else -> ApiSource.IBS_IT
                }
                _lookupUiState.value = LookupUiState.Searching(
                    currentSource = nextSource,
                    isbn = cleanIsbn,
                    logs = stepLogs.toList()
                )
            }

            if (result != null) {
                android.util.Log.i("BookLookup", "Cascade lookup SUCCEEDED for ISBN '$cleanIsbn'. Found title: '${result.title}' via source: ${result.sourceApi}")
                _lookupUiState.value = LookupUiState.Success(
                    result = result,
                    logs = stepLogs.toList(),
                    alreadyExistsInDb = existingInDb
                )
            } else {
                val detailedLogs = stepLogs.joinToString("; ") { "${it.source}: success=${it.success}, msg='${it.message}'" }
                android.util.Log.e("BookLookup", "Cascade lookup FAILED (NotFound) for ISBN: '$cleanIsbn'. Attempted sources logs: [$detailedLogs]")
                _lookupUiState.value = LookupUiState.NotFound(
                    isbn = cleanIsbn,
                    logs = stepLogs.toList()
                )
            }
        }
    }

    fun saveBookFromLookup(
        result: LookupResult,
        status: String,
        shelfLocation: String,
        userNotes: String,
        rating: Float,
        genre: String = result.genre,
        customTags: String = ""
    ) {
        viewModelScope.launch {
            val existingBook = if (result.isbn.isNotBlank()) {
                repository.findBookByIsbn(result.isbn.trim())
            } else {
                repository.findBookByTitleAndAuthor(result.title.trim(), result.author.trim())
            }

            val entity = BookEntity(
                id = existingBook?.id ?: 0,
                isbn = result.isbn.trim(),
                title = result.title.trim(),
                author = result.author.trim(),
                publisher = result.publisher.trim(),
                publishedYear = result.publishedYear.trim(),
                description = result.description.trim(),
                coverUrl = result.coverUrl ?: existingBook?.coverUrl,
                pageCount = result.pageCount,
                genre = genre.ifBlank { result.genre },
                customTags = customTags.ifBlank { existingBook?.customTags ?: "" },
                readingStatus = if (status.isNotBlank()) status else (existingBook?.readingStatus ?: "DA_LEGGERE"),
                rating = if (rating > 0f) rating else (existingBook?.rating ?: 0f),
                userNotes = userNotes.ifBlank { existingBook?.userNotes ?: "" },
                shelfLocation = shelfLocation.ifBlank { existingBook?.shelfLocation ?: "" },
                sourceApi = result.sourceApi,
                dateAdded = existingBook?.dateAdded ?: System.currentTimeMillis()
            )
            repository.insertBook(entity)
            _lookupUiState.value = LookupUiState.Idle
        }
    }

    fun saveManualBook(book: BookEntity) {
        viewModelScope.launch {
            val existingBook = if (book.isbn.isNotBlank()) {
                repository.findBookByIsbn(book.isbn.trim())
            } else {
                repository.findBookByTitleAndAuthor(book.title.trim(), book.author.trim())
            }

            val mergedBook = if (existingBook != null) {
                book.copy(
                    id = existingBook.id,
                    customTags = book.customTags.ifBlank { existingBook.customTags },
                    readingStatus = if (book.readingStatus == "DA_LEGGERE" && existingBook.readingStatus != "DA_LEGGERE") existingBook.readingStatus else book.readingStatus,
                    rating = if (book.rating == 0f) existingBook.rating else book.rating,
                    userNotes = book.userNotes.ifBlank { existingBook.userNotes },
                    shelfLocation = book.shelfLocation.ifBlank { existingBook.shelfLocation },
                    dateAdded = existingBook.dateAdded
                )
            } else {
                book
            }

            repository.insertBook(mergedBook)
            _showManualAddDialog.value = false
            _lookupUiState.value = LookupUiState.Idle
        }
    }

    fun updateBook(book: BookEntity) {
        viewModelScope.launch {
            repository.updateBook(book)
            if (_selectedBookForDetail.value?.id == book.id) {
                _selectedBookForDetail.value = book
            }
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
            if (_selectedBookForDetail.value?.id == book.id) {
                _selectedBookForDetail.value = null
            }
        }
    }

    suspend fun addBookToFirestore(book: BookEntity): Result<String> {
        return repository.addBookToFirestore(book)
    }

    suspend fun syncReadBooksToFirestore(): Result<Int> {
        return repository.syncReadBooksToFirestore()
    }

    suspend fun syncAllUnsyncedBooksToFirestore(): Result<Int> {
        return repository.syncAllUnsyncedBooksToFirestore()
    }

    private val _firestoreSyncLogs = MutableStateFlow<List<String>>(emptyList())
    val firestoreSyncLogs: StateFlow<List<String>> = _firestoreSyncLogs.asStateFlow()

    fun addSyncLog(msg: String) {
        val current = _firestoreSyncLogs.value.toMutableList()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        current.add("[$timestamp] $msg")
        _firestoreSyncLogs.value = current
    }

    fun clearSyncLogs() {
        _firestoreSyncLogs.value = emptyList()
    }

    suspend fun forceSyncToFirestoreWithConflictResolution(): Result<SyncReport> {
        clearSyncLogs()
        addSyncLog("Avvio sincronizzazione manuale...")
        return repository.forceSyncToFirestoreWithConflictResolution { addSyncLog(it) }
    }

    suspend fun directExportAllBooksToFirestore(): Result<DirectExportResult> {
        clearSyncLogs()
        addSyncLog("Avvio esportazione diretta...")
        return repository.directExportAllBooksToFirestore { addSyncLog(it) }
    }

    suspend fun directImportAllBooksFromFirestore(): Result<DirectImportResult> {
        clearSyncLogs()
        addSyncLog("Avvio importazione diretta da Firestore...")
        return repository.directImportAllBooksFromFirestore { addSyncLog(it) }
    }

    suspend fun testFirestoreConnection(): com.example.data.remote.FirebaseDiagnosticResult {
        clearSyncLogs()
        addSyncLog("Avvio test di connessione Firestore...")
        val res = com.example.data.remote.FirebaseHelper.testConnection()
        addSyncLog("ID Progetto configurato: '${res.projectId}'")
        if (res.isSuccess) {
            addSyncLog("ESITO: SUCCESSO! ${res.message}")
        } else {
            addSyncLog("ESITO: ERRORE DI CONNESSIONE")
            addSyncLog("Dettaglio: ${res.message}")
            if (res.recommendation != null) {
                addSyncLog("Consiglio: ${res.recommendation}")
            }
        }
        return res
    }

    fun cacheBookOffline(book: BookEntity) {
        viewModelScope.launch {
            repository.cacheBookOffline(book)
        }
    }

    private val sharedPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        when (sharedPrefs.getString("theme_mode", "SYSTEM")) {
            "LIGHT" -> AppThemeMode.LIGHT
            "DARK" -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    suspend fun importBooksFromJson(jsonString: String): Result<Int> {
        return try {
            val rawParsed = mutableListOf<BookEntity>()
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val array = obj.optJSONArray("books") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    rawParsed.add(parseBookFromJson(item))
                }
            } else if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    rawParsed.add(parseBookFromJson(item))
                }
            } else {
                return Result.failure(IllegalArgumentException("Formato JSON non valido. Deve iniziare con { o ["))
            }

            if (rawParsed.isEmpty()) {
                return Result.failure(IllegalArgumentException("Nessun libro valido trovato nel testo JSON"))
            }

            // Mappa per associare gli ID esistenti se l'ISBN coincide
            val booksToSave = mutableListOf<BookEntity>()
            for (book in rawParsed) {
                if (book.isbn.isNotBlank()) {
                    val existing = repository.findBookByIsbn(book.isbn)
                    if (existing != null) {
                        booksToSave.add(book.copy(id = existing.id))
                    } else {
                        booksToSave.add(book)
                    }
                } else {
                    booksToSave.add(book)
                }
            }

            repository.insertBooks(booksToSave)
            Result.success(booksToSave.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseBookFromJson(item: JSONObject): BookEntity {
        val rawCoverUrl = if (item.has("coverUrl") && !item.isNull("coverUrl")) item.optString("coverUrl") else null
        val coverImageBase64 = if (item.has("coverImageBase64") && !item.isNull("coverImageBase64")) {
            item.optString("coverImageBase64")
        } else if (item.has("coverImage") && !item.isNull("coverImage")) {
            item.optString("coverImage")
        } else null

        val resolvedCoverUrl = if (!coverImageBase64.isNullOrBlank()) {
            val isbn = item.optString("isbn", "").trim()
            val id = item.optString("id", "")
            FirestoreImageHelper.saveBase64ToLocalStorage(
                context = getApplication(),
                base64String = coverImageBase64,
                identifier = if (isbn.isNotBlank()) isbn else id
            ) ?: rawCoverUrl
        } else {
            rawCoverUrl
        }

        return BookEntity(
            id = 0,
            isbn = item.optString("isbn", "").trim(),
            title = item.optString("title", "Titolo Sconosciuto"),
            author = item.optString("author", "Autore Sconosciuto"),
            publisher = item.optString("publisher", ""),
            publishedYear = item.optString("publishedYear", ""),
            genre = item.optString("genre", ""),
            coverUrl = resolvedCoverUrl,
            description = item.optString("description", ""),
            customTags = item.optString("customTags", ""),
            readingStatus = item.optString("readingStatus", "DA_LEGGERE"),
            rating = item.optDouble("rating", 0.0).toFloat(),
            shelfLocation = item.optString("shelfLocation", ""),
            userNotes = item.optString("userNotes", ""),
            pageCount = item.optInt("pageCount", 0),
            sourceApi = item.optString("sourceApi", "CLOUD_IMPORT"),
            dateAdded = item.optLong("dateAdded", System.currentTimeMillis())
        )
    }

    private fun playBeepAndVibrate() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {
        }

        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(80)
            }
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator?.release()
        toneGenerator = null
    }
}
