package com.kiaraen.app.ui.settings

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.BuildConfig
import com.kiaraen.app.R
import com.kiaraen.app.ui.common.LOCAL_USER_ID
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.KiaraSwitch
import com.kiaraen.app.ui.kiara.SegmentedPill
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Danger
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn

/**
 * تنظیمات — the approved #settings screen: three groups (یادگیری / حساب کاربری
 * / دربارهٔ kiara) with real switches + the pronunciation-speed segment,
 * saved-words counter, rate/invite/share rows and the sign-out pill.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenSavedWords: () -> Unit,
    onSignOut: () -> Unit,
    onGoAuth: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userId = rememberUserId()

    var sounds by remember { mutableStateOf(true) }
    var rate by remember { mutableStateOf(1) }       // 0=slow 1=normal 2=fast
    var meaningFirst by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf(0) }
    var signingOut by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        sounds = container.appPrefs.soundsOn.first()
        rate = when (container.appPrefs.speechRate.first()) {
            "slow" -> 0
            "fast" -> 2
            else -> 1
        }
        meaningFirst = container.appPrefs.showMeaningFirst.first()
        savedCount = runCatching {
            container.database.progressDao().bookmarkedWords(userId).size
        }.getOrDefault(0)
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
            DetailHeader(title = "تنظیمات", subtitle = "شخصی‌سازی kiara", onBack = onBack)

            GroupLabel("یادگیری")
            GroupCard {
                SwitchRow(R.drawable.ki_g_speaker, "صداها", "افکت‌ها و صدای درست و غلط", sounds) {
                    sounds = it
                    scope.launch { runCatching { container.appPrefs.setSoundsOn(it) } }
                }
                KiaraDivider(Modifier.padding(horizontal = 16.dp))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ki_g_mic), null, Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("سرعت تلفظ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.weight(1f))
                        SegmentedPill(
                            options = listOf("کند", "معمولی", "سریع"),
                            selected = rate,
                            onSelect = { i ->
                                rate = i
                                scope.launch {
                                    runCatching {
                                        container.appPrefs.setSpeechRate(listOf("slow", "normal", "fast")[i])
                                    }
                                }
                            },
                        )
                    }
                }
                KiaraDivider(Modifier.padding(horizontal = 16.dp))
                SwitchRow(R.drawable.ki_g_chart, "نمایش خودکار معنی", "پشت فلش‌کارت بدون اشاره باز شود", meaningFirst) {
                    meaningFirst = it
                    scope.launch { runCatching { container.appPrefs.setShowMeaningFirst(it) } }
                }
            }

            GroupLabel("حساب کاربری")
            GroupCard {
                NavRow(R.drawable.ki_person, "ویرایش پروفایل", "نام و آواتار تو") { onOpenEditProfile() }
                KiaraDivider(Modifier.padding(horizontal = 16.dp))
                NavRow(R.drawable.ki_ui_bookmark, "واژه‌های ذخیره‌شده", "${faNum(savedCount)} واژه") {
                    onOpenSavedWords()
                }
            }

            GroupLabel("دربارهٔ kiara")
            GroupCard {
                NavRow(R.drawable.ki_trophy_gold, "امتیاز به kiara", "اگر لذت بردی به ما ستاره بده") {
                    runCatching {
                        val i = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/pvwvuow/kiaraen"),
                        )
                        context.startActivity(i)
                    }
                }
                KiaraDivider(Modifier.padding(horizontal = 16.dp))
                NavRow(R.drawable.ki_g_gift, "اشتراک‌گذاری اپ", "معرفی kiara به دوستات") {
                    runCatching {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "کیارا — یادگیری انگلیسی با روش تکرار فاصله‌دار:\nhttps://github.com/pvwvuow/kiaraen/releases",
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(i, "اشتراک‌گذاری kiara"))
                    }
                }
                KiaraDivider(Modifier.padding(horizontal = 16.dp))
                InfoRow("نسخهٔ برنامه", BuildConfig.VERSION_NAME)
            }

            Spacer(Modifier.height(18.dp))
            if (userId == LOCAL_USER_ID) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(percent = 50))
                        .clickable { onGoAuth() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "ورود یا ثبت‌نام",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(percent = 50))
                        .clickable(enabled = !signingOut) {
                            signingOut = true
                            scope.launch {
                                runCatching { container.authRepository.signOut() }
                                runCatching { container.appPrefs.setGuestMode(false) }
                                onSignOut()
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (signingOut) "در حال خروج…" else "خروج از حساب کاربری",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Danger,
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
    
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = Muted,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(24.dp))
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(subtitle, fontSize = 11.sp, color = Muted)
        }
        KiaraSwitch(on = checked, onChange = onChange)
    }
}

@Composable
private fun NavRow(icon: Int, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = Muted)
        Spacer(Modifier.width(8.dp))
        Text("‹", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Muted)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ki_aacal), null, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
    }
}
