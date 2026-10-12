package com.kiaraen.app.domain

/**
 * Pure sync decision rules — the client side of the server fences
 * (00005_sync_guards.sql). Kept pure so both are unit-testable.
 */
object SyncRules {

    /**
     * Last-Write-Wins: a pulled (remote) state row replaces the local row only
     * when its device timestamp is strictly newer. Ties keep the local copy
     * (the server guard mirrors this: `<=` rejects).
     */
    fun remoteWins(localClientUpdatedAtMs: Long, remoteClientUpdatedAtMs: Long): Boolean =
        remoteClientUpdatedAtMs > localClientUpdatedAtMs

    /**
     * Event idempotency: every review/attempt carries a client-generated UUID;
     * retries reuse the SAME id, so unique (user_id, client_event_id) on the
     * server and the local unique index both collapse replays.
     */
    fun isReplay(existingEventIds: Set<String>, candidate: String): Boolean =
        candidate in existingEventIds
}
