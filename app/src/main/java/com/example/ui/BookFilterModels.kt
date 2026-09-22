package com.example.ui

data class AdvancedFilterCriteria(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val year: String = "",
    val category: String = "",
    val tag: String = "",
    val status: String? = null
) {
    val isActive: Boolean
        get() = title.isNotBlank() ||
                author.isNotBlank() ||
                publisher.isNotBlank() ||
                year.isNotBlank() ||
                category.isNotBlank() ||
                tag.isNotBlank() ||
                status != null

    val activeCount: Int
        get() {
            var count = 0
            if (title.isNotBlank()) count++
            if (author.isNotBlank()) count++
            if (publisher.isNotBlank()) count++
            if (year.isNotBlank()) count++
            if (category.isNotBlank()) count++
            if (tag.isNotBlank()) count++
            if (status != null) count++
            return count
        }
}

val POPULAR_CATEGORIES = listOf(
    "Fantascienza",
    "Storia",
    "Saggistica",
    "Narrativa",
    "Giallo & Thriller",
    "Biografia",
    "Filosofia",
    "Fantasy",
    "Poesia",
    "Scienza",
    "Arte",
    "Classici",
    "Ragazzi",
    "Economia"
)

val POPULAR_TAGS = listOf(
    "preferito",
    "da leggere",
    "in lettura",
    "letto",
    "prestato",
    "capolavoro",
    "da rileggere",
    "raro",
    "consigliato",
    "acquistato"
)
