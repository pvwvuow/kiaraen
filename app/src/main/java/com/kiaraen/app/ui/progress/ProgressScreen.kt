package com.kiaraen.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.data.local.ReviewDayCount
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.SoftCard
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * تب «پیشرفت» — مطابق طرح تأییدشده: کارت نشان سطح فعلی، چهار آمار،
 * نمودار فعالیت هفته + جزئیات کامل.
 */
@Composable
fun ProgressScreen() {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val progressDao = container.database.progressDao()
    val contentDao = container.database.contentDao()

    val statsFlow = remember(uid) { progressDao.stats(uid) }
    val learnedFlow = remember(uid) { progressDao.learnedCount(uid) }
    val stats by statsFlow.collectAsState(initial = null)
    val learned by learnedFlow.collectAsState(initial = 0)

    var week by remember { mutableStateOf<List<Int>>(List(7) { 0 }) }
    var levelInfo by remember { mutableStateOf<Triple<String, Int, Float>?>(null) } // id, toNext, fraction

    LaunchedEffect(uid) {
        val offset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
        val counts: List<ReviewDayCount> =
            runCatching { progressDao.reviewCountsByDay(uid, offset) }.getOrDefault(emptyList())
        val byDay = counts.associate { it.day to it.cnt }
        val today = LocalDate.now()
        val todayIdx = (today.dayOfWeek.value + 1) % 7 // Sat=0..Fri=6
        val start = today.toEpochDay() - todayIdx
        week = (0..6).map { i -> byDay[start + i] ?: 0 }

        // current CEFR level by cumulative learned words
        runCatching {
            val levels = contentDao.levels().first()
            val countsPer = levels.map { lv -> lv to contentDao.wordCountOf(lv.id).first() }
            val totalLearned = maxOf(stats?.totalLearned ?: 0, learned)
            var cum = 0
            var current = levels.firstOrNull()?.id ?: "A1"
            var toNext = 0
            var fraction = 1f
            for ((lv, cnt) in countsPer) {
                if (totalLearned < cum + cnt) {
                    current = lv.id
                    toNext = cum + cnt - totalLearned
                    fraction = if (cnt > 0) (totalLearned - cum).toFloat() / cnt else 1f
                    break
                }
                cum += cnt
                current = lv.id
                toNext = 0
                fraction = 1f
            }
            levelInfo = Triple(current, toNext, fraction.coerceIn(0.05f, 1f))
        }
    }

    val reviews = stats?.totalReviews ?: 0
    val correct = stats?.totalCorrect ?: 0
    val accuracy = if (reviews == 0) 0 else (correct * 100) / reviews

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("پیشرفت تو", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
                Text(
                    "هر روز بهتر از دیروز",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        LevelCard(
            levelId = levelInfo?.first ?: "A1",
            toNext = levelInfo?.second ?: 0,
            progress = levelInfo?.third ?: 0.05f,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(R.drawable.ki_books, faNum(maxOf(stats?.totalLearned ?: 0, learned)), "واژه آموخته", Modifier.weight(1f))
            StatCard(R.drawable.ki_a_coin, faNum(stats?.totalXp ?: 0), "سکه طلا", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(R.drawable.ki_a_clock, "$accuracy٪", "دقت تمرین‌ها", Modifier.weight(1f))
            StatCard(R.drawable.ki_flame, faNum(stats?.currentStreak ?: 0), "روز پیوسته", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        WeekChart(week)

        Spacer(Modifier.height(14.dp))
        SoftCard {
            DetailRow("تسلط کامل", faNum(stats?.totalMastered ?: 0))
            KiaraDivider(Modifier.padding(vertical = 8.dp))
            DetailRow("رکورد استریک", "${faNum(stats?.longestStreak ?: 0)} روز")
            KiaraDivider(Modifier.padding(vertical = 8.dp))
            DetailRow("کل مرورها", faNum(reviews))
            KiaraDivider(Modifier.padding(vertical = 8.dp))
            DetailRow("سکه طلا (XP)", faNum(stats?.totalXp ?: 0))
        }
        Text(
            "این آمار آفلاین از داده‌های محلی ساخته می‌شود و با ورود به حساب بین دستگاه‌ها همگام می‌شود.",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
    }
}

/** کارت نشان سطح فعلی. */
@Composable
private fun LevelCard(levelId: String, toNext: Int, progress: Float) {
    SoftCard(corner = 26.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(74.dp)
                    .background(Gold, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        levelId,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                    )
                    Text("سطح", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ink.copy(0.7f))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "سطح فعلی: $levelId",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(Line, RoundedCornerShape(7.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Gold, RoundedCornerShape(7.dp)),
                    )
                }
                Text(
                    if (toNext > 0) "${faNum(toNext)} واژه تا سطح بعد" else "همه واژه‌های این سطح ✨",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** کارت آمار: آیکون سه‌بعدی + عدد درشت + برچسب. */
@Composable
private fun StatCard(iconRes: Int, value: String, label: String, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier, corner = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(iconRes), null, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
                Text(label, fontSize = 11.sp, color = Muted)
            }
        }
    }
}

/** نمودار میله‌ای هفته (شنبه..جمعه) — میله امروز طلایی. */
@Composable
private fun WeekChart(week: List<Int>) {
    val labels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val todayIdx = (LocalDate.now().dayOfWeek.value + 1) % 7
    SoftCard(corner = 26.dp) {
        Text("فعالیت این هفته", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(14.dp))
        val maxV = maxOf(week.maxOrNull() ?: 0, 1)
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEachIndexed { i, v ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val h = (v.toFloat() / maxV * 88.dp.value).dp.coerceAtLeast(10.dp)
                    Canvas(Modifier.width(16.dp).height(h)) {
                        drawRoundRect(
                            color = if (i == todayIdx) Gold else Line,
                            cornerRadius = CornerRadius(20f, 20f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    fontSize = 11.sp,
                    fontWeight = if (i == todayIdx) FontWeight.Black else FontWeight.SemiBold,
                    color = if (i == todayIdx) Ink else Muted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = GoldDark)
    }
}
