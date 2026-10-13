package com.kiaraen.app.ui.levels

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
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
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.widthIn

/**
 * پکیج سطح — the approved #level screen: hero with the level book
 * (gold/ivory) + badge, real progress tube, «ادامه یادگیری» + «آزمون سطح»
 * CTAs, then the 2-per-row category grid with covers and per-category
 * learned progress. [directLevel] opens straight into a level behind the
 * cinematic «در حال آماده‌سازی پکیج سطح…» loader (approved #level-load).
 */
@Composable
fun LevelsScreen(
    onOpenQuiz: (String) -> Unit,
    onOpenLearn: (String, String) -> Unit = { _, _ -> },
    directLevel: String? = null,
    onBack: () -> Unit = {},
) {
    val contentDao = LocalAppContainer.current.database.contentDao()

    var selectedLevel by rememberSaveable { mutableStateOf(directLevel) }
    LaunchedEffect(directLevel) { if (directLevel != null) selectedLevel = directLevel }
    val levelsFlow = remember { contentDao.levels() }
    val levels by levelsFlow.collectAsState(initial = emptyList())

    BackHandler(enabled = selectedLevel != null) { onBack() }

    val levelId = selectedLevel
    if (levelId == null) {
        // picker (kept simple; the books shelf on «واژه‌ها» is the main entry)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Paper)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "سطح‌ها",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                )
            }
            items(levels.size) { i ->
                val lv = levels[i]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(18.dp))
                        .clickable { selectedLevel = lv.id }
                        .padding(16.dp),
                ) {
                    Text(lv.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
                }
            }
        }
    } else {
        LevelDetail(
            levelId = levelId,
            onBack = onBack,
            onOpenQuiz = onOpenQuiz,
            onOpenLearn = onOpenLearn,
        )
    }
}

@Composable
private fun LevelDetail(
    levelId: String,
    onBack: () -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenLearn: (String, String) -> Unit,
) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val contentDao = container.database.contentDao()
    val progressDao = container.database.progressDao()

    val catsFlow = remember(levelId) { contentDao.categoriesOf(levelId) }
    val cats by catsFlow.collectAsState(initial = emptyList())
    val wordCount by remember(levelId) { contentDao.wordCountOf(levelId) }.collectAsState(initial = 0)
    val learnedInLv by remember(levelId, uid) { progressDao.learnedInLevel(levelId, uid) }
        .collectAsState(initial = 0)
    val perCat by remember(uid) { progressDao.learnedPerCategory(uid) }
        .collectAsState(initial = emptyList())
    val perCatMap = remember(perCat) { perCat.associate { it.catId to it.cnt } }

    val levelTag = remember(levelId) {
        mapOf(
            "A1" to "مبتدی", "A2" to "پایه", "B1" to "میانی",
            "B2" to "متوسط رو به بالا", "C1" to "پیشرفته", "C2" to "تسلط کامل",
        )[levelId] ?: ""
    }
    val progress = if (wordCount > 0) learnedInLv.toFloat() / wordCount else 0f

    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            DetailHeader(
                title = "سطح $levelId",
                subtitle = "${faNum(wordCount)} واژه · $levelTag",
                onBack = onBack,
            )

            LazyColumn(Modifier.weight(1f)) {
                item {
                    // hero — approved «پکیج واژگان» card
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(26.dp))
                            .padding(18.dp),
                    ) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "پکیج واژگان $levelId",
                                    fontFamily = Lalezar,
                                    fontSize = 24.sp,
                                    color = Ink,
                                )
                                Text(
                                    "واژه‌های اصلی این سطح برای مکالمهٔ روزمره و حرفه‌ای.",
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 4.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(14.dp))
                                CapsuleBar(fraction = progress, height = 12.dp)
                                Text(
                                    "${faNum(learnedInLv)} از ${faNum(wordCount)} واژه مرور شده · ${faNum((progress * 100).toInt())}٪",
                                    fontSize = 11.sp,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ClayButton(
                                        text = "ادامه یادگیری",
                                        onClick = { onOpenLearn("level:$levelId", "flash") },
                                        modifier = Modifier.weight(1f),
                                        height = 48.dp,
                                        fontSize = 14.sp,
                                    )
                                    OutlineButton(
                                        text = "آزمون سطح",
                                        onClick = { onOpenQuiz("level:$levelId") },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                            Box(contentAlignment = Alignment.TopCenter) {
                                Image(
                                    painterResource(
                                        if (isGoldLevel(levelId)) R.drawable.ki_book_gold else R.drawable.ki_book_ivory,
                                    ),
                                    null,
                                    modifier = Modifier.size(width = 96.dp, height = 112.dp),
                                )
                                Box(
                                    Modifier
                                        .padding(top = 8.dp)
                                        .background(Gold, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(levelId, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ink)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("دسته‌بندی واژه‌ها", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
                        Text(
                            "${faNum(cats.size)} دسته",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // category grid — 2 per row (approved), lazy chunks
                val rows = cats.chunked(2)
                rows.forEachIndexed { rowIndex, pair ->
                    item(key = "row_$rowIndex") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { cat ->
                                CategoryCard(
                                    title = cat.title,
                                    catId = cat.id,
                                    iconRes = catCoverRes(cats.indexOf(cat)),
                                    modifier = Modifier.weight(1f),
                                ) { onOpenLearn("category:${cat.id}", "flash") }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                item { Spacer(Modifier.height(26.dp)) }
            }
        }
        // level-load cinematic overlay (approved #level-load)
        LevelLoader()
    }
}

/** کارت دسته: کاور کتاب + نام + شمارنده واژه — گرید ۲تایی طرح. */
@Composable
private fun CategoryCard(
    title: String,
    catId: Long,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = LocalAppContainer.current
    val total by remember(catId) { container.database.contentDao().wordCountOfCategory(catId) }
        .collectAsState(initial = 0)

    Column(
        modifier
            .background(Cream, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(iconRes),
            null,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
        )
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "${faNum(total)} واژه",
            fontSize = 10.sp,
            color = Muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** کاور دسته از ۱۶ کتاب موضوعی (چرخش پایدار). */
internal fun catCoverRes(index: Int): Int = com.kiaraen.app.ui.words.topicCoverRes(index)

private fun isGoldLevel(id: String): Boolean = when (id) {
    "A1", "B1", "C1" -> true
    else -> false
}

/**
 * لودر سینمایی — approved level-load: paper overlay, the level book scales
 * in, khead peeks from behind, caption + 3 bouncing dots, fade out (~2.4s,
 * skippable by tap).
 */
@Composable
private fun LevelLoader() {
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2400)
        show = false
    }
    val scale = remember { Animatable(0.55f) }
    LaunchedEffect(show) {
        if (show) scale.animateTo(1f, tween(460))
    }
    AnimatedVisibility(
        visible = show,
        exit = fadeOut(tween(320)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Paper.copy(alpha = 0.98f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { show = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Image(
                        painterResource(R.drawable.khead),
                        null,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(bottom = 118.dp)
                            .size(118.dp),
                    )
                    Image(
                        painterResource(R.drawable.ki_book_gold),
                        null,
                        modifier = Modifier
                            .size(200.dp)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            },
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "در حال آماده‌سازی پکیج سطح…",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        val bob = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                bob.animateTo(
                                    -6f,
                                    keyframes { durationMillis = 620; 0f at 0; -6f at 310; 0f at 620 },
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(9.dp)
                                .graphicsLayer { translationY = bob.value }
                                .background(Gold.copy(alpha = 0.75f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}
