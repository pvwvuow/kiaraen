package com.kiaraen.app.domain

/**
 * SRS-lite: a conservative subset of SM-2, runnable fully offline.
 * The server stores ease/interval/reps columns too, so a smarter scheduler
 * (full SM-2, FSRS) can be introduced later WITHOUT a schema migration.
 *
 * Intervals are in days (fractional allowed). All math is pure → unit tested.
 */
object SrsLite {

    data class State(
        val status: String,          // new | learning | review | mastered
        val easeFactor: Float,       // 1.3 .. 3.0
        val intervalDays: Float,
        val repetitions: Int,
        val dueAtEpochMs: Long,
    )

    val newStartingState = State(
        status = "new", easeFactor = 2.5f, intervalDays = 0f,
        repetitions = 0, dueAtEpochMs = 0L,
    )

    private const val DAY_MS = 86_400_000L
    private const val MAX_INTERVAL = 180f

    /**
     * @param grade  true = correct/easy, false = wrong/again
     */
    fun next(state: State, grade: Boolean, nowEpochMs: Long): State {
        return if (grade) {
            val reps = state.repetitions + 1
            val interval = when {
                reps == 1 -> 1f
                reps == 2 -> 3f
                reps == 3 -> 7f
                else -> (state.intervalDays * state.easeFactor).coerceAtMost(MAX_INTERVAL)
            }
            val ease = (state.easeFactor + 0.05f).coerceAtMost(3.0f)
            val status = if (reps >= 5) "mastered" else "review"
            State(status, ease, interval, reps, nowEpochMs + (interval * DAY_MS).toLong())
        } else {
            val ease = (state.easeFactor - 0.2f).coerceAtLeast(1.3f)
            State("learning", ease, 1f, 0, nowEpochMs + DAY_MS)
        }
    }
}
