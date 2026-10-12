package com.kiaraen.app

import com.kiaraen.app.domain.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private val today = 20_000L

    @Test
    fun `empty history has no streak`() {
        assertEquals(StreakCalculator.Streaks(0, 0), StreakCalculator.compute(emptySet(), today))
    }

    @Test
    fun `single day yesterday`() {
        val s = StreakCalculator.compute(setOf(today - 1), today)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
    }

    @Test
    fun `consecutive run ending yesterday counts current`() {
        val days = setOf(today - 4, today - 3, today - 2, today - 1)
        val s = StreakCalculator.compute(days, today)
        assertEquals(4, s.current)
        assertEquals(4, s.longest)
    }

    @Test
    fun `streak broken by a gap`() {
        val days = setOf(today - 5, today - 4, today - 1)
        val s = StreakCalculator.compute(days, today)
        assertEquals(1, s.current)
        assertEquals(2, s.longest)
    }

    @Test
    fun `run ending two days ago is not current but is longest`() {
        val days = setOf(today - 6, today - 5, today - 4, today - 2)
        val s = StreakCalculator.compute(days, today)
        assertEquals(0, s.current)
        assertEquals(3, s.longest)
    }

    @Test
    fun `activity today counts`() {
        val s = StreakCalculator.compute(setOf(today - 1, today), today)
        assertEquals(2, s.current)
    }

    @Test
    fun `unordered input is handled`() {
        val days = setOf(today - 1, today - 3, today - 2)
        val s = StreakCalculator.compute(days, today)
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
    }
}
