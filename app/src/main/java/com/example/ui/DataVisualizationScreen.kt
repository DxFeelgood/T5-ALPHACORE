package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookEntity

/**
 * Pagina dedicata alla visualizzazione dati grafica (Grafici Interattivi Jetpack Compose e D3/SVG),
 * con indicatori chiave di performance (KPI), metriche per genere, anno di pubblicazione, stato e valutazione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataVisualizationScreen(
    books: List<BookEntity>,
    modifier: Modifier = Modifier
) {
    val totalBooks = books.size
    val readBooks = remember(books) { books.count { it.readingStatus == "LETTO" } }
    val uniqueGenres = remember(books) {
        books.map { it.genre.trim() }.filter { it.isNotBlank() }.distinct().size
    }
    val uniqueAuthors = remember(books) {
        books.map { it.author.trim() }.filter { it.isNotBlank() }.distinct().size
    }
    val completionPercentage = if (totalBooks > 0) (readBooks.toFloat() / totalBooks) * 100f else 0f

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
                                imageVector = Icons.Default.QueryStats,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Visualizzazione Dati",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Statistiche e metriche di lettura",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.testTag("data_visualization_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Riepilogo Statistico KPI
                val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatKpiCard(
                        title = "Volumi",
                        value = totalBooks.toString(),
                        subtitle = "in archivio",
                        icon = Icons.Default.CollectionsBookmark,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkTheme) 0.65f else 0.5f),
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiCard(
                        title = "Generi",
                        value = uniqueGenres.toString(),
                        subtitle = "categorie",
                        icon = Icons.Default.Category,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDarkTheme) 0.65f else 0.5f),
                        iconTint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiCard(
                        title = "Letti",
                        value = readBooks.toString(),
                        subtitle = "${String.format("%.0f", completionPercentage)}%",
                        icon = Icons.Default.BookmarkAdded,
                        containerColor = if (isDarkTheme) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                        iconTint = if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Componente Principale D3 / Recharts / Compose
            item {
                BookDataVisualizationSection(
                    books = books
                )
            }

            // Curiosità e Statistiche Aggiuntive sulla Collezione
            if (books.isNotEmpty()) {
                item {
                    CollectionInsightsCard(
                        books = books,
                        uniqueAuthors = uniqueAuthors
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun StatKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CollectionInsightsCard(
    books: List<BookEntity>,
    uniqueAuthors: Int
) {
    val topAuthor = remember(books) {
        books.groupBy { it.author.trim() }
            .filterKeys { it.isNotBlank() }
            .maxByOrNull { it.value.size }
    }
    val topShelf = remember(books) {
        books.groupBy { it.shelfLocation.trim() }
            .filterKeys { it.isNotBlank() }
            .maxByOrNull { it.value.size }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Approfondimenti Collezione",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (topAuthor != null) {
                InsightRow(
                    label = "Autore più presente",
                    value = "${topAuthor.key} (${topAuthor.value.size} opere)"
                )
            }

            InsightRow(
                label = "Autori totali distinti",
                value = "$uniqueAuthors autori"
            )

            if (topShelf != null) {
                InsightRow(
                    label = "Scaffale con più volumi",
                    value = "${topShelf.key} (${topShelf.value.size} libri)"
                )
            }
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
