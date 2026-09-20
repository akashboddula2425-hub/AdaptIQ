package com.adaptiq.tutor.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeGapDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGap(gap: KnowledgeGapEntity)

    @Update
    suspend fun updateGap(gap: KnowledgeGapEntity)

    @Delete
    suspend fun deleteGap(gap: KnowledgeGapEntity)

    @Query("SELECT * FROM knowledge_gaps WHERE id = :id")
    suspend fun getGapById(id: Long): KnowledgeGapEntity?

    @Query("SELECT * FROM knowledge_gaps WHERE isMastered = 0 AND nextReviewAt <= :currentTime ORDER BY nextReviewAt ASC")
    fun getGapsDueForReview(currentTime: Long): Flow<List<KnowledgeGapEntity>>

    @Query("SELECT * FROM knowledge_gaps WHERE subject = :subject ORDER BY createdAt DESC")
    fun getGapsBySubject(subject: String): Flow<List<KnowledgeGapEntity>>

    @Query("SELECT * FROM knowledge_gaps")
    suspend fun getAllGapsSnapshot(): List<KnowledgeGapEntity>

    /**
     * FTS4 full-text search across concept and topic fields.
     * Requires the FTS4 virtual table to be set up in the database.
     */
    @Query("SELECT knowledge_gaps.* FROM knowledge_gaps JOIN knowledge_gaps_fts ON knowledge_gaps.rowid = knowledge_gaps_fts.rowid WHERE knowledge_gaps_fts MATCH :query")
    fun searchGaps(query: String): Flow<List<KnowledgeGapEntity>>

    @Query("SELECT COUNT(*) FROM knowledge_gaps WHERE isMastered = 1")
    suspend fun getMasteredCount(): Int

    @Query("SELECT COUNT(*) FROM knowledge_gaps")
    suspend fun getTotalCount(): Int
}
