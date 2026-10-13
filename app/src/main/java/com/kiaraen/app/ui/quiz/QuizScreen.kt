package com.kiaraen.app.ui.quiz

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.data.local.ContentDao
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.data.sync.SyncWorker
import com.kiaraen.app.domain.ReviewMode
import com.kiaraen.app.domain.ReviewResult
import com.kiaraen.app.ui.common.LOCAL_USER_ID
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.kiara.CapsuleBar
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.HeartsRow
import com.kiaraen.app.ui.kiara.OutlineButton
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Danger
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Lalezar
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.foundation.layout.widthIn

/**
 * آزمون — the approved quiz design: hearts, coin counter, puffy option cards
 * with ✓/✗ marks, and the trophy end panel. quizRef formats:
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
private const val COINS_PER_CORRECT = 5

/** Persian quiz title for a ref (نوار بالای صفحه آزمون). */
suspend fun quizTitle(ref: String, dao: ContentDao): String = when {
    ref == REVIEW_QUIZ -> "آزمون سریع"
    ref.startsWith("level:") -> "آزمون سطح ${ref.removePrefix("level:").substringBefore(":")}"
    ref.startsWith("collection:") ->
        ref.removePrefix("collection:").toLongOrNull()
            ?.let { id -> runCatching { dao.collections().first() }.getOrNull()?.firstOrNull { it.id == id }?.title }
            ?.let { "آزمون $it" }
            ?: "آزمون مجموعه"
    else -> "آزمون"
}

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
    var title by remember { mutableStateOf("آزمون") }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var picked by remember { mutableStateOf<String?>(null) }   // locked-in option
    var finished by remember { mutableStateOf(false) }
    val startedAt = remember { System.currentTimeMillis() }

    LaunchedEffect(quizRef) {
        // resolve the owner FIRST so answers are never recorded under a stale id
        val uid = runCatching { container.authRepository.currentUserId() }.getOrNull()
            ?: LOCAL_USER_ID
        ownerId = uid
        title = runCatching { quizTitle(quizRef, contentDao) }.getOrDefault("آزمون")
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

    fun finish() {
        if (finished) return
        finished = true
        scope.launch {
            runCatching {
                progressRepo.recordQuizAttempt(
                    userId = ownerId, quizRef = quizRef, score = score,
                    total = maxOf(index, 1), durationMs = System.currentTimeMillis() - startedAt,
                )
            }.onFailure { Log.e(TAG, "recordQuizAttempt failed", it) }
            runCatching { SyncWorker.enqueueOneShot(context) }
        }
    }

    Box(Modifier.fillMaxSize().background(com.kiaraen.app.ui.theme.Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 20.dp),
            ) {

                Spacer(Modifier.height(16.dp))

                when {
                    loading -> {
                        Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                            Text("داریم آماده می‌کنیم…", color = Muted, fontSize = 14.sp)
                        }
                    }
                    questions.isEmpty() -> {
                        Spacer(Modifier.height(120.dp))
                        EmptyPanel(onExit)
                    }
                    finished || index >= questions.size -> {
                        Spacer(Modifier.height(40.dp))
                        ResultPanel(
                            title = title,
                            score = score,
                            total = questions.size,
                            coins = score * COINS_PER_CORRECT,
                            onExit = onExit,
                            onRetry = {
                                index = 0; score = 0; lives = 3; picked = null
                                finished = false
                                scope.launch {
                                    questions = runCatching {
                                        buildQuiz(quizRef, contentDao)
                                    }.getOrDefault(questions)
                                }
                            },
                        )
                    }
                    else -> {
                        val q = questions[index]
                        // header: hearts (right in RTL) — title — coins
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeartsRow(lives = lives)
                            Spacer(Modifier.weight(1f))
                            Text(
                                title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Ink,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(faNum(score * COINS_PER_CORRECT), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Ink)
                                Spacer(Modifier.width(4.dp))
                                Image(painterResource(R.drawable.ki_a_coin), null, Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        CapsuleBar(fraction = index.toFloat() / questions.size, height = 9.dp)
                        Spacer(Modifier.height(22.dp))
                        Text(
                            "سوال ${faNum(index + 1)} از ${faNum(questions.size)}",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                        Spacer(Modifier.height(10.dp))
                        // prompt card
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Cream, RoundedCornerShape(26.dp))
                                .padding(vertical = 46.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    q.prompt,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Ink,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        q.options.forEach { option ->
                            val state = when {
                                picked == null -> OptionState.IDLE
                                option == q.answer -> OptionState.RIGHT
                                option == picked -> OptionState.WRONG
                                else -> OptionState.DIM
                            }
                            OptionCard(
                                text = option,
                                state = state,
                                enabled = picked == null,
                            ) {
                                picked = option
                                val correct = option == q.answer
                                if (correct) score++
                                else lives--
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
                                scope.launch {
                                    delay(850)
                                    if (lives <= 0 && !correct) {
                                        finish()
                                    } else {
                                        if (index + 1 >= questions.size) finish() else index++
                                        picked = null
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
        
            }
        }
    }
}

private enum class OptionState { IDLE, RIGHT, WRONG, DIM }

@Composable
private fun OptionCard(text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit) {
    val border = when (state) {
        OptionState.RIGHT -> Gold
        OptionState.WRONG -> Danger
        else -> Color(0xFFE7E2D4)
    }
    val fill = when (state) {
        OptionState.RIGHT -> Color(0xFFFFF6DC)
        OptionState.WRONG -> Color(0xFFFDF0EE)
        else -> Cream
    }
    val alpha by animateFloatAsState(
        if (state == OptionState.DIM) 0.45f else 1f,
        tween(220), label = "dim",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(fill, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (state == OptionState.RIGHT) {
                Image(painterResource(R.drawable.ki_m_check), null, Modifier.size(26.dp))
            } else if (state == OptionState.WRONG) {
                Image(painterResource(R.drawable.ki_m_cross), null, Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun EmptyPanel(onExit: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(26.dp))
            .padding(28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.ki_g_quiz), null, Modifier.size(86.dp))
            Spacer(Modifier.height(14.dp))
            Text("سوالی موجود نیست", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "کمی بعد دوباره تلاش کن — بانک واژه‌ها آماده می‌شود",
                fontSize = 13.sp,
                color = Muted,
            )
            Spacer(Modifier.height(20.dp))
            ClayButton(text = "بازگشت", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResultPanel(
    title: String,
    score: Int,
    total: Int,
    coins: Int,
    onExit: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(28.dp))
            .padding(28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.ki_trophy), null, Modifier.size(110.dp))
            Spacer(Modifier.height(16.dp))
            Text("آزمون تموم شد!", fontFamily = Lalezar, fontSize = 30.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "${faNum(score)} از ${faNum(total)} درست · سکه ${faNum(coins)}",
                fontSize = 14.sp,
                color = Muted,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton(
                    text = "بازگشت",
                    onClick = onExit,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                ClayButton(
                    text = "دوباره",
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                )
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
