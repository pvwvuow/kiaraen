package com.kiaraen.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

/**
 * Progress push/pull. All requests carry the user's JWT automatically
 * (GoTrue plugin) → PostgREST enforces RLS; a client can only ever touch
 * its own rows even if the code tried otherwise.
 */
class ProgressRemoteDataSource(private val client: SupabaseClient) {

    suspend fun pushProgress(rows: List<ProgressDto>) {
        if (rows.isEmpty()) return
        client.from("user_word_progress")
            .upsert(rows, onConflict = "user_id,word_id")
    }

    suspend fun pushReviewLogs(rows: List<ReviewLogDto>) {
        if (rows.isEmpty()) return
        // ignoreDuplicates → a retried batch with the same client_event_id is a no-op
        client.from("review_log").upsert(rows, onConflict = "user_id,client_event_id", ignoreDuplicates = true)
    }

    suspend fun pushQuizAttempts(rows: List<QuizAttemptDto>) {
        if (rows.isEmpty()) return
        client.from("quiz_attempts").upsert(rows, onConflict = "user_id,client_event_id", ignoreDuplicates = true)
    }

    suspend fun pullProgress(userId: String): List<ProgressDto> =
        client.from("user_word_progress")
            .select { filter { eq("user_id", userId) } }
            .decodeList()

    suspend fun pullStats(userId: String): UserStatsDto? =
        client.from("user_stats")
            .select { filter { eq("user_id", userId) }; limit(1) }
            .decodeSingleOrNull<UserStatsDto>()
}
