package com.adaptiq.tutor.agent

import com.adaptiq.tutor.data.LearnerProfile
import com.adaptiq.tutor.data.LearningStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AdaptiveDiagnosticManager evaluates natural conversation to infer the
 * user's comprehension level without rigid diagnostic forms.
 *
 * It analyzes response patterns, vocabulary complexity, answer correctness,
 * and engagement signals to dynamically adjust the learner profile.
 */
class AdaptiveDiagnosticManager {

    data class DiagnosticState(
        val phase: DiagnosticPhase = DiagnosticPhase.INITIAL_GREETING,
        val questionsAsked: Int = 0,
        val correctAnswers: Int = 0,
        val averageResponseLength: Float = 0f,
        val vocabularyComplexityScore: Float = 0f,
        val confidenceLevel: Float = 0f,
        val inferredGradeLevel: Int? = null,
        val inferredLearningStyle: LearningStyle? = null,
        val isComplete: Boolean = false
    )

    enum class DiagnosticPhase {
        INITIAL_GREETING,
        TOPIC_EXPLORATION,
        DEPTH_PROBING,
        STYLE_ASSESSMENT,
        COMPLETE
    }

    private val _state = MutableStateFlow(DiagnosticState())
    val state: StateFlow<DiagnosticState> = _state.asStateFlow()

    // Tracks conversation turns for analysis
    private val conversationHistory = mutableListOf<ConversationTurn>()

    data class ConversationTurn(
        val isUser: Boolean,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Generates the next diagnostic prompt based on current phase.
     * Returns null if diagnostics are complete.
     */
    fun getNextDiagnosticPrompt(): String? {
        val currentState = _state.value
        if (currentState.isComplete) return null

        return when (currentState.phase) {
            DiagnosticPhase.INITIAL_GREETING -> buildString {
                appendLine("You are AdaptIQ, a friendly AI tutor.")
                appendLine("The user has just started the app. Ask them for their name.")
                appendLine("IMPORTANT: Keep your response to a single short sentence. Do NOT repeat yourself.")
            }
            DiagnosticPhase.TOPIC_EXPLORATION -> buildString {
                appendLine("The student has chosen a topic. Ask an open-ended question about it.")
                appendLine("Gauge their existing knowledge through their response.")
                appendLine("Adjust question difficulty based on their vocabulary and confidence.")
            }
            DiagnosticPhase.DEPTH_PROBING -> buildString {
                appendLine("Ask a slightly challenging follow-up question.")
                appendLine("This helps determine their grade level ceiling.")
                appendLine("If they struggle, gently guide without revealing the answer.")
            }
            DiagnosticPhase.STYLE_ASSESSMENT -> buildString {
                appendLine("Present the concept in two different ways:")
                appendLine("1. A visual/spatial explanation")
                appendLine("2. A logical/sequential explanation")
                appendLine("Ask which made more sense to identify learning style.")
            }
            DiagnosticPhase.COMPLETE -> null
        }
    }

    /**
     * Analyzes a user response and updates the diagnostic state.
     * Called after each user message during the onboarding phase.
     */
    fun analyzeResponse(userResponse: String) {
        conversationHistory.add(ConversationTurn(isUser = true, content = userResponse))

        val wordCount = userResponse.split("\\s+".toRegex()).size
        val avgWordLength = userResponse.split("\\s+".toRegex())
            .map { it.length.toFloat() }
            .average()
            .toFloat()

        _state.update { current ->
            val newQuestionsAsked = current.questionsAsked + 1
            val newAvgResponseLength = (
                current.averageResponseLength * current.questionsAsked + wordCount
            ) / newQuestionsAsked

            // Simple vocabulary complexity heuristic:
            // Average word length correlates with vocabulary sophistication
            val vocabScore = (avgWordLength / 8f).coerceIn(0f, 1f)
            val newVocabScore = (
                current.vocabularyComplexityScore * current.questionsAsked + vocabScore
            ) / newQuestionsAsked

            // Infer grade level from vocabulary and response patterns
            val inferredGrade = inferGradeLevel(newVocabScore, newAvgResponseLength)

            // Advance phase based on questions asked
            val newPhase = when {
                newQuestionsAsked >= 6 -> DiagnosticPhase.COMPLETE
                newQuestionsAsked >= 4 -> DiagnosticPhase.STYLE_ASSESSMENT
                newQuestionsAsked >= 2 -> DiagnosticPhase.DEPTH_PROBING
                else -> DiagnosticPhase.TOPIC_EXPLORATION
            }

            current.copy(
                phase = newPhase,
                questionsAsked = newQuestionsAsked,
                averageResponseLength = newAvgResponseLength,
                vocabularyComplexityScore = newVocabScore,
                inferredGradeLevel = inferredGrade,
                isComplete = newPhase == DiagnosticPhase.COMPLETE
            )
        }
    }

    /**
     * Heuristic grade-level inference based on linguistic signals.
     */
    private fun inferGradeLevel(vocabScore: Float, avgResponseLength: Float): Int {
        val score = (vocabScore * 0.6f + (avgResponseLength / 50f).coerceIn(0f, 1f) * 0.4f)
        return when {
            score < 0.2f -> 3
            score < 0.35f -> 5
            score < 0.5f -> 7
            score < 0.65f -> 9
            score < 0.8f -> 10
            else -> 12
        }
    }

    /**
     * Builds a [LearnerProfile] from the diagnostic results.
     * Should only be called after diagnostics are complete.
     */
    fun buildProfile(name: String): LearnerProfile {
        val currentState = _state.value
        return LearnerProfile(
            name = name,
            gradeLevel = currentState.inferredGradeLevel ?: 7,
            learningStyle = currentState.inferredLearningStyle ?: LearningStyle.BALANCED,
            strengths = emptyList(),
            weaknesses = emptyList()
        )
    }

    fun reset() {
        _state.value = DiagnosticState()
        conversationHistory.clear()
    }
}
