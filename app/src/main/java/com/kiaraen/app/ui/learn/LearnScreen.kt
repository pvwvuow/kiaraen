package com.kiaraen.app.ui.learn

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.core.TtsPlayer
import com.kiaraen.app.data.local.ContentDao
import com.kiaraen.app.data.local.WordBookmarkEntity
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.data.sync.SyncWorker
import com.kiaraen.app.domain.ReviewMode
import com.kiaraen.app.domain.ReviewResult
import com.kiaraen.app.ui.common.LOCAL_USER_ID
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.kiara.CapsuleBar
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.OutlineButton
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Lalezar
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.foundation.layout.widthIn

/**
 * یادگیری (فلش‌کارت) — the approved learn screen: a big 3D-flip card
 * (word ↔ meaning), bookmark, speaker (TTS), «بلدم / یادآوری» buttons and
 * the coin end-panel with the quick-quiz CTA. Modes from the topic screen:
 *   flash     → manual flip
 *   listen    → auto-speaks the word on every card (بلندخوانی)
 *   pronounce → auto-speaks too (تلفظ تکی)
 * Every graded card is a real FLASHCARD review → SRS + sync untouched.
 */
private const val TAG = "LearnScreen"
private const val SESSION_SIZE = 12
private const val COINS_PER_CARD = 5

