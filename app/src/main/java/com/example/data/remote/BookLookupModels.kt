package com.example.data.remote

import com.squareup.moshi.JsonClass

data class LookupResult(
    val isbn: String,
    val title: String,
    val author: String,
    val publisher: String,
    val publishedYear: String,
    val description: String,
    val coverUrl: String?,
    val pageCount: Int,
    val genre: String,
    val sourceApi: String
)

enum class ApiSource(val displayName: String) {
    AMAZON_IT("Amazon.it"),
    LIBRACCIO_IT("Libraccio.it"),
    IBS_IT("IBS.it")
}

data class CascadeStepStatus(
    val source: ApiSource,
    val success: Boolean,
    val message: String
)

@JsonClass(generateAdapter = true)
data class ApifyDatasetItem(
    val title: String? = null,
    val author: String? = null,
    val authors: List<String>? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val publicationDate: String? = null,
    val description: String? = null,
    val productDescription: String? = null,
    val thumbnail: String? = null,
    val image: String? = null,
    val imageUrl: String? = null,
    val pageCount: Int? = null,
    val pages: Int? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val genre: String? = null
)
