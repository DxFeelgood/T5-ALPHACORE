package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import com.example.data.local.BookEntity
import com.example.data.remote.LookupResult

private val POPULAR_BOOK_CATEGORIES = listOf(
    "Narrativa", "Romanzo", "Fantascienza", "Giallo / Thriller",
    "Storico", "Saggistica", "Biografia", "Filosofia", "Poesia", "Arte", "Fumetti"
)

private val POPULAR_BOOK_TAGS = listOf(
    "capolavoro", "preferito", "da_leggere", "prestito", "prima_edizione", "firmato", "classico"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualAddBookDialog(
    initialIsbn: String = "",
    viewModel: BookViewModel? = null,
    onDismiss: () -> Unit,
    onSaveBook: (BookEntity) -> Unit
) {
    var isbn by remember { mutableStateOf(initialIsbn) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var customCategoryInput by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var newTagInput by remember { mutableStateOf("") }
    var shelf by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("DA_LEGGERE") }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var sourceApi by remember { mutableStateOf("Manuale") }
    var selectedEditionSummary by remember { mutableStateOf<String?>(null) }
    var selectedEdition by remember { mutableStateOf<LookupResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var titleError by remember { mutableStateOf(false) }

    val editionResults by (viewModel?.editionSearchResults?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val isSearchingEditions by (viewModel?.isSearchingEditions?.collectAsState() ?: remember { mutableStateOf(false) })
    val searchEditionsError by (viewModel?.searchEditionsError?.collectAsState() ?: remember { mutableStateOf<String?>(null) })

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .navigationBarsPadding()
            .imePadding()
            .padding(vertical = 16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Aggiungi Libro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SCHEDA 1: Titolo e Autore + Pulsante di Ricerca
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1. Inserisci Titolo ed Autore per cercare online",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Titolo (obbligatorio)
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                if (it.isNotBlank()) titleError = false
                            },
                            label = { Text("Titolo del libro *") },
                            placeholder = { Text("es. Il nome della rosa, 1984...") },
                            isError = titleError,
                            supportingText = {
                                if (titleError) Text("Il titolo è obbligatorio", color = MaterialTheme.colorScheme.error)
                            },
                            trailingIcon = {
                                if (title.isNotBlank()) {
                                    IconButton(onClick = {
                                        title = ""
                                        viewModel?.clearEditionResults()
                                        selectedEditionSummary = null
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Pulisci titolo", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_title_input")
                        )

                        // Autore
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("Autore") },
                            placeholder = { Text("es. Umberto Eco, George Orwell...") },
                            trailingIcon = {
                                if (author.isNotBlank()) {
                                    IconButton(onClick = {
                                        author = ""
                                        viewModel?.clearEditionResults()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Pulisci autore", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_author_input")
                        )

                        // Pulsante Ricerca Esplicita
                        Button(
                            onClick = {
                                viewModel?.searchEditions(title, author)
                            },
                            enabled = (title.isNotBlank() || author.isNotBlank()) && !isSearchingEditions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_edition_search_btn")
                        ) {
                            if (isSearchingEditions) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ricerca edizioni in corso...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔍 Cerca Dettagli ed Edizioni Online")
                            }
                        }
                    }
                }

                // Indicatore di Caricamento Ricerca Edizioni Online
                if (isSearchingEditions) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("manual_search_spinner"),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Scansione fonti online...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Verifica di IBS.it e Libraccio.it in corso...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Messaggio di Errore o Esito Ricerca vuoto
                if (searchEditionsError != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("manual_search_error_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Errore o Nessun Risultato",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Esito Ricerca Online",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = searchEditionsError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // SCHEDA 2: Risultati Ricerca Edizioni (Menu a Tendina)
                if (editionResults.isNotEmpty()) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Edizioni Trovate Online (${editionResults.size}):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Seleziona dal menu sotto",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("select_edition_dropdown_trigger"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedEditionSummary ?: "Scegli un'edizione dal menu a tendina...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedEditionSummary != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Apri menu a tendina",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                editionResults.forEach { edition ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Miniatura della copertina nel menu a tendina
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 38.dp, height = 54.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!edition.coverUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(edition.coverUrl)
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = edition.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.MenuBook,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = edition.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (edition.author.isNotBlank()) {
                                                        Text(
                                                            text = edition.author,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = buildString {
                                                            if (edition.publisher.isNotBlank()) append("🏢 ${edition.publisher}")
                                                            if (edition.publishedYear.isNotBlank() && edition.publishedYear != "2000") {
                                                                if (isNotEmpty()) append(" • ")
                                                                append("📅 ${edition.publishedYear}")
                                                            }
                                                            if (edition.pageCount > 0) {
                                                                if (isNotEmpty()) append(" • ")
                                                                append("📄 ${edition.pageCount} pag.")
                                                            }
                                                            if (edition.isbn.isNotBlank()) {
                                                                if (isNotEmpty()) append(" • ")
                                                                append("🏷️ ${edition.isbn}")
                                                            }
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            selectedEdition = edition
                                            title = edition.title
                                            author = edition.author
                                            if (edition.publisher.isNotBlank()) publisher = edition.publisher.trim()
                                            if (edition.publishedYear.isNotBlank() && edition.publishedYear != "2000") year = edition.publishedYear.trim()
                                            if (edition.pageCount > 0) pages = edition.pageCount.toString()
                                            if (edition.isbn.isNotBlank()) isbn = edition.isbn.trim()
                                            if (edition.genre.isNotBlank()) category = edition.genre
                                            if (!edition.coverUrl.isNullOrBlank()) coverUrl = edition.coverUrl
                                            if (edition.description.isNotBlank()) description = edition.description
                                            sourceApi = edition.sourceApi

                                            val pubInfo = buildString {
                                                if (edition.publisher.isNotBlank()) append(edition.publisher)
                                                if (edition.publishedYear.isNotBlank() && edition.publishedYear != "2000") {
                                                    if (isNotEmpty()) append(" (") else append("")
                                                    append(edition.publishedYear)
                                                    append(")")
                                                }
                                                if (edition.pageCount > 0) {
                                                    if (isNotEmpty()) append(" - ")
                                                    append("${edition.pageCount} pag.")
                                                }
                                            }
                                            selectedEditionSummary = if (pubInfo.isNotBlank()) "${edition.title} • $pubInfo" else edition.title

                                            // Se editore o anno o copertina sono assenti, arricchimento istantaneo in background
                                            if (publisher.isBlank() || year.isBlank() || coverUrl.isNullOrBlank()) {
                                                coroutineScope.launch {
                                                    try {
                                                        val enriched = viewModel?.enrichEditionMetadata(edition)
                                                        if (enriched != null) {
                                                            if (publisher.isBlank() && enriched.publisher.isNotBlank()) {
                                                                publisher = enriched.publisher.trim()
                                                            }
                                                            if (year.isBlank() && enriched.publishedYear.isNotBlank() && enriched.publishedYear != "2000") {
                                                                year = enriched.publishedYear.trim()
                                                            }
                                                            if (pages.isBlank() && enriched.pageCount > 0) {
                                                                pages = enriched.pageCount.toString()
                                                            }
                                                            if (coverUrl.isNullOrBlank() && !enriched.coverUrl.isNullOrBlank()) {
                                                                coverUrl = enriched.coverUrl
                                                            }
                                                            val newPubInfo = buildString {
                                                                if (publisher.isNotBlank()) append(publisher)
                                                                if (year.isNotBlank() && year != "2000") {
                                                                    if (isNotEmpty()) append(" (") else append("")
                                                                    append(year)
                                                                    append(")")
                                                                }
                                                                if (pages.isNotBlank() && pages != "0") {
                                                                    if (isNotEmpty()) append(" - ")
                                                                    append("$pages pag.")
                                                                }
                                                            }
                                                            if (newPubInfo.isNotBlank()) {
                                                                selectedEditionSummary = "${edition.title} • $newPubInfo"
                                                            }
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edition_menu_item_${edition.isbn}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Banner Edizione Selezionata con Copertina in Primo Piano
                if (selectedEditionSummary != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().testTag("selected_edition_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Visualizzazione Copertina dell'Edizione Selezionata
                            Box(
                                modifier = Modifier
                                    .size(width = 65.dp, height = 95.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Copertina dell'edizione selezionata",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("selected_edition_cover_image")
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = "Nessuna copertina",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Nessuna\ncopertina",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Info dettagliate dell'Edizione Selezionata
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Edizione Selezionata",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = title.ifBlank { selectedEditionSummary ?: "" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (author.isNotBlank()) {
                                    Text(
                                        text = "di $author",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (publisher.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = "🏢 $publisher",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    if (year.isNotBlank() && year != "2000") {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = "📅 $year",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    if (pages.isNotBlank() && pages != "0") {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = "📄 $pages pag.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isbn.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = "🏷️ $isbn",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SCHEDA 3: Dettagli completi del Libro
                Text(
                    text = "2. Dettagli ed Informazioni Libro",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Row: Editore & Anno
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = publisher,
                        onValueChange = { publisher = it },
                        label = { Text("Editore") },
                        placeholder = { Text("es. Mondadori, Feltrinelli...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_publisher_input")
                    )

                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Anno") },
                        placeholder = { Text("es. 2021") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.7f)
                            .testTag("manual_year_input")
                    )
                }

                // Row: Pagine & ISBN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = pages,
                        onValueChange = { pages = it },
                        label = { Text("N° Pagine") },
                        placeholder = { Text("es. 320") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("manual_pages_input")
                    )

                    OutlinedTextField(
                        value = isbn,
                        onValueChange = { isbn = it },
                        label = { Text("Codice ISBN") },
                        placeholder = { Text("es. 9788804735397") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("manual_isbn_input")
                    )
                }

                // Genere / Categoria
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Genere / Categoria:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        POPULAR_BOOK_CATEGORIES.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = {
                                    category = if (category == cat) "" else cat
                                },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Genere personalizzato") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Stato Lettura
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Stato Lettura:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "DA_LEGGERE" to "Da Leggere",
                            "IN_LETTURA" to "In Lettura",
                            "LETTO" to "Letto",
                            "ABBANDONATO" to "Abbandonato"
                        ).forEach { (stKey, stLabel) ->
                            FilterChip(
                                selected = status == stKey,
                                onClick = { status = stKey },
                                label = { Text(stLabel, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Scaffale
                OutlinedTextField(
                    value = shelf,
                    onValueChange = { shelf = it },
                    label = { Text("Scaffale / Posizione Libreria") },
                    placeholder = { Text("es. Soggiorno - Ripiano 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Note
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note Personali") },
                    placeholder = { Text("es. Regalo di compleanno, letto in estate...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val bookEntity = BookEntity(
                        isbn = isbn.trim(),
                        title = title.trim(),
                        author = author.trim(),
                        publisher = publisher.trim(),
                        publishedYear = year.trim(),
                        description = description.trim(),
                        coverUrl = coverUrl,
                        pageCount = pages.toIntOrNull() ?: 0,
                        genre = category.ifBlank { "Narrativa" },
                        customTags = selectedTags.joinToString(","),
                        readingStatus = status,
                        shelfLocation = shelf.trim(),
                        userNotes = notes.trim(),
                        sourceApi = sourceApi
                    )
                    onSaveBook(bookEntity)
                },
                modifier = Modifier.testTag("save_manual_book_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salva Libro")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun EditionItemCard(
    edition: LookupResult,
    onSelect: (LookupResult) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(edition) }
            .testTag("edition_card_${edition.isbn}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copertina
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 64.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!edition.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(edition.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = edition.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Dettagli
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = edition.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (edition.author.isNotBlank()) edition.author else "Autore non spec.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Editore, Anno e Pagine
                    Text(
                        text = buildString {
                            if (edition.publisher.isNotBlank()) append(edition.publisher)
                            if (edition.publishedYear.isNotBlank() && edition.publishedYear != "2000") {
                                if (isNotEmpty()) append(" • ")
                                append(edition.publishedYear)
                            }
                            if (edition.pageCount > 0) {
                                if (isNotEmpty()) append(" • ")
                                append("${edition.pageCount} pag.")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Usa questi dati",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }
    }
}
