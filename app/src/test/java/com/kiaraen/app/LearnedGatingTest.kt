package com.kiaraen.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiaraen.app.data.local.KiaraDatabase
import com.kiaraen.app.data.remote.ProgressRemoteDataSource
import com.kiaraen.app.data.remote.SupabaseProvider
import com.kiaraen.app.data.repository.ProgressRepository
import com.kiaraen.app.domain.ReviewMode
import com.kiaraen.app.domain.ReviewResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The «یادگرفته» gate (v0.7.0):
 *   • reading/flashcards NEVER set learnedAt — words stay "learning";
 *   • every wrong answer lands in the ONE mistakes list;
 *   • completing a quiz promotes its correct answers and clears their mistakes;
 *   • an abandoned quiz (hearts out → no completeQuiz call) promotes nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class LearnedGatingTest {

    private lateinit var db: KiaraDatabase
    private lateinit var repo: ProgressRepository
    private val uid = "u"
    private var t = 1_760_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            KiaraDatabase::class.java,
        ).allowMainThreadQueries().build()
        // production wiring — the remote is lazy and never called by these tests
        SupabaseProvider.init(ApplicationProvider.getApplicationContext())
        repo = ProgressRepository(db, ProgressRemoteDataSource(SupabaseProvider.client))
    }

    @After
    fun tearDown() = db.close()

    private fun nextTime(): Long {
        t += 1_000
        return t
    }

    @Test
    fun `flashcard reading never grants learned`() = runBlocking {
        val dao = db.progressDao()
        repo.recordReview(uid, 1, ReviewResult.CORRECT, ReviewMode.FLASHCARD, now = nextTime())
        val p = dao.progressOf(uid, 1)!!
        assertEquals("learning", p.status)
        assertNull(p.learnedAt)
        assertEquals(0, dao.learnedCountOnce(uid))
    }

    @Test
    fun `even four flashcard corrects stay learning`() = runBlocking {
        val dao = db.progressDao()
        repeat(4) { repo.recordReview(uid, 1, ReviewResult.CORRECT, ReviewMode.FLASHCARD, now = nextTime()) }
        val p = dao.progressOf(uid, 1)!!
        assertEquals("learning", p.status)
        assertNull(p.learnedAt)
        assertEquals(4, p.repetitions)   // SRS math kept — completion will use it
    }

    @Test
    fun `already learned words keep progressing via review`() = runBlocking {
        val dao = db.progressDao()
        // 3 flashcard corrects → reps=3, still gated at learning
        repeat(3) { repo.recordReview(uid, 1, ReviewResult.CORRECT, ReviewMode.FLASHCARD, now = nextTime()) }
        assertEquals("learning", dao.progressOf(uid, 1)!!.status)

        // completing a quiz unlocks: reps=3 → review, learnedAt set
        repo.completeQuiz(uid, listOf(1), now = nextTime())
        val p = dao.progressOf(uid, 1)!!
        assertEquals("review", p.status)
        assertNotNull(p.learnedAt)

        // one more correct → reps=4 → the SrsLite mastered contract holds
        repo.recordReview(uid, 1, ReviewResult.CORRECT, ReviewMode.MANUAL, now = nextTime())
        assertEquals("mastered", dao.progressOf(uid, 1)!!.status)
    }

    @Test
    fun `completeQuiz promotes correct answers and clears their mistakes`() = runBlocking {
        val dao = db.progressDao()
        // user read word 1 (flashcard), then made a mistake on word 2 somewhere
        repo.recordReview(uid, 1, ReviewResult.CORRECT, ReviewMode.FLASHCARD, now = nextTime())
        repo.recordReview(uid, 2, ReviewResult.WRONG, ReviewMode.QUIZ, now = nextTime())
        assertEquals(1, dao.mistakeWordIds(uid, 10).size)

        // completed quiz: word 1 correct, word 2 still wrong in THIS run
        val promoted = repo.completeQuiz(uid, listOf(1), now = nextTime())
        assertEquals(1, promoted)

        val p1 = dao.progressOf(uid, 1)!!
        assertTrue(p1.status == "review" || p1.status == "mastered")
        assertNotNull(p1.learnedAt)
        assertEquals(1, dao.learnedCountOnce(uid))
        // word 2's mistake was NOT part of this completed quiz → it stays listed
        assertEquals(listOf(2L), dao.mistakeWordIds(uid, 10))
    }

    @Test
    fun `wrong answers land in one place and survive until a completed quiz`() = runBlocking {
        val dao = db.progressDao()
        repo.recordReview(uid, 10, ReviewResult.WRONG, ReviewMode.FLASHCARD, now = nextTime())
        repo.recordReview(uid, 10, ReviewResult.WRONG, ReviewMode.MANUAL, now = nextTime())
        repo.recordReview(uid, 11, ReviewResult.WRONG, ReviewMode.QUIZ, now = nextTime())

        val mistakes = dao.mistakeWordIds(uid, 20)
        assertEquals(setOf(10L, 11L), mistakes.toSet())
        val row = dao.mistakeOf(uid, 10)!!
        assertEquals(2, row.wrongCount)   // incremented, not duplicated

        // word 10 answered CORRECTLY inside a completed quiz → mistake cleared,
        // word learned; word 11 stays listed for «مرور اشتباهات»
        repo.completeQuiz(uid, listOf(10), now = nextTime())
        assertEquals(listOf(11L), dao.mistakeWordIds(uid, 20))

        // the correct-in-completed-quiz answer granted learned for 10 only
        assertEquals(1, dao.learnedCountOnce(uid))
        val p = dao.progressOf(uid, 11)!!
        assertEquals("learning", p.status)
    }

    @Test
    fun `abandoned quiz promotes nothing`() = runBlocking {
        val dao = db.progressDao()
        // hearts ran out: only per-answer recordReview happened (no completeQuiz)
        repo.recordReview(uid, 5, ReviewResult.CORRECT, ReviewMode.QUIZ, now = nextTime())
        repo.recordReview(uid, 6, ReviewResult.WRONG, ReviewMode.QUIZ, now = nextTime())
        assertNull(dao.progressOf(uid, 5)!!.learnedAt)
        assertEquals("learning", dao.progressOf(uid, 5)!!.status)
        assertEquals(0, dao.learnedCountOnce(uid))
        assertEquals(listOf(6L), dao.mistakeWordIds(uid, 20))
    }

    @Test
    fun `clearUserData also clears mistakes and hand-off migrates them`() = runBlocking {
        val dao = db.progressDao()
        val local = "local"   // guest rows are written under the local id
        repo.recordReview(local, 7, ReviewResult.WRONG, ReviewMode.QUIZ, now = nextTime())
        assertEquals(1, dao.mistakeWordIds(local, 10).size)
        dao.migrateLocalData("acc")   // guest hand-off on sign-up
        // after migration the mistake belongs to the account
        assertEquals(0, dao.mistakeWordIds(local, 10).size)
        assertEquals(1, db.progressDao().mistakeWordIds("acc", 10).size)
        db.progressDao().clearUserData("acc")
        assertEquals(0, db.progressDao().mistakeWordIds("acc", 10).size)
    }
}
