package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchBottomSheet(
    isOpen: Boolean,
    currentFilter: AdvancedFilterCriteria,
    onDismiss: () -> Unit,
    onApplyFilter: (AdvancedFilterCriteria) -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(currentFilter) { mutableStateOf(currentFilter.title) }
    var author by remember(currentFilter) { mutableStateOf(currentFilter.author) }
    var publisher by remember(currentFilter) { mutableStateOf(currentFilter.publisher) }
    var year by remember(currentFilter) { mutableStateOf(currentFilter.year) }
    var selectedCategory by remember(currentFilter) { mutableStateOf(currentFilter.category) }
    var selectedTag by remember(currentFilter) { mutableStateOf(currentFilter.tag) }
    var selectedStatus by remember(currentFilter) { mutableStateOf(currentFilter.status) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("advanced_filter_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ricerca Avanzata & Filtri",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_advanced_filter_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi")
                }
            }

            // Filtri Testuali
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Filtra per Titolo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("filter_title_input")
            )

            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Filtra per Autore") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("filter_author_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = { Text("Editore") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("filter_publisher_input")
                )

                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Anno") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("filter_year_input")
                )
            }

            // Categorizzazione / Genere
            Text(
                text = "Categoria / Genere",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                POPULAR_CATEGORIES.forEach { categoryName ->
                    val isSelected = selectedCategory.equals(categoryName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategory = if (isSelected) "" else categoryName
                        },
                        label = { Text(categoryName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (selectedCategory.isNotBlank() && !POPULAR_CATEGORIES.any { it.equals(selectedCategory, ignoreCase = true) }) {
                Text(
                    text = "Categoria personalizzata: $selectedCategory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Tag Personalizzati
            Text(
                text = "Tag Personalizzati",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                POPULAR_TAGS.forEach { tagName ->
                    val isSelected = selectedTag.equals(tagName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTag = if (isSelected) "" else tagName
                        },
                        label = { Text("#$tagName") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            OutlinedTextField(
                value = selectedTag,
                onValueChange = { selectedTag = it },
                label = { Text("Oppure scrivi un tag personalizzato...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("filter_custom_tag_input")
            )

            // Stato di lettura
            Text(
                text = "Stato di Lettura",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    null to "Qualsiasi",
                    "DA_LEGGERE" to "Da Leggere",
                    "IN_LETTURA" to "In Lettura",
                    "LETTO" to "Letto"
                ).forEach { (statusKey, label) ->
                    FilterChip(
                        selected = selectedStatus == statusKey,
                        onClick = { selectedStatus = statusKey },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Azioni
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        title = ""
                        author = ""
                        publisher = ""
                        year = ""
                        selectedCategory = ""
                        selectedTag = ""
                        selectedStatus = null
                        onApplyFilter(AdvancedFilterCriteria())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).testTag("reset_advanced_filter_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Azzera")
                }

                Button(
                    onClick = {
                        val criteria = AdvancedFilterCriteria(
                            title = title.trim(),
                            author = author.trim(),
                            publisher = publisher.trim(),
                            year = year.trim(),
                            category = selectedCategory.trim(),
                            tag = selectedTag.trim(),
                            status = selectedStatus
                        )
                        onApplyFilter(criteria)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.5f).testTag("apply_advanced_filter_button")
                ) {
                    Text("Applica Filtri")
                }
            }
        }
    }
}
