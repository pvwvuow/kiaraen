package com.kiaraen.app.data.repository

import com.kiaraen.app.data.local.KiaraDatabase
import com.kiaraen.app.data.local.QuizAttemptEntity
import com.kiaraen.app.data.local.ReviewLogEntity
import com.kiaraen.app.data.local.WordProgressEntity
import com.kiaraen.app.data.remote.ProgressDto
import com.kiaraen.app.data.remote.ProgressRemoteDataSource
import com.kiaraen.app.domain.ReviewMode
import com.kiaraen.app.domain.ReviewResult
import com.kiaraen.app.domain.SrsLite
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

/**
 * All learning writes happen here: local Room FIRST (offline-first, instant UI),
 * rows flagged pendingSync; SyncEngine pushes them later. The server dedupes
 * retries (client_event_id) and resolves multi-device races (LWW guard).
 */
class ProgressRepository(
    private val db: KiaraDatabase,
    private val remote: ProgressRemoteDataSource,
) {
    private val dao = db.progressDao()

    /** One answered card/question → SRS update + append-only event. */
    suspend fun recordReview(
        userId: String,
        wordId: Long,
        result: ReviewResult,
        mode: ReviewMode,
        quizRef: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val grade = result == ReviewResult.CORRECT || result == ReviewResult.EASY
        val current = dao.progressOf(userId, wordId)
            ?: WordProgressEntity(userId = userId, wordId = wordId)
        val next = SrsLite.next(
            SrsLite.State(
                status = current.status, easeFactor = current.easeFactor,
                intervalDays = current.intervalDays, repetitions = current.repetitions,
                dueAtEpochMs = current.dueAt,
            ), grade, now,
        )
        dao.upsertProgress(
            listOf(
                current.copy(
                    status = next.status,
                    easeFactor = next.easeFactor,
                    intervalDays = next.intervalDays,
                    repetitions = next.repetitions,
                    dueAt = next.dueAtEpochMs,
                    correctCount = current.correctCount + if (grade) 1 else 0,
                    wrongCount = current.wrongCount + if (grade) 0 else 1,
                    lastReviewedAt = now,
                    learnedAt = if (current.learnedAt == null && next.status != "new" && next.status != "learning") now else current.learnedAt,
                    clientUpdatedAt = now,
                    pendingSync = true,
                ),
            ),
        )
        dao.insertReviewLog(
            ReviewLogEntity(
                userId = userId, wordId = wordId,
                result = result.name.lowercase(), mode = mode.name.lowercase(),
                quizRef = quizRef, xp = if (grade) XP_CORRECT else XP_WRONG,
                clientEventId = UUID.randomUUID().toString(), createdAt = now,
            ),
        )
        dao.recomputeLocalStats(userId, todayDay(now))
    }

    suspend fun recordQuizAttempt(
        userId: String, quizRef: String, score: Int, total: Int, durationMs: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        dao.insertQuizAttempt(
            QuizAttemptEntity(
                userId = userId, quizRef = quizRef, score = score, total = total,
                durationMs = durationMs, clientEventId = UUID.randomUUID().toString(), createdAt = now,
            ),
        )
        dao.recomputeLocalStats(userId, todayDay(now))
    }

    suspend fun dueQueue(userId: String, limit: Int = 20): List<WordProgressEntity> =
        dao.dueQueue(userId, System.currentTimeMillis(), limit)

    suspend fun dueCountOnce(userId: String): Int =
        dao.dueCount(userId, System.currentTimeMillis()).first()

    /**
     * Merge a pulled row set (other device's writes) into local state by
     * Last-Write-Wins on clientUpdatedAt. Pure logic → unit tested.
     */
    suspend fun mergePulledProgress(userId: String, pulled: List<ProgressDto>) {
        val rows = pulled.map { dto ->
            Triple(dto, toEpoch(dto.clientUpdatedAt), toEpoch(dto.dueAt))
        }
        val winners = rows.mapNotNull { (dto, clientUpdated, due) ->
            val local = dao.progressOf(userId, dto.wordId)
            if (local != null && !com.kiaraen.app.domain.SyncRules.remoteWins(local.clientUpdatedAt, clientUpdated)) {
                return@mapNotNull null
            }
            WordProgressEntity(
                userId = userId, wordId = dto.wordId, status = dto.status,
                familiarity = dto.familiarity, correctCount = dto.correctCount,
                wrongCount = dto.wrongCount, easeFactor = dto.easeFactor,
                intervalDays = dto.intervalDays, repetitions = dto.repetitions,
                dueAt = due, lastReviewedAt = dto.lastReviewedAt?.let(::toEpoch),
                learnedAt = dto.learnedAt?.let(::toEpoch), source = dto.source,
                clientUpdatedAt = clientUpdated, pendingSync = false,
            )
        }
        if (winners.isNotEmpty()) dao.upsertProgress(winners)
    }

    suspend fun mergePulledStats(userId: String, stats: com.kiaraen.app.data.remote.UserStatsDto?) {
        if (stats == null) return
        dao.upsertStats(
            com.kiaraen.app.data.local.UserStatsEntity(
                userId = userId, totalReviews = stats.totalReviews,
                totalCorrect = stats.totalCorrect, totalLearned = stats.totalLearned,
                totalMastered = stats.totalMastered, currentStreak = stats.currentStreak,
                longestStreak = stats.longestStreak, totalXp = stats.totalXp,
                totalQuizAttempts = stats.totalQuizAttempts,
            ),
        )
    }

    suspend fun clearUserData(userId: String) = dao.clearUserData(userId)

    /* ── helpers ──────────────────────────────────────────────────────────── */

    private fun toEpoch(iso: String): Long =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse { 0L }

    private fun todayDay(now: Long): Long = now / 86_400_000L

    companion object {
        const val XP_CORRECT = 10
        const val XP_WRONG = 2
    }
}
