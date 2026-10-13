package com.kiaraen.app.ui.words

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.core.TtsPlayer
import com.kiaraen.app.data.local.WordBookmarkEntity
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.CapsuleBar
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.DetailHeader
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
 * صفحه موضوع (جزئیات مجموعه) — دقیقاً مطابق طرح تأییدشده:
 * هیرو با کاور کتاب موضوعی + عنوان + کپسول پیشرفت + «N واژه این مجموعه مرور شد»،
 * سه چیپ حالت (بلندخوانی / تلفظ تکی / فلش‌کارت)، ردیف‌های واژه با نشانک و بلندگو،
 * و CTA طلایی «آزمون این مجموعه». JOIN query keeps big collections safe on
 * Android 8-10 (SQLite 999-variable limit).
 */
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenLearn: (String, String) -> Unit = { _, _ -> },
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentDao = container.database.contentDao()
    val progressDao = container.database.progressDao()
    val uid = rememberUserId()

    BackHandler { onBack() }

    val all by remember { contentDao.collections() }.collectAsState(initial = emptyList())
    val col = all.firstOrNull { it.id == collectionId }
    val coverIdx = all.indexOfFirst { it.id == collectionId }

    var words by remember(collectionId) { mutableStateOf<List<WordEntity>>(emptyList()) }
    var bookmarks by remember(uid) { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(collectionId) {
        words = runCatching { contentDao.wordsOfCollectionOnce(collectionId) }
            .getOrDefault(emptyList())
    }
    LaunchedEffect(uid) {
        bookmarks = runCatching { progressDao.bookmarkIds(uid).first() }.getOrDefault(emptyList()).toSet()
    }

    val learnedFlow = remember(collectionId, uid) { contentDao.learnedInCollection(collectionId, uid) }
    val learned by learnedFlow.collectAsState(initial = 0)
    val dueFlow = remember(collectionId, uid) { progressDao.dueInCollection(collectionId, uid, System.currentTimeMillis()) }
    val dueNow by dueFlow.collectAsState(initial = 0)

    val total = words.size.coerceAtLeast(1)
    val progress = (learned.toFloat() / total).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            DetailHeader(
                title = col?.title ?: "...",
                subtitle = "${faNum(words.size)} واژه در این موضوع",
                onBack = onBack,
            )
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp),
        ) {
            // hero card
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(26.dp))
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                col?.title ?: "",
                                fontFamily = Lalezar,
                                fontSize = 24.sp,
                                color = Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${faNum(words.size)} واژه برای ${purposeOf(col?.title.orEmpty())}",
                                fontSize = 12.sp,
                                color = Muted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            CapsuleBar(fraction = progress, height = 12.dp)
                            Text(
                                "${faNum(dueNow)} واژه این مجموعه مرور شد",
                                fontSize = 11.sp,
                                color = Muted,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Image(
                            painterResource(topicCoverRes(coverIdx)),
                            null,
                            modifier = Modifier.size(width = 86.dp, height = 96.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                // mode chips — approved trio
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeChip(
                        label = "بلندخوانی",
                        icon = R.drawable.ki_headphones,
                        modifier = Modifier.weight(1f),
                    ) { onOpenLearn("collection:$collectionId", "listen") }
                    ModeChip(
                        label = "تلفظ تکی",
                        icon = R.drawable.ki_g_mic,
                        modifier = Modifier.weight(1f),
                    ) { onOpenLearn("collection:$collectionId", "pronounce") }
                    ModeChip(
                        label = "فلش‌کارت",
                        icon = R.drawable.ki_g_play,
                        modifier = Modifier.weight(1f),
                    ) { onOpenLearn("collection:$collectionId", "flash") }
                }

                Spacer(Modifier.height(14.dp))
            }

            val byLevel = words.groupBy { it.levelId }
            byLevel.entries.sortedBy { it.key }.forEach { (lv, ws) ->
                item(key = "h_$lv") {
                    Text(
                        "سطح $lv · ${faNum(ws.size)} واژه",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                    )
                }
                itemsIndexed(ws, key = { _, w -> w.id }) { _, w ->
                    WordRow(
                        word = w.word,
                        meaning = w.meaningFa.orEmpty(),
                        pos = w.pos,
                        bookmarked = w.id in bookmarks,
                        onBookmark = {
                            val owner = uid
                            scope.launch {
                                val added = runCatching {
                                    if (w.id in bookmarks) {
                                        progressDao.removeBookmark(owner, w.id)
                                        false
                                    } else {
                                        progressDao.addBookmark(
                                            WordBookmarkEntity(
                                                userId = owner, wordId = w.id, createdAt = System.currentTimeMillis(),
                                            ),
                                        )
                                        true
                                    }
                                }.getOrNull()
                                if (added != null) {
                                    bookmarks = if (added) bookmarks + w.id else bookmarks - w.id
                                }
                            }
                        },
                        onSpeak = {
                            scope.launch {
                                val rate = runCatching { container.appPrefs.speechRate.first() }.getOrDefault("normal")
                                TtsPlayer.speakWord(context, w.word, rate)
                            }
                        },
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                ClayButton(
                    text = "آزمون این مجموعه",
                    onClick = { onOpenQuiz("collection:$collectionId") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

/** چیپ حالت: کپسولی کرم با آیکون سه‌بعدی و لیبل — سه حالت صفحه موضوع. */
@Composable
private fun ModeChip(label: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(Cream, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(icon), null, Modifier.size(30.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
        )
    }
}

/** ردیف واژه — کارت کرم با واژه درشت، نشانک و بلندگو (مطابق طرح). */
@Composable
private fun WordRow(
    word: String,
    meaning: String,
    pos: String?,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onSpeak: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(Cream, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        word,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                    )
                    pos?.let {
                        Text(
                            "  $it",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                }
                Text(
                    meaning,
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Image(
                painterResource(R.drawable.ki_g_speaker),
                null,
                modifier = Modifier
                    .size(26.dp)
                    .alpha(0.85f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSpeak() },
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painterResource(R.drawable.ki_ui_bookmark),
                null,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (bookmarked) 1f else 0.4f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBookmark() },
            )
        }
    }
}

internal fun purposeOf(title: String): String = when {
    title.contains("سفر") -> "سفر کردن"
    title.contains("مکالمه") || title.contains("روزمره") -> "صحبت‌های هر روز"
    title.contains("خانه") -> "زندگی روزمره"
    title.contains("خرید") -> "بازار و فروشگاه"
    title.contains("سلامت") -> "مطب و داروخانه"
    title.contains("آموزش") -> "مدرسه و دانشگاه"
    title.contains("کار") -> "محیط کار"
    title.contains("غذا") -> "رستوران و کافه"
    else -> "گسترش دایره لغت"
}
