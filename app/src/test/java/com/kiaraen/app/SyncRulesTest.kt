package com.kiaraen.app

import com.kiaraen.app.domain.SyncRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {

    @Test
    fun `strictly newer remote wins`() {
        assertTrue(SyncRules.remoteWins(localClientUpdatedAtMs = 1_000, remoteClientUpdatedAtMs = 2_000))
    }

    @Test
    fun `tie keeps local (server guard mirrors this)`() {
        assertFalse(SyncRules.remoteWins(localClientUpdatedAtMs = 2_000, remoteClientUpdatedAtMs = 2_000))
    }

    @Test
    fun `older remote never overwrites local`() {
        assertFalse(SyncRules.remoteWins(localClientUpdatedAtMs = 5_000, remoteClientUpdatedAtMs = 1_000))
    }

    @Test
    fun `replay detection by client event id`() {
        val seen = setOf("evt-1", "evt-2")
        assertTrue(SyncRules.isReplay(seen, "evt-1"))
        assertFalse(SyncRules.isReplay(seen, "evt-3"))
    }
}
