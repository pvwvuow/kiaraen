package com.kiaraen.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs — field names match the Postgres columns exactly (snake_case).
 * Domain models live in domain/Models.kt; mapping happens in repositories.
 */
@Serializable
data class ContentPackMetaDto(
    val version: Int,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("word_count") val wordCount: Int,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class ProgressDto(
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: Long,
    val status: String,
    val familiarity: Int,
    @SerialName("correct_count") val correctCount: Int,
    @SerialName("wrong_count") val wrongCount: Int,
    @SerialName("ease_factor") val easeFactor: Float,
    @SerialName("interval_days") val intervalDays: Float,
    val repetitions: Int,
    @SerialName("due_at") val dueAt: String,
    @SerialName("last_reviewed_at") val lastReviewedAt: String? = null,
    @SerialName("learned_at") val learnedAt: String? = null,
    val source: String,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class ReviewLogDto(
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: Long,
    val result: String,
    val mode: String,
    @SerialName("quiz_id") val quizId: Long? = null,
    val xp: Int,
    @SerialName("client_event_id") val clientEventId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class QuizAttemptDto(
    @SerialName("user_id") val userId: String,
    @SerialName("quiz_id") val quizId: Long? = null,
    @SerialName("quiz_ref") val quizRef: String,
    val score: Int,
    val total: Int,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("client_event_id") val clientEventId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class UserStatsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("total_reviews") val totalReviews: Int,
    @SerialName("total_correct") val totalCorrect: Int,
    @SerialName("total_learned") val totalLearned: Int,
    @SerialName("total_mastered") val totalMastered: Int,
    @SerialName("current_streak") val currentStreak: Int,
    @SerialName("longest_streak") val longestStreak: Int,
    @SerialName("last_activity_date") val lastActivityDate: String? = null,
    @SerialName("total_xp") val totalXp: Int,
    @SerialName("total_quiz_attempts") val totalQuizAttempts: Int,
)
