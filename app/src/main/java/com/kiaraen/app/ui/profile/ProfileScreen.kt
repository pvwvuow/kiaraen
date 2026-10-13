package com.kiaraen.app.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.words.currentCefr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val GoldTab = Brush.verticalGradient(listOf(Color(0xFFFFD34D), Color(0xFFF5B301)))
private val RowDivider = Color(0x12000000) // rgba(28,26,23,.07)
private val RowInk = Color(0xFF29261F)

/**
 * تب «من» — بازسازی یک‌به‌یک kiara-app.html (#scr-profile):
 * کارت پروفایل با تب طلایی، نام/پیل سطح/دکمهٔ ویرایش، قاب آواتار، خط جداکننده و
 * دو آمار؛ قفسهٔ نشان‌ها (جام/کتاب/شعله)؛ چهار ردیف هدف/یادآور/تنظیمات/راهنما.
 * همهٔ منطق قبلی (حساب، خروج، گزارش کرش، نسخه) دست‌نخورده باقی است.
 * ریسپانسیو: ستون محتوا روی صفحه‌های پهن ۵۲۰dp وسط‌چین می‌شود.
 */
@Composable
fun ProfileScreen(
    onGoAuth: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenGoal: () -> Unit = {},
    onOpenRemind: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenEdit: () -> Unit = {},
    onOpenAwards: () -> Unit = {},
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
    var displayName by remember { mutableStateOf("") }
    var avatarIndex by remember { mutableStateOf(0) }
    var remindTime by remember { mutableStateOf("۲۰:۰۰") }
    var statsSnapshot by remember { mutableStateOf<Pair<Int, Int>?>(null) } // learned, streak

    LaunchedEffect(userId) {
        if (SupabaseProvider.isConfigured && userId != LOCAL_USER_ID) {
            runCatching { container.authRepository.userEmail() }.getOrNull()?.let { email = it }
        }
        dailyGoal = runCatching { container.appPrefs.dailyGoal.first() }.getOrDefault(15)
        displayName = runCatching { container.appPrefs.displayName.first() }.getOrDefault("")
        avatarIndex = runCatching { container.appPrefs.avatarIndex.first() }.getOrDefault(0)
        val h = runCatching { container.appPrefs.remindHour.first() }.getOrDefault(20)
        val m = runCatching { container.appPrefs.remindMinute.first() }.getOrDefault(0)
        remindTime = "${faNum(h)}:${faNum(m).padStart(2, '۰')}"
        val dao = container.database.progressDao()
        val s = runCatching { dao.statsOnce(userId) }.getOrNull()
        statsSnapshot = (s?.totalLearned ?: 0) to (s?.currentStreak ?: 0)
    }

    val learned = statsSnapshot?.first ?: 0
    val streak = statsSnapshot?.second ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            // ── header: «من» + kiara ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "من",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                com.kiaraen.app.ui.kiara.KiaraLogotype(size = 35.sp)
            }

            Spacer(Modifier.height(20.dp))
            // ── gold tab (attached to the card top, right side in RTL) ──
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(GoldTab, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    Text(
                        "پروفایل من",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }
            // ── profile card (approved artwork) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(850f / 612f),
            ) {
                Image(
                    painterResource(R.drawable.ki_pcard),
                    null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 26.dp, end = 30.dp, top = 40.dp, bottom = 26.dp),
                ) {
                    // avatar + inset frame (right side in RTL)
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.ki_pinset),
                            null,
                            modifier = Modifier.size(width = 122.dp, height = 150.dp),
                        )
                        Image(
                            painterResource(avatarRes(avatarIndex)),
                            null,
                            modifier = Modifier.size(width = 88.dp, height = 114.dp),
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    // name / level / edit (left of avatar, right-aligned text)
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            displayName.ifBlank { email?.substringBefore('@') ?: "مهمان" },
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            Modifier
                                .padding(top = 10.dp)
                                .background(Cream, RoundedCornerShape(percent = 50))
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        ) {
                            Text(
                                "سطح ${currentCefr(learned)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Ink,
                            )
                        }
                        // «ویرایش پروفایل» — white pill, dark 1.6dp outline
                        Row(
                            Modifier
                                .padding(top = 16.dp)
                                .background(Color(0xFFFFFDF8), RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onOpenEdit() }
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .background(Color.Transparent, RoundedCornerShape(14.dp))
                                    .border2dp(),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(
                                        painterResource(R.drawable.ki_ppencil),
                                        null,
                                        modifier = Modifier.height(14.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "ویرایش پروفایل",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Ink,
                                    )
                                }
                            }
                        }
                    }
                }
                // divider + stats pinned to the card bottom
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 36.dp, vertical = 24.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.4.dp)
                            .background(RowDivider),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${faNum(learned)} واژه آموخته",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Image(
                            painterResource(R.drawable.ki2_badge_flame),
                            null,
                            modifier = Modifier.height(22.dp),
                        )
                        Text(
                            " ${faNum(streak)} روز پیوسته",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Muted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            // ── badges ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "نشان‌های من",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "دیدن همه",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                    modifier = Modifier.clickable { onOpenAwards() },
                )
            }
            Spacer(Modifier.height(12.dp))
            BadgeShelf(learned = learned, streak = streak, onSeeAll = onOpenAwards)

            Spacer(Modifier.height(26.dp))
            // ── settings rows (exact icon set + values from real prefs) ──
            SettingRow(
                iconRes = R.drawable.ki_target,
                title = "هدف روزانه",
                value = "${faNum(dailyGoal)} واژه",
                height = 58.dp,
                onClick = onOpenGoal,
            )
            RowDividerLine()
            SettingRow(
                iconRes = R.drawable.ki_bell,
                title = "یادآور یادگیری",
                value = remindTime,
                height = 52.dp,
                onClick = onOpenRemind,
            )
            RowDividerLine()
            SettingRow(
                iconRes = R.drawable.ki_gear,
                title = "تنظیمات",
                value = "",
                height = 52.dp,
                onClick = onOpenSettings,
            )
            RowDividerLine()
            SettingRow(
                iconRes = R.drawable.ki2_topic_chat,
                title = "راهنما و پشتیبانی",
                value = "",
                height = 50.dp,
                onClick = onOpenHelp,
            )
            RowDividerLine()

            // ── account section (existing logic, design-styled) ──
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
}

