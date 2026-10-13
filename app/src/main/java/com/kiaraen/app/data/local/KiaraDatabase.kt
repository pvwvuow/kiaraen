package com.kiaraen.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        WordBookmarkEntity::class,
    ],
    version = 2,
    exportSchema = true,   // schemas/ is committed (docs/07)
)
abstract class KiaraDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun progressDao(): ProgressDao

    companion object {
        /**
         * v2: word_bookmarks (ذخیره‌شده‌ها). User data must survive — an
         * explicit migration, NOT destructive fallback.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `word_bookmarks` (" +
                        "`userId` TEXT NOT NULL, `wordId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`userId`, `wordId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_word_bookmarks_userId_createdAt` " +
                        "ON `word_bookmarks` (`userId`, `createdAt`)",
                )
            }
        }

        @Volatile private var instance: KiaraDatabase? = null

        fun get(context: Context): KiaraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, KiaraDatabase::class.java, "kiaraen.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
