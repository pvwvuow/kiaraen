package com.kiaraen.app.ui.goal

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.CapsuleBar
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.SpinningGoldRing
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldLight
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Lalezar
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn

/**
 * هدف روزانه — the approved #goal screen: flame hero with the streak and
 * today's tube, the real 7-day week chips (activity from review_log),
 * the 5-option picker with a live minutes estimate, and the save CTA
 * writing the goal into AppPrefs (the «من» row + home loop read it).
 */
private val GOAL_OPTIONS = listOf(5, 10, 15, 20, 30)
private val GOAL_LABELS = listOf("آرام", "جدی", "متوسط", "پیشرفته", "حرفه‌ای")

@Composable
fun GoalScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val uid = rememberUserId()
    val progressDao = container.database.progressDao()

    var goal by remember { mutableIntStateOf(15) }
    var streak by remember { mutableIntStateOf(0) }
    var todayDone by remember { mutableIntStateOf(0) }
    var week by remember { mutableStateOf(List(7) { 0 }) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        goal = runCatching { container.appPrefs.dailyGoal.first() }.getOrDefault(15)
        streak = runCatching { progressDao.statsOnce(uid)?.currentStreak ?: 0 }.getOrDefault(0)
        val offset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
        val counts = runCatching { progressDao.reviewCountsByDay(uid, offset) }.getOrDefault(emptyList())
        val byDay = counts.associate { it.day to it.cnt }
        val today = LocalDate.now()
        val todayIdx = (today.dayOfWeek.value + 1) % 7 // Sat=0..Fri=6
        val start = today.toEpochDay() - todayIdx
        week = (0..6).map { i -> byDay[start + i] ?: 0 }
        todayDone = week[todayIdx]
    }

    val minutes = ((goal + 1) / 2).coerceIn(3, 30) // ~2 words/min feel

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
                title = "هدف روزانه",
                subtitle = "${faNum(goal)} واژه در روز",
                onBack = onBack,
            )

            Spacer(Modifier.height(6.dp))
            // hero: flame in ring + streak + today's tube
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(26.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpinningGoldRing(corner = 18.dp, modifier = Modifier.size(72.dp)) {
                        Image(
                            painterResource(R.drawable.ki_btn_fire),
                            null,
                            Modifier.size(44.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "روز پیوسته",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Ink,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                faNum(streak),
                                fontFamily = Lalezar,
                                fontSize = 26.sp,
                                color = Ink,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        CapsuleBar(fraction = (todayDone.toFloat() / goal).coerceIn(0f, 1f), height = 12.dp)
                        Text(
                            "امروز ${faNum(todayDone)} از ${faNum(goal)} واژهٔ کامل شده",
                            fontSize = 11.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            // week — هدف این هفته with real activity
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(26.dp))
                    .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("هدف این هفته", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                    Spacer(Modifier.height(12.dp))
                    WeekActivity(week, goal)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${faNum(week.count { it >= goal })} روز از ۷ روز این هفته کامل شده؛ تا یادگیری امروز می‌شود ${faNum(streak + 1)}!",
                        fontSize = 11.sp,
                        color = Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("هدفِ روزانه‌ات چند واژه باشه؟", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GOAL_OPTIONS.forEachIndexed { i, g ->
                    val selected = g == goal
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (selected) Gold else Cream,
                                RoundedCornerShape(18.dp),
                            )
                            .clickable { goal = g }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                faNum(g),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Ink,
                            )
                            Text(
                                GOAL_LABELS[i],
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Ink.copy(alpha = 0.7f) else Muted,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ki_a_clock), null, Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "حدود ${faNum(minutes)} دقیقه در روز را می‌گیرد",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ki_a_coin), null, Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "هر روز که به هدفت برسی ${faNum(goal * 2)} سکه می‌گیری.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            ClayButton(
                text = "ذخیرهٔ هدف روزانه",
                onClick = {
                    scope.launch {
                        runCatching { container.appPrefs.setDailyGoal(goal) }
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (saved) {
                Text(
                    "ذخیره شد ✓",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Gold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(26.dp))
    
        }
    }
}

@Composable
private fun WeekActivity(week: List<Int>, goal: Int) {
    val labels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val todayIdx = (LocalDate.now().dayOfWeek.value + 1) % 7
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, count ->
            val complete = count >= goal
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(
                            when {
                                complete -> Gold
                                count > 0 -> GoldLight
                                else -> Color(0xFFF1EDE4)
                            },
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (complete) {
                        Image(painterResource(R.drawable.ki_flame), null, Modifier.size(22.dp))
                    } else {
                        Text(
                            if (count > 0) faNum(count) else "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Ink,
                        )
                    }
                }
                Text(
                    labels[i],
                    fontSize = 11.sp,
                    fontWeight = if (i == todayIdx) FontWeight.Black else FontWeight.SemiBold,
                    color = if (i == todayIdx) Ink else Muted,
                )
            }
        }
    }
}
