package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookEntity
import com.example.data.remote.DirectExportResult
import com.example.data.remote.DirectImportResult
import com.example.data.remote.SyncReport
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maschera delle Impostazioni dell'applicazione:
 * - Selezione della Skin grafica (Chiara, Scura, Sistema)
 * - Gestione completa di Import ed Export su Google Cloud / Drive / Backup JSON
 * - Zone collassabili chiuse di default
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BookViewModel,
    books: List<BookEntity>,
    onBackClick: (() -> Unit)? = null,
    showBackButton: Boolean = false,
    onOpenExportDetail: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentThemeMode by viewModel.themeMode.collectAsState()

    val syncLogs by viewModel.firestoreSyncLogs.collectAsState()
    var showLogsDialog by remember { mutableStateOf(false) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isSyncingNow by remember { mutableStateOf(false) }
    var syncReport by remember { mutableStateOf<SyncReport?>(null) }
    var isDirectExporting by remember { mutableStateOf(false) }
    var directExportResult by remember { mutableStateOf<DirectExportResult?>(null) }
    var isDirectImporting by remember { mutableStateOf(false) }
    var directImportResult by remember { mutableStateOf<DirectImportResult?>(null) }
    var showPdfExportSheet by remember { mutableStateOf(false) }

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
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Impostazioni",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gestione dati, backup e preferenze",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton && onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Torna indietro"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Messaggio di conferma importazione avvenuta con successo
                if (importSuccessMessage != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDCFCE7)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = importSuccessMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF14532D),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // SEZIONE 1: SKIN E TEMA GRAFICO
            item {
                ThemeSelectionSection(
                    currentMode = currentThemeMode,
                    onSelectMode = { mode ->
                        viewModel.setThemeMode(mode)
                    }
                )
            }

            // SEZIONE 2: GOOGLE CLOUD & BACKUP (ESPORTAZIONE DIRETTA FIRESTORE / SINCRONIZZAZIONE)
            item {
                CloudSyncSection(
                    books = books,
                    isSyncing = isSyncingNow,
                    syncReport = syncReport,
                    isDirectExporting = isDirectExporting,
                    directExportResult = directExportResult,
                    isDirectImporting = isDirectImporting,
                    directImportResult = directImportResult,
                    onDirectExport = {
                        showLogsDialog = true
                        coroutineScope.launch {
                            isDirectExporting = true
                            val result = viewModel.directExportAllBooksToFirestore()
                            isDirectExporting = false
                            result.onSuccess { report ->
                                directExportResult = report
                                Toast.makeText(context, report.message, Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Errore Firestore: ${err.localizedMessage ?: "Verifica la configurazione di google-services.json"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDirectImport = {
                        showLogsDialog = true
                        coroutineScope.launch {
                            isDirectImporting = true
                            val result = viewModel.directImportAllBooksFromFirestore()
                            isDirectImporting = false
                            result.onSuccess { report ->
                                directImportResult = report
                                Toast.makeText(context, report.message, Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Errore Firestore: ${err.localizedMessage ?: "Verifica la configurazione di google-services.json"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onForceSyncNow = {
                        showLogsDialog = true
                        coroutineScope.launch {
                            isSyncingNow = true
                            val result = viewModel.forceSyncToFirestoreWithConflictResolution()
                            isSyncingNow = false
                            result.onSuccess { report ->
                                syncReport = report
                                Toast.makeText(context, report.message, Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Errore di sincronizzazione: ${err.localizedMessage ?: "Errore di rete"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onTestFirebase = {
                        showLogsDialog = true
                        coroutineScope.launch {
                            val res = viewModel.testFirestoreConnection()
                            if (res.isSuccess) {
                                Toast.makeText(context, "Connessione Firestore riuscita!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Test Firebase fallito: vedi finestra log.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onExportCloudJson = {
                        val json = buildExportJson(books)
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_TITLE, "backup_archivio_libri.json")
                            putExtra(Intent.EXTRA_SUBJECT, "Backup Archivio Libri (${books.size} volumi)")
                            type = "application/json"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Carica / Condividi File JSON")
                        context.startActivity(shareIntent)
                    },
                    onCopyJson = {
                        val json = buildExportJson(books)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Backup JSON Catalogo", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "JSON copiato negli appunti!", Toast.LENGTH_SHORT).show()
                    },
                    onOpenExportDetail = onOpenExportDetail,
                    onOpenImportDialog = {
                        importJsonText = ""
                        importError = null
                        showImportDialog = true
                    }
                )
            }

            // SEZIONE 3: RIEPILOGO PDF & STAMPA COLLEZIONE
            item {
                PdfReportSummarySection(
                    booksCount = books.size,
                    onOpenPdfExport = { showPdfExportSheet = true }
                )
            }

            // SEZIONE 4: INFORMAZIONI APP
            item {
                AppInfoSection(booksCount = books.size)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // FINESTRA LOG BACKUP/SINCRONIZZAZIONE
    if (showLogsDialog) {
        Dialog(
            onDismissRequest = {
                if (!isDirectExporting && !isSyncingNow) {
                    showLogsDialog = false
                }
            }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("firestore_logs_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tracciamento Sincronizzazione",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Finestra di log simile a un terminale
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A)) // Grigio scuro ardesia
                            .padding(10.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        androidx.compose.runtime.LaunchedEffect(syncLogs.size) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }

                        Column(
                            modifier = Modifier
                                .verticalScroll(scrollState)
                                .fillMaxWidth()
                        ) {
                            if (syncLogs.isEmpty()) {
                                Text(
                                    text = "In attesa dei log...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF64748B)
                                    )
                                )
                            } else {
                                syncLogs.forEach { log ->
                                    val textColor = when {
                                        log.contains("ERRORE", ignoreCase = true) -> Color(0xFFF87171) // Rosso
                                        log.contains("completato", ignoreCase = true) || log.contains("successo", ignoreCase = true) -> Color(0xFF4ADE80) // Verde
                                        else -> Color(0xFFE2E8F0) // Bianco/grigio chiaro
                                    }
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = textColor
                                        ),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isDirectExporting || isSyncingNow) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.CenterVertically),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Elaborazione...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        } else {
                            TextButton(
                                onClick = { showLogsDialog = false }
                            ) {
                                Text("Chiudi", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOGO IMPORTAZIONE BACKUP DA CLOUD / JSON
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) showImportDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Importa Backup da Cloud",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Incolla il testo del backup JSON scaricato da Google Cloud o esportato in precedenza:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importError = null
                        },
                        placeholder = { Text("{\n  \"books\": [...]\n}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("import_json_input"),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                    if (pasted.isNotBlank()) {
                                        importJsonText = pasted
                                        Toast.makeText(context, "Testo incollato dagli appunti", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Incolla appunti")
                        }

                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }

                    if (importError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = importError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isBlank()) {
                            importError = "Inserisci o incolla il codice JSON del backup"
                            return@Button
                        }
                        coroutineScope.launch {
                            isImporting = true
                            importError = null
                            val result = viewModel.importBooksFromJson(importJsonText)
                            isImporting = false
                            result.onSuccess { count ->
                                showImportDialog = false
                                importSuccessMessage = "✓ $count libri importati/aggiornati con successo nella tua libreria!"
                            }.onFailure { err ->
                                importError = "Errore durante l'importazione: ${err.localizedMessage ?: "Formato non valido"}"
                            }
                        }
                    },
                    enabled = !isImporting && importJsonText.isNotBlank(),
                    modifier = Modifier.testTag("confirm_import_button")
                ) {
                    Text("Importa e Salva")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    enabled = !isImporting
                ) {
                    Text("Annulla")
                }
            }
        )
    }

    // BOTTOM SHEET GENERATORE & STAMPA PDF
    if (showPdfExportSheet) {
        PdfExportBottomSheet(
            books = books,
            onDismiss = { showPdfExportSheet = false }
        )
    }
}

/**
 * Sezione di Selezione Skin Chiara / Scura / Sistema (collassabile, chiusa di default)
 */
@Composable
private fun ThemeSelectionSection(
    currentMode: AppThemeMode,
    onSelectMode: (AppThemeMode) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_theme_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aspetto & Skin Grafica",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Attiva: ${currentMode.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("expand_theme_section_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Comprimi tema" else "Espandi tema",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    ThemeOptionCard(
                        mode = AppThemeMode.LIGHT,
                        title = "Skin Chiara",
                        subtitle = "Tonalità calde pergamena e ambra, ideale di giorno",
                        icon = Icons.Default.LightMode,
                        accentColor = Color(0xFFD97706),
                        bgColor = Color(0xFFFFFBEB),
                        isSelected = currentMode == AppThemeMode.LIGHT,
                        onClick = { onSelectMode(AppThemeMode.LIGHT) },
                        testTag = "settings_theme_light"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ThemeOptionCard(
                        mode = AppThemeMode.DARK,
                        title = "Skin Scura",
                        subtitle = "Sfondo ardesia scuro e contrasto rilassante per gli occhi",
                        icon = Icons.Default.DarkMode,
                        accentColor = Color(0xFFF59E0B),
                        bgColor = Color(0xFF1E293B),
                        isSelected = currentMode == AppThemeMode.DARK,
                        onClick = { onSelectMode(AppThemeMode.DARK) },
                        testTag = "settings_theme_dark"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ThemeOptionCard(
                        mode = AppThemeMode.SYSTEM,
                        title = "Automatico (Sistema)",
                        subtitle = "Si adatta automaticamente al tema predefinito di Android",
                        icon = Icons.Default.SettingsBrightness,
                        accentColor = MaterialTheme.colorScheme.primary,
                        bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        isSelected = currentMode == AppThemeMode.SYSTEM,
                        onClick = { onSelectMode(AppThemeMode.SYSTEM) },
                        testTag = "settings_theme_system"
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    mode: AppThemeMode,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    bgColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

/**
 * Sezione Google Cloud & Backup (Esportazione Diretta Firestore, Sincronizzazione, Importazione - collassabile)
 */
@Composable
private fun CloudSyncSection(
    books: List<BookEntity>,
    isSyncing: Boolean = false,
    syncReport: SyncReport? = null,
    isDirectExporting: Boolean = false,
    directExportResult: DirectExportResult? = null,
    isDirectImporting: Boolean = false,
    directImportResult: DirectImportResult? = null,
    onDirectExport: () -> Unit = {},
    onDirectImport: () -> Unit = {},
    onForceSyncNow: () -> Unit = {},
    onTestFirebase: () -> Unit = {},
    onExportCloudJson: () -> Unit,
    onCopyJson: () -> Unit,
    onOpenExportDetail: () -> Unit,
    onOpenImportDialog: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showJsonOptions by remember { mutableStateOf(false) }
    var showTroubleshooting by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_cloud_section")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Cloud & Sincronizzazione",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${books.size} volumi memorizzati in locale (Room)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("expand_cloud_section_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Comprimi cloud" else "Espandi cloud",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    // SCHEDA 1: ESPORTAZIONE DIRETTA SU FIRESTORE (SENZA JSON)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Esportazione Diretta su Firestore",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Scrittura diretta documenti Cloud NoSQL (senza stringa JSON)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Popola direttamente la collezione 'books' del tuo database Cloud Firestore (configurato tramite google-services.json) salvando ogni volume come documento atomico, senza richiedere né generare alcun file o stringa JSON intermedia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = onDirectExport,
                                enabled = !isDirectExporting && !isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("direct_export_firestore_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isDirectExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Esportazione diretta in corso...", fontSize = 13.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Esporta Direttamente su Firestore", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Report esito esportazione diretta
                            if (directExportResult != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val isCompleteSuccess = directExportResult.failedCount == 0
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCompleteSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isCompleteSuccess) Color(0xFFA5D6A7) else Color(0xFFFFCDD2)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("direct_export_report_card")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isCompleteSuccess) Icons.Default.Check else Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (isCompleteSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isCompleteSuccess) "Esportazione Cloud Riuscita" else if (directExportResult.exportedCount > 0) "Esportazione Parziale" else "Esportazione Non Riuscita",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCompleteSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = directExportResult.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = Color.Black.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SCHEDA 2: IMPORTAZIONE DIRETTA DA FIRESTORE
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Importazione Diretta da Firestore",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Download e salvataggio nel database locale Room",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF0369A1),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Scarica l'intera collezione 'books' dal tuo progetto Cloud Firestore e popola il database locale del dispositivo. I volumi già esistenti vengono aggiornati, preservando tutte le note e i metadati.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = onDirectImport,
                                enabled = !isDirectImporting && !isDirectExporting && !isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("direct_import_firestore_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0284C7)
                                )
                            ) {
                                if (isDirectImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Importazione diretta in corso...", fontSize = 13.sp, color = Color.White)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Importa Direttamente da Firestore", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                            }

                            // Report esito importazione diretta
                            if (directImportResult != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val isCompleteSuccess = directImportResult.failedCount == 0 && directImportResult.totalRemote > 0
                                val isEmpty = directImportResult.totalRemote == 0
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isEmpty) Color(0xFFFFFBEB) else if (isCompleteSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isEmpty) Color(0xFFFDE68A) else if (isCompleteSuccess) Color(0xFFA5D6A7) else Color(0xFFFFCDD2)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("direct_import_report_card")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isCompleteSuccess) Icons.Default.Check else Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (isEmpty) Color(0xFFB45309) else if (isCompleteSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isEmpty) "Collezione Cloud Vuota" else if (isCompleteSuccess) "Importazione Cloud Riuscita" else "Importazione Parziale",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isEmpty) Color(0xFF92400E) else if (isCompleteSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = directImportResult.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = Color.Black.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SCHEDA 3: SINCRONIZZA ORA SU FIRESTORE CON GESTIONE CONFLITTI
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Sincronizza & Risolvi Conflitti",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Merge bidirezionale intelligente tra Room e Firestore",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Allinea e unisce intelligentemente le modifiche concorrenti unendo le note e mantenendo lo stato più aggiornato per ogni libro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            FilledTonalButton(
                                onClick = onForceSyncNow,
                                enabled = !isSyncing && !isDirectExporting,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sync_now_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sincronizzazione in corso...", fontSize = 13.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sincronizza Ora", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Report esito sincronizzazione
                            if (syncReport != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (syncReport.errorCount == 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (syncReport.errorCount == 0) Color(0xFFA5D6A7) else Color(0xFFFFCDD2)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sync_report_card")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (syncReport.errorCount == 0) Icons.Default.Check else Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (syncReport.errorCount == 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (syncReport.errorCount == 0) "Sincronizzazione Completata" else "Sincronizzazione Parziale",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (syncReport.errorCount == 0) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = syncReport.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = Color.Black.copy(alpha = 0.75f)
                                        )
                                        if (syncReport.resolvedConflictsCount > 0) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "⚡ Risolti ${syncReport.resolvedConflictsCount} conflitti di versione con merge note.",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFE65100),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SCHEDA 3: DIAGNOSTICA & GUIDA FIRESTORE
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTroubleshooting = !showTroubleshooting },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Diagnostica & Guida Risoluzione Errori",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Progetto: ${com.example.data.remote.FirebaseHelper.getProjectId()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (showTroubleshooting) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = showTroubleshooting) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        text = "Se l'invio su Firestore fallisce, verifica quanto segue:\n\n" +
                                                "1. Regole di Sicurezza Firestore: Su console.firebase.google.com > Firestore Database > scheda 'Regole', assicurati che la scrittura sia consentita:\n" +
                                                "   allow read, write: if true;\n\n" +
                                                "2. Progetto Google Cloud: Il file google-services.json deve essere scaricato dal tuo progetto Firebase attivo.\n\n" +
                                                "3. Backup Diretto JSON (Zero Config): Se non hai configurato Firebase, puoi usare in qualsiasi momento l'opzione sottostante 'Opzioni File JSON Offline' per esportare/importare l'intero archivio senza necessità di cloud.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = onTestFirebase,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verifica Connessione Firestore Ora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SCHEDA 4: DETTAGLI SCHEMA TABELLE FIRESTORE
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Schema Tabelle NoSQL & Gestione Cloud",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Visualizza la struttura dei campi Firestore (collezione 'books') e gestisci il ripristino o l'importazione remota.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = onOpenExportDetail,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_cloud_schema_button")
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apri Gestore Cloud & Schema Tabelle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SCHEDA 5: OPZIONI SECONDARIE FILE JSON (COLLASSABILI)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showJsonOptions = !showJsonOptions },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Opzioni File JSON Offline (Avanzate)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showJsonOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = showJsonOptions) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        text = "Salva o importa una copia locale in formato JSON se non desideri utilizzare la connessione diretta a Firestore.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onExportCloudJson,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Condividi JSON", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = onCopyJson,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copia JSON", modifier = Modifier.size(14.dp))
                                        }
                                        OutlinedButton(
                                            onClick = onOpenImportDialog,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Importa JSON", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sezione Riepilogo PDF & Stampa Collezione (collassabile, chiusa di default)
 */
@Composable
private fun PdfReportSummarySection(
    booksCount: Int,
    onOpenPdfExport: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_summary_settings_section")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF Report",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Riepilogo PDF & Stampa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Genera catalogo A4 consultabile offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("expand_pdf_section_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Comprimi sezione PDF" else "Espandi sezione PDF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Crea un documento PDF elegante e impaginato di tutti i tuoi $booksCount volumi con statistiche, schede dettagliate, note personali e supporto per la stampa cartacea o visualizzazione offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onOpenPdfExport,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_pdf_export_sheet_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Genera & Stampa PDF Collezione",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sezione Informazioni Database Locale (collassabile, chiusa di default)
 */
@Composable
private fun AppInfoSection(booksCount: Int) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Informazioni Database Locale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Room SQLite (Offline-first)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("expand_info_section_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Comprimi info" else "Espandi info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Motore Database:", style = MaterialTheme.typography.bodySmall)
                        Text("Room SQLite (Offline-first)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Libri in archivio:", style = MaterialTheme.typography.bodySmall)
                        Text("$booksCount volumi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Genera il payload JSON completo di esportazione per Google Cloud / Drive
 */
private fun buildExportJson(books: List<BookEntity>): String {
    val root = JSONObject()
    root.put("exportSource", "Archivio Libri - Personal Library Catalog")
    root.put("exportTimestamp", System.currentTimeMillis())
    root.put("totalBooks", books.size)

    val booksArray = JSONArray()
    for (b in books) {
        val item = JSONObject()
        item.put("id", b.id)
        item.put("isbn", b.isbn)
        item.put("title", b.title)
        item.put("author", b.author)
        item.put("publisher", b.publisher)
        item.put("publishedYear", b.publishedYear)
        item.put("genre", b.genre)
        item.put("customTags", b.customTags)
        item.put("readingStatus", b.readingStatus)
        item.put("rating", b.rating.toDouble())
        item.put("shelfLocation", b.shelfLocation)
        item.put("userNotes", b.userNotes)
        item.put("pageCount", b.pageCount)
        item.put("sourceApi", b.sourceApi)
        item.put("coverUrl", b.coverUrl ?: "")
        item.put("dateAdded", b.dateAdded)
        booksArray.put(item)
    }
    root.put("books", booksArray)
    return root.toString(2)
}
