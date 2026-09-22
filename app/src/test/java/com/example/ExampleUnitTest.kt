package com.example

import com.example.data.local.BookEntity
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBookGenreDistribution() {
    val books = listOf(
      BookEntity(id = 1, isbn = "123", title = "Dune", author = "Frank Herbert", genre = "Fantascienza", publishedYear = "1965"),
      BookEntity(id = 2, isbn = "124", title = "Fondazione", author = "Isaac Asimov", genre = "Fantascienza", publishedYear = "1951"),
      BookEntity(id = 3, isbn = "125", title = "Il nome della rosa", author = "Umberto Eco", genre = "Giallo Storico", publishedYear = "1980")
    )

    val genreCounts = books.groupBy { it.genre }.mapValues { it.value.size }
    assertEquals(2, genreCounts["Fantascienza"])
    assertEquals(1, genreCounts["Giallo Storico"])
  }

  @Test
  fun testBookYearExtraction() {
    val yearRegex = Regex("""\b(18\d{2}|19\d{2}|20\d{2})\b""")
    val rawYears = listOf("1980", "Pubblicato nel 2021", "2015-05-12", "Sconosciuto")
    val extracted = rawYears.map { raw ->
      yearRegex.find(raw)?.value ?: raw.take(4).ifBlank { "Sconosciuto" }
    }

    assertEquals("1980", extracted[0])
    assertEquals("2021", extracted[1])
    assertEquals("2015", extracted[2])
    assertEquals("Scon", extracted[3])
  }

  @Test
  fun testThemeModeValues() {
    val modes = com.example.ui.AppThemeMode.values()
    assertTrue(modes.contains(com.example.ui.AppThemeMode.LIGHT))
    assertTrue(modes.contains(com.example.ui.AppThemeMode.DARK))
    assertTrue(modes.contains(com.example.ui.AppThemeMode.SYSTEM))
  }

  @Test
  fun testBookSearchServiceTitleMatching() {
    // Valid matches (genuine editions of the searched book)
    assertTrue(com.example.data.remote.BookSearchService.isTitleMatch("Il nome della rosa", "Il nome della rosa"))
    assertTrue(com.example.data.remote.BookSearchService.isTitleMatch("Il nome della rosa", "Il nome della rosa (Oscar moderni)"))
    assertTrue(com.example.data.remote.BookSearchService.isTitleMatch("Il nome della rosa", "Il nome della rosa: Edizione speciale"))
    assertTrue(com.example.data.remote.BookSearchService.isTitleMatch("Dune", "Dune"))
    assertTrue(com.example.data.remote.BookSearchService.isTitleMatch("Dune", "Dune - Copertina rigida"))
    assertTrue(com.example.data.remote.BookSearchService.isTitleMatch("Orgoglio e pregiudizio", "Orgoglio e pregiudizio (Classici)"))
    assertTrue(com.example.data.remote.BookSearchService.isTitleMatch("Harry Potter e la pietra filosofale", "Harry Potter e la pietra filosofale: 1"))

    // Invalid matches (completely different books or sequels/prequels)
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Il nome della rosa", "Il pendolo di Foucault"))
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Il nome della rosa", "Baudolino"))
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Il nome della rosa", "Il cimitero di Praga"))
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Dune", "I figli di Dune"))
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Dune", "Messia di Dune"))
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Dune", "Gli eretici di Dune"))
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Orgoglio e pregiudizio", "Ragione e sentimento"))
    assertFalse(com.example.data.remote.BookSearchService.isTitleMatch("Harry Potter e la pietra filosofale", "Harry Potter e la camera dei segreti"))
  }

  @Test
  fun testBookSearchServiceAuthorMatching() {
    assertTrue(com.example.data.remote.BookSearchService.isAuthorMatch("Umberto Eco", "Umberto Eco"))
    assertTrue(com.example.data.remote.BookSearchService.isAuthorMatch("Umberto Eco", "Eco, Umberto"))
    assertTrue(com.example.data.remote.BookSearchService.isAuthorMatch("Frank Herbert", "Herbert, Frank"))
    assertFalse(com.example.data.remote.BookSearchService.isAuthorMatch("Umberto Eco", "Italo Calvino"))
    assertFalse(com.example.data.remote.BookSearchService.isAuthorMatch("George Orwell", "Aldous Huxley"))
  }

  @Test
  fun testNetworkClientRetrofitInstances() {
    assertNotNull(com.example.data.remote.NetworkClient.okHttpClient)
    assertNotNull(com.example.data.remote.NetworkClient.googleBooksRetrofit)
    assertNotNull(com.example.data.remote.NetworkClient.openLibraryRetrofit)
    assertEquals("https://www.googleapis.com/", com.example.data.remote.NetworkClient.googleBooksRetrofit.baseUrl().toString())
    assertEquals("https://openlibrary.org/", com.example.data.remote.NetworkClient.openLibraryRetrofit.baseUrl().toString())
  }

  @Test
  fun testBarcodeApiSources() {
    val sources = com.example.data.remote.ApiSource.values()
    assertEquals(3, sources.size)
    assertTrue(sources.contains(com.example.data.remote.ApiSource.AMAZON_IT))
    assertTrue(sources.contains(com.example.data.remote.ApiSource.LIBRACCIO_IT))
    assertTrue(sources.contains(com.example.data.remote.ApiSource.IBS_IT))
    assertEquals("Amazon.it", com.example.data.remote.ApiSource.AMAZON_IT.displayName)
    assertEquals("Libraccio.it", com.example.data.remote.ApiSource.LIBRACCIO_IT.displayName)
    assertEquals("IBS.it", com.example.data.remote.ApiSource.IBS_IT.displayName)
  }
}
