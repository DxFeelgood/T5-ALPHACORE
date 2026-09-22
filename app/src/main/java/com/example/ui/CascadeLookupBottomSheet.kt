package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.BookEntity
import com.example.data.remote.ApiSource
import com.example.data.remote.CascadeStepStatus
import com.example.data.remote.LookupResult
import com.example.ui.theme.AmazonOrange
import com.example.ui.theme.IbsBlue
import com.example.ui.theme.LibraccioRed
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CascadeLookupBottomSheet(
    lookupState: LookupUiState,
    isBatchMode: Boolean = false,
    onDismiss: () -> Unit,
    onSaveBook: (LookupResult, String, String, String, Float, String, String, Boolean) -> Unit,
    onManualAdd: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (lookupState !is LookupUiState.Idle) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("lookup_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ricerca a Cascata",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_lookup_sheet_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (lookupState) {
                    is LookupUiState.Searching -> {
                        CascadeSearchingView(
                            currentSource = lookupState.currentSource,
                            isbn = lookupState.isbn,
                            logs = lookupState.logs
                        )
                    }

                    is LookupUiState.Success -> {
                        CascadeSuccessView(
                            result = lookupState.result,
                            logs = lookupState.logs,
                            alreadyExists = lookupState.alreadyExistsInDb,
                            isBatchMode = isBatchMode,
                            onSave = { status, shelf, notes, rating, genre, tags, continueBatch ->
                                onSaveBook(lookupState.result, status, shelf, notes, rating, genre, tags, continueBatch)
                            }
                        )
                    }

                    is LookupUiState.NotFound -> {
                        CascadeNotFoundView(
                            isbn = lookupState.isbn,
                            logs = lookupState.logs,
                            onManualAdd = { onManualAdd(lookupState.isbn) }
                        )
                    }

                    is LookupUiState.Error -> {
                        Text(
                            text = "Errore: ${lookupState.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    LookupUiState.Idle -> {}
                }
            }
        }
    }
}

@Composable
fun CascadeSearchingView(
    currentSource: ApiSource,
    isbn: String,
    logs: List<CascadeStepStatus>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Catalogazione ISBN: $isbn",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Interrogazione API in corso...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Indicatori visivi dei livelli di cascata Web Scraping
            CascadeTimelineItem(
                title = "1ª scelta: IBS.it",
                subtitle = "Catalogo IBS.it & dataLayer",
                state = when {
                    logs.any { it.source == ApiSource.IBS_IT && it.success } -> StepState.SUCCESS
                    logs.any { it.source == ApiSource.IBS_IT && !it.success } -> StepState.FAILED
                    currentSource == ApiSource.IBS_IT -> StepState.IN_PROGRESS
                    else -> StepState.WAITING
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            CascadeTimelineItem(
                title = "2ª scelta: Libraccio.it",
                subtitle = "Catalogo e metadati JSON-LD Libraccio.it",
                state = when {
                    logs.any { it.source == ApiSource.LIBRACCIO_IT && it.success } -> StepState.SUCCESS
                    logs.any { it.source == ApiSource.LIBRACCIO_IT && !it.success } -> StepState.FAILED
                    currentSource == ApiSource.LIBRACCIO_IT -> StepState.IN_PROGRESS
                    else -> StepState.WAITING
                }
            )
        }
    }
}

enum class StepState { WAITING, IN_PROGRESS, SUCCESS, FAILED }

