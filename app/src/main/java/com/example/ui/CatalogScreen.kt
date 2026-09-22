package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as columnItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.remote.WikipediaAuthorService
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.BookEntity
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Modello di raggruppamento per autore con statistiche calcolate
 */
data class AuthorGroup(
    val authorName: String,
    val books: List<BookEntity>
) {
    val totalBooks: Int get() = books.size
    val readBooks: Int get() = books.count { it.readingStatus == "LETTO" }
    val readingBooks: Int get() = books.count { it.readingStatus == "IN_LETTURA" }
    val averageRating: Float get() = if (books.any { it.rating > 0f }) {
        books.filter { it.rating > 0f }.map { it.rating }.average().toFloat()
    } else 0f
    val mainGenres: List<String> get() = books.map { it.genre.trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(2)
        .map { it.key }
}

/**
 * Criteri di ordinamento per gli autori
 */
enum class AuthorSortMode(val label: String, val icon: ImageVector) {
    ALPHABETICAL("A-Z", Icons.Default.SortByAlpha),
    MOST_BOOKS("Più Opere", Icons.Default.Whatshot),
    TOP_RATED("Più Amati", Icons.Default.Star),
    MOST_READ("Più Letti", Icons.Default.CheckCircle)
}

/**
 * Stili di visualizzazione per l'elenco autori
 */
enum class AuthorViewStyle(val label: String) {
    CARD_STACK("Ventaglio Copertine"),
    DETAILED_LIST("Lista Dettagliata"),
    QUICK_CAROUSEL("Vista Rapida")
}

/**
 * Stili di visualizzazione per i libri dell'autore
 */
enum class BookShelfViewStyle(val label: String) {
    GRID_CARDS("Carte Illustrate"),
    WOODEN_SHELF("Scaffale Libreria"),
    QUICK_CAROUSEL("Vista Rapida")
}

// Palette gradienti vivaci e allegri per autori
val AuthorCardGradients = listOf(
    listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)), // Indaco / Viola
    listOf(Color(0xFFEC4899), Color(0xFFBE185D)), // Rosa brillante
    listOf(Color(0xFF0284C7), Color(0xFF0369A1)), // Ciano profondo
    listOf(Color(0xFFEA580C), Color(0xFFC2410C)), // Arancio caldo
    listOf(Color(0xFF059669), Color(0xFF047857)), // Smeraldo
    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)), // Violetto
    listOf(Color(0xFFD97706), Color(0xFFB45309)), // Ambra dorata
    listOf(Color(0xFF0D9488), Color(0xFF0F766E))  // Turchese
)

/**
 * Funzione di utilità per associare emoji allegri e vivaci ai generi letterari
 */
fun getGenreEmoji(genre: String): String {
    val g = genre.lowercase()
    return when {
        g.contains("fantascienza") || g.contains("sci-fi") || g.contains("scifi") || g.contains("cyberpunk") -> "🚀"
        g.contains("fantasy") || g.contains("fiaba") || g.contains("magia") || g.contains("epico") -> "🧙"
        g.contains("giallo") || g.contains("thriller") || g.contains("mistero") || g.contains("noir") || g.contains("detective") -> "🔍"
        g.contains("storia") || g.contains("storico") || g.contains("saggio") || g.contains("biografia") || g.contains("memorie") -> "📜"
        g.contains("romanzo") || g.contains("narrativa") || g.contains("letteratura") || g.contains("classico") -> "📖"
        g.contains("romant") || g.contains("amore") || g.contains("love") || g.contains("sentimentale") -> "💖"
        g.contains("filosofia") || g.contains("scienza") || g.contains("saggistica") || g.contains("divulgazione") -> "💡"
        g.contains("arte") || g.contains("musica") || g.contains("fumett") || g.contains("manga") || g.contains("graphic") -> "🎨"
        g.contains("horror") || g.contains("terrore") || g.contains("gotico") -> "👻"
        g.contains("avventura") || g.contains("viagg") || g.contains("esplorazione") -> "🗺️"
        g.contains("poesia") || g.contains("lirica") -> "🪶"
        g.contains("cucina") || g.contains("ricette") || g.contains("gastronomia") -> "🍳"
        g.contains("ragazzi") || g.contains("bambini") || g.contains("infanzia") -> "🧸"
        else -> "📚"
    }
}

