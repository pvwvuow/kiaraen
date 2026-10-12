package com.kiaraen.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    // ── word progress (STATE) ────────────────────────────────────────────────
    @Upsert
    suspend fun upsertProgress(items: List<WordProgressEntity>)

    @Query("SELECT * FROM word_progress WHERE userId = :userId AND wordId = :wordId")
    suspend fun progressOf(userId: String, wordId: Long): WordProgressEntity?

    @Query("SELECT * FROM word_progress WHERE userId = :userId AND status != 'new' AND dueAt <= :now ORDER BY dueAt LIMIT :limit")
    suspend fun dueQueue(userId: String, now: Long, limit: Int): List<WordProgressEntity>

    @Query("SELECT COUNT(*) FROM word_progress WHERE userId = :userId AND status != 'new' AND dueAt <= :now")
    fun dueCount(userId: String, now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_progress WHERE userId = :userId AND status IN ('review','mastered') AND learnedAt IS NOT NULL")
    fun learnedCount(userId: String): Flow<Int>

    @Query("SELECT * FROM word_progress WHERE userId = :userId AND pendingSync = 1 LIMIT :limit")
    suspend fun pendingProgress(userId: String, limit: Int): List<WordProgressEntity>

    @Query("UPDATE word_progress SET pendingSync = 0 WHERE userId = :userId AND wordId IN (:wordIds)")
    suspend fun markProgressSynced(userId: String, wordIds: List<Long>)

    @Query("SELECT * FROM word_progress WHERE userId = :userId")
    suspend fun allProgress(userId: String): List<WordProgressEntity>

    // ── review events ────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)   // unique clientEventId fence
    suspend fun insertReviewLog(item: ReviewLogEntity): Long

    @Query("SELECT * FROM review_log WHERE pendingSync = 1 ORDER BY id LIMIT :limit")
    suspend fun pendingReviewLogs(limit: Int): List<ReviewLogEntity>

    @Query("UPDATE review_log SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markReviewLogsSynced(ids: List<Long>)

    @Query("DELETE FROM review_log WHERE pendingSync = 0 AND createdAt < :before")
    suspend fun pruneSyncedReviewLogs(before: Long)

    @Query("SELECT DISTINCT createdAt / 86400000 AS day FROM review_log WHERE userId = :userId")
    suspend fun activeDays(userId: String): List<Long>

    @Query("SELECT COALESCE(SUM(xp), 0) FROM review_log WHERE userId = :userId")
    suspend fun totalXp(userId: String): Int

    // ── quiz attempts ────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuizAttempt(item: QuizAttemptEntity): Long

    @Query("SELECT * FROM quiz_attempts WHERE pendingSync = 1 ORDER BY id LIMIT :limit")
    suspend fun pendingQuizAttempts(limit: Int): List<QuizAttemptEntity>

    @Query("UPDATE quiz_attempts SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markQuizAttemptsSynced(ids: List<Long>)

    // ── stats cache (local; refreshed from review_log + server pull) ────────
    @Upsert
    suspend fun upsertStats(item: UserStatsEntity)

    @Query("SELECT * FROM user_stats WHERE userId = :userId")
    fun stats(userId: String): Flow<UserStatsEntity?>

    @Transaction
    suspend fun recomputeLocalStats(userId: String, todayDay: Long) {
        val days = activeDays(userId).toSet()
        val s = com.kiaraen.app.domain.StreakCalculator.compute(days, todayDay)
        val xp = totalXp(userId)
        val existing = statsOnce(userId) ?: UserStatsEntity(userId = userId)
        upsertStats(
            existing.copy(
                currentStreak = s.current,
                longestStreak = maxOf(s.longest, existing.longestStreak),
                lastActivityDay = days.maxOrNull(),
                totalXp = xp,
            )
        )
    }

    @Query("SELECT * FROM user_stats WHERE userId = :userId")
    suspend fun statsOnce(userId: String): UserStatsEntity?

    // ── account switch / sign-out hygiene ───────────────────────────────────
    @Query("DELETE FROM word_progress WHERE userId = :userId")
    suspend fun clearProgress(userId: String)

    @Query("DELETE FROM review_log WHERE userId = :userId")
    suspend fun clearReviewLog(userId: String)

    @Query("DELETE FROM quiz_attempts WHERE userId = :userId")
    suspend fun clearQuizAttempts(userId: String)

    @Query("DELETE FROM user_stats WHERE userId = :userId")
    suspend fun clearStats(userId: String)

    @Transaction
    suspend fun clearUserData(userId: String) {
        clearProgress(); clearReviewLog(); clearQuizAttempts(); clearStats()
    }

    // ── local-mode → account linking: keep everything the user did offline ──
    @Query("UPDATE word_progress SET userId = :to WHERE userId = 'local'")
    suspend fun migrateProgressLocal(to: String)

    @Query("UPDATE review_log SET userId = :to WHERE userId = 'local'")
    suspend fun migrateReviewLogLocal(to: String)

    @Query("UPDATE quiz_attempts SET userId = :to WHERE userId = 'local'")
    suspend fun migrateQuizAttemptsLocal(to: String)

    @Query("UPDATE user_stats SET userId = :to WHERE userId = 'local'")
    suspend fun migrateStatsLocal(to: String)

    @Query("DELETE FROM user_stats WHERE userId = 'local'")
    suspend fun deleteLocalStats()

    @Transaction
    suspend fun migrateLocalData(to: String) {
        migrateProgressLocal(to)
        migrateReviewLogLocal(to)
        migrateQuizAttemptsLocal(to)
        migrateStatsLocal(to)
        deleteLocalStats()
    }
}
