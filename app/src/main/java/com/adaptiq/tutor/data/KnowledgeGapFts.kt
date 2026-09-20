package com.adaptiq.tutor.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * FTS4 virtual table for full-text search over knowledge gap concepts.
 * Mirrors the concept and topic columns of [KnowledgeGapEntity].
 */
@Fts4(contentEntity = KnowledgeGapEntity::class, tokenizer = FtsOptions.TOKENIZER_PORTER)
@Entity(tableName = "knowledge_gaps_fts")
data class KnowledgeGapFts(
    val concept: String,
    val topic: String,
    val subject: String
)
