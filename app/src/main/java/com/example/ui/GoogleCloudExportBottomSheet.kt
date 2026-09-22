package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.remote.FirebaseHelper
import com.example.data.remote.FirestoreImageHelper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookEntity
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleCloudExportBottomSheet(
    isOpen: Boolean,
    books: List<BookEntity>,
    onDismiss: () -> Unit,
    onImportBackup: suspend (String) -> Result<Int>
) {
    if (!isOpen) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val jsonExportString = remember(books) {
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
            item.put("rating", b.rating)
            item.put("shelfLocation", b.shelfLocation)
            item.put("userNotes", b.userNotes)
            item.put("pageCount", b.pageCount)
            item.put("sourceApi", b.sourceApi)
            item.put("dateAdded", b.dateAdded)
            booksArray.put(item)
        }
        root.put("books", booksArray)
        root.toString(2)
    }

    val totalAuthors = remember(books) {
        books.map { it.author.trim() }.filter { it.isNotBlank() }.distinct().size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Google Cloud",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Sincronizzazione Firestore",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Popola le tabelle del tuo database Cloud NoSQL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Statistiche locali
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatExportItem(
                        value = books.size.toString(),
                        label = "Libri locali"
                    )
                    StatExportItem(
                        value = totalAuthors.toString(),
                        label = "Autori unici"
                    )
                    StatExportItem(
                        value = "NoSQL",
                        label = "Tipo Tabella"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Operazioni Database Cloud",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Azione 1: Salva su Firestore Cloud come singole righe/documenti con immagini
            Button(
                onClick = {
                    isUploading = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val db = FirebaseHelper.getFirestore()
                            val chunkSize = 25
                            val chunks = books.chunked(chunkSize)
                            var totalImages = 0

                            for (chunk in chunks) {
                                val batch = db.batch()
                                for (b in chunk) {
                                    val docRef = db.collection("books").document(b.id.toString())
                                    val coverBase64 = FirestoreImageHelper.getBase64ImageForCover(context, b.coverUrl)
                                    if (coverBase64 != null) totalImages++

                                    val bookData = hashMapOf<String, Any?>(
                                        "id" to b.id,
                                        "isbn" to b.isbn,
                                        "title" to b.title,
                                        "author" to b.author,
                                        "publisher" to b.publisher,
                                        "publishedYear" to b.publishedYear,
                                        "genre" to b.genre,
                                        "customTags" to b.customTags,
                                        "readingStatus" to b.readingStatus,
                                        "rating" to b.rating,
                                        "shelfLocation" to b.shelfLocation,
                                        "userNotes" to b.userNotes,
                                        "pageCount" to b.pageCount,
                                        "sourceApi" to b.sourceApi,
                                        "dateAdded" to b.dateAdded,
                                        "coverUrl" to b.coverUrl,
                                        "coverImageBase64" to coverBase64,
                                        "hasCoverImage" to (coverBase64 != null),
                                        "description" to b.description,
                                        "isSynced" to true,
                                        "lastSyncedAt" to System.currentTimeMillis()
                                    )
                                    batch.set(docRef, bookData)
                                }
                                Tasks.await(batch.commit(), 15, java.util.concurrent.TimeUnit.SECONDS)
                            }

                            withContext(Dispatchers.Main) {
                                isUploading = false
                                Toast.makeText(context, "Database Firestore sincronizzato: inseriti ${books.size} libri e $totalImages copertine nella collezione 'books'!", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isUploading = false
                                val formatted = FirebaseHelper.formatFirestoreError(e)
                                Toast.makeText(context, formatted, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isUploading && !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("backup_firestore_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Invia a Collezione 'books' (Firestore)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Azione 2: Ripristina leggendo la collezione "books" con ripristino copertine
            FilledTonalButton(
                onClick = {
                    isDownloading = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val db = FirebaseHelper.getFirestore()
                            val snapshot = Tasks.await(
                                db.collection("books").get(),
                                15,
                                java.util.concurrent.TimeUnit.SECONDS
                            )

                            if (snapshot != null && !snapshot.isEmpty) {
                                val rootJson = JSONObject()
                                val booksArr = JSONArray()
                                var restoredImages = 0

                                for (doc in snapshot.documents) {
                                    val item = JSONObject()
                                    item.put("id", doc.id)
                                    val isbn = doc.getString("isbn") ?: ""
                                    item.put("isbn", isbn)
                                    item.put("title", doc.getString("title") ?: "")
                                    item.put("author", doc.getString("author") ?: "")
                                    item.put("publisher", doc.getString("publisher") ?: "")
                                    item.put("publishedYear", doc.getString("publishedYear") ?: "")
                                    item.put("genre", doc.getString("genre") ?: "")

                                    val rawCoverUrl = doc.getString("coverUrl")
                                    val coverImageBase64 = doc.getString("coverImageBase64")
                                        ?: doc.getString("coverImage")
                                        ?: doc.getString("coverBase64")

                                    val resolvedCover = if (!coverImageBase64.isNullOrBlank()) {
                                        val localFile = FirestoreImageHelper.saveBase64ToLocalStorage(
                                            context = context,
                                            base64String = coverImageBase64,
                                            identifier = if (isbn.isNotBlank()) isbn else doc.id
                                        )
                                        if (localFile != null) restoredImages++
                                        localFile ?: rawCoverUrl
                                    } else {
                                        rawCoverUrl
                                    }

                                    item.put("coverUrl", resolvedCover ?: JSONObject.NULL)
                                    if (coverImageBase64 != null) {
                                        item.put("coverImageBase64", coverImageBase64)
                                    }
                                    item.put("description", doc.getString("description") ?: "")
                                    item.put("customTags", doc.getString("customTags") ?: "")
                                    item.put("readingStatus", doc.getString("readingStatus") ?: "DA_LEGGERE")
                                    item.put("rating", doc.getDouble("rating") ?: 0.0)
                                    item.put("shelfLocation", doc.getString("shelfLocation") ?: "")
                                    item.put("userNotes", doc.getString("userNotes") ?: "")
                                    item.put("pageCount", doc.getLong("pageCount")?.toInt() ?: 0)
                                    item.put("sourceApi", doc.getString("sourceApi") ?: "CLOUD_IMPORT")
                                    item.put("dateAdded", doc.getLong("dateAdded") ?: System.currentTimeMillis())
                                    booksArr.put(item)
                                }
                                rootJson.put("books", booksArr)

                                val importRes = onImportBackup(rootJson.toString())
                                withContext(Dispatchers.Main) {
                                    isDownloading = false
                                    if (importRes.isSuccess) {
                                        Toast.makeText(context, "Sincronizzati ${importRes.getOrNull()} libri e $restoredImages copertine dalla collezione 'books'!", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Errore importazione locale: ${importRes.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isDownloading = false
                                    Toast.makeText(context, "Nessun libro trovato nella collezione 'books' di Firestore.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isDownloading = false
                                val formatted = FirebaseHelper.formatFirestoreError(e)
                                Toast.makeText(context, formatted, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isUploading && !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("restore_firestore_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recupera da Collezione 'books'", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mappatura Schema Cloud
            Text(
                text = "Schema Tabella NoSQL ('libri')",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Collezione: libri\nDocument ID: [ID del Libro]",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• title: String (Titolo libro)\n" +
                               "• author: String (Autore)\n" +
                               "• isbn: String (Identificativo)\n" +
                               "• coverUrl: String (URL o percorso locale)\n" +
                               "• coverImageBase64: String (Immagine copertina compattata in Base64)\n" +
                               "• rating: Double (Valutazione stellare)\n" +
                               "• readingStatus: String (Stato lettura)\n" +
                               "• pageCount: Int (Pagine complessive)\n" +
                               "• customTags: String (Etichette utente)\n" +
                               "• userNotes: String (Note personali)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Chiudi")
            }
        }
    }
}

@Composable
private fun StatExportItem(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