@Composable
fun CascadeTimelineItem(
    title: String,
    subtitle: String,
    state: StepState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            StepState.WAITING -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("•", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
            StepState.IN_PROGRESS -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            StepState.SUCCESS -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Successo",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(24.dp)
                )
            }
            StepState.FAILED -> {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Non trovato",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (state == StepState.IN_PROGRESS || state == StepState.SUCCESS) FontWeight.Bold else FontWeight.Normal,
                color = if (state == StepState.FAILED) Color.Gray else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CascadeSuccessView(
    result: LookupResult,
    logs: List<CascadeStepStatus>,
    alreadyExists: Boolean,
    isBatchMode: Boolean = false,
    onSave: (status: String, shelf: String, notes: String, rating: Float, genre: String, tags: String, continueBatch: Boolean) -> Unit
) {
    var selectedStatus by remember { mutableStateOf("DA_LEGGERE") }
    var shelfLocation by remember { mutableStateOf("") }
    var userNotes by remember { mutableStateOf("") }
    var rating by remember { mutableFloatStateOf(0f) }
    var selectedCategory by remember { mutableStateOf(result.genre) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var continueBatch by remember(isBatchMode) { mutableStateOf(isBatchMode) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Badge sorgente API che ha risposto
        ApiSourceBadge(sourceApi = result.sourceApi)

        Spacer(modifier = Modifier.height(10.dp))

        if (alreadyExists) {
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Questo libro è già presente nel tuo archivio. Verrà aggiornato o salvato di nuovo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF92400E)
                    )
                }
            }
        }

        // Scheda libro trovato
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Copertina
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 145.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!result.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = result.coverUrl,
                        contentDescription = "Copertina ${result.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Copertina non disp.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = result.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (result.publisher.isNotBlank()) {
                    Text(
                        text = "Editore: ${result.publisher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (result.publishedYear.isNotBlank()) {
                    Text(
                        text = "Anno: ${result.publishedYear}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (result.pageCount > 0) {
                    Text(
                        text = "Pagine: ${result.pageCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "ISBN: ${result.isbn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (result.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = result.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Personalizzazione archiviazione
        Text(
            text = "Dettagli di catalogazione",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Stato di lettura
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "DA_LEGGERE" to "Da Leggere",
                "IN_LETTURA" to "In Lettura",
                "LETTO" to "Letto"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = selectedStatus == key,
                    onClick = { selectedStatus = key },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("status_chip_$key")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Posizione scaffale
        OutlinedTextField(
            value = shelfLocation,
            onValueChange = { shelfLocation = it },
            label = { Text("Posizione / Scaffale (es. Salotto ripiano 2, Studio)") },
            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shelf_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Note personali
        OutlinedTextField(
            value = userNotes,
            onValueChange = { userNotes = it },
            label = { Text("Note personali o citazioni") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notes_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Categoria / Genere
        Text(
            text = "Categoria / Genere:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            POPULAR_CATEGORIES.take(8).forEach { cat ->
                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedCategory = if (isSelected) "" else cat
                    },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tag Personalizzati
        Text(
            text = "Tag Personalizzati:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            POPULAR_TAGS.take(6).forEach { tag ->
                val isSelected = selectedTags.contains(tag)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                    },
                    label = { Text("#$tag") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Valutazione stelle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = "Valutazione:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            (1..5).forEach { starIndex ->
                IconButton(
                    onClick = {
                        rating = if (rating == starIndex.toFloat()) 0f else starIndex.toFloat()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (rating >= starIndex) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Stella $starIndex",
                        tint = if (rating >= starIndex) WarmAmber else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                showConfirmationDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("confirm_save_book_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(if (continueBatch) Icons.Default.QrCodeScanner else Icons.Default.Bookmark, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (continueBatch) "Salva e Continua Scansione" else "Salva nella Libreria", fontWeight = FontWeight.Bold)
        }

        if (showConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmationDialog = false },
                icon = {
                    Icon(
                        imageVector = if (continueBatch) Icons.Default.QrCodeScanner else Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = if (continueBatch) "Conferma e Continua Scansione" else "Conferma salvataggio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Vuoi salvare questo libro nella tua libreria?",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = result.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = result.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (result.isbn.isNotBlank()) {
                                    Text(
                                        text = "ISBN: ${result.isbn}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val statusLabel = when (selectedStatus) {
                                    "IN_LETTURA" -> "In Lettura"
                                    "LETTO" -> "Letto"
                                    else -> "Da Leggere"
                                }
                                Text(
                                    text = "Stato: $statusLabel" + if (shelfLocation.isNotBlank()) " • Scaffale: $shelfLocation" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        if (isBatchMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { continueBatch = !continueBatch }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = continueBatch,
                                    onCheckedChange = { continueBatch = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Riapri fotocamera per scansionare il prossimo libro (batch)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (alreadyExists) {
                            Text(
                                text = "Nota: Un libro con questo ISBN è già presente nel catalogo e verrà aggiornato.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmationDialog = false
                            onSave(
                                selectedStatus,
                                shelfLocation,
                                userNotes,
                                rating,
                                selectedCategory.trim(),
                                selectedTags.joinToString(", "),
                                continueBatch
                            )
                        },
                        modifier = Modifier.testTag("confirm_dialog_save_button")
                    ) {
                        Icon(
                            imageVector = if (continueBatch) Icons.Default.QrCodeScanner else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (continueBatch) "Conferma e Continua" else "Conferma e Salva")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirmationDialog = false },
                        modifier = Modifier.testTag("confirm_dialog_cancel_button")
                    ) {
                        Text("Annulla")
                    }
                }
            )
        }
    }
}

@Composable
fun CascadeNotFoundView(
    isbn: String,
    logs: List<CascadeStepStatus>,
    onManualAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Nessun volume trovato",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "La ricerca in cascata per l'ISBN $isbn è stata completata su tutte e 3 le fonti:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            logs.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onManualAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_add_from_not_found_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inserisci Dati Manualmente")
            }
        }
    }
}

@Composable
fun ApiSourceBadge(sourceApi: String) {
    val (bgColor, textColor, label) = when (sourceApi) {
        "IBS.it" -> Triple(IbsBlue.copy(alpha = 0.15f), IbsBlue, "Trovato su IBS.it (1ª Scelta)")
        "Libraccio.it" -> Triple(LibraccioRed.copy(alpha = 0.15f), LibraccioRed, "Trovato su Libraccio.it (2ª Scelta)")
        "Amazon.it", "Apify Amazon Scraper", "Amazon Direct Web Scraper" -> Triple(AmazonOrange.copy(alpha = 0.15f), AmazonOrange, "Origine: Amazon.it")
        else -> Triple(Color.Gray.copy(alpha = 0.15f), Color.DarkGray, "Origine: $sourceApi")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("api_source_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
