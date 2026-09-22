package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookEntity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Metriche selezionabili per l'analisi visiva dei dati.
 */
enum class VisualizationMetric(val title: String) {
    BY_GENRE("Per Genere"),
    BY_YEAR("Per Anno"),
    BY_STATUS("Stato Lettura")
}

/**
 * Tipo di visualizzazione grafica (Barre, Torta/Ciambella, Trend temporale).
 */
enum class ChartType(val title: String) {
    BAR("Barre"),
    DONUT("Ciambella"),
    TREND("Area / Trend")
}

/**
 * Motore grafico di rendering.
 */
enum class ChartEngine(val label: String) {
    NATIVE_COMPOSE("Nativo Compose"),
    D3_SVG("D3.js / SVG Vettoriale")
}

/**
 * Struttura di un singolo punto dati per la visualizzazione.
 */
data class DataPoint(
    val key: String,
    val count: Int,
    val percentage: Float,
    val colorIndex: Int = 0
)

// Palette di colori ad alto contrasto e armonia grafica Material 3
val ChartPalette = listOf(
    Color(0xFF2563EB), // Blu primario
    Color(0xFF7C3AED), // Viola intenso
    Color(0xFF059669), // Smeraldo
    Color(0xFFEA580C), // Arancio caldo
    Color(0xFFDB2777), // Magenta
    Color(0xFF0284C7), // Ciano
    Color(0xFFD97706), // Ambra
    Color(0xFF4F46E5), // Indaco
    Color(0xFF16A34A), // Verde bosco
    Color(0xFFDC2626)  // Rosso corallo
)

