package com.adaptiq.tutor.data

import kotlinx.serialization.Serializable

@Serializable
data class LearnerProfile(
    val name: String = "Student",
    val gradeLevel: Int = 7,
    val learningStyle: LearningStyle = LearningStyle.BALANCED,
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList()
)

@Serializable
enum class LearningStyle(val displayName: String) {
    VISUAL("Visual / Spatial"),
    AUDITORY("Auditory / Verbal"),
    LOGICAL("Logical / Sequential"),
    KINESTHETIC("Hands-on / Kinesthetic"),
    BALANCED("Balanced / Mixed")
}
