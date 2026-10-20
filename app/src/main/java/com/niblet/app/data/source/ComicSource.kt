package com.niblet.app.data.source

import com.niblet.app.data.model.Chapter
import com.niblet.app.data.model.Series

interface ComicSource {
    val id: String
    val displayName: String
    suspend fun listSeries(): List<Series>
    suspend fun listChapters(seriesTitle: String): List<Chapter>
    suspend fun readPage(chapter: Chapter, index: Int): ByteArray
    fun close()
}
