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
 *   "mistakes"              → مرور اشتباهات (the one-place mistakes list)
 *   "level:A1"              → default quiz of a level
 *   "collection:<id>"       → collection drill
 * Questions are generated ON DEVICE from the cached pack → zero server cost.
 *
 * «یادگرفته» gate: a quiz only pays out when finished TO THE END — correct
 * answers are promoted to learned (completeQuiz) and cleared from mistakes;
 * wrong answers join the one-place mistakes list. Hearts running out =
 * abandoned → nothing is added to learned.
 * Nothing in here may crash the app: generation and every write are guarded.
 */
data class Question(val wordId: Long, val prompt: String, val options: List<String>, val answer: String)

const val REVIEW_QUIZ = "review"
const val MISTAKES_QUIZ = "mistakes"
private const val TAG = "QuizScreen"
private const val QUIZ_SIZE = 10
private const val RANDOM_POOL = 60
private const val COINS_PER_CORRECT = 5

/** Persian quiz title for a ref (نوار بالای صفحه آزمون). */
suspend fun quizTitle(ref: String, dao: ContentDao): String = when {
    ref == REVIEW_QUIZ -> "آزمون سریع"
    ref == MISTAKES_QUIZ -> "مرور اشتباهات"
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
    // completion bookkeeping: (wordId, correct) per answered question —
    // completed == the user answered EVERY question (hearts never ran out)
    var results by remember { mutableStateOf(listOf<Pair<Long, Boolean>>()) }
    var completed by remember { mutableStateOf(false) }
    var promotedCount by remember { mutableIntStateOf(0) }
    val startedAt = remember { System.currentTimeMillis() }

    // one loader for the first run AND retries: mistakes/review keep their
    // source filter (retry used to drop it and leak random words in)
    suspend fun loadQuestions(uid: String) {
        questions = runCatching {
            when {
                quizRef == MISTAKES_QUIZ ->
                    buildQuiz(quizRef, contentDao, progressDao.mistakeWordIds(uid, QUIZ_SIZE))
                quizRef == REVIEW_QUIZ ->
                    buildQuiz(
                        quizRef, contentDao,
                        progressDao.dueQueue(uid, System.currentTimeMillis(), QUIZ_SIZE).map { it.wordId },
                    )
                else -> buildQuiz(quizRef, contentDao)
            }
        }.getOrElse {
            Log.e(TAG, "quiz generation failed for $quizRef", it)
            emptyList()
        }
    }

    LaunchedEffect(quizRef) {
        // resolve the owner FIRST so answers are never recorded under a stale id
        val uid = runCatching { container.authRepository.currentUserId() }.getOrNull()
            ?: LOCAL_USER_ID
        ownerId = uid
        title = runCatching { quizTitle(quizRef, contentDao) }.getOrDefault("آزمون")
        loadQuestions(uid)
        loading = false
    }

    fun finish() {
        if (finished) return
        finished = true
        val wasCompleted = completed
        val correctIds = results.filter { it.second }.map { it.first }
        val uid = ownerId
        scope.launch {
            runCatching {
                progressRepo.recordQuizAttempt(
                    userId = uid, quizRef = quizRef, score = score,
                    total = if (wasCompleted) questions.size else maxOf(results.size, 1),
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            }.onFailure { Log.e(TAG, "recordQuizAttempt failed", it) }
            if (wasCompleted && correctIds.isNotEmpty()) {
                // completed to the end → correct words become «یادگرفته»
                runCatching { progressRepo.completeQuiz(uid, correctIds) }
                    .onSuccess { promotedCount = it }
                    .onFailure { Log.e(TAG, "completeQuiz failed", it) }
            }
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
                        EmptyPanel(
                            onExit,
                            title = if (quizRef == MISTAKES_QUIZ) "اشتباهی برای مرور نیست!" else "سوالی موجود نیست",
                            subtitle = if (quizRef == MISTAKES_QUIZ) {
                                "همهٔ اشتباهاتت رو درست جواب دادی — عالی بود"
                            } else {
                                "کمی بعد دوباره تلاش کن — بانک واژه‌ها آماده می‌شود"
                            },
                        )
                    }
                    finished || index >= questions.size -> {
                        Spacer(Modifier.height(40.dp))
                        ResultPanel(
                            title = title,
                            score = score,
                            total = questions.size,
                            coins = score * COINS_PER_CORRECT,
                            promoted = promotedCount,
                            completed = completed,
                            mistakes = results.count { !it.second },
                            onExit = onExit,
                            onRetry = {
                                index = 0; score = 0; lives = 3; picked = null
                                results = emptyList(); completed = false; promotedCount = 0
                                finished = false
                                scope.launch { loadQuestions(ownerId) }
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
                                if (correct) score++ else lives--
                                results = results + (q.wordId to correct)
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
                                        completed = false   // hearts ran out → abandoned
                                        finish()
                                    } else {
                                        if (index + 1 >= questions.size) {
                                            completed = true   // answered everything → the gate opens
                                            finish()
                                        } else index++
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
private fun EmptyPanel(onExit: () -> Unit, title: String = "سوالی موجود نیست", subtitle: String = "کمی بعد دوباره تلاش کن — بانک واژه‌ها آماده می‌شود") {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(26.dp))
            .padding(28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.ki_g_quiz), null, Modifier.size(86.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
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
    promoted: Int,
    completed: Boolean,
    mistakes: Int,
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
            Image(
                painterResource(if (completed) R.drawable.ki_trophy else R.drawable.ki_g_heart),
                null,
                Modifier.size(110.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (completed) "آزمون تموم شد!" else "جون‌هات تموم شد!",
                fontFamily = Lalezar,
                fontSize = 30.sp,
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${faNum(score)} از ${faNum(total)} درست · سکه ${faNum(coins)}",
                fontSize = 14.sp,
                color = Muted,
            )
            if (completed && promoted > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ki_m_check), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${faNum(promoted)} واژه به یادگرفته‌ها اضافه شد",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldDark,
                    )
                }
            }
            if (!completed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "آزمون کامل نشد؛ واژه‌ای به یادگرفته‌ها اضافه نشد",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Danger,
                    textAlign = TextAlign.Center,
                )
            }
            if (mistakes > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${faNum(mistakes)} اشتباه به «مرور اشتباهات» رفت",
                    fontSize = 13.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
            }
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
        quizRef == MISTAKES_QUIZ -> {
            // مرور اشتباهات — ONLY mistake words are asked; random words may
            // serve as distractors but never as targets.
            val mistakeWords = if (dueWordIds.isEmpty()) emptyList() else dao.wordsByIds(dueWordIds.take(500))
            targets = mistakeWords
            pool = (mistakeWords + dao.randomWords(RANDOM_POOL)).distinctBy { it.id }
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
