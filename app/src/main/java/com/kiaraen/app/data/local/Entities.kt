package com.kiaraen.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * CONTENT tables — a read-only local mirror of the content pack.
 * Replaced wholesale on content sync; never written by user actions.
 */
@Entity(tableName = "levels")
data class LevelEntity(
    @PrimaryKey val id: String,          // A1..C2
    val title: String,
    val sort: Int,
)

@Entity(
    tableName = "categories",
    indices = [Index("levelId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val levelId: String,
    val key: String,                     // stable slug, e.g. 'animals'
    val title: String,
    val icon: String,
    val sort: Int,
)

@Entity(
    tableName = "words",
    // same word may exist as noun AND verb → uniqueness is the (word,pos) pair
    indices = [Index("levelId", "categoryId"), Index(value = ["word", "pos"], unique = true)],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val pos: String?,
    val meaningFa: String?,
    val levelId: String,
    val categoryId: Long,
    val difficulty: Int,
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["slug"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val title: String,
    val sort: Int,
)

@Entity(
    tableName = "collection_words",
    primaryKeys = ["collectionId", "wordId"],
    indices = [Index("wordId")],
)
data class CollectionWordEntity(
    val collectionId: Long,
    val wordId: Long,
)

/** POJO for [ProgressDao.reviewCountsByDay] — reviews per local epoch day. */
data class ReviewDayCount(
    val day: Long,
    val cnt: Int,
)

@Entity(tableName = "content_meta")
data class ContentMetaEntity(
    @PrimaryKey val id: Int = 1,
    val version: Int,
    val wordCount: Int,
    val syncedAt: Long,
)

/*
 * USER tables — written locally FIRST (offline-first), then pushed by SyncEngine.
 * pendingSync = 1 marks a row that still needs a push; the server deduplicates
 * event retries via (user_id, client_event_id) and merges state via LWW.
 */
@Entity(
    tableName = "word_progress",
    primaryKeys = ["userId", "wordId"],
    indices = [Index("pendingSync"), Index("dueAt")],
)
data class WordProgressEntity(
    val userId: String,
    val wordId: Long,
    val status: String = "new",          // new | learning | review | mastered
    val familiarity: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val easeFactor: Float = 2.5f,
    val intervalDays: Float = 0f,
    val repetitions: Int = 0,
    val dueAt: Long = 0L,
    val lastReviewedAt: Long? = null,
    val learnedAt: Long? = null,
    val source: String = "lesson",
    val clientUpdatedAt: Long = 0L,
    val pendingSync: Boolean = false,
)

@Entity(
    tableName = "review_log",
    indices = [
        Index(value = ["clientEventId"], unique = true),
        Index("userId", "createdAt"),
        Index("pendingSync"),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val wordId: Long,
    val result: String,                  // correct | wrong | easy | hard | again
    val mode: String,                    // flashcard | quiz | typing | listening | manual
    val quizRef: String? = null,
    val xp: Int,
    val clientEventId: String,           // generated on device → idempotent retries
    val createdAt: Long,
    val pendingSync: Boolean = true,
)

@Entity(
    tableName = "quiz_attempts",
    indices = [
        Index(value = ["clientEventId"], unique = true),
        Index("userId", "createdAt"),
        Index("pendingSync"),
    ],
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val quizRef: String,                 // e.g. 'cat:12:auto' | 'level:A1:default'
    val score: Int,
    val total: Int,
    val durationMs: Long,
    val clientEventId: String,
    val createdAt: Long,
    val pendingSync: Boolean = true,
)

@Entity(
    tableName = "user_stats",
    primaryKeys = ["userId"],
)
data class UserStatsEntity(
    val userId: String,
    val totalReviews: Int = 0,
    val totalCorrect: Int = 0,
    val totalLearned: Int = 0,
    val totalMastered: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActivityDay: Long? = null,   // epoch day
    val totalXp: Int = 0,
    val totalQuizAttempts: Int = 0,
)
