package com.kiaraen.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.download
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Content pack access: check active version (1 tiny request), then fetch the
 * whole pack from Storage only when the local version is behind (docs/04).
 */
class ContentRemoteDataSource(private val client: SupabaseClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun activePackMeta(): ContentPackMetaDto? =
        client.from("content_packs")
            .select { eq("is_active", true); limit(1) }
            .decodeSingleOrNull<ContentPackMetaDto>()

    suspend fun downloadPack(storagePath: String): ParsedPack =
        parsePack(client.storage.from("content-packs").download(storagePath).decodeToString())

    /* ── pack parsing (compact arrays → parsed models) ────────────────────── */

    data class ParsedPack(
        val version: Int,
        val levels: List<PLevel>,
        val categories: List<PCategory>,
        val collections: List<PCollection>,
        val words: List<PWord>,
        val links: List<PLink>,
    )

    data class PLevel(val id: String, val title: String, val sort: Int)
    data class PCategory(val levelId: String, val key: String, val title: String, val icon: String, val sort: Int)
    data class PCollection(val slug: String, val title: String, val sort: Int)
    data class PWord(val word: String, val pos: String?, val meaningFa: String, val categoryIndex: Int, val difficulty: Int)
    data class PLink(val collectionIndex: Int, val wordIndex: Int)

    private fun parsePack(text: String): ParsedPack {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["v"]!!.jsonPrimitive.content.toInt()

        val levels = root["lvls"]!!.jsonArray.mapIndexed { i, e ->
            val o = e.jsonObject
            PLevel(
                o["id"]!!.jsonPrimitive.content,
                o["t"]!!.jsonPrimitive.content,
                o["s"]?.jsonPrimitive?.content?.toInt() ?: i,
            )
        }
        val lvlIndexOf = levels.withIndex().associate { (i, l) -> l.id to i }

        val cats = root["cats"]!!.jsonArray.mapIndexed { i, e ->
            val o = e.jsonObject
            PCategory(
                levelId = o["lv"]!!.jsonPrimitive.content,
                key = o["k"]!!.jsonPrimitive.content,
                title = o["t"]!!.jsonPrimitive.content,
                icon = o["i"]?.jsonPrimitive?.content ?: "chat",
                sort = i,
            )
        }

        val colls = root["colls"]!!.jsonArray.mapIndexed { i, e ->
            val o = e.jsonObject
            PCollection(
                slug = o["s"]!!.jsonPrimitive.content,
                title = o["t"]!!.jsonPrimitive.content,
                sort = i,
            )
        }

        val wordRows = root["words"]!!.jsonArray
        val words = wordRows.mapIndexed { _, e ->
            val a = e.jsonArray
            val catIdx = a[3].jsonPrimitive.content.toInt()
            val lv = cats[catIdx].levelId
            PWord(
                word = a[0].jsonPrimitive.content,
                pos = a[1].jsonPrimitive.content.takeIf { it.isNotBlank() },
                meaningFa = a[2].jsonPrimitive.content,
                categoryIndex = catIdx,
                difficulty = (lvlIndexOf[lv] ?: 2) + 1,
            )
        }
        val links = mutableListOf<PLink>()
        wordRows.forEachIndexed { wi, e ->
            e.jsonArray[4].jsonArray.forEach { c ->
                links.add(PLink(collectionIndex = c.jsonPrimitive.content.toInt() - 1, wordIndex = wi))
            }
        }
        return ParsedPack(version, levels, cats, colls, words, links)
    }
}
