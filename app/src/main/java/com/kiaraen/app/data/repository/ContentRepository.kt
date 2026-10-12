package com.kiaraen.app.data.repository

import android.content.Context
import com.kiaraen.app.data.local.CategoryEntity
import com.kiaraen.app.data.local.CollectionEntity
import com.kiaraen.app.data.local.CollectionWordEntity
import com.kiaraen.app.data.local.ContentDao
import com.kiaraen.app.data.local.ContentMetaEntity
import com.kiaraen.app.data.local.KiaraDatabase
import com.kiaraen.app.data.local.LevelEntity
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.data.remote.ContentRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Offline-first content access:
 *   1. first launch  → install the pack bundled in assets (works fully offline)
 *   2. on each sync  → check ONE tiny row (content_packs) and only download a
 *      new pack when the local version is behind
 */
class ContentRepository(
    private val context: Context,
    private val db: KiaraDatabase,
    private val remote: ContentRemoteDataSource,
) {
    private val dao: ContentDao = db.contentDao()

    /* ── bootstrapping ─────────────────────────────────────────────────────── */

    suspend fun bootstrapIfEmpty() = withContext(Dispatchers.IO) {
        if ((dao.contentVersion() ?: 0) == 0 || dao.wordCount() == 0) {
            installPackText(readBundledPack(), source = "bundled")
        }
    }

    private fun readBundledPack(): String =
        context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }

    suspend fun refreshIfAvailable(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val meta = remote.activePackMeta() ?: return@runCatching
            val local = dao.contentVersion() ?: 0
            if (meta.version > local) {
                installPackText(remote.downloadPack(meta.storagePath), source = "remote")
            }
        }
    }

    private suspend fun installPackText(text: String, source: String) {
        val parsed = PackTextParser.parse(text)
        // Content mirror uses EXPLICIT ids derived from pack indexes → installs
        // are deterministic and idempotent (links stay valid across refreshes).
        dao.installPack(
            levels = parsed.levels.map { LevelEntity(it.id, it.title, it.sort) },
            categories = parsed.categories.mapIndexed { i, it ->
                CategoryEntity(id = i + 1L, levelId = it.levelId, key = it.key, title = it.title, icon = it.icon, sort = it.sort)
            },
            words = parsed.words.mapIndexed { wi, it ->
                WordEntity(
                    id = wi + 1L,
                    word = it.word, pos = it.pos, meaningFa = it.meaningFa,
                    levelId = parsed.categories[it.categoryIndex].levelId,
                    categoryId = it.categoryIndex + 1L,
                    difficulty = it.difficulty,
                )
            },
            collections = parsed.collections.mapIndexed { i, it ->
                CollectionEntity(id = i + 1L, slug = it.slug, title = it.title, sort = it.sort)
            },
            links = parsed.links.map {
                CollectionWordEntity(collectionId = it.collectionIndex + 1L, wordId = it.wordIndex + 1L)
            },
            meta = ContentMetaEntity(
                id = 1, version = parsed.version, wordCount = parsed.words.size,
                syncedAt = System.currentTimeMillis(),
            ),
        )
    }

    /* ── read APIs (all served from Room — zero network on every open) ────── */

    fun levels(): Flow<List<com.kiaraen.app.data.local.LevelEntity>> = dao.levels()
    fun categoriesOf(levelId: String) = dao.categoriesOf(levelId)
    fun wordsOf(categoryId: Long) = dao.wordsOf(categoryId)
    fun wordCountOf(levelId: String) = dao.wordCountOf(levelId)
    fun searchWords(q: String) = dao.searchWords(q)
    fun collections() = dao.collections()
    fun collectionSize(id: Long) = dao.collectionSize(id)

    suspend fun wordsOfCollection(collectionId: Long) = dao.wordIdsOfCollection(collectionId).let { dao.wordsByIds(it) }
    suspend fun collectionIdBySlug(slug: String) = dao.collectionIdBySlug(slug)
    suspend fun categoryIdOfWord(wordId: Long) = dao.categoryIdOfWord(wordId)

    companion object {
        const val BUNDLED_ASSET = "content/bootstrap-pack-v1.json"
    }
}

/** Minimal parser for the compact pack format (see scripts/build_content_pack.py). */
private object PackTextParser {
    private val json = Json { ignoreUnknownKeys = true }

    data class Parsed(
        val version: Int,
        val levels: List<ContentRemoteDataSource.PLevel>,
        val categories: List<ContentRemoteDataSource.PCategory>,
        val collections: List<ContentRemoteDataSource.PCollection>,
        val words: List<ContentRemoteDataSource.PWord>,
        val links: List<ContentRemoteDataSource.PLink>,
    )

    fun parse(text: String): Parsed {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["v"]!!.jsonPrimitive.content.toInt()
        val levels = root["lvls"]!!.jsonArray.mapIndexed { i, e ->
            val o = e.jsonObject
            ContentRemoteDataSource.PLevel(
                o["id"]!!.jsonPrimitive.content, o["t"]!!.jsonPrimitive.content,
                o["s"]?.jsonPrimitive?.content?.toInt() ?: i,
            )
        }
        val cats = root["cats"]!!.jsonArray.mapIndexed { i, e ->
            val o = e.jsonObject
            ContentRemoteDataSource.PCategory(
                o["lv"]!!.jsonPrimitive.content, o["k"]!!.jsonPrimitive.content,
                o["t"]!!.jsonPrimitive.content,
                o["i"]?.jsonPrimitive?.content ?: "chat", i,
            )
        }
        val colls = root["colls"]!!.jsonArray.mapIndexed { i, e ->
            val o = e.jsonObject
            ContentRemoteDataSource.PCollection(
                o["s"]!!.jsonPrimitive.content, o["t"]!!.jsonPrimitive.content, i,
            )
        }
        val lvlIdx = levels.withIndex().associate { (i, l) -> l.id to i }
        val words = root["words"]!!.jsonArray.map { e ->
            val a = e.jsonArray
            val catIdx = a[3].jsonPrimitive.content.toInt()
            ContentRemoteDataSource.PWord(
                word = a[0].jsonPrimitive.content,
                pos = a[1].jsonPrimitive.content.takeIf { it.isNotBlank() },
                meaningFa = a[2].jsonPrimitive.content,
                categoryIndex = catIdx,
                difficulty = (lvlIdx[cats[catIdx].levelId] ?: 2) + 1,
            )
        }
        val links = mutableListOf<ContentRemoteDataSource.PLink>()
        root["words"]!!.jsonArray.forEachIndexed { wi, e ->
            e.jsonArray[4].jsonArray.forEach {
                links.add(ContentRemoteDataSource.PLink(it.jsonPrimitive.content.toInt() - 1, wi))
            }
        }
        return Parsed(version, levels, cats, colls, words, links)
    }
}
