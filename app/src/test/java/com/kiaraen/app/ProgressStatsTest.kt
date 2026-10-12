package com.kiaraen.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiaraen.app.data.local.KiaraDatabase
import com.kiaraen.app.data.local.QuizAttemptEntity
import com.kiaraen.app.data.local.ReviewLogEntity
import com.kiaraen.app.data.local.WordProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Local stats + guest hand-off on a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProgressStatsTest {

    private lateinit var db: KiaraDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            KiaraDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `recompute fills every total`() = runBlocking {
        val dao = db.progressDao()
        val t = 1_760_000_000_000L
        val today = t / DAY
        dao.upsertProgress(
            listOf(
                WordProgressEntity(userId = "u", wordId = 1, status = "review", learnedAt = t),
                WordProgressEntity(userId = "u", wordId = 2, status = "mastered", learnedAt = t),
                WordProgressEntity(userId = "u", wordId = 3, status = "learning"),
            ),
        )
        dao.insertReviewLog(log("a", "correct", 10, t))
        dao.insertReviewLog(log("b", "wrong", 2, t))
        dao.insertReviewLog(log("c", "easy", 10, t))
        dao.insertQuizAttempt(
            QuizAttemptEntity(
                userId = "u", quizRef = "review", score = 2, total = 3,
                durationMs = 1, clientEventId = "q", createdAt = t,
            ),
        )

        dao.recomputeLocalStats("u", today, 0)
        val s = dao.statsOnce("u")!!

        assertEquals(3, s.totalReviews)
        assertEquals(2, s.totalCorrect)
        assertEquals(2, s.totalLearned)
        assertEquals(1, s.totalMastered)
        assertEquals(22, s.totalXp)
        assertEquals(1, s.totalQuizAttempts)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun `active days follow the device offset`() = runBlocking {
        val dao = db.progressDao()
        // 22:00 UTC = 01:30 next day in Tehran (+3:30)
        val lateUtc = 1_760_047_200_000L
        val tehran = 12_600_000L
        dao.insertReviewLog(log("x", "correct", 10, lateUtc))
        assertEquals(listOf(lateUtc / DAY), dao.activeDays("u", 0))
        assertEquals(listOf((lateUtc + tehran) / DAY), dao.activeDays("u", tehran))
        assertTrue((lateUtc + tehran) / DAY == lateUtc / DAY + 1)
    }

    @Test
    fun `guest hand-off survives a word the account already owns`() = runBlocking {
        val dao = db.progressDao()
        dao.upsertProgress(
            listOf(
                WordProgressEntity(userId = "local", wordId = 1, status = "learning"),
                WordProgressEntity(userId = "local", wordId = 2, status = "review"),
                WordProgressEntity(userId = "acc", wordId = 1, status = "mastered"),
            ),
        )
        dao.migrateLocalData("acc")   // used to throw SQLiteConstraintException

        assertEquals("mastered", dao.progressOf("acc", 1)!!.status)
        assertEquals("review", dao.progressOf("acc", 2)!!.status)
        assertNull(dao.progressOf("local", 1))
        assertTrue(dao.allProgress("local").isEmpty())
    }

    private fun log(id: String, result: String, xp: Int, at: Long) = ReviewLogEntity(
        userId = "u", wordId = 1, result = result, mode = "quiz",
        xp = xp, clientEventId = id, createdAt = at,
    )

    private companion object {
        const val DAY = 86_400_000L
    }
}
