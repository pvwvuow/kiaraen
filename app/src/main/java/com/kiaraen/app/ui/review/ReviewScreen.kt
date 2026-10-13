package com.kiaraen.app.ui.review

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kiaraen.app.R
import com.kiaraen.app.core.TtsPlayer
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
import androidx.compose.foundation.layout.widthIn

/**
 * مرور روزانه — the approved review screen: the current card sits on a stack
 * of turned cards, «X از ۱۲» + tube progress, and the two real actions:
 * «مرور شد» (correct → SRS grade) و «فردا» (again → due tomorrow).
 */
private const val TAG = "ReviewScreen"
private const val SESSION_SIZE = 12
private const val COINS_PER_CARD = 5

@Composable
fun ReviewScreen(onExit: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentDao = container.database.contentDao()
    val progressDao = container.database.progressDao()
    val progressRepo = container.progressRepository

    var ownerId by remember { mutableStateOf(LOCAL_USER_ID) }
    var loading by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf<List<com.kiaraen.app.data.local.WordEntity>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var doneCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val uid = runCatching { container.authRepository.currentUserId() }.getOrNull() ?: LOCAL_USER_ID
        ownerId = uid
        cards = runCatching {
            val due = progressDao.dueQueue(uid, System.currentTimeMillis(), SESSION_SIZE)
            val ids = due.map { it.wordId }
            if (ids.isEmpty()) emptyList() else contentDao.wordsByIds(ids)
        }.getOrElse {
            Log.e(TAG, "review queue failed", it)
            emptyList()
        }
        loading = false
    }

    fun answer(knew: Boolean) {
        val card = cards.getOrNull(index) ?: return
        doneCount++
        val uid = ownerId
        scope.launch {
            runCatching {
                progressRepo.recordReview(
                    userId = uid, wordId = card.id,
                    result = if (knew) ReviewResult.CORRECT else ReviewResult.WRONG,
                    mode = ReviewMode.MANUAL,
                    quizRef = "review",
                )
            }.onFailure { Log.e(TAG, "recordReview failed", it) }
        }
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
            DetailHeader(
                title = "مرور روزانه",
                subtitle = "واژه‌هایی که منتظر تو بودن",
                onBack = onExit,
            )

            when {
                loading -> Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                    Text("داریم کارت‌های امروز رو میاریم…", color = Muted, fontSize = 14.sp)
                }
                cards.isEmpty() -> {
                    Spacer(Modifier.height(60.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(28.dp))
                            .padding(28.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Image(painterResource(R.drawable.ki_aacal), null, Modifier.size(96.dp))
                            Spacer(Modifier.height(14.dp))
                            Text("چیزی برای مرور نمونده!", fontFamily = Lalezar, fontSize = 26.sp, color = Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "همه واژه‌های امروز مرور شدند؛ بعداً دوباره سر بزن 🌟",
                                fontSize = 13.sp,
                                color = Muted,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            ClayButton(text = "بازگشت", onClick = onExit, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                index >= cards.size -> {
                    Spacer(Modifier.height(40.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(28.dp))
                            .padding(28.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Image(painterResource(R.drawable.ki_g_star), null, Modifier.size(96.dp))
                            Spacer(Modifier.height(14.dp))
                            Text("مرور تموم شد!", fontFamily = Lalezar, fontSize = 30.sp, color = Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${faNum(doneCount)} واژه مرور شد · سکه ${faNum(doneCount * COINS_PER_CARD)}",
                                fontSize = 14.sp,
                                color = Muted,
                            )
                            Spacer(Modifier.height(24.dp))
                            ClayButton(text = "بازگشت", onClick = onExit, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                else -> {
                    val card = cards[index]
                    Spacer(Modifier.height(6.dp))
                    CapsuleBar(fraction = doneCount.toFloat() / cards.size, height = 10.dp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${faNum(doneCount)} از ${faNum(cards.size)} مرور شد",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    CardStack(
                        word = card.word,
                        pos = card.pos,
                        meaning = card.meaningFa.orEmpty(),
                        remaining = (cards.size - index - 1).coerceAtMost(2),
                        onSpeak = {
                            scope.launch {
                                val rate = runCatching { container.appPrefs.speechRate.first() }.getOrDefault("normal")
                                TtsPlayer.speakWord(context, card.word, rate)
                            }
                        },
                    )

                    Spacer(Modifier.height(30.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlineButton(
                            text = "فردا",
                            onClick = { answer(false) },
                            modifier = Modifier.weight(1f).height(54.dp),
                            leading = {
                                Image(painterResource(R.drawable.ki_a_clock), null, Modifier.size(22.dp))
                            },
                        )
                        ClayButton(
                            text = "مرور شد",
                            onClick = { answer(true) },
                            modifier = Modifier.weight(1f),
                            leading = {
                                Image(painterResource(R.drawable.ki_m_check), null, Modifier.size(22.dp))
                            },
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
    
        }
    }
}

/** کارت روی پشته کارت‌های چرخیده — approved visual. */
@Composable
private fun CardStack(
    word: String,
    pos: String?,
    meaning: String,
    remaining: Int,
    onSpeak: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.TopCenter) {
        // back cards
        for (i in remaining downTo 1) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .offset(y = (i * 14).dp)
                    .graphicsLayer {
                        rotationZ = if (i % 2 == 0) -2.2f * i else 2.2f * i
                        alpha = 0.75f
                    }
                    .zIndex(-i.toFloat())
                    .background(Cream, RoundedCornerShape(26.dp)),
            )
        }
        // current card
        Column(
            Modifier
                .fillMaxWidth()
                .height(310.dp)
                .zIndex(1f)
                .background(Cream, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                pos?.let {
                    Text(it, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                word,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                meaning,
                fontSize = 14.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(54.dp)
                    .background(Gold, RoundedCornerShape(17.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSpeak() },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ki_g_speaker), null, Modifier.size(30.dp))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
