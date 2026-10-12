package com.kiaraen.app.domain

/**
 * Streak math over distinct active days (epoch day numbers, e.g. from review_log).
 * Pure + unit tested; the server trigger in 00004_stats_triggers.sql implements
 * the same rules — parity is asserted in docs/07-testing.md.
 */
object StreakCalculator {

    data class Streaks(val current: Int, val longest: Int)

    /**
     * @param activeDays distinct epoch-days (any order)
     * @param today      the epoch-day to anchor "current" to
     */
    fun compute(activeDays: Set<Long>, today: Long): Streaks {
        if (activeDays.isEmpty()) return Streaks(0, 0)

        val sorted = activeDays.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] - sorted[i - 1] == 1L) run + 1 else 1
            if (run > longest) longest = run
        }

        // current = the run that reaches today or yesterday
        val last = sorted.last()
        var current = 0
        if (last == today || last == today - 1) {
            current = 1
            var i = sorted.size - 1
            while (i > 0 && sorted[i] - sorted[i - 1] == 1L) {
                current++
                i--
            }
        }
        return Streaks(current, longest)
    }
}
