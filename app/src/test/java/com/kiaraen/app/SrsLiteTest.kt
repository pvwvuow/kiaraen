package com.kiaraen.app

import com.kiaraen.app.domain.SrsLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsLiteTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `first correct answer schedules 1 day`() {
        val next = SrsLite.next(SrsLite.newStartingState, grade = true, nowEpochMs = now)
        assertEquals(1, next.repetitions)
        assertEquals(1f, next.intervalDays)
        assertEquals("review", next.status)
        assertEquals(now + 86_400_000L, next.dueAtEpochMs)
    }

    @Test
    fun `interval grows by ease factor`() {
        var s = SrsLite.newStartingState
        repeat(4) { s = SrsLite.next(s, true, now) }
        // reps 1,2,3 are fixed (1,3,7); reps 4 = 7 * 2.65 ≈ 18.5
        assertTrue("interval was ${s.intervalDays}", s.intervalDays in 17f..20f)
        assertEquals("mastered", s.status)
    }

    @Test
    fun `wrong answer resets and lowers ease`() {
        var s = SrsLite.newStartingState
        repeat(3) { s = SrsLite.next(s, true, now) }
        val easeBefore = s.easeFactor
        s = SrsLite.next(s, false, now)
        assertEquals(0, s.repetitions)
        assertEquals("learning", s.status)
        assertEquals(1f, s.intervalDays)
        assertTrue(s.easeFactor < easeBefore)
    }

    @Test
    fun `ease never drops below 1_3`() {
        var s = SrsLite.newStartingState
        repeat(20) { s = SrsLite.next(s, false, now) }
        assertEquals(1.3f, s.easeFactor)
    }

    @Test
    fun `interval capped at 180 days`() {
        var s = SrsLite.newStartingState
        repeat(15) { s = SrsLite.next(s, true, now) }
        assertTrue(s.intervalDays <= 180f)
    }
}