private fun avatarRes(index: Int): Int = when (index) {
    1 -> R.drawable.ki_person
    2 -> R.drawable.ki_navy
    else -> R.drawable.ki_pavatar
}

/** قفسه نشان‌ها — سه نشان دقیق طرح روی قفسه با برچسب‌ها. */
@Composable
private fun BadgeShelf(learned: Int, streak: Int, onSeeAll: () -> Unit = {}) {
    val badges = listOf(
        Triple(R.drawable.ki2_badge_trophy, "اولین مجموعه", true),
        Triple(R.drawable.ki2_folder_books, "۱۰۰ واژه", learned >= 100),
        Triple(R.drawable.ki2_badge_flame, "۷ روز پیوسته", streak >= 7),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            badges.forEach { (res, _, _) ->
                if (res == R.drawable.ki2_folder_books) {
                    // books-b: width:145px, height auto (approved CSS)
                    Image(
                        painterResource(res),
                        null,
                        modifier = Modifier.width(64.dp),
                    )
                } else {
                    // trophy-b / flame-b: height:115px, width auto
                    Image(
                        painterResource(res),
                        null,
                        modifier = Modifier.height(52.dp),
                    )
                }
            }
        }
        Image(
            painterResource(R.drawable.ki_pshelf),
            null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(700f / 106f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            badges.forEach { (_, label, _) ->
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6E675A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(88.dp)
                        .clickable { onSeeAll() },
                )
            }
        }
    }
}

/** ردیف تنظیمات — آیکون سه‌بعدی راست، عنوان، مقدار خاکستری، شورون چپ. */
@Composable
private fun SettingRow(
    iconRes: Int,
    title: String,
    value: String,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(iconRes), null, modifier = Modifier.height(if (iconRes == R.drawable.ki_bell) 27.dp else 29.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = RowInk,
        )
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted)
            Spacer(Modifier.width(10.dp))
        }
        // chevron «next» — the tiny approved asset
        Image(
            painterResource(R.drawable.ki_pchev),
            null,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun RowDividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RowDivider),
    )
}

/** outline 3.5px→1.6dp #29261F for the edit pill (kept tiny + local). */
private fun Modifier.border2dp(): Modifier =
    this.border(1.6.dp, RowInk, RoundedCornerShape(14.dp))