@Composable
fun LearnScreen(
    ref: String,          // collection:<id> | level:A1 | goal
    mode: String = "flash",
    onExit: () -> Unit,
    onOpenQuiz: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentDao = container.database.contentDao()
    val progressDao = container.database.progressDao()
    val progressRepo = container.progressRepository

    var ownerId by remember { mutableStateOf(LOCAL_USER_ID) }
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("یادگیری") }
    var cards by remember { mutableStateOf<List<WordEntity>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var knownCount by remember { mutableIntStateOf(0) }
    var bookmarks by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(ref) {
        val uid = runCatching { container.authRepository.currentUserId() }.getOrNull() ?: LOCAL_USER_ID
        ownerId = uid
        title = when {
            ref.startsWith("collection:") ->
                ref.removePrefix("collection:").toLongOrNull()
                    ?.let { id ->
                        runCatching { contentDao.collections().first() }.getOrNull()
                            ?.firstOrNull { it.id == id }?.title
                    }
                    ?.let { "مجموعه $it" } ?: "فلش‌کارت"
            ref.startsWith("level:") -> "پکیج سطح ${ref.removePrefix("level:")}"
            ref.startsWith("category:") ->
                ref.removePrefix("category:").toLongOrNull()
                    ?.let { runCatching { contentDao.categoryById(it) }.getOrNull()?.title }
                    ?.let { "دسته $it" } ?: "فلش‌کارت"
            else -> "یادگیری امروز"
        }
        val dueIds = if (ref == "goal") {
            runCatching {
                progressDao.dueQueue(uid, System.currentTimeMillis(), SESSION_SIZE).map { it.wordId }
            }.getOrDefault(emptyList())
        } else emptyList()
        cards = runCatching { poolFor(ref, contentDao, uid, dueIds) }.getOrElse {
            Log.e(TAG, "learn pool failed for $ref", it)
            emptyList()
        }
        bookmarks = runCatching { progressDao.bookmarkIds(uid).first() }
            .getOrDefault(emptyList()).toSet()
        loading = false
    }

    // auto-speak for the listening / pronunciation modes
    LaunchedEffect(index, mode, loading) {
        if (loading || cards.isEmpty() || index >= cards.size) return@LaunchedEffect
        if (mode == "listen" || mode == "pronounce") {
            val rate = runCatching { container.appPrefs.speechRate.first() }.getOrDefault("normal")
            TtsPlayer.speakWord(context, cards[index].word, rate)
        }
    }

    fun grade(knew: Boolean) {
        val card = cards.getOrNull(index) ?: return
        if (knew) knownCount++
        val uid = ownerId
        scope.launch {
            runCatching {
                progressRepo.recordReview(
                    userId = uid, wordId = card.id,
                    result = if (knew) ReviewResult.CORRECT else ReviewResult.AGAIN,
                    mode = ReviewMode.FLASHCARD,
                    quizRef = ref,
                )
            }.onFailure { Log.e(TAG, "recordReview failed", it) }
        }
        flipped = false
        if (index + 1 >= cards.size) {
            scope.launch { runCatching { SyncWorker.enqueueOneShot(context) } }
        }
        index++
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp),
        ) {

            Spacer(Modifier.height(14.dp))
            DetailHeader(title = title, subtitle = "درسِ روزمره", onBack = onExit)

            when {
                loading -> Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                    Text("داریم کارت‌ها رو می‌چینیم…", color = Muted, fontSize = 14.sp)
                }
                cards.isEmpty() -> Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                    Text("این بخش هنوز کارت آماده نداره", color = Muted, fontSize = 14.sp)
                }
                index >= cards.size -> {
                    Spacer(Modifier.height(30.dp))
                    EndPanel(
                        learned = knownCount,
                        total = cards.size,
                        coins = knownCount * COINS_PER_CARD,
                        onBack = onExit,
                        onQuiz = { onOpenQuiz(if (ref == "goal") "review" else ref) },
                    )
                }
                else -> {
                    val card = cards[index]
                    Spacer(Modifier.height(8.dp))
                    CapsuleBar(fraction = index.toFloat() / cards.size, height = 10.dp)
                    Text(
                        "${faNum(index + 1)} از ${faNum(cards.size)} کارت",
                        fontSize = 12.sp,
                        color = Muted,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )

                    Spacer(Modifier.height(14.dp))
                    FlipCard(
                        word = card.word,
                        pos = card.pos,
                        meaning = card.meaningFa.orEmpty(),
                        flipped = flipped,
                        onFlip = { flipped = !flipped },
                        bookmarked = card.id in bookmarks,
                        onBookmark = {
                            val uid = ownerId
                            scope.launch {
                                val added = runCatching {
                                    if (card.id in bookmarks) {
                                        progressDao.removeBookmark(uid, card.id)
                                        false
                                    } else {
                                        progressDao.addBookmark(
                                            WordBookmarkEntity(
                                                userId = uid, wordId = card.id, createdAt = System.currentTimeMillis(),
                                            ),
                                        )
                                        true
                                    }
                                }.getOrNull()
                                if (added != null) {
                                    bookmarks = if (added) bookmarks + card.id else bookmarks - card.id
                                }
                            }
                        },
                        onSpeak = {
                            scope.launch {
                                val rate = runCatching { container.appPrefs.speechRate.first() }.getOrDefault("normal")
                                TtsPlayer.speakWord(context, card.word, rate)
                            }
                        },
                    )

                    Spacer(Modifier.height(26.dp))
                    if (flipped) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlineButton(
                                text = "یادآوری",
                                onClick = { grade(false) },
                                modifier = Modifier.weight(1f).height(54.dp),
                            )
                            ClayButton(
                                text = "بلدم",
                                onClick = { grade(true) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        ClayButton(
                            text = "برگردون کارت رو",
                            onClick = { flipped = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
    
        }
    }
}

/** Session pool: 12 mixed cards for the ref (due-first for the daily goal). */
suspend fun poolFor(
    ref: String,
    contentDao: ContentDao,
    uid: String,
    dueWordIds: List<Long> = emptyList(),
): List<WordEntity> {
    val list = when {
        ref.startsWith("collection:") ->
            ref.removePrefix("collection:").toLongOrNull()
                ?.let { contentDao.wordsOfCollectionOnce(it) }.orEmpty()
        ref.startsWith("level:") ->
            contentDao.wordsOfLevelOnce(ref.removePrefix("level:"))
        ref.startsWith("category:") ->
            ref.removePrefix("category:").toLongOrNull()
                ?.let { contentDao.wordsOfCategoryOnce(it) }.orEmpty()
        else -> {
            val dueWords = if (dueWordIds.isEmpty()) emptyList() else contentDao.wordsByIds(dueWordIds.take(SESSION_SIZE))
            (dueWords + contentDao.randomWords(SESSION_SIZE)).distinctBy { it.id }
        }
    }
    return list.filter { !it.meaningFa.isNullOrBlank() }
        .shuffled(Random(System.currentTimeMillis()))
        .take(SESSION_SIZE)
}

/** The 3D flip card of the approved learn screen. */
@Composable
private fun FlipCard(
    word: String,
    pos: String?,
    meaning: String,
    flipped: Boolean,
    onFlip: () -> Unit,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onSpeak: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(420),
        label = "flip",
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardH = maxHeight * 0.42f
        Box(
            Modifier
                .fillMaxWidth()
                .height(cardH),
        ) {
            CardFace(
                visible = rotation <= 90f,
                rotation = rotation,
                corner = 28.dp,
            ) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTap(R.drawable.ki_ui_bookmark, "ذخیره", onBookmark, active = bookmarked)
                        Spacer(Modifier.weight(1f))
                        pos?.let {
                            Text(it, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        word,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(Gold, RoundedCornerShape(16.dp))
                            .clickable { onSpeak() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ki_g_speaker), null, Modifier.size(30.dp))
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            CardFace(
                visible = rotation > 90f,
                rotation = rotation - 180f,
                corner = 28.dp,
                fill = Gold,
                onFlip = onFlip,
            ) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        meaning.ifBlank { "معنی ثبت نشده" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        word,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardFace(
    visible: Boolean,
    rotation: Float,
    corner: androidx.compose.ui.unit.Dp,
    fill: Color = Cream,
    onFlip: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (!visible) return
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .background(fill, RoundedCornerShape(corner))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onFlip?.invoke() },
            ),
        content = content,
    )
}

/** Ghost icon button (bookmark etc.) — no ripple, gold when active. */
@Composable
private fun IconTap(res: Int, desc: String, onClick: () -> Unit, active: Boolean) {
    Image(
        painterResource(res),
        desc,
        Modifier
            .size(34.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .graphicsLayer { alpha = if (active) 1f else 0.55f },
    )
}

@Composable
private fun EndPanel(learned: Int, total: Int, coins: Int, onBack: () -> Unit, onQuiz: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(28.dp))
            .padding(28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.ki_g_star), null, Modifier.size(96.dp))
            Spacer(Modifier.height(14.dp))
            Text("درس تموم شد!", fontFamily = Lalezar, fontSize = 30.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "${faNum(learned)} واژه یاد گرفتی · سکه ${faNum(coins)}",
                fontSize = 14.sp,
                color = Muted,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton(
                    text = "بازگشت",
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                ClayButton(
                    text = "آزمون سریع",
                    onClick = onQuiz,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
