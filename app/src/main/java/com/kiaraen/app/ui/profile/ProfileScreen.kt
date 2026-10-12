package com.kiaraen.app.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.BuildConfig
import com.kiaraen.app.KiaraApplication
import com.kiaraen.app.R
import com.kiaraen.app.data.remote.SupabaseProvider
import com.kiaraen.app.ui.common.LOCAL_USER_ID
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.KiaraLogotype
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.kiara.showSoon
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.GoldLight
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * تب «من» — دقیقاً مطابق طرح تأییدشده: کارت پروفایل با تب طلایی، آواتار،
 * قفسه نشان‌ها، ردیف‌های هدف روزانه/یادآور/تنظیمات/راهنما + ورود/خروج حساب.
 */
@Composable
fun ProfileScreen(
    onGoAuth: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    val userId = rememberUserId()
    val app = KiaraApplication.instance

    var email by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var crashReport by remember { mutableStateOf(app?.lastCrashReport()) }
    var dailyGoal by remember { mutableStateOf(15) }
    var statsSnapshot by remember { mutableStateOf<Pair<Int, Int>?>(null) } // learned, streak

    LaunchedEffect(userId) {
        if (SupabaseProvider.isConfigured && userId != LOCAL_USER_ID) {
            runCatching { container.authRepository.userEmail() }.getOrNull()?.let { email = it }
        }
        dailyGoal = runCatching { container.appPrefs.dailyGoal.first() }.getOrDefault(15)
        val dao = container.database.progressDao()
        val s = runCatching { dao.statsOnce(userId) }.getOrNull()
        statsSnapshot = (s?.totalLearned ?: 0) to (s?.currentStreak ?: 0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("من", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.weight(1f))
            KiaraLogotype(size = 28.sp)
        }

        Spacer(Modifier.height(16.dp))
        ProfileCard(
            name = email?.substringBefore('@') ?: "مهمان",
            subtitle = if (userId == LOCAL_USER_ID) "مهمان — پیشرفت روی همین دستگاه"
            else "حسابت متصل است",
            learned = statsSnapshot?.first ?: 0,
            streak = statsSnapshot?.second ?: 0,
            onEdit = { onGoAuth() },
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("نشان‌های من", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        BadgeShelf(
            learned = statsSnapshot?.first ?: 0,
            streak = statsSnapshot?.second ?: 0,
        )

        Spacer(Modifier.height(20.dp))
        // rows
        SettingRow(iconRes = R.drawable.ki_target, title = "هدف روزانه", value = "${faNum(dailyGoal)} واژه") {
            val options = listOf(10, 15, 20, 30, 50)
            val picked = options[(options.indexOf(dailyGoal) + 1) % options.size]
            dailyGoal = picked
            scope.launch { runCatching { container.appPrefs.setDailyGoal(picked) } }
        }
        KiaraDivider()
        SettingRow(iconRes = R.drawable.ki_bell, title = "یادآور یادگیری", value = "۲۰:۰۰") {
            showSoon(context)
        }
        KiaraDivider()
        SettingRow(iconRes = R.drawable.ki_gear, title = "تنظیمات", value = "") {
            showSoon(context)
        }
        KiaraDivider()
        SettingRow(iconRes = R.drawable.ki_chat, title = "راهنما و پشتیبانی", value = "") {
            showSoon(context)
        }
        KiaraDivider()

        // account section (existing logic, design-styled)
        Spacer(Modifier.height(18.dp))
        if (userId == LOCAL_USER_ID) {
            ClayButton(text = "ورود یا ثبت‌نام", onClick = onGoAuth)
            Spacer(Modifier.height(6.dp))
            Text(
                "با ساخت حساب، همین پیشرفت منتقل می‌شود",
                fontSize = 11.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Cream, RoundedCornerShape(25.dp))
                    .clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                container.authRepository.signOut()
                                runCatching { container.appPrefs.setGuestMode(false) }
                            } finally {
                                busy = false
                            }
                            onSignedOut()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "خروج از حساب کاربری",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFB3261E),
                )
            }
        }

        // crash report (keeps the two-tap debug path from v0.2.1+)
        crashReport?.let { report ->
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3F0), RoundedCornerShape(16.dp))
                    .clickable {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Kiaraen crash report")
                            putExtra(android.content.Intent.EXTRA_TEXT, report.take(4000))
                        }
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(share, "گزارش خطا"),
                            )
                        }
                        app?.clearCrashReport()
                        crashReport = null
                    }
                    .padding(14.dp),
            ) {
                Text(
                    "گزارش آخرین خطا — ارسال برای تیم",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB3261E),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("وضعیت سرور", fontSize = 12.sp, color = Muted)
            Text(
                if (SupabaseProvider.isConfigured) "متصل" else "تنظیم نشده (آفلاین)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (SupabaseProvider.isConfigured) GoldDark else Muted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("نسخه برنامه", fontSize = 12.sp, color = Muted)
            Text(BuildConfig.VERSION_NAME, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** کارت پروفایل — تصویر کارت تأییدشده + محتوا. */
@Composable
private fun ProfileCard(
    name: String,
    subtitle: String,
    learned: Int,
    streak: Int,
    onEdit: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Column {
            // gold tab (top-right under RTL: first child of the next Row)
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(Gold, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                ) {
                    Text("پروفایل من", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ink)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent),
            ) {
                Image(
                    painterResource(R.drawable.ki_pcard),
                    null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(850f / 525f),
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, top = 26.dp, end = 30.dp, bottom = 20.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Ink,
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .background(GoldLight, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(subtitle, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .background(Cream, RoundedCornerShape(15.dp))
                                .clickable { onEdit() }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painterResource(R.drawable.ki_ppencil),
                                    null,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "ویرایش پروفایل",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Ink,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${faNum(learned)} واژه آموخته",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                            )
                            Spacer(Modifier.width(16.dp))
                            Image(
                                painterResource(R.drawable.ki_flame),
                                null,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                " ${faNum(streak)} روز پیوسته",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                            )
                        }
                    }
                    // avatar inset + avatar
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.ki_pinset),
                            null,
                            modifier = Modifier.size(width = 104.dp, height = 136.dp),
                        )
                        Image(
                            painterResource(R.drawable.ki_pavatar),
                            null,
                            modifier = Modifier.size(width = 86.dp, height = 114.dp),
                        )
                    }
                }
            }
        }
    }
}

/** قفسه نشان‌ها — سه نشان روی قفسه، برچسب زیر قفسه. */
@Composable
private fun BadgeShelf(learned: Int, streak: Int) {
    val badges = listOf(
        Triple(R.drawable.ki_trophy_gold, "اولین مجموعه", true),
        Triple(R.drawable.ki_books, "۱۰۰ واژه", learned >= 100),
        Triple(R.drawable.ki_flame, "۷ روز پیوسته", streak >= 7),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            badges.forEach { (res, _, _) ->
                Image(
                    painterResource(res),
                    null,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Image(
            painterResource(R.drawable.ki_pshelf),
            null,
            modifier = Modifier.fillMaxWidth().aspectRatio(700f / 106f),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            badges.forEach { (_, label, _) ->
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

/** ردیف تنظیمات: آیکون راست + عنوان + مقدار + شورون چپ. */
@Composable
private fun SettingRow(
    iconRes: Int,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(iconRes), null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, fontSize = 12.sp, color = Muted)
            Spacer(Modifier.width(8.dp))
        }
        Text("‹", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Muted)
    }
}
