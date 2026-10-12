package com.kiaraen.app.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kiaraen.app.data.local.ContentDao
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.data.sync.SyncWorker
import com.kiaraen.app.domain.ReviewMode
import com.kiaraen.app.domain.ReviewResult
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Default quiz generator + runner. quizRef formats:
 *   "review"                → due SRS cards (fallback: random category drill)
 *   "level:A1"              → default quiz of a level
 *   "collection:<id>"       → collection drill
 * Questions are generated ON DEVICE from the cached pack → zero server cost.
 */
data class Question(val wordId: Long, val prompt: String, val options: List<String>, val answer: String)

@Composable
fun QuizScreen(quizRef: String, onExit: () -> Unit) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentDao = container.database.contentDao()
    val progressRepo = container.progressRepository

    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    val startedAt = remember { System.currentTimeMillis() }

    LaunchedEffect(quizRef) { questions = buildQuiz(quizRef, contentDao) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            questions.isEmpty() -> {
                Text("سوالی موجود نیست — کمی بعد دوباره تلاش کنید.")
                Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
            }
            index >= questions.size -> {
                Text("پایان آزمون!", style = MaterialTheme.typography.titleLarge)
                Text("امتیاز: $score از ${questions.size}")
                LaunchedEffect(Unit) {
                    progressRepo.recordQuizAttempt(
                        userId = uid, quizRef = quizRef, score = score,
                        total = questions.size, durationMs = System.currentTimeMillis() - startedAt,
                    )
                    SyncWorker.enqueueOneShot(context)
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
                            val correct = option == q.answer
                            if (correct) score++
                            scope.launch {
                                progressRepo.recordReview(
                                    userId = uid, wordId = q.wordId,
                                    result = if (correct) ReviewResult.CORRECT else ReviewResult.WRONG,
                                    mode = ReviewMode.QUIZ, quizRef = quizRef,
                                )
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

suspend fun buildQuiz(quizRef: String, dao: ContentDao): List<Question> {
    val pool: List<WordEntity> = when {
        quizRef.startsWith("level:") ->
            dao.wordsOfLevelOnce(quizRef.removePrefix("level:").substringBefore(":"))
        quizRef.startsWith("collection:") ->
            dao.wordsByIds(
                dao.wordIdsOfCollection(
                    quizRef.removePrefix("collection:").toLongOrNull() ?: return emptyList(),
                ),
            )
        else -> emptyList()   // "review" SRS mode lands in Phase 3 (docs roadmap)
    }
    if (pool.size < 4) return emptyList()

    val rnd = Random(quizRef.hashCode())
    return pool.shuffled(rnd).take(10).mapNotNull { target ->
        val answer = target.meaningFa ?: return@mapNotNull null
        val distractors = pool.filter { it.id != target.id }
            .mapNotNull { it.meaningFa }.distinct()
            .shuffled(rnd).take(3)
        if (distractors.size < 3) return@mapNotNull null
        Question(
            wordId = target.id,
            prompt = target.word,
            options = (distractors + answer).shuffled(rnd),
            answer = answer,
        )
    }
}
