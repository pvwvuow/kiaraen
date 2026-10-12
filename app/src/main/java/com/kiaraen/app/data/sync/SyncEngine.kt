package com.kiaraen.app.data.sync

import android.util.Log
import com.kiaraen.app.data.local.QuizAttemptEntity
import com.kiaraen.app.data.local.ReviewLogEntity
import com.kiaraen.app.data.repository.AuthRepository
import com.kiaraen.app.data.repository.ContentRepository
import com.kiaraen.app.data.repository.ProgressRepository
import com.kiaraen.app.data.remote.ProgressDto
import com.kiaraen.app.data.remote.ProgressRemoteDataSource
import com.kiaraen.app.data.remote.QuizAttemptDto
import com.kiaraen.app.data.remote.ReviewLogDto
import java.time.Instant

/**
 * Push-pull sync over WorkManager.
 *
 * Push (batched, order matters — events before state):
 *   review_log → quiz_attempts → user_word_progress
 * Pull (only once per session or after login):
 *   user_word_progress (LWW-merge) → user_stats (server cache is authoritative)
 *
 * Every step is independently retryable; batches are marked synced only after
 * an acknowledged response. Server fences:
 *   events  → unique (user_id, client_event_id)   → replays are no-ops
 *   state   → LWW guard trigger (00005)           → stale writes are dropped
 */
class SyncEngine(
    private val db: com.kiaraen.app.data.local.KiaraDatabase,
    private val remote: ProgressRemoteDataSource,
    private val content: ContentRepository,
    private val auth: AuthRepository,
    private val progressRepo: ProgressRepository,
) {
    private val dao = db.progressDao()

    suspend fun syncAll(pullOnStart: Boolean = false): Boolean {
        val userId = auth.currentUserId() ?: return true   // not signed in → nothing to sync
        var ok = true

        ok = pushReviewLogs(userId) && ok
        ok = pushQuizAttempts(userId) && ok
        ok = pushProgress(userId) && ok

        if (pullOnStart || ok) {
            ok = runStep("pull") {
                val pulled = remote.pullProgress(userId)
                progressRepo.mergePulledProgress(userId, pulled)
                progressRepo.mergePulledStats(userId, remote.pullStats(userId))
            } && ok
        }

        runStep("content") { content.refreshIfAvailable().getOrThrow() } // best-effort, non-fatal

        return ok
    }

    private suspend fun pushReviewLogs(userId: String): Boolean {
        var allSynced = true
        while (true) {
            val batch: List<ReviewLogEntity> = dao.pendingReviewLogs(PUSH_BATCH)
            if (batch.isEmpty()) break
            val ok = runStep("review_log[${batch.size}]") {
                remote.pushReviewLogs(
                    batch.map {
                        ReviewLogDto(
                            userId = userId, wordId = it.wordId, result = it.result,
                            mode = it.mode, quizId = null, xp = it.xp,
                            clientEventId = it.clientEventId,
                            createdAt = Instant.ofEpochMilli(it.createdAt).toString(),
                        )
                    },
                )
                dao.markReviewLogsSynced(batch.map { it.id })
            }
            if (!ok) { allSynced = false; break }
            if (batch.size < PUSH_BATCH) break
        }
        return allSynced
    }

    private suspend fun pushQuizAttempts(userId: String): Boolean {
        var allSynced = true
        while (true) {
            val batch: List<QuizAttemptEntity> = dao.pendingQuizAttempts(PUSH_BATCH)
            if (batch.isEmpty()) break
            val ok = runStep("quiz_attempts[${batch.size}]") {
                remote.pushQuizAttempts(
                    batch.map {
                        QuizAttemptDto(
                            userId = userId, quizId = null, quizRef = it.quizRef,
                            score = it.score, total = it.total, durationMs = it.durationMs,
                            clientEventId = it.clientEventId,
                            createdAt = Instant.ofEpochMilli(it.createdAt).toString(),
                        )
                    },
                )
                dao.markQuizAttemptsSynced(batch.map { it.id })
            }
            if (!ok) { allSynced = false; break }
            if (batch.size < PUSH_BATCH) break
        }
        return allSynced
    }

    private suspend fun pushProgress(userId: String): Boolean {
        var allSynced = true
        while (true) {
            val batch = dao.pendingProgress(userId, PUSH_BATCH)
            if (batch.isEmpty()) break
            val ok = runStep("progress[${batch.size}]") {
                remote.pushProgress(
                    batch.map {
                        ProgressDto(
                            userId = userId, wordId = it.wordId, status = it.status,
                            familiarity = it.familiarity, correctCount = it.correctCount,
                            wrongCount = it.wrongCount, easeFactor = it.easeFactor,
                            intervalDays = it.intervalDays, repetitions = it.repetitions,
                            dueAt = Instant.ofEpochMilli(it.dueAt).toString(),
                            lastReviewedAt = it.lastReviewedAt?.let { t -> Instant.ofEpochMilli(t).toString() },
                            learnedAt = it.learnedAt?.let { t -> Instant.ofEpochMilli(t).toString() },
                            source = it.source,
                            clientUpdatedAt = Instant.ofEpochMilli(it.clientUpdatedAt).toString(),
                        )
                    },
                )
                dao.markProgressSynced(userId, batch.map { it.wordId })
            }
            if (!ok) { allSynced = false; break }
            if (batch.size < PUSH_BATCH) break
        }
        return allSynced
    }

    private inline fun runStep(name: String, block: () -> Unit): Boolean =
        try {
            block(); true
        } catch (t: Throwable) {
            Log.w(TAG, "sync step '$name' failed, will retry: ${t.message}")
            false
        }

    companion object {
        private const val TAG = "SyncEngine"
        private const val PUSH_BATCH = 200
    }
}
