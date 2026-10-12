package com.kiaraen.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.data.local.CollectionEntity
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.GoldPill
import com.kiaraen.app.ui.kiara.KiaraLogotype
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.SoftCard
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.quiz.REVIEW_QUIZ
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.GoldLight
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.TrackBrown
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * صفحه خانه — دقیقاً مطابق طرح تأییدشده (kiara-home / kiara-app):
 * لوگوتایپ + دکمه شعله استریک، تیتر، چیپ‌های روز هفته، کارت پوشه «ادامه یادگیری»،
 * ردیف «وقت مرور رسیده»، بخش «چی دوست داری یاد بگیری؟».
 */
@Composable
fun HomeScreen(
    onOpenQuiz: (String) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onSeeAllCollections: () -> Unit,
) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val progressDao = container.database.progressDao()
    val contentDao = container.database.contentDao()

    val statsFlow = remember(uid) { progressDao.stats(uid) }
    val dueFlow = remember(uid) { progressDao.dueCount(uid, System.currentTimeMillis()) }
    val collectionsFlow = remember { contentDao.collections() }
    val stats by statsFlow.collectAsState(initial = null)
    val due by dueFlow.collectAsState(initial = 0)
    val collections by collectionsFlow.collectAsState(initial = emptyList())

    val folder = collections.firstOrNull()
    val folderLearnedFlow = remember(folder?.id, uid) {
        folder?.id?.let { cid -> contentDao.learnedInCollection(cid, uid) }
    }
    val folderLearned by (folderLearnedFlow ?: kotlinx.coroutines.flow.flowOf(0))
        .collectAsState(initial = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        // header: streak button (right) + logotype (left) in RTL
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.ki_btn_fireb),
                    null,
                    modifier = Modifier.size(46.dp),
                )
                Text(
                    faNum(stats?.currentStreak ?: 0),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            KiaraLogotype(size = 32.sp)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "یه قدم جلوتر، هر روز",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = Ink,
        )
        Text(
            "آموزش برای خودت وقت بذار",
            fontSize = 13.sp,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        WeekChips()

        Spacer(Modifier.height(18.dp))
        folder?.let { FolderCard(it, folderLearned) { onOpenCollection(it.id) } }

        Spacer(Modifier.height(16.dp))
        KiaraDivider()

        Spacer(Modifier.height(14.dp))
        // review row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.ki_aacal),
                null,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("وقت مرور رسیده", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
                Text(
                    "${faNum(due)} لغت منتظرِ تو",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            GoldPill(
                text = "مرور",
                onClick = { onOpenQuiz(REVIEW_QUIZ) },
                modifier = Modifier.testTag("home_review_cta"),
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("چی دوست داری یاد بگیری؟", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(
                "دیدن همه",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
                modifier = Modifier.clickable { onSeeAllCollections() },
            )
        }

        Spacer(Modifier.height(12.dp))
        val explore = collections.drop(1).take(2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            explore.forEachIndexed { i, col ->
                Box(Modifier.weight(1f)) {
                    ExploreCard(
                        col = col,
                        iconRes = if (i == 0) R.drawable.ki_globe else R.drawable.ki_chat,
                    ) { onOpenCollection(col.id) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** هفت چیپ روز هفته: گذشته=نقطه، امروز=شعله با قاب، آینده=یخ‌زده. */
@Composable
private fun WeekChips() {
    val today = LocalDate.now()
    val todayIdx = (today.dayOfWeek.value + 1) % 7 // Sat=0 .. Fri=6
    val labels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    // RTL row: first day (شنبه) must be the RIGHT-most chip
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (i == todayIdx) Ink else Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEachIndexed { i, _ ->
                val res = when {
                    i == todayIdx -> R.drawable.ki_btn_fireb
                    i < todayIdx -> R.drawable.ki_chip_dot
                    else -> R.drawable.ki_btn_ice
                }
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == todayIdx) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Transparent, RoundedCornerShape(14.dp))
                                .padding(2.dp),
                        )
                    }
                    Image(
                        painterResource(res),
                        null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/** کارت پوشه «ادامه یادگیری» — تصویر پوشه طلایی + محتوای واقعی. */
@Composable
private fun FolderCard(col: CollectionEntity, learned: Int, onContinue: () -> Unit) {
    val container = LocalAppContainer.current
    val sizeFlow = remember(col.id) { container.database.contentDao().collectionSize(col.id) }
    val size by sizeFlow.collectAsState(initial = 0)

    val total = size.coerceAtLeast(1)
    val progress = (learned.toFloat() / total).coerceIn(0f, 1f)
    val lessons = ceil(total / 12.0).toInt().coerceAtLeast(1)
    val lesson = ((progress * lessons).roundToInt()).coerceIn(0, lessons).let { if (it == 0) 1 else it }

    Column {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            // tab (top-right in RTL): first child = right side under RTL
            Box(
                Modifier
                    .background(Gold, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    "ادامه یادگیری",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(621f / 426f),
        ) {
            Image(
                painterResource(R.drawable.ki_folder),
                null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, top = 26.dp, end = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).height(150.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        col.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                    )
                    Text(
                        "درس ${faNum(lesson)} از ${faNum(lessons)}",
                        fontSize = 12.sp,
                        color = Ink.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    // progress: dark track + gold fill
                    Box(
                        Modifier
                            .fillMaxWidth(0.9f)
                            .height(10.dp)
                            .background(TrackBrown, RoundedCornerShape(6.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceAtLeast(0.03f))
                                .fillMaxHeight()
                                .background(Gold, RoundedCornerShape(6.dp)),
                        )
                    }
                }
                Image(
                    painterResource(R.drawable.ki_books),
                    null,
                    modifier = Modifier.size(92.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(GoldLight.copy(alpha = 0.55f), RoundedCornerShape(25.dp))
                .clickable { onContinue() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "ادامه بده",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
            )
            Text(
                "▶",
                color = GoldDark,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 26.dp),
            )
        }
    }
}

/** کارت «سفر / مکالمه» با آیکون سه‌بعدی + تعداد واژه + شورون. */
@Composable
private fun ExploreCard(col: CollectionEntity, iconRes: Int, onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val sizeFlow = remember(col.id) { container.database.contentDao().collectionSize(col.id) }
    val size by sizeFlow.collectAsState(initial = 0)

    SoftCard(modifier = Modifier.clickable { onClick() }, corner = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    col.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                    maxLines = 1,
                )
                Text(
                    "${faNum(size)} واژه",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Image(
                painterResource(iconRes),
                null,
                modifier = Modifier.size(58.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(22.dp)
                .background(Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Ink)
        }
    }
}
