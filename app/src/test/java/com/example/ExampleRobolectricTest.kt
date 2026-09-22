package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Archivio Libri", appName)
  }

  @Test
  fun testLookupBookCascade() = kotlinx.coroutines.runBlocking {
    val service = com.example.data.remote.BookLookupService()
    val searchService = com.example.data.remote.BookSearchService()
    val raw = searchService.queryIsbnSearchOrgByIsbn("9788845938856")
    println("RAW_ISBN_RES: $raw")
    if (raw != null) {
      val enriched = service.enrichMetadata(raw)
      println("ENRICHED_RES: $enriched")
    }
  }
}
