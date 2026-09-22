package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) per gestire tutte le operazioni di persistenza,
 * ricerca e filtraggio dei libri nel database locale Room.
 */
@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY dateAdded DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY dateAdded DESC")
    suspend fun getAllBooksDirect(): List<BookEntity>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR isbn LIKE '%' || :query || '%' OR publisher LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' OR publishedYear LIKE '%' || :query || '%' OR customTags LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Query("SELECT DISTINCT genre FROM books WHERE genre != '' ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>

    @Query("SELECT * FROM books WHERE genre = :genre ORDER BY dateAdded DESC")
    fun getBooksByGenre(genre: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE readingStatus = :status ORDER BY dateAdded DESC")
    fun filterByStatus(status: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun getBookById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findBookByIsbn(isbn: String): BookEntity?

    @Query("SELECT * FROM books WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND LOWER(TRIM(author)) = LOWER(TRIM(:author)) LIMIT 1")
    suspend fun findBookByTitleAndAuthor(title: String, author: String): BookEntity?

    @Query("SELECT * FROM books WHERE shelfLocation = :shelfLocation ORDER BY dateAdded DESC")
    fun getBooksByShelf(shelfLocation: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>): List<Long>

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()

    @Query("SELECT COUNT(*) FROM books")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE readingStatus = 'LETTO'")
    fun getReadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE readingStatus = 'IN_LETTURA'")
    fun getCurrentlyReadingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE readingStatus = 'DA_LEGGERE'")
    fun getToReadCount(): Flow<Int>

    // --- Query per gestione offline e sincronizzazione Firestore ---

    @Query("SELECT * FROM books WHERE readingStatus = 'LETTO' ORDER BY dateAdded DESC")
    fun getReadBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isSynced = 0 ORDER BY dateAdded DESC")
    suspend fun getUnsyncedBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE readingStatus = 'LETTO' AND isSynced = 0 ORDER BY dateAdded DESC")
    suspend fun getUnsyncedReadBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookByIdDirect(id: Long): BookEntity?

    @Query("UPDATE books SET isSynced = 1, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markAsSynced(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isSynced = 1, lastSyncedAt = :timestamp WHERE id IN (:ids)")
    suspend fun markBooksAsSynced(ids: List<Long>, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isSynced = 1, lastSyncedAt = :timestamp")
    suspend fun markAllAsSynced(timestamp: Long = System.currentTimeMillis())
}

/**
 * Alias in lingua italiana per l'interfaccia DAO di gestione libri.
 */
typealias LibroDao = BookDao
