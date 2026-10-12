package com.kiaraen.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage

/**
 * Content pack access: check active version (1 tiny request), then fetch the
 * whole pack from Storage only when the local version is behind (docs/04).
 * The single pack parser lives in ContentRepository (PackTextParser) and
 * produces the compact [PLevel]…[PLink] models declared here.
 */
class ContentRemoteDataSource(private val client: SupabaseClient) {

    suspend fun activePackMeta(): ContentPackMetaDto? =
        client.from("content_packs")
            .select { filter { eq("is_active", true) }; limit(1) }
            .decodeSingleOrNull<ContentPackMetaDto>()

    suspend fun downloadPack(storagePath: String): String =
        client.storage.from("content-packs").downloadAuthenticated(storagePath).decodeToString()

    /* ── pack models (compact arrays → parsed shapes) ─────────────────────── */

    data class PLevel(val id: String, val title: String, val sort: Int)
    data class PCategory(val levelId: String, val key: String, val title: String, val icon: String, val sort: Int)
    data class PCollection(val slug: String, val title: String, val sort: Int)
    data class PWord(val word: String, val pos: String?, val meaningFa: String, val categoryIndex: Int, val difficulty: Int)
    data class PLink(val collectionIndex: Int, val wordIndex: Int)
}
