package com.kiaraen.app.domain

/**
 * Pure domain models — no Room/Supabase/Compose types leak into this layer.
 */
data class Word(
    val id: Long,
    val word: String,
    val pos: String?,
    val meaningFa: String?,
    val levelId: String,
    val categoryId: Long,
)

data class Level(
    val id: String,
    val title: String,
    val sort: Int,
)

data class Category(
    val id: Long,
    val levelId: String,
    val key: String,
    val title: String,
    val icon: String,
)

data class Collection(
    val id: Long,
    val slug: String,
    val title: String,
)

data class UserStats(
    val totalReviews: Int = 0,
    val totalCorrect: Int = 0,
    val totalLearned: Int = 0,
    val totalMastered: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalXp: Int = 0,
)

enum class ReviewResult { CORRECT, WRONG, EASY, HARD, AGAIN;

    companion object {
        fun from(raw: String) = entries.firstOrNull { it.name.lowercase() == raw } ?: AGAIN
    }
}

enum class ReviewMode { FLASHCARD, QUIZ, TYPING, LISTENING, MANUAL;

    companion object {
        fun from(raw: String) = entries.firstOrNull { it.name.lowercase() == raw } ?: QUIZ
    }
}
