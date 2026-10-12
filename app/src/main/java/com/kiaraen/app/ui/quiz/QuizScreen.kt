package com.kiaraen.app.ui.quiz

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kiaraen.app.data.local.ContentDao
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.data.sync.SyncWorker
import com.kiaraen.app.domain.ReviewMode
import com.kiaraen.app.domain.ReviewResult
import com.kiaraen.app.ui.common.LOCAL_USER_ID
import com.kiaraen.app.ui.common.LocalAppContainer
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Default quiz generator + runner. quizRef formats:
 *   "review"                → due SRS cards (fallback: random drill)
 *   "level:A1"              → default quiz of a level
 *   "collection:<id>"       → collection drill
 * Questions are generated ON DEVICE from the cached pack → zero server cost.
 * Nothing in here may crash the app: generation and every write are guarded.
 */
data class Question(val wordId: Long, val prompt: String, val options: List<String>, val answer: String)

const val REVIEW_QUIZ = "review"
private const val TAG = "QuizScreen"
private const val QUIZ_SIZE = 10
private const val RANDOM_POOL = 60

@Composable
fun QuizScreen(quizRef: String, onExit: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentDao = container.database.contentDao()
    val progressDao = container.database.progressDao()
    val progressRepo = container.progressRepository

    var ownerId by remember { mutableStateOf(LOCAL_USER_ID) }
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    val startedAt = remember { System.currentTimeMillis() }

    LaunchedEffect(quizRef) {
        // resolve the owner FIRST so answers are never recorded under a stale id
        val uid = runCatching { container.authRepository.currentUserId() }.getOrNull()
            ?: LOCAL_USER_ID
        ownerId = uid
        questions = runCatching {
            val due = if (quizRef == REVIEW_QUIZ) {
                progressDao.dueQueue(uid, System.currentTimeMillis(), QUIZ_SIZE).map { it.wordId }
            } else {
                emptyList()
            }
            buildQuiz(quizRef, contentDao, due)
        }.getOrElse {
            Log.e(TAG, "quiz generation failed for $quizRef", it)
            emptyList()
        }
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            questions.isEmpty() -> {
                Text("سوالی موجود نیست — کمی بعد دوباره تلاش کنید.")
                Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
            }
            index >= questions.size -> {
                Text("پایان آزمون!", style = MaterialTheme.typography.titleLarge)
                Text("امتیاز: $score از ${questions.size}")
                LaunchedEffect(Unit) {
                    runCatching {
                        progressRepo.recordQuizAttempt(
                            userId = ownerId, quizRef = quizRef, score = score,
                            total = questions.size, durationMs = System.currentTimeMillis() - startedAt,
                        )
                    }.onFailure { Log.e(TAG, "recordQuizAttempt failed", it) }
                    runCatching { SyncWorker.enqueueOneShot(context) }
                }
                Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
            }
            else -> {
                val q = questions[index]
                Text("سوال ${index + 1} از ${questions.size}", style = MaterialTheme.typography.labelMedium)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(q.prompt, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                q.options.forEach { option ->
                    Button(
                        onClick = {
                            if (index >= questions.size) return@Button
                            val correct = option == q.answer
                            if (correct) score++
                            val uid = ownerId
                            scope.launch {
                                runCatching {
                                    progressRepo.recordReview(
                                        userId = uid, wordId = q.wordId,
                                        result = if (correct) ReviewResult.CORRECT else ReviewResult.WRONG,
                                        mode = ReviewMode.QUIZ, quizRef = quizRef,
                                    )
                                }.onFailure { Log.e(TAG, "recordReview failed", it) }
                            }
                            index++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(option) }
                }
            }
        }
    }
}

/* ── quiz generation (pure, testable) ──────────────────────────────────────── */

suspend fun buildQuiz(
    quizRef: String,
    dao: ContentDao,
    dueWordIds: List<Long> = emptyList(),
    rnd: Random = Random.Default,
): List<Question> {
    val targets: List<WordEntity>
    val pool: List<WordEntity>
    when {
        quizRef.startsWith("level:") -> {
            pool = dao.wordsOfLevelOnce(quizRef.removePrefix("level:").substringBefore(":"))
            targets = pool
        }
        quizRef.startsWith("collection:") -> {
            val id = quizRef.removePrefix("collection:").toLongOrNull() ?: return emptyList()
            pool = dao.wordsOfCollectionOnce(id)
            targets = pool
        }
        quizRef == REVIEW_QUIZ -> {
            val due = if (dueWordIds.isEmpty()) emptyList() else dao.wordsByIds(dueWordIds.take(500))
            val random = dao.randomWords(RANDOM_POOL)
            targets = due.ifEmpty { random }
            pool = (due + random).distinctBy { it.id }
        }
        else -> return emptyList()
    }
    if (pool.size < 4) return emptyList()

    return targets.shuffled(rnd).take(QUIZ_SIZE).mapNotNull { target ->
        val answer = target.meaningFa?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val distractors = pool.asSequence()
            .filter { it.id != target.id }
            .mapNotNull { it.meaningFa?.takeIf { m -> m.isNotBlank() && m != answer } }
            .distinct()
            .toList()
            .shuffled(rnd)
            .take(3)
        if (distractors.size < 3) return@mapNotNull null
        Question(
            wordId = target.id,
            prompt = target.word,
            options = (distractors + answer).shuffled(rnd),
            answer = answer,
        )
    }
}
