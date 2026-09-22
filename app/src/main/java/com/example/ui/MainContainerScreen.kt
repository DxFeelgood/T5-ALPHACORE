package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home_tab"
    ),
    CATALOG(
        title = "Catalogo",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        testTag = "nav_catalog_tab"
    ),
    STATS(
        title = "Statistiche",
        selectedIcon = Icons.Filled.QueryStats,
        unselectedIcon = Icons.Outlined.QueryStats,
        testTag = "nav_stats_tab"
    ),
    AI_EXPERT(
        title = "Esperto",
        selectedIcon = Icons.Filled.Psychology,
        unselectedIcon = Icons.Outlined.Psychology,
        testTag = "nav_ai_tab"
    ),
    SETTINGS(
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings_tab"
    )
}

@Composable
fun MainContainerScreen(
    viewModel: BookViewModel
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    val allBooks by viewModel.allBooks.collectAsState()
    val lookupUiState by viewModel.lookupUiState.collectAsState()
    val selectedBookForDetail by viewModel.selectedBookForDetail.collectAsState()
    val showManualAddDialog by viewModel.showManualAddDialog.collectAsState()
    val selectedAuthorForWikipedia by viewModel.selectedAuthorForWikipedia.collectAsState()
    val authorWikipediaUiState by viewModel.authorWikipediaUiState.collectAsState()

    var isScannerOpen by remember { mutableStateOf(false) }
    var reopenScannerAfterSave by remember { mutableStateOf(false) }
    var showCloudExportBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    AppTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.HOME -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onScanClick = {
                            reopenScannerAfterSave = true
                            isScannerOpen = true
                        },
                        onOpenSettings = { currentTab = AppTab.SETTINGS },
                        onNavigateToCatalog = { currentTab = AppTab.CATALOG }
                    )
                }
                AppTab.CATALOG -> {
                    CatalogScreen(
                        books = allBooks,
                        onBookClick = { book ->
                            viewModel.selectBook(book)
                        },
                        onUpdateBook = { updatedBook ->
                            viewModel.updateBook(updatedBook)
                        },
                        onOpenAuthorWikipedia = { author ->
                            viewModel.openAuthorWikipedia(author)
                        }
                    )
                }
                AppTab.STATS -> {
                    DataVisualizationScreen(
                        books = allBooks
                    )
                }
                AppTab.AI_EXPERT -> {
                    LiteratureChatScreen(
                        books = allBooks
                    )
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        books = allBooks,
                        onBackClick = { currentTab = AppTab.HOME },
                        onOpenExportDetail = { showCloudExportBottomSheet = true }
                    )
                }
            }
        }
    }

    // Modal Scanner Fotocamera Barcode
    if (isScannerOpen) {
        ScannerModalScreen(
            onDismiss = {
                isScannerOpen = false
                reopenScannerAfterSave = false
            },
            onBarcodeScanned = { code: String ->
                isScannerOpen = false
                reopenScannerAfterSave = true
                viewModel.onBarcodeDetected(code)
            }
        )
    }

    // Bottom Sheet Risultati Ricerca a Cascata (Google Books -> OPAC SBN -> Open Library)
    CascadeLookupBottomSheet(
        lookupState = lookupUiState,
        isBatchMode = reopenScannerAfterSave,
        onDismiss = {
            viewModel.dismissLookup()
            reopenScannerAfterSave = false
        },
        onSaveBook = { result, status, shelf, notes, rating, genre, tags, continueBatch ->
            viewModel.saveBookFromLookup(result, status, shelf, notes, rating, genre, tags)
            if (continueBatch) {
                Toast.makeText(
                    context,
                    "Libro \"${result.title}\" salvato! Riapertura fotocamera per il prossimo volume...",
                    Toast.LENGTH_SHORT
                ).show()
                reopenScannerAfterSave = true
                isScannerOpen = true
            } else {
                reopenScannerAfterSave = false
                Toast.makeText(
                    context,
                    "Libro \"${result.title}\" salvato nella libreria!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onManualAdd = { isbn ->
            viewModel.dismissLookup()
            viewModel.openManualAdd(isbn)
        }
    )

    // Bottom Sheet Dettaglio e Modifica Libro
    BookDetailBottomSheet(
        book = selectedBookForDetail,
        onDismiss = { viewModel.selectBook(null) },
        onUpdateBook = { viewModel.updateBook(it) },
        onDeleteBook = { viewModel.deleteBook(it) },
        onReQueryApi = { isbn ->
            viewModel.selectBook(null)
            reopenScannerAfterSave = false
            viewModel.startCascadeLookup(isbn)
        },
        onOpenAuthorWikipedia = { author ->
            viewModel.openAuthorWikipedia(author)
        }
    )

    val manualAddInitialIsbn by viewModel.manualAddInitialIsbn.collectAsState()

    // Dialogo Aggiunta Manuale
    if (showManualAddDialog) {
        ManualAddBookDialog(
            initialIsbn = manualAddInitialIsbn,
            viewModel = viewModel,
            onDismiss = {
                viewModel.closeManualAdd()
                reopenScannerAfterSave = false
            },
            onSaveBook = {
                viewModel.saveManualBook(it)
                if (reopenScannerAfterSave) {
                    Toast.makeText(
                        context,
                        "Libro \"${it.title}\" salvato! Riapertura fotocamera...",
                        Toast.LENGTH_SHORT
                    ).show()
                    isScannerOpen = true
                }
            }
        )
    }

    // Bottom Sheet Esportazione Google Cloud
    GoogleCloudExportBottomSheet(
        isOpen = showCloudExportBottomSheet,
        books = allBooks,
        onDismiss = { showCloudExportBottomSheet = false },
        onImportBackup = { json -> viewModel.importBooksFromJson(json) }
    )

    // Scheda Biografica Autore in Stile Wikipedia
    if (selectedAuthorForWikipedia != null) {
        AuthorWikipediaBottomSheet(
            authorName = selectedAuthorForWikipedia!!,
            uiState = authorWikipediaUiState,
            onDismiss = { viewModel.closeAuthorWikipedia() },
            onRetry = { viewModel.openAuthorWikipedia(selectedAuthorForWikipedia!!) },
            onSearchAuthorBooks = { author ->
                viewModel.closeAuthorWikipedia()
                currentTab = AppTab.CATALOG
            }
        )
    }
}
