package com.kiaraen.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.data.local.ReviewDayCount
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.CapsuleBar
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.SoftCard
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.CapsuleGradient
import com.kiaraen.app.ui.theme.Charcoal
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Lalezar
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.min
import androidx.compose.foundation.layout.widthIn

/**
 * تب «پیشرفت» — دقیقاً مطابق طرح تأییدشده با پک pr:
 * هیرو پنل + مدال + نشان سطح و کپسول واقعی، ۴ کاشی آمار، نمودار ستونی هفته
 * با ستون طلای امروز، حلقهٔ تسلط روی pr-disc و شورتکات تیرهٔ «آزمون سریع».
 */
@Composable
fun ProgressScreen(onOpenQuiz: (String) -> Unit = {}) {
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
    val mastered = stats?.totalMastered ?: 0
    val totalLearnedVal = maxOf(stats?.totalLearned ?: 0, learned)
    val masteryFraction = if (totalLearnedVal == 0) 0f else mastered.toFloat() / totalLearnedVal

    Column(
        modifier = Modifier
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

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("پیشرفت تو", fontFamily = Lalezar, fontSize = 26.sp, color = Ink)
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
            LevelHero(
                levelId = levelInfo?.first ?: "A1",
                toNext = levelInfo?.second ?: 0,
                progress = levelInfo?.third ?: 0.05f,
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrTile(R.drawable.ki_book_hq, faNum(totalLearnedVal), "واژه آموخته", Modifier.weight(1f))
                PrTile(R.drawable.ki_a_coin, faNum(stats?.totalXp ?: 0), "سکه طلا", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrTile(R.drawable.ki_a_clock, "$accuracy٪", "دقت تمرین‌ها", Modifier.weight(1f))
                PrTile(R.drawable.ki_flame, faNum(stats?.currentStreak ?: 0), "روز پیوسته", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            WeekChart(week)

            Spacer(Modifier.height(16.dp))
            MasteryCard(masteryFraction)

            Spacer(Modifier.height(16.dp))
            // dark quick-quiz shortcut (approved «آزمون سریع»)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Charcoal, RoundedCornerShape(24.dp))
                    .clickable { onOpenQuiz("review") }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ki_g_quiz), null, Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("آزمون سریع", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Gold)
                        Text(
                            "چند سوال جواب بده و سکه جمع کن",
                            fontSize = 11.sp,
                            color = Color(0xFFCFC9BD),
                        )
                    }
                    Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Gold)
                }
            }

            Spacer(Modifier.height(14.dp))
            SoftCard {
                DetailRow("تسلط کامل", faNum(mastered))
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
}

/** هیرو پنل: مدال + نشان سطح + کپسول واقعی (پک pr). */
@Composable
private fun LevelHero(levelId: String, toNext: Int, progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painterResource(R.drawable.ki_pr_medal),
                    null,
                    modifier = Modifier.size(width = 96.dp, height = 112.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(levelId, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF5C430B))
                    Text("سطح", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5C430B))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("سطح فعلی: $levelId", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
                Spacer(Modifier.height(10.dp))
                CapsuleBar(fraction = progress, height = 13.dp)
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

/** کاشی آمار با کاور کتاب — کارت کرم، آیکون سه‌بعدی، عدد درشت. */
@Composable
private fun PrTile(iconRes: Int, value: String, label: String, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier, corner = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(iconRes), null, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink)
                Text(label, fontSize = 11.sp, color = Muted)
            }
        }
    }
}

/** نمودار هفته — ستون‌های گرد با گرادیان طلا؛ ستون امروز طلایی پررنگ + حلقه. */
@Composable
private fun WeekChart(week: List<Int>) {
    val labels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val todayIdx = (LocalDate.now().dayOfWeek.value + 1) % 7
    SoftCard(corner = 26.dp) {
        Text("فعالیت این هفته", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(14.dp))
        val maxV = maxOf(week.maxOrNull() ?: 0, 1)
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEachIndexed { i, v ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val hFrac = (v.toFloat() / maxV).coerceIn(if (v > 0) 0.08f else 0f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Canvas(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(hFrac),
                        ) {
                            val r = size.width / 2
                            if (i == todayIdx) {
                                drawRoundRect(
                                    brush = CapsuleGradient,
                                    cornerRadius = CornerRadius(r, r),
                                )
                            } else {
                                drawRoundRect(
                                    color = Line,
                                    cornerRadius = CornerRadius(r, r),
                                )
                            }
                        }
                    }
                    if (i == todayIdx) {
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .androidDot(),
                        )
                    } else {
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(Color.Transparent),
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
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun Modifier.androidDot(): Modifier = this.background(Gold, androidx.compose.foundation.shape.CircleShape)

/** کارت تسلط — pr-disc با حلقهٔ conic واقعی داخل فرورفتگی (درصد mastered). */
@Composable
private fun MasteryCard(fraction: Float) {
    SoftCard(corner = 26.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
                Image(
                    painterResource(R.drawable.ki_pr_disc),
                    null,
                    modifier = Modifier.fillMaxSize(),
                )
                MasteryRing(fraction, Modifier.size(72.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("تسلط واژه‌ها", fontFamily = Lalezar, fontSize = 21.sp, color = Ink)
                Text(
                    "واژه‌هایی که ۴ بار درست جواب دادی دیگر برنمی‌گردند — این‌ها دارایی واقعی تو هستند.",
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** حلقهٔ درصد تسلط — conic arc gold on a hairline track. */
@Composable
private fun MasteryRing(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier) {
        val stroke = size.width * 0.12f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color(0xFFEFEAE0),
            startAngle = 90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            brush = CapsuleGradient,
            startAngle = 90f,
            sweepAngle = 360f * f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        // center text is drawn by the parent Column overlay
    }
    Column(
        modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            faNum((f * 100).toInt()) + "٪",
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = Ink,
        )
        Text("تسلط", fontSize = 9.sp, color = Muted)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = GoldDark)
    }
}
