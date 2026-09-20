package com.adaptiq.tutor.agent

import com.adaptiq.tutor.data.KnowledgeGapDao
import com.adaptiq.tutor.data.KnowledgeGapEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * KnowledgeGapTracker records misunderstood concepts and schedules
 * spaced-repetition reinforcement using Room SQLite FTS4.
 *
 * Uses a simplified Leitner-style spacing algorithm:
 * - Box 1: Review after 1 day
 * - Box 2: Review after 3 days
 * - Box 3: Review after 7 days
 * - Box 4: Review after 14 days
 * - Box 5: Mastered (no further review)
 */
class KnowledgeGapTracker(
    private val knowledgeGapDao: KnowledgeGapDao
) {
    companion object {
        private val REVIEW_INTERVALS_MS = longArrayOf(
            TimeUnit.DAYS.toMillis(1),   // Box 1
            TimeUnit.DAYS.toMillis(3),   // Box 2
            TimeUnit.DAYS.toMillis(7),   // Box 3
            TimeUnit.DAYS.toMillis(14),  // Box 4
            Long.MAX_VALUE               // Box 5: Mastered
        )
        const val MAX_BOX = 5
    }

    /**
     * Records a knowledge gap identified during tutoring.
     *
     * @param subject The subject area (e.g., "Math", "Science")
     * @param topic The specific topic (e.g., "Fractions")
     * @param concept The misunderstood concept description
     * @param userResponse The student's incorrect/incomplete response
     */
    suspend fun recordGap(
        subject: String,
        topic: String,
        concept: String,
        userResponse: String
    ) {
        val now = System.currentTimeMillis()
        val entity = KnowledgeGapEntity(
            subject = subject,
            topic = topic,
            concept = concept,
            userResponse = userResponse,
            spacedRepetitionBox = 1,
            nextReviewAt = now + REVIEW_INTERVALS_MS[0],
            createdAt = now,
            lastReviewedAt = now,
            reviewCount = 0,
            isMastered = false
        )
        knowledgeGapDao.insertGap(entity)
    }

    /**
     * Returns all concepts due for review (next review time <= now).
     */
    fun getGapsDueForReview(): Flow<List<KnowledgeGapEntity>> {
        return knowledgeGapDao.getGapsDueForReview(System.currentTimeMillis())
    }

    /**
     * Marks a concept as correctly reviewed, advancing it to the next box.
     */
    suspend fun markReviewed(gapId: Long, wasCorrect: Boolean) {
        val gap = knowledgeGapDao.getGapById(gapId) ?: return
        val now = System.currentTimeMillis()

        val newBox = if (wasCorrect) {
            (gap.spacedRepetitionBox + 1).coerceAtMost(MAX_BOX)
        } else {
            // Regression: move back one box (minimum box 1)
            (gap.spacedRepetitionBox - 1).coerceAtLeast(1)
        }

        val nextReview = if (newBox >= MAX_BOX) {
            Long.MAX_VALUE // Mastered
        } else {
            now + REVIEW_INTERVALS_MS[newBox - 1]
        }

        knowledgeGapDao.updateGap(
            gap.copy(
                spacedRepetitionBox = newBox,
                nextReviewAt = nextReview,
                lastReviewedAt = now,
                reviewCount = gap.reviewCount + 1,
                isMastered = newBox >= MAX_BOX
            )
        )
    }

    /**
     * Searches for knowledge gaps matching a query using FTS4.
     */
    fun searchGaps(query: String): Flow<List<KnowledgeGapEntity>> {
        return knowledgeGapDao.searchGaps(query)
    }

    /**
     * Returns all gaps for a specific subject.
     */
    fun getGapsBySubject(subject: String): Flow<List<KnowledgeGapEntity>> {
        return knowledgeGapDao.getGapsBySubject(subject)
    }

    /**
     * Returns mastery statistics.
     */
    suspend fun getMasteryStats(): MasteryStats {
        val allGaps = knowledgeGapDao.getAllGapsSnapshot()
        val mastered = allGaps.count { it.isMastered }
        val pending = allGaps.count { !it.isMastered }
        val dueNow = allGaps.count {
            !it.isMastered && it.nextReviewAt <= System.currentTimeMillis()
        }
        return MasteryStats(
            totalConcepts = allGaps.size,
            masteredConcepts = mastered,
            pendingConcepts = pending,
            dueForReview = dueNow
        )
    }
}

data class MasteryStats(
    val totalConcepts: Int,
    val masteredConcepts: Int,
    val pendingConcepts: Int,
    val dueForReview: Int
) {
    val masteryPercentage: Float
        get() = if (totalConcepts > 0) masteredConcepts.toFloat() / totalConcepts else 0f
}
