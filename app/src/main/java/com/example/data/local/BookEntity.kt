package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity Room per la memorizzazione e persistenza dei volumi catalogati ('Libro').
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val isbn: String,
    val title: String,
    val author: String,
    val publisher: String = "",
    val publishedYear: String = "",
    val description: String = "",
    val coverUrl: String? = null,
    val pageCount: Int = 0,
    val genre: String = "", // Categoria/Genere (es. 'Fantascienza', 'Storia', 'Saggistica')
    val customTags: String = "", // Tag personalizzati separati da virgola (es. 'preferito, da leggere, classico')
    val readingStatus: String = "DA_LEGGERE", // DA_LEGGERE, IN_LETTURA, LETTO
    val rating: Float = 0f, // 0..5 stelle
    val userNotes: String = "",
    val shelfLocation: String = "", // Scaffale / Stanza
    val sourceApi: String = "Manuale", // "Google Books", "OPAC SBN", "Open Library", "Manuale"
    val dateAdded: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false, // Flag di sincronizzazione su Firestore
    val lastSyncedAt: Long = 0L // Timestamp dell'ultima sincronizzazione cloud
) {
    /**
     * Restituisce la lista dei tag individuali ripuliti da spazi superflui.
     */
    fun getTagList(): List<String> {
        if (customTags.isBlank()) return emptyList()
        return customTags.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Verifica se il libro include uno specifico tag.
     */
    fun hasTag(tag: String): Boolean {
        return getTagList().any { it.equals(tag.trim(), ignoreCase = true) }
    }
}

/**
 * Alias in lingua italiana per la classe Entity 'Libro'.
 */
typealias Libro = BookEntity
typealias LibroEntity = BookEntity
