package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "PDF", "WORD", "EXCEL", "PPT", "TEXT"
    val size: String,
    val date: Long,
    val folder: String,
    val isFavorite: Boolean = false,
    val content: String, // Stringified content structure (eg. formatted text, grid data, slide decks)
    val pageCount: Int = 1,
    val isSimulated: Boolean = true
)
