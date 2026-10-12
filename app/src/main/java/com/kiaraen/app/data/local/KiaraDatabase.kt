package com.kiaraen.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LevelEntity::class,
        CategoryEntity::class,
        WordEntity::class,
        CollectionEntity::class,
        CollectionWordEntity::class,
        ContentMetaEntity::class,
        WordProgressEntity::class,
        ReviewLogEntity::class,
        QuizAttemptEntity::class,
        UserStatsEntity::class,
    ],
    version = 1,
    exportSchema = true,   // schemas/ is committed → migration tests in CI (docs/07)
)
abstract class KiaraDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile private var instance: KiaraDatabase? = null

        fun get(context: Context): KiaraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, KiaraDatabase::class.java, "kiaraen.db",
                )
                    // content tables are rebuilt from packs; destructive fallback keeps
                    // schema migrations simple until version 2 (docs/06-ci-cd.md)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