/**
 * Schermata Catalogo Autori & Libri riprogettata: allegra, interattiva, colorata e divertente!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    books: List<BookEntity>,
    onBookClick: (BookEntity) -> Unit,
    onUpdateBook: (BookEntity) -> Unit = {},
    onOpenAuthorWikipedia: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedAuthor by remember { mutableStateOf<String?>(null) }
    var showPdfExportSheet by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(AuthorSortMode.ALPHABETICAL) }
    var authorViewStyle by remember { mutableStateOf(AuthorViewStyle.CARD_STACK) }

    // Mantenimento della posizione di scroll per ripristinarla al ritorno
    val authorGridState = rememberLazyGridState()
    val authorListState = rememberLazyListState()

    // Dialog sorpresa autore casuale
    var surpriseAuthor by remember { mutableStateOf<AuthorGroup?>(null) }
    var surpriseBook by remember { mutableStateOf<BookEntity?>(null) }

    // Raggruppa i libri per autore con supporto a ricerca e ordinamento vivace
    val authorGroups by remember(books, searchQuery, sortMode) {
        derivedStateOf {
            val normalizedQuery = searchQuery.trim().lowercase()
            val filteredBooks = if (normalizedQuery.isBlank()) {
                books
            } else {
                books.filter {
                    it.author.lowercase().contains(normalizedQuery) ||
                    it.title.lowercase().contains(normalizedQuery) ||
                    it.genre.lowercase().contains(normalizedQuery) ||
                    it.customTags.lowercase().contains(normalizedQuery)
                }
            }

            val grouped = filteredBooks
                .groupBy { it.author.trim().ifBlank { "Autore non specificato" } }
                .map { (author, bookList) ->
                    AuthorGroup(
                        authorName = author,
                        books = bookList.sortedBy { it.title.lowercase() }
                    )
                }

            when (sortMode) {
                AuthorSortMode.ALPHABETICAL -> grouped.sortedBy { it.authorName.lowercase() }
                AuthorSortMode.MOST_BOOKS -> grouped.sortedByDescending { it.totalBooks }
                AuthorSortMode.TOP_RATED -> grouped.sortedByDescending { it.averageRating }
                AuthorSortMode.MOST_READ -> grouped.sortedByDescending { it.readBooks }
            }
        }
    }

    // Se un autore è selezionato, gestisci il pulsante back
    BackHandler(enabled = selectedAuthor != null) {
        selectedAuthor = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedAuthor != null) Icons.Default.Person else Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (selectedAuthor != null) selectedAuthor!! else "Galleria Autori ✨",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedAuthor != null)
                                    "Tutte le edizioni catalogate"
                                else
                                    "${authorGroups.size} scrittori • ${books.size} volumi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectedAuthor != null) {
                        IconButton(
                            onClick = { selectedAuthor = null },
                            modifier = Modifier.testTag("catalog_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Torna agli autori"
                            )
                        }
                    }
                },
                actions = {
                    // Pulsante "Sorprendimi! / Pesca a caso"
                    IconButton(
                        onClick = {
                            if (selectedAuthor == null) {
                                if (authorGroups.isNotEmpty()) {
                                    val randomIndex = Random.nextInt(authorGroups.size)
                                    surpriseAuthor = authorGroups[randomIndex]
                                }
                            } else {
                                val currentBooks = books.filter {
                                    it.author.trim().equals(selectedAuthor?.trim(), ignoreCase = true) ||
                                    (selectedAuthor == "Autore non specificato" && it.author.isBlank())
                                }
                                if (currentBooks.isNotEmpty()) {
                                    val randomIndex = Random.nextInt(currentBooks.size)
                                    surpriseBook = currentBooks[randomIndex]
                                }
                            }
                        },
                        modifier = Modifier.testTag("catalog_random_surprise_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Pesca casuale",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Esporta PDF
                    IconButton(
                        onClick = { showPdfExportSheet = true },
                        modifier = Modifier.testTag("catalog_export_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Esporta & Stampa PDF",
                            tint = Color(0xFFDC2626)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedAuthor == null) {
                // LIVELLO 1: ELENCO DEGLI AUTORI (Allegro, colorato e ricco di opzioni)
                JoyfulAuthorsListView(
                    authorGroups = authorGroups,
                    allBooks = books,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    viewStyle = authorViewStyle,
                    onViewStyleChange = { authorViewStyle = it },
                    onSelectAuthor = { authorName ->
                        selectedAuthor = authorName
                    },
                    onRandomAuthorPick = {
                        if (authorGroups.isNotEmpty()) {
                            val randomIndex = Random.nextInt(authorGroups.size)
                            surpriseAuthor = authorGroups[randomIndex]
                        }
                    },
                    onBookClick = onBookClick,
                    onUpdateBook = onUpdateBook,
                    onOpenAuthorWikipedia = onOpenAuthorWikipedia,
                    totalBooksCount = books.size,
                    gridState = authorGridState,
                    listState = authorListState
                )
            } else {
                // LIVELLO 2: CATALOGO DEI LIBRI DELL'AUTORE (Con Scaffale o Schede illustrate)
                val currentAuthorBooks = remember(books, selectedAuthor) {
                    books.filter {
                        it.author.trim().equals(selectedAuthor?.trim(), ignoreCase = true) ||
                        (selectedAuthor == "Autore non specificato" && it.author.isBlank())
                    }.sortedBy { it.title.lowercase() }
                }

                JoyfulAuthorBooksListView(
                    authorName = selectedAuthor ?: "",
                    books = currentAuthorBooks,
                    onBack = { selectedAuthor = null },
                    onBookClick = onBookClick,
                    onUpdateBook = onUpdateBook,
                    onOpenAuthorWikipedia = onOpenAuthorWikipedia,
                    onRandomBookPick = {
                        if (currentAuthorBooks.isNotEmpty()) {
                            val randomIndex = Random.nextInt(currentAuthorBooks.size)
                            surpriseBook = currentAuthorBooks[randomIndex]
                        }
                    }
                )
            }
        }
    }

    // Modal Sorpresa Autore Casuale
    if (surpriseAuthor != null) {
        RandomAuthorSurpriseDialog(
            authorGroup = surpriseAuthor!!,
            onDismiss = { surpriseAuthor = null },
            onExplore = {
                val author = surpriseAuthor!!.authorName
                surpriseAuthor = null
                selectedAuthor = author
            }
        )
    }

    // Modal Sorpresa Libro Casuale
    if (surpriseBook != null) {
        RandomBookSurpriseDialog(
            book = surpriseBook!!,
            onDismiss = { surpriseBook = null },
            onOpenBook = {
                val bk = surpriseBook!!
                surpriseBook = null
                onBookClick(bk)
            }
        )
    }

    if (showPdfExportSheet) {
        PdfExportBottomSheet(
            books = books,
            onDismiss = { showPdfExportSheet = false }
        )
    }
}

/**
 * Vista Autori Allegra e Vivace con filtri, ordinamenti, banner interattivo e stili di vista multipli
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JoyfulAuthorsListView(
    authorGroups: List<AuthorGroup>,
    allBooks: List<BookEntity> = emptyList(),
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: AuthorSortMode,
    onSortModeChange: (AuthorSortMode) -> Unit,
    viewStyle: AuthorViewStyle,
    onViewStyleChange: (AuthorViewStyle) -> Unit,
    onSelectAuthor: (String) -> Unit,
    onRandomAuthorPick: () -> Unit,
    onBookClick: (BookEntity) -> Unit = {},
    onUpdateBook: (BookEntity) -> Unit = {},
    onOpenAuthorWikipedia: (String) -> Unit = {},
    totalBooksCount: Int,
    gridState: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState()
) {
    // Filtro libri per modalità carosello rapido
    val filteredBooksForCarousel = remember(allBooks, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) allBooks else allBooks.filter {
            it.title.lowercase().contains(q) ||
            it.author.lowercase().contains(q) ||
            it.genre.lowercase().contains(q) ||
            it.customTags.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Barra di ricerca con pillola moderna
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Cerca autore, titolo o genere (es. Sci-Fi)...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cerca",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Cancella")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("catalog_search_input")
        )

        // Barra di Cambio Stile Vista (Ventaglio, Lista, Vista Rapida Carosello)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = viewStyle == AuthorViewStyle.CARD_STACK,
                onClick = { onViewStyleChange(AuthorViewStyle.CARD_STACK) },
                label = { Text("Ventaglio", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier.testTag("author_view_card_stack")
            )
            FilterChip(
                selected = viewStyle == AuthorViewStyle.DETAILED_LIST,
                onClick = { onViewStyleChange(AuthorViewStyle.DETAILED_LIST) },
                label = { Text("Lista", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.TableRows,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier.testTag("author_view_detailed_list")
            )
            FilterChip(
                selected = viewStyle == AuthorViewStyle.QUICK_CAROUSEL,
                onClick = { onViewStyleChange(AuthorViewStyle.QUICK_CAROUSEL) },
                label = { Text("Vista Rapida 🎠", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                ),
                modifier = Modifier.testTag("author_view_quick_carousel")
            )
        }

        // Barra di Ordinamento
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AuthorSortMode.values()) { mode ->
                    val isSelected = sortMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSortModeChange(mode) },
                        label = {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("sort_chip_${mode.name.lowercase()}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (authorGroups.isEmpty() && viewStyle != AuthorViewStyle.QUICK_CAROUSEL) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔍📖", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Nessun autore trovato per \"$searchQuery\"" else "Nessun libro presente nel catalogo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Prova a modificare i filtri o scansiona un nuovo libro con la fotocamera!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            when (viewStyle) {
                AuthorViewStyle.CARD_STACK -> {
                    // MODALITÀ 1: GRIGLIA A VENTAGLIO CON COPERTINE SOVRAPPOSTE E COLORI VIVACI
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp, top = 6.dp)
                    ) {
                        gridItemsIndexed(authorGroups, key = { _, group -> group.authorName }) { index, group ->
                            JoyfulAuthorFanCard(
                                authorGroup = group,
                                index = index,
                                onClick = { onSelectAuthor(group.authorName) }
                            )
                        }
                    }
                }
                AuthorViewStyle.DETAILED_LIST -> {
                    // MODALITÀ 2: LISTA DETTAGLIATA CON PROGRESSO, GENERI E ANTEPRIMA COPERTINE
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 28.dp, top = 6.dp)
                    ) {
                        columnItemsIndexed(authorGroups, key = { _, group -> group.authorName }) { index, group ->
                            JoyfulAuthorDetailedRow(
                                authorGroup = group,
                                index = index,
                                onClick = { onSelectAuthor(group.authorName) },
                                onOpenAuthorWikipedia = onOpenAuthorWikipedia
                            )
                        }
                    }
                }
                AuthorViewStyle.QUICK_CAROUSEL -> {
                    // MODALITÀ 3: VISTA RAPIDA A CAROSELLO COPERTINE
                    JoyfulBooksQuickCarouselView(
                        books = filteredBooksForCarousel,
                        onBookClick = onBookClick,
                        onUpdateBook = onUpdateBook,
                        onOpenAuthorWikipedia = onOpenAuthorWikipedia,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Scheda Autore allegra in Griglia con Ventaglio di mini-copertine dei suoi libri
 */
