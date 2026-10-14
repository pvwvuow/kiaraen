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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.TimeZone
import java.util.UUID

/**
 * All learning writes happen here: local Room FIRST (offline-first, instant UI),
 * rows flagged pendingSync; SyncEngine pushes them later. The server dedupes
 * retries (client_event_id) and resolves multi-device races (LWW guard).
 *
 * «یادگرفته» gate (v0.7.0): reading a card NEVER grants learned on its own.
 * recordReview keeps un-learned words at "learning" and defers learnedAt;
 * only completeQuiz() — called when the user finishes a quiz to the END —
 * promotes the words answered correctly and clears their mistakes.
 */
class ProgressRepository(
    private val db: KiaraDatabase,
    private val remote: ProgressRemoteDataSource,
) {
    private val dao = db.progressDao()

    /**
     * ALL state writes are confined to one thread: recordReview (per answer)
     * and completeQuiz (at the end) are separate suspend calls that do
     * read-modify-write on the same rows — interleaving them lost promotions
     * under the quiz's per-answer coroutines. One queue makes the gate airtight.
     */
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** One answered card/question → SRS update + append-only event. */
    suspend fun recordReview(
        userId: String,
        wordId: Long,
        result: ReviewResult,
        mode: ReviewMode,
        quizRef: String? = null,
        now: Long = System.currentTimeMillis(),
    ) = withContext(writeDispatcher) {
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
        // Gate: a word the user never passed in a completed quiz stays
        // "learning" — SRS still schedules it (dueAt/intervals kept), but it
        // never counts as learned and never leaves the learning rotation.
        val alreadyLearned = current.learnedAt != null
        val status = if (!alreadyLearned && next.status != "learning") "learning" else next.status
        dao.upsertProgress(
            listOf(
                current.copy(
                    status = status,
                    easeFactor = next.easeFactor,
                    intervalDays = next.intervalDays,
                    repetitions = next.repetitions,
                    dueAt = next.dueAtEpochMs,
                    correctCount = current.correctCount + if (grade) 1 else 0,
                    wrongCount = current.wrongCount + if (grade) 0 else 1,
                    lastReviewedAt = now,
                    learnedAt = current.learnedAt,   // only completeQuiz() sets it
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
        // every wrong answer — flashcard «یادآوری», review «فردا», quiz ✗ — lands
        // in the ONE mistakes list (اشتباهات رو یک جا بنویس).
        if (!grade) dao.recordMistake(userId, wordId, now)
        dao.recomputeLocalStats(userId, todayDay(now), offsetMs(now))
    }

    suspend fun recordQuizAttempt(
        userId: String, quizRef: String, score: Int, total: Int, durationMs: Long,
        now: Long = System.currentTimeMillis(),
    ) = withContext(writeDispatcher) {
        dao.insertQuizAttempt(
            QuizAttemptEntity(
                userId = userId, quizRef = quizRef, score = score, total = total,
                durationMs = durationMs, clientEventId = UUID.randomUUID().toString(), createdAt = now,
            ),
        )
        dao.recomputeLocalStats(userId, todayDay(now), offsetMs(now))
    }

    /**
     * The quiz gate — called ONCE when a quiz is completed to the end
     * («آزمون رو کامل زد»). Correct answers:
     *   • promote the word to learned (learnedAt set, status review/mastered
     *     per the repetitions the SRS already counted),
     *   • clear its mistake row (the mistake is fixed).
     * Wrong answers were already recorded by recordReview — they stay in
     * اشتباهات for «مرور اشتباهات». An ABANDONED quiz (hearts ran out) never
     * calls this → nothing is added to learned.
     *
     * @return the number of words that became «یادگرفته» right now.
     */
    suspend fun completeQuiz(
        userId: String,
        correctWordIds: List<Long>,
        now: Long = System.currentTimeMillis(),
    ): Int = withContext(writeDispatcher) {
        var promoted = 0
        for (wordId in correctWordIds.distinct()) {
            // fallback row: a fast completion must not depend on the per-answer
            // write having landed first (the dispatcher orders them anyway)
            val current = dao.progressOf(userId, wordId)
                ?: WordProgressEntity(userId = userId, wordId = wordId)
            if (current.learnedAt != null) continue          // already learned
            dao.upsertProgress(
                listOf(
                    current.copy(
                        status = if (current.repetitions >= MASTERED_REPS) "mastered" else "review",
                        learnedAt = now,
                        clientUpdatedAt = now,
                        pendingSync = true,
                    ),
                ),
            )
            promoted++
        }
        correctWordIds.distinct().forEach { dao.resolveMistake(userId, it) }
        dao.recomputeLocalStats(userId, todayDay(now), offsetMs(now))
        promoted
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

    /**
     * Guest → account hand-off (idempotent): everything the user learned while
     * offline under the "local" id is re-owned by the real account, keeping the
     * pendingSync flags so the very next sync pushes it to Supabase.
     */
    suspend fun linkLocalData(userId: String) = dao.migrateLocalData(userId)

    /* ── helpers ───────────────────────────────────────────────────────────────────── */

    private fun toEpoch(iso: String): Long =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse { 0L }

    /** Device UTC offset (DST-aware) — Tehran is +3:30, so UTC days were wrong. */
    private fun offsetMs(now: Long): Long = TimeZone.getDefault().getOffset(now).toLong()

    private fun todayDay(now: Long): Long = (now + offsetMs(now)) / 86_400_000L

    companion object {
        const val XP_CORRECT = 10
        const val XP_WRONG = 2
        const val MASTERED_REPS = 4   // SrsLite contract: 4 correct → mastered
    }
}
