package com.kiaraen.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {

    // ── pack installation (atomic replace) ──────────────────────────────────
    @Transaction
    suspend fun installPack(
        levels: List<LevelEntity>,
        categories: List<CategoryEntity>,
        words: List<WordEntity>,
        collections: List<CollectionEntity>,
        links: List<CollectionWordEntity>,
        meta: ContentMetaEntity,
    ) {
        clearContent()
        insertLevels(levels)
        insertCategories(categories)
        insertWords(words)
        insertCollections(collections)
        insertLinks(links)
        insertMeta(meta)
    }

    @Query("DELETE FROM levels")
    suspend fun clearLevels()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM words")
    suspend fun clearWords()

    @Query("DELETE FROM collections")
    suspend fun clearCollections()

    @Query("DELETE FROM collection_words")
    suspend fun clearLinks()

    @Query("DELETE FROM content_meta")
    suspend fun clearMeta()

    @Transaction
    suspend fun clearContent() {
        clearLinks(); clearWords(); clearCategories(); clearCollections(); clearLevels(); clearMeta()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevels(items: List<LevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(items: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(items: List<CollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(items: List<CollectionWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(meta: ContentMetaEntity)

    @Query("SELECT version FROM content_meta WHERE id = 1")
    suspend fun contentVersion(): Int?

    @Query("SELECT COUNT(*) FROM words")
    suspend fun wordCount(): Int

    // ── queries used by the UI ───────────────────────────────────────────────
    @Query("SELECT * FROM levels ORDER BY sort")
    fun levels(): Flow<List<LevelEntity>>

    @Query("SELECT * FROM categories WHERE levelId = :levelId ORDER BY sort")
    fun categoriesOf(levelId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM words WHERE categoryId = :categoryId ORDER BY id")
    fun wordsOf(categoryId: Long): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE categoryId = :categoryId ORDER BY id LIMIT 300")
    suspend fun wordsOfCategoryOnce(categoryId: Long): List<WordEntity>

    @Query("SELECT * FROM words WHERE levelId = :levelId ORDER BY id LIMIT 300")
    suspend fun wordsOfLevelOnce(levelId: String): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE levelId = :levelId")
    fun wordCountOf(levelId: String): Flow<Int>

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun wordsByIds(ids: List<Long>): List<WordEntity>

    @Query("SELECT * FROM words WHERE word LIKE :query || '%' ORDER BY word LIMIT 50")
    fun searchWords(query: String): Flow<List<WordEntity>>

    @Query("SELECT * FROM collections ORDER BY sort")
    fun collections(): Flow<List<CollectionEntity>>

    @Query("SELECT COUNT(*) FROM collection_words WHERE collectionId = :collectionId")
    fun collectionSize(collectionId: Long): Flow<Int>

    @Query("SELECT wordId FROM collection_words WHERE collectionId = :collectionId")
    suspend fun wordIdsOfCollection(collectionId: Long): List<Long>

    @Query("SELECT c.id FROM collections c WHERE c.slug = :slug")
    suspend fun collectionIdBySlug(slug: String): Long?

    @Query("SELECT categoryId FROM words WHERE id = :wordId")
    suspend fun categoryIdOfWord(wordId: Long): Long?
}
