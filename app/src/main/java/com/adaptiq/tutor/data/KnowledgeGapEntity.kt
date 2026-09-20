package com.adaptiq.tutor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_gaps")
data class KnowledgeGapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val topic: String,
    val concept: String,
    val userResponse: String,
    val spacedRepetitionBox: Int = 1,
    val nextReviewAt: Long,
    val createdAt: Long,
    val lastReviewedAt: Long,
    val reviewCount: Int = 0,
    val isMastered: Boolean = false
)