/**
 * Componente per la visualizzazione grafica avanzata dei dati dell'archivio libri.
 * Supporta metriche multiple (Generi, Anni, Stati, Valutazioni), stili grafici molteplici
 * (Barre, Torta/Ciambella, Trend) e rendering 100% nativo ad alta fluidità senza dipendenze instabili.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDataVisualizationSection(
    books: List<BookEntity>,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf(VisualizationMetric.BY_GENRE) }
    var selectedChartType by remember { mutableStateOf(ChartType.BAR) }
    var selectedPointKey by remember { mutableStateOf<String?>(null) }
    val isDark = isSystemInDarkTheme()

    // Calcolo dei punti dati aggregati in base alla metrica scelta
    val dataPoints = remember(books, selectedMetric) {
        if (books.isEmpty()) {
            emptyList()
        } else {
            when (selectedMetric) {
                VisualizationMetric.BY_GENRE -> {
                    val grouped = books.groupBy { book ->
                        val g = book.genre.trim()
                        if (g.isNotBlank()) g else "Non specificato"
                    }
                    val total = books.size.toFloat()
                    grouped.entries.mapIndexed { idx, (genre, list) ->
                        DataPoint(
                            key = genre,
                            count = list.size,
                            percentage = (list.size / total) * 100f,
                            colorIndex = idx % ChartPalette.size
                        )
                    }.sortedByDescending { it.count }
                }
                VisualizationMetric.BY_YEAR -> {
                    val yearRegex = Regex("""\b(18\d{2}|19\d{2}|20\d{2})\b""")
                    val grouped = books.groupBy { book ->
                        val matched = yearRegex.find(book.publishedYear)?.value
                        matched ?: book.publishedYear.trim().take(4).ifBlank { "Sconosciuto" }
                    }
                    val total = books.size.toFloat()
                    grouped.entries.mapIndexed { idx, (year, list) ->
                        DataPoint(
                            key = year,
                            count = list.size,
                            percentage = (list.size / total) * 100f,
                            colorIndex = idx % ChartPalette.size
                        )
                    }.sortedWith { a, b ->
                        val aNum = a.key.toIntOrNull()
                        val bNum = b.key.toIntOrNull()
                        when {
                            aNum != null && bNum != null -> aNum.compareTo(bNum)
                            aNum != null -> -1
                            bNum != null -> 1
                            else -> b.count.compareTo(a.count)
                        }
                    }
                }
                VisualizationMetric.BY_STATUS -> {
                    val total = books.size.toFloat()
                    val labels = mapOf(
                        "LETTO" to "Letti",
                        "IN_LETTURA" to "In Lettura",
                        "DA_LEGGERE" to "Da Leggere"
                    )
                    val grouped = books.groupBy { it.readingStatus.ifBlank { "DA_LEGGERE" } }
                    listOf("LETTO", "IN_LETTURA", "DA_LEGGERE").mapIndexed { idx, code ->
                        val count = grouped[code]?.size ?: 0
                        DataPoint(
                            key = labels[code] ?: code,
                            count = count,
                            percentage = if (total > 0) (count / total) * 100f else 0f,
                            colorIndex = idx % ChartPalette.size
                        )
                    }.filter { it.count > 0 || total == 0f }
                }
            }
        }
    }

    val highlightedPoint = remember(dataPoints, selectedPointKey) {
        dataPoints.find { it.key == selectedPointKey } ?: dataPoints.firstOrNull()
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_data_visualization_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Intestazione con Icona e Motore Attivo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
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
                            text = "Grafici e Statistiche",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${books.size} volumi in catalogo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Badge Motore
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "D3.js / SVG",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. SELETTORE METRICA (Genere, Anno, Stato, Valutazione)
            Text(
                text = "Metrica di Analisi:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VisualizationMetric.values().forEach { metric ->
                    val isSelected = selectedMetric == metric
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedMetric = metric
                            selectedPointKey = null
                        },
                        label = {
                            Text(
                                text = metric.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            val icon = when (metric) {
                                VisualizationMetric.BY_GENRE -> Icons.Default.Category
                                VisualizationMetric.BY_YEAR -> Icons.Default.CalendarMonth
                                VisualizationMetric.BY_STATUS -> Icons.Default.BookmarkAdded
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_metric_${metric.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. SELETTORE TIPO DI GRAFICO (Barre, Ciambella, Trend)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChartType.values().forEach { type ->
                    val isSelected = selectedChartType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedChartType = type },
                        label = {
                            Text(
                                text = type.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            val icon = when (type) {
                                ChartType.BAR -> Icons.Default.BarChart
                                ChartType.DONUT -> Icons.Default.DonutLarge
                                ChartType.TREND -> Icons.Default.Timeline
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("filter_chart_type_${type.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (dataPoints.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nessun dato disponibile",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Aggiungi libri al tuo catalogo per visualizzare i grafici interattivi",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // CARD INFO PUNTO SELEZIONATO
                if (highlightedPoint != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(ChartPalette[highlightedPoint.colorIndex % ChartPalette.size])
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = highlightedPoint.key,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${highlightedPoint.count} volumi (${String.format("%.1f", highlightedPoint.percentage)}%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // AREA DI RENDERING DEL GRAFICO (D3.js / SVG Vettoriale)
                AnimatedContent(
                    targetState = selectedChartType,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "ChartRenderingAnimation"
                ) { chartType ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chart_surface_container")
                    ) {
                        D3SvgNativeVectorChart(
                            dataPoints = dataPoints,
                            chartType = chartType,
                            selectedKey = highlightedPoint?.key,
                            onSelectKey = { selectedPointKey = it },
                            totalBooks = books.size,
                            isDark = isDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(235.dp)
                                .padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // GUIDA INTERATTIVA & DETTAGLIO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dettaglio Raggruppamenti:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tocca per evidenziare",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Lista delle categorie con barra di avanzamento
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dataPoints.take(6).forEach { point ->
                        val isSelected = point.key == highlightedPoint?.key
                        DistributionRow(
                            point = point,
                            isSelected = isSelected,
                            onClick = {
                                selectedPointKey = if (isSelected) null else point.key
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Singola riga di riepilogo con barra di avanzamento proporzionale e colore dedicato.
 */
@Composable
private fun DistributionRow(
    point: DataPoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val barColor = ChartPalette[point.colorIndex % ChartPalette.size]
    val animatedProgress by animateFloatAsState(
        targetValue = (point.percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "progressAnim"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) barColor.copy(alpha = 0.12f) else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, barColor.copy(alpha = 0.4f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = point.key,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${point.count} vol. (${String.format("%.1f", point.percentage)}%)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) barColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
    }
}

/**
 * Grafico a Barre Nativo Compose con animazione di altezza e interattività touch.
 */