@Composable
private fun JoyfulAuthorFanCard(
    authorGroup: AuthorGroup,
    index: Int = 0,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val gradientColors = AuthorCardGradients[Math.abs(authorGroup.authorName.hashCode()) % AuthorCardGradients.size]
    val covers = remember(authorGroup) { authorGroup.books.mapNotNull { it.coverUrl }.filter { it.isNotBlank() }.take(3) }
    val primaryGenre = authorGroup.mainGenres.firstOrNull() ?: ""

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scaleAnim"
    )

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.2.dp, gradientColors[0].copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick)
            .testTag("author_card_${authorGroup.authorName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sezione Superiore: Badge Avatar o Ventaglio Copertine
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
                contentAlignment = Alignment.Center
            ) {
                if (covers.isNotEmpty()) {
                    // Ventaglio di copertine inclinate per un effetto tangibile e allegro!
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (covers.size >= 3) {
                            MiniCoverTile(
                                coverUrl = covers[2],
                                rotation = -12f,
                                offsetX = (-18).dp,
                                offsetY = 4.dp
                            )
                        }
                        if (covers.size >= 2) {
                            MiniCoverTile(
                                coverUrl = covers[1],
                                rotation = 12f,
                                offsetX = 18.dp,
                                offsetY = 4.dp
                            )
                        }
                        MiniCoverTile(
                            coverUrl = covers[0],
                            rotation = 0f,
                            offsetX = 0.dp,
                            offsetY = 0.dp,
                            isFront = true
                        )
                    }
                } else {
                    // Avatar colorato con iniziali dell'autore e aura sfumata
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradientColors))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = authorGroup.authorName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Badge top autore se ha >= 3 libri
                if (authorGroup.totalBooks >= 3) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF08A),
                        border = BorderStroke(0.5.dp, Color(0xFFCA8A04)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(text = "👑", fontSize = 10.sp)
                            Text(
                                text = "Top",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF854D0E),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nome Autore
            Text(
                text = authorGroup.authorName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Genere Principale con Emoji
            if (primaryGenre.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = getGenreEmoji(primaryGenre), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = primaryGenre,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badge Contatore Libri e Completamento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Conteggio Libri
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = gradientColors[0].copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "📚 ${authorGroup.totalBooks} ${if (authorGroup.totalBooks == 1) "libro" else "libri"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = gradientColors[0],
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        fontSize = 10.sp
                    )
                }

                // Stelline medie o badge letti
                if (authorGroup.averageRating > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(text = "⭐", fontSize = 9.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", authorGroup.averageRating),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF92400E),
                            fontSize = 10.sp
                        )
                    }
                } else if (authorGroup.readBooks > 0) {
                    Text(
                        text = "☕ ${authorGroup.readBooks} letti",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Mini copertina singola per il ventaglio animato
 */
@Composable
private fun MiniCoverTile(
    coverUrl: String,
    rotation: Float,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    isFront: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        shadowElevation = if (isFront) 6.dp else 2.dp,
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .rotate(rotation)
            .width(44.dp)
            .height(64.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Scheda Autore in Lista Dettagliata con barra di progresso e pillole colorate
 */
@Composable
private fun JoyfulAuthorDetailedRow(
    authorGroup: AuthorGroup,
    index: Int = 0,
    onClick: () -> Unit,
    onOpenAuthorWikipedia: (String) -> Unit = {}
) {
    val gradientColors = AuthorCardGradients[Math.abs(authorGroup.authorName.hashCode()) % AuthorCardGradients.size]
    val readProgress = if (authorGroup.totalBooks > 0) authorGroup.readBooks.toFloat() / authorGroup.totalBooks else 0f

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("author_row_${authorGroup.authorName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Autore Sfumato
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = authorGroup.authorName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = authorGroup.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (authorGroup.authorName.isNotBlank() && authorGroup.authorName != "Autore non specificato") {
                            AuthorWikipediaIconButton(
                                authorName = authorGroup.authorName,
                                onClick = { onOpenAuthorWikipedia(authorGroup.authorName) },
                                isCompact = true
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = gradientColors[0].copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${authorGroup.totalBooks} vol.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = gradientColors[0],
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Generi Principali con Emojis
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    authorGroup.mainGenres.forEach { genre ->
                        Text(
                            text = "${getGenreEmoji(genre)} $genre",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Barra di avanzamento lettura
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(readProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF10B981))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${authorGroup.readBooks}/${authorGroup.totalBooks} letti",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Vista Livello 2: Catalogo Opere dell'Autore con switcher per Scaffale Libreria o Griglia Illustrata
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JoyfulAuthorBooksListView(
    authorName: String,
    books: List<BookEntity>,
    onBack: () -> Unit,
    onBookClick: (BookEntity) -> Unit,
    onUpdateBook: (BookEntity) -> Unit,
    onRandomBookPick: () -> Unit,
    onOpenAuthorWikipedia: (String) -> Unit = {}
) {
    var selectedStatusFilter by remember { mutableStateOf<String?>("TUTTI") }
    var bookShelfStyle by remember { mutableStateOf(BookShelfViewStyle.GRID_CARDS) }
    val gradientColors = AuthorCardGradients[Math.abs(authorName.hashCode()) % AuthorCardGradients.size]

    // Gestione interna della scheda biografica Wikipedia dell'autore
    var showWikipediaBio by remember { mutableStateOf(false) }
    var wikipediaInfoState by remember(authorName) { mutableStateOf<AuthorWikipediaUiState>(AuthorWikipediaUiState.Idle) }
    val wikipediaService = remember { WikipediaAuthorService() }
    val coroutineScope = rememberCoroutineScope()

    fun loadWikipedia() {
        val clean = authorName.trim()
        if (clean.isNotBlank() && clean != "Autore non specificato") {
            wikipediaInfoState = AuthorWikipediaUiState.Loading(clean)
            coroutineScope.launch {
                val result = wikipediaService.getAuthorBiography(clean)
                result.fold(
                    onSuccess = { info ->
                        wikipediaInfoState = AuthorWikipediaUiState.Success(info)
                    },
                    onFailure = { error ->
                        wikipediaInfoState = AuthorWikipediaUiState.NotFound(
                            clean,
                            error.localizedMessage ?: "Nessuna biografia trovata per \"$clean\" su Wikipedia."
                        )
                    }
                )
            }
        }
    }

    val handleAuthorWikipedia: (String) -> Unit = { targetAuthor ->
        val cleanTarget = targetAuthor.trim()
        val cleanCurrent = authorName.trim()
        if (cleanTarget.equals(cleanCurrent, ignoreCase = true) || cleanCurrent.contains(cleanTarget, ignoreCase = true) || cleanTarget.contains(cleanCurrent, ignoreCase = true)) {
            showWikipediaBio = true
            if (wikipediaInfoState is AuthorWikipediaUiState.Idle || wikipediaInfoState is AuthorWikipediaUiState.Error) {
                loadWikipedia()
            }
        } else {
            onOpenAuthorWikipedia(targetAuthor)
        }
    }

    val readCount = remember(books) { books.count { it.readingStatus == "LETTO" } }
    val readingCount = remember(books) { books.count { it.readingStatus == "IN_LETTURA" } }
    val toReadCount = remember(books) { books.count { it.readingStatus == "DA_LEGGERE" } }

    val filteredBooks = remember(books, selectedStatusFilter) {
        when (selectedStatusFilter) {
            "LETTO" -> books.filter { it.readingStatus == "LETTO" }
            "IN_LETTURA" -> books.filter { it.readingStatus == "IN_LETTURA" }
            "DA_LEGGERE" -> books.filter { it.readingStatus == "DA_LEGGERE" }
            "RATED" -> books.filter { it.rating >= 4f }
            else -> books
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Banner Autore Eroe con statistiche e pulsante Wikipedia W (sostituisce Pesca)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = gradientColors[0].copy(alpha = 0.12f),
            border = BorderStroke(1.2.dp, gradientColors[0].copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(gradientColors)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = authorName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = authorName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${books.size} volumi • ☕ $readCount letti • 📖 $readingCount in lettura",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Pulsante Wikipedia "W" al posto del pulsante "Pesca!"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (showWikipediaBio) MaterialTheme.colorScheme.primaryContainer else gradientColors[0],
                        border = if (showWikipediaBio) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showWikipediaBio = !showWikipediaBio
                                if (showWikipediaBio && (wikipediaInfoState is AuthorWikipediaUiState.Idle || wikipediaInfoState is AuthorWikipediaUiState.Error)) {
                                    loadWikipedia()
                                }
                            }
                            .testTag("btn_author_wikipedia_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF202122)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "W",
                                    color = Color.White,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showWikipediaBio) "Chiudi Bio" else "Wikipedia",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (showWikipediaBio) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                            )
                        }
                    }
                }

                // Scheda Biografica Wikipedia Interna (espandibile direttamente nella scheda autore)
                AnimatedVisibility(
                    visible = showWikipediaBio,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        AuthorWikipediaInlineCard(
                            uiState = wikipediaInfoState,
                            onRetry = { loadWikipedia() },
                            onClose = { showWikipediaBio = false }
                        )
                    }
                }
            }
        }

        // Modalità di Visualizzazione Scaffale (Carte, Scaffale, Vista Rapida Carosello)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = bookShelfStyle == BookShelfViewStyle.GRID_CARDS,
                onClick = { bookShelfStyle = BookShelfViewStyle.GRID_CARDS },
                label = { Text("Carte", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier.testTag("author_shelf_grid")
            )
            FilterChip(
                selected = bookShelfStyle == BookShelfViewStyle.WOODEN_SHELF,
                onClick = { bookShelfStyle = BookShelfViewStyle.WOODEN_SHELF },
                label = { Text("Scaffale", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ViewAgenda,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier.testTag("author_shelf_wooden")
            )
            FilterChip(
                selected = bookShelfStyle == BookShelfViewStyle.QUICK_CAROUSEL,
                onClick = { bookShelfStyle = BookShelfViewStyle.QUICK_CAROUSEL },
                label = { Text("Vista Rapida 🎠", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                ),
                modifier = Modifier.testTag("author_shelf_carousel")
            )
        }

        // Filtri di stato lettura
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedStatusFilter == "TUTTI",
                        onClick = { selectedStatusFilter = "TUTTI" },
                        label = { Text("🌈 Tutti (${books.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatusFilter == "LETTO",
                        onClick = { selectedStatusFilter = "LETTO" },
                        label = { Text("☕ Letti ($readCount)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatusFilter == "IN_LETTURA",
                        onClick = { selectedStatusFilter = "IN_LETTURA" },
                        label = { Text("📖 In Lettura ($readingCount)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatusFilter == "DA_LEGGERE",
                        onClick = { selectedStatusFilter = "DA_LEGGERE" },
                        label = { Text("⏳ Da Leggere ($toReadCount)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (filteredBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "✨📚", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nessun volume corrisponde al filtro selezionato",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            when (bookShelfStyle) {
                BookShelfViewStyle.GRID_CARDS -> {
                    // MODALITÀ CARTE ILLUSTRATE CON CAMBIO STATO RAPIDO AL TOCCO
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp, top = 4.dp)
                    ) {
                        gridItemsIndexed(filteredBooks, key = { _, book -> book.id }) { index, book ->
                            JoyfulBookCard(
                                book = book,
                                index = index,
                                onClick = { onBookClick(book) },
                                onQuickToggleStatus = {
                                    val nextStatus = when (book.readingStatus) {
                                        "DA_LEGGERE" -> "IN_LETTURA"
                                        "IN_LETTURA" -> "LETTO"
                                        else -> "DA_LEGGERE"
                                    }
                                    onUpdateBook(book.copy(readingStatus = nextStatus))
                                },
                                onOpenAuthorWikipedia = handleAuthorWikipedia
                            )
                        }
                    }
                }
                BookShelfViewStyle.WOODEN_SHELF -> {
                    // MODALITÀ SCAFFALE LIBRERIA VIRTUALE IN LEGNO CON LIBRI IN PIEDI E SEGNALIBRI (🔖)
                    val shelfChunks = remember(filteredBooks) { filteredBooks.chunked(3) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp, top = 6.dp)
                    ) {
                        columnItemsIndexed(shelfChunks) { shelfIndex, shelfBooks ->
                            WoodenBookShelfRow(
                                books = shelfBooks,
                                shelfNumber = shelfIndex + 1,
                                onBookClick = onBookClick
                            )
                        }
                    }
                }
                BookShelfViewStyle.QUICK_CAROUSEL -> {
                    // MODALITÀ VISTA RAPIDA CAROSELLO
                    JoyfulBooksQuickCarouselView(
                        books = filteredBooks,
                        onBookClick = onBookClick,
                        onUpdateBook = onUpdateBook,
                        onOpenAuthorWikipedia = handleAuthorWikipedia,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Scheda Libro vivace in Griglia con Copertina alta, Genere con Emoji, Segnalibro colorato e Cambio Stato rapido
 */
@Composable
private fun JoyfulBookCard(
    book: BookEntity,
    index: Int = 0,
    onClick: () -> Unit,
    onQuickToggleStatus: () -> Unit,
    onOpenAuthorWikipedia: ((String) -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bookScale"
    )

    val genreEmoji = remember(book.genre) { getGenreEmoji(book.genre) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick)
            .testTag("catalog_book_item_${book.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Copertina con nastro segnalibro
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!book.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(book.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = genreEmoji, fontSize = 36.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Segnalibro a nastro colorato in alto a sinistra
                val ribbonColor = when (book.readingStatus) {
                    "LETTO" -> Color(0xFF10B981)
                    "IN_LETTURA" -> Color(0xFF3B82F6)
                    else -> Color(0xFFF97316)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp)
                        .width(14.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(ribbonColor)
                        .shadow(2.dp)
                )

                // Indicatore Sincronizzazione Cloud
                SyncStatusIndicatorDot(
                    isSynced = book.isSynced,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Titolo
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            if (book.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onOpenAuthorWikipedia != null && book.author != "Autore non specificato") {
                        AuthorWikipediaIconButton(
                            authorName = book.author,
                            onClick = { onOpenAuthorWikipedia(book.author) },
                            isCompact = true
                        )
                    }
                }
            }

            // Genere con Emoji & Anno
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (book.genre.isNotBlank()) {
                    Text(
                        text = "$genreEmoji ${book.genre}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (book.publishedYear.isNotBlank()) {
                    if (book.genre.isNotBlank()) {
                        Text(text = " • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        text = book.publishedYear,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pillola Stato Interattiva (Tocca per cambiare stato) + Valutazione
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                JoyfulReadingStatusInteractiveChip(
                    status = book.readingStatus,
                    onClick = onQuickToggleStatus
                )

                if (book.rating > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(text = "⭐", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", book.rating),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF92400E),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chip Stato di Lettura Interattivo e Allegro
 */
@Composable
private fun JoyfulReadingStatusInteractiveChip(
    status: String,
    onClick: () -> Unit
) {
    val (label, bg, fg) = when (status) {
        "LETTO" -> Triple("☕ Letto", Color(0xFFDCFCE7), Color(0xFF166534))
        "IN_LETTURA" -> Triple("📖 Leggendo", Color(0xFFDBEAFE), Color(0xFF1E40AF))
        else -> Triple("⏳ Da leggere", Color(0xFFFFEDD5), Color(0xFF9A3412))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(0.8.dp, fg.copy(alpha = 0.3f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            fontSize = 10.sp
        )
    }
}

/**
 * Ripiano in Legno per la modalità Scaffale Libreria
 */
@Composable
private fun WoodenBookShelfRow(
    books: List<BookEntity>,
    shelfNumber: Int,
    onBookClick: (BookEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // I Libri posizionati sul ripiano
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            books.forEach { book ->
                val genreEmoji = getGenreEmoji(book.genre)
                val ribbonColor = when (book.readingStatus) {
                    "LETTO" -> Color(0xFF10B981)
                    "IN_LETTURA" -> Color(0xFF3B82F6)
                    else -> Color(0xFFF97316)
                }

                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(145.dp)
                        .clickable { onBookClick(book) }
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!book.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(book.coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            )
                                        )
                                    )
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = genreEmoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    // Segnalibro pendente dal libro
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 6.dp)
                            .width(10.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(ribbonColor)
                    )
                }
            }
        }

        // Texture del ripiano di legno con gradiente caldo e venature
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFD97706),
                            Color(0xFF92400E),
                            Color(0xFF78350F)
                        )
                    )
                )
                .shadow(4.dp)
        )
    }
}

/**
 * Dialog con animazione allegra quando si pesca un autore a caso
 */
@Composable
private fun RandomAuthorSurpriseDialog(
    authorGroup: AuthorGroup,
    onDismiss: () -> Unit,
    onExplore: () -> Unit
) {
    val gradientColors = AuthorCardGradients[Math.abs(authorGroup.authorName.hashCode()) % AuthorCardGradients.size]

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onExplore,
                colors = ButtonDefaults.buttonColors(containerColor = gradientColors[0])
            ) {
                Text("Sfoglia Opere 📖", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎲 ✨", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Autore a Sorpresa!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = authorGroup.authorName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = authorGroup.authorName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Hai ${authorGroup.totalBooks} ${if (authorGroup.totalBooks == 1) "volume" else "volumi"} di questo scrittore in collezione (${authorGroup.readBooks} letti). Cosa ne dici di una nuova lettura?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Dialog con animazione allegra quando si pesca un libro a caso
 */
@Composable
private fun RandomBookSurpriseDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
    onOpenBook: () -> Unit
) {
    val genreEmoji = getGenreEmoji(book.genre)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onOpenBook,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apri Libro 📖", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎉 📖", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ecco la tua prossima lettura!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .width(100.dp)
                        .height(145.dp)
                ) {
                    if (!book.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(book.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = genreEmoji, fontSize = 36.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = book.author.ifBlank { "Autore non specificato" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Stato attuale: ${when (book.readingStatus) { "LETTO" -> "☕ Già Letto"; "IN_LETTURA" -> "📖 In corso"; else -> "⏳ Da Leggere" }}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Vista Rapida in stile Carosello delle copertine dei libri per scorrere velocemente la libreria
 */
@Composable
fun JoyfulBooksQuickCarouselView(
    books: List<BookEntity>,
    onBookClick: (BookEntity) -> Unit,
    onUpdateBook: (BookEntity) -> Unit = {},
    onOpenAuthorWikipedia: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "📚🎠", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Nessun libro trovato per questa selezione",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { books.size })
    val filmstripListState = rememberLazyListState()

    // Sincronizzazione automatica filmstrip inferiore con la pagina attiva del carosello
    LaunchedEffect(pagerState.currentPage) {
        val targetIndex = (pagerState.currentPage - 2).coerceAtLeast(0)
        filmstripListState.animateScrollToItem(targetIndex)
    }

    val currentBook = books.getOrNull(pagerState.currentPage)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Barra indicatore rapido superiore & pulsanti prev/next
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (pagerState.currentPage > 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("carousel_prev_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = "Libro precedente",
                    tint = if (pagerState.currentPage > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Volume ${pagerState.currentPage + 1} di ${books.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            IconButton(
                onClick = {
                    if (pagerState.currentPage < books.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                enabled = pagerState.currentPage < books.size - 1,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("carousel_next_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Libro successivo",
                    tint = if (pagerState.currentPage < books.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 2. Carosello principale copertine (HorizontalPager con scorrimento fluido)
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 60.dp),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
                .testTag("library_quick_carousel_pager")
        ) { page ->
            val book = books[page]
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val scale = lerp(0.82f, 1.0f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
            val alpha = lerp(0.5f, 1.0f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                contentAlignment = Alignment.Center
            ) {
                QuickCarouselCoverCard(
                    book = book,
                    isActive = page == pagerState.currentPage,
                    onClick = { onBookClick(book) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Titolo e autore del libro corrente sotto la copertina
        if (currentBook != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp)
                    .clickable { onBookClick(currentBook) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentBook.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentBook.author.ifBlank { "Autore sconosciuto" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onOpenAuthorWikipedia != null && currentBook.author.isNotBlank() && currentBook.author != "Autore sconosciuto") {
                        Spacer(modifier = Modifier.width(6.dp))
                        AuthorWikipediaIconButton(
                            authorName = currentBook.author,
                            onClick = { onOpenAuthorWikipedia(currentBook.author) },
                            isCompact = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = { onBookClick(currentBook) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Modifica Dati",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Striscia Filmstrip (mini-copertine) a scorrimento rapido continuo in basso
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            LazyRow(
                state = filmstripListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .testTag("carousel_filmstrip_row")
            ) {
                columnItemsIndexed(books) { index, bk ->
                    val isSelected = index == pagerState.currentPage
                    val borderAnim by animateFloatAsState(
                        targetValue = if (isSelected) 2.5f else 0.5f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "border"
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            width = borderAnim.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        shadowElevation = if (isSelected) 6.dp else 1.dp,
                        modifier = Modifier
                            .width(if (isSelected) 44.dp else 38.dp)
                            .height(if (isSelected) 60.dp else 52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                            .testTag("carousel_thumbnail_$index")
                    ) {
                        if (!bk.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(bk.coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = bk.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val colorIdx = (bk.title.hashCode().absoluteValue) % AuthorCardGradients.size
                            val gradient = AuthorCardGradients[colorIdx]
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(gradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getGenreEmoji(bk.genre),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Copertina grande del libro nel carosello con effetto costa del libro realistica e badge
 */
@Composable
private fun QuickCarouselCoverCard(
    book: BookEntity,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bookShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)

    Card(
        shape = bookShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 10.dp else 3.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .aspectRatio(0.68f)
            .clip(bookShape)
            .clickable { onClick() }
            .testTag("carousel_cover_card_${book.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!book.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val colorIdx = (book.title.hashCode().absoluteValue) % AuthorCardGradients.size
                val gradient = AuthorCardGradients[colorIdx]

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    gradient[0],
                                    gradient[1],
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = getGenreEmoji(book.genre),
                            fontSize = 38.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.author.ifBlank { "Autore sconosciuto" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Effetto costa del libro (ombra sfumata a sinistra per realismo tridimensionale)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Indicatore segnalibro piccolo in alto a destra
            val bookmarkColor = when (book.readingStatus) {
                "LETTO" -> Color(0xFF16A34A)
                "IN_LETTURA" -> Color(0xFF0284C7)
                else -> Color(0xFFEA580C)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = bookmarkColor,
                    shadowElevation = 3.dp
                ) {
                    Text(
                        text = when (book.readingStatus) { "LETTO" -> "☕"; "IN_LETTURA" -> "📖"; else -> "⏳" },
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
