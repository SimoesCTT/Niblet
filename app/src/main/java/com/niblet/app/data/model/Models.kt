package com.niblet.app.data.model

data class Series(
    val id: String,
    val title: String,
    val chapterCount: Int,
    val sourceId: String
)

data class Chapter(
    val id: String,
    val title: String,
    val fileUri: String,
    val seriesId: String
)
