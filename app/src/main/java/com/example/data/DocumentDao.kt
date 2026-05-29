package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY date DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE type = :type ORDER BY date DESC")
    fun getDocumentsByType(type: String): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY date DESC")
    fun getFavoriteDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Int): Document?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document)

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(documents: List<Document>)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int
}
