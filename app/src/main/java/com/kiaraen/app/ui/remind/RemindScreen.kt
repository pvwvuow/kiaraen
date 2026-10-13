package com.kiaraen.app.ui.remind

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.core.ReminderScheduler
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.KiaraSwitch
import com.kiaraen.app.ui.kiara.OutlineButton
import com.kiaraen.app.ui.kiara.SegmentedPill
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldLight
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Lalezar
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn

/**
 * یادآور یادگیری — the approved #remind screen wired to a REAL notification:
 * master switch, big time pill + 4 quick times, 7 toggleable weekday
 * circles, the smart-time switch, and «آزمایش یادآور» posting immediately.
 * Settings persist in AppPrefs; ReminderScheduler keeps a 30-min checker.
 */
private val TIME_CHIPS = listOf(8 to 0, 14 to 0, 20 to 0, 22 to 0)

@Composable
fun RemindScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var enabled by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf(20) }
    var minute by remember { mutableStateOf(0) }
    var days by remember { mutableStateOf(DayOfWeek.entries.map { it.name }.toSet()) }
    var smart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = container.appPrefs.remindEnabled.first()
        hour = container.appPrefs.remindHour.first()
        minute = container.appPrefs.remindMinute.first()
        days = container.appPrefs.remindDays.first()
        smart = container.appPrefs.remindSmart.first()
    }

    fun persist() {
        val e = enabled; val h = hour; val m = minute; val d = days; val s = smart
        scope.launch {
            runCatching { container.appPrefs.setReminder(e, h, m, d, s) }
            runCatching { ReminderScheduler.reschedule(context, e) }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) {
            ReminderScheduler.postTest(context)
        }
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
                title = "یادآور یادگیری",
                subtitle = "هر روز ساعت ${faNum(hour)}:${"%02d".format(minute)}",
                onBack = onBack,
            )

            Spacer(Modifier.height(6.dp))
            // master switch card
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ki_bell), null, Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("یادآور روزانه", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                        Text(
                            "یادت می‌ندازه بالاتر بری که امروز را تمام کنی",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                    KiaraSwitch(on = enabled, onChange = {
                        enabled = it
                        persist()
                    })
                }
            }

            Spacer(Modifier.height(12.dp))
            // time card
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ki_a_clock), null, Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ساعت یادآور", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "%02d:%02d".format(hour, minute)
                            .map { if (it.isDigit()) faNum(it.digitToInt()).toString() else it.toString() }
                            .joinToString(""),
                        fontFamily = Lalezar,
                        fontSize = 40.sp,
                        color = Ink,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TIME_CHIPS.forEachIndexed { i, (h, m) ->
                            val selected = h == hour
                            Box(
                                Modifier
                                    .background(
                                        if (selected) Gold else Color(0xFFF1EDE4),
                                        RoundedCornerShape(percent = 50),
                                    )
                                    .clickable { hour = h; persist() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    faNum(h) + ":" + faNum(m) + "۰",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Ink,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // weekdays
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${faNum(days.size)} روز فعال است",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                        Text("روزهای هفته", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                    }
                    Spacer(Modifier.height(14.dp))
                    WeekdayCircles(days) { picked ->
                        days = picked
                        persist()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // smart time
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("زمان هوشمند", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                        Text(
                            "در ساعاتی که بیشتر یاد می‌گیری کمتر می‌کنیم",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                    KiaraSwitch(on = smart, onChange = {
                        smart = it
                        persist()
                    })
                }
            }

            Spacer(Modifier.height(18.dp))
            OutlineButton(
                text = "آزمایش یادآور",
                onClick = {
                    if (ReminderScheduler.hasNotificationPermission(context)) {
                        ReminderScheduler.postTest(context)
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                leading = {
                    Image(painterResource(R.drawable.ki_bell), null, Modifier.size(22.dp))
                },
            )
            Spacer(Modifier.height(26.dp))
    
        }
    }
}

/** هفت دایره روز هفته — تاگل‌پذیر با شمارنده (طرح تأییدشده). */
@Composable
private fun WeekdayCircles(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    // RTL visual order: شنبه first on the right
    val labels = listOf(
        "ش" to DayOfWeek.SATURDAY, "ی" to DayOfWeek.SUNDAY, "د" to DayOfWeek.MONDAY,
        "س" to DayOfWeek.TUESDAY, "چ" to DayOfWeek.WEDNESDAY, "پ" to DayOfWeek.THURSDAY,
        "ج" to DayOfWeek.FRIDAY,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { (label, day) ->
            val on = day.name in selected
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (on) Gold else Color(0xFFF1EDE4), CircleShape)
                    .clickable {
                        onChange(if (on) selected - day.name else selected + day.name)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (on) Ink else Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