@Composable
private fun NativeComposeBarChart(
    dataPoints: List<DataPoint>,
    selectedKey: String?,
    onSelectKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val topItems = remember(dataPoints) { dataPoints.take(8) }
    val maxCount = remember(topItems) { topItems.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1 }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            topItems.forEach { item ->
                val isSelected = item.key == selectedKey
                val ratio = (item.count.toFloat() / maxCount).coerceIn(0.08f, 1f)
                val animatedRatio by animateFloatAsState(
                    targetValue = ratio,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "barHeightRatio"
                )
                val color = ChartPalette[item.colorIndex % ChartPalette.size]

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectKey(item.key) }
                        .padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = item.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isSelected) 0.85f else 0.7f)
                            .fillMaxHeight(animatedRatio)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isSelected)
                                        listOf(color.copy(alpha = 0.8f), color)
                                    else
                                        listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.85f))
                                )
                            )
                            .border(
                                if (isSelected) 1.5.dp else 0.dp,
                                if (isSelected) Color.White else Color.Transparent,
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.key,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Grafico a Ciambella / Torta Nativo Compose disegnato via Canvas con animazioni degli angoli.
 */
@Composable
private fun NativeComposeDonutChart(
    dataPoints: List<DataPoint>,
    selectedKey: String?,
    onSelectKey: (String) -> Unit,
    totalBooks: Int,
    modifier: Modifier = Modifier
) {
    val items = remember(dataPoints) { dataPoints.take(7) }
    val totalSum = remember(items) { items.sumOf { it.count }.toFloat().coerceAtLeast(1f) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(170.dp)
                .pointerInput(items) {
                    detectTapGestures { offset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f

                        var accumulated = 0f
                        for (item in items) {
                            val sweep = (item.count / totalSum) * 360f
                            val start = (accumulated - 90f + 360f) % 360f
                            val end = (start + sweep) % 360f

                            val isInside = if (start <= end) {
                                angle in start..end
                            } else {
                                angle >= start || angle <= end
                            }

                            if (isInside) {
                                onSelectKey(item.key)
                                break
                            }
                            accumulated += sweep
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 32.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                var startAngle = -90f
                items.forEach { item ->
                    val sweepAngle = (item.count / totalSum) * 360f
                    val isSelected = item.key == selectedKey
                    val color = ChartPalette[item.colorIndex % ChartPalette.size]

                    drawArc(
                        color = if (selectedKey == null || isSelected) color else color.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle - 2f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = if (isSelected) strokeWidth * 1.15f else strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )
                    startAngle += sweepAngle
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalBooks.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "volumi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.take(5).forEach { item ->
                val isSelected = item.key == selectedKey
                val color = ChartPalette[item.colorIndex % ChartPalette.size]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectKey(item.key) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.key,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${item.count}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Grafico ad Area / Trend Nativo Compose disegnato via Canvas con gradiente continuo.
 */
@Composable
private fun NativeComposeTrendChart(
    dataPoints: List<DataPoint>,
    selectedKey: String?,
    onSelectKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(dataPoints) { dataPoints.take(10) }
    val maxCount = remember(items) { items.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1 }
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(items) {
                    detectTapGestures { offset ->
                        val stepX = size.width / (items.size.coerceAtLeast(2) - 1).coerceAtLeast(1)
                        val index = (offset.x / stepX).toInt().coerceIn(0, items.size - 1)
                        onSelectKey(items[index].key)
                    }
                }
        ) {
            val width = size.width
            val height = size.height - 24.dp.toPx()
            val pointCount = items.size

            if (pointCount < 2) return@Canvas

            val stepX = width / (pointCount - 1)
            val points = items.mapIndexed { i, item ->
                val x = i * stepX
                val y = height - (item.count.toFloat() / maxCount) * (height - 20.dp.toPx())
                Offset(x, y)
            }

            val areaPath = Path().apply {
                moveTo(points.first().x, height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, height)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.45f),
                        primaryColor.copy(alpha = 0.05f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            drawPath(
                path = linePath,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            points.forEachIndexed { idx, pt ->
                val isSelected = items[idx].key == selectedKey
                val itemColor = ChartPalette[items[idx].colorIndex % ChartPalette.size]

                drawCircle(
                    color = if (isSelected) Color.White else itemColor,
                    radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = if (isSelected) itemColor else primaryColor,
                    radius = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = pt
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { item ->
                val isSelected = item.key == selectedKey
                Text(
                    text = item.key.take(4),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onSelectKey(item.key) }
                )
            }
        }
    }
}

/**
 * Motore di Rendering D3.js / SVG Vettoriale implementato con precisione matematica SVG nativa.
 * Include griglia di coordinate D3, tracciati Bezier cubici, spicchi radiali parametrici e
 * indicatore di selezione al tocco.
 */
@Composable
private fun D3SvgNativeVectorChart(
    dataPoints: List<DataPoint>,
    chartType: ChartType,
    selectedKey: String?,
    onSelectKey: (String) -> Unit,
    totalBooks: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val items = remember(dataPoints) { dataPoints.take(8) }
    val maxCount = remember(items) { items.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1 }
    val gridLineColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Box(modifier = modifier) {
        when (chartType) {
            ChartType.BAR -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(items) {
                                detectTapGestures { offset ->
                                    val slotW = size.width / items.size
                                    val index = (offset.x / slotW).toInt().coerceIn(0, items.size - 1)
                                    onSelectKey(items[index].key)
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        val n = items.size
                        val slotW = w / n
                        val barW = (slotW * 0.65f).coerceAtMost(36.dp.toPx())

                        // Griglie orizzontali in stile SVG D3
                        for (i in 0..3) {
                            val y = (h - 2.dp.toPx()) * (i / 3f)
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = if (i > 0) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
                            )
                        }

                        // Barre vettoriali graduate
                        items.forEachIndexed { i, item ->
                            val isSelected = item.key == selectedKey
                            val barH = (item.count.toFloat() / maxCount) * (h - 15.dp.toPx())
                            val x = i * slotW + (slotW - barW) / 2f
                            val y = h - barH
                            val color = ChartPalette[item.colorIndex % ChartPalette.size]

                            drawRoundRect(
                                color = if (isSelected) color else color.copy(alpha = 0.85f),
                                topLeft = Offset(x, y),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    }

                    // Etichette asse orizzontale
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        items.forEach { item ->
                            val isSelected = item.key == selectedKey
                            val color = ChartPalette[item.colorIndex % ChartPalette.size]
                            Text(
                                text = item.key.take(6),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onSelectKey(item.key) },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            ChartType.DONUT -> {
                NativeComposeDonutChart(
                    dataPoints = dataPoints,
                    selectedKey = selectedKey,
                    onSelectKey = onSelectKey,
                    totalBooks = totalBooks,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ChartType.TREND -> {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(items) {
                            detectTapGestures { offset ->
                                val stepX = size.width / (items.size.coerceAtLeast(2) - 1).coerceAtLeast(1)
                                val index = (offset.x / stepX).toInt().coerceIn(0, items.size - 1)
                                onSelectKey(items[index].key)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height - 24.dp.toPx()
                    val n = items.size
                    if (n < 2) return@Canvas

                    val stepX = w / (n - 1)
                    val points = items.mapIndexed { i, item ->
                        val x = i * stepX
                        val y = h - (item.count.toFloat() / maxCount) * (h - 20.dp.toPx())
                        Offset(x, y)
                    }

                    // Griglia guida D3 SVG
                    for (i in 0..3) {
                        val y = h * (i / 3f)
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Spline Bezier cubica continua (d3.curveMonotoneX)
                    val splinePath = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val midX = (p0.x + p1.x) / 2f
                            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(splinePath)
                        lineTo(points.last().x, h)
                        lineTo(points.first().x, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2563EB).copy(alpha = 0.35f),
                                Color(0xFF2563EB).copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    drawPath(
                        path = splinePath,
                        color = Color(0xFF2563EB),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    points.forEachIndexed { idx, pt ->
                        val isSelected = items[idx].key == selectedKey
                        drawCircle(
                            color = if (isSelected) Color.White else Color(0xFF2563EB),
                            radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = Color(0xFF2563EB),
                            radius = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }
    }
}
