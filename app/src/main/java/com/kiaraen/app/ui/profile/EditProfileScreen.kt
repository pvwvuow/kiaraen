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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldLight
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn

/**
 * ویرایش پروفایل — the approved #edit screen: the pinset frame with the big
 * avatar, the 3-avatar picker (gold ring on selection), the name/mobile
 * fields (real inputs; mobile is a local display value), the level pill
 * from real learned counts, and the save CTA writing to AppPrefs.
 */
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val uid = rememberUserId()

    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf<String?>(null) }
    var avatar by remember { mutableIntStateOf(0) }
    var learned by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        name = container.appPrefs.displayName.first()
        avatar = container.appPrefs.avatarIndex.first()
        learned = runCatching {
            container.database.progressDao().statsOnce(uid)?.totalLearned ?: 0
        }.getOrDefault(0)
        if (uid != com.kiaraen.app.ui.common.LOCAL_USER_ID) {
            email = runCatching { container.authRepository.userEmail() }.getOrNull()
        }
    }

    val levelPill = when {
        learned >= 1200 -> "C1"
        learned >= 900 -> "B2"
        learned >= 600 -> "B1"
        learned >= 300 -> "A2"
        else -> "A1"
    }
    val nextLevel = when (levelPill) {
        "A1" -> "A2"; "A2" -> "B1"; "B1" -> "B2"; "B2" -> "C1"; else -> "C2"
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
                title = "ویرایش پروفایل",
                subtitle = "پروفایلت را به‌روز کن",
                onBack = onBack,
            )

            Spacer(Modifier.height(10.dp))
            // avatar frame
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(R.drawable.ki_pinset),
                    null,
                    modifier = Modifier.size(width = 128.dp, height = 158.dp),
                )
                Image(
                    painterResource(AVATARS[avatar]),
                    null,
                    modifier = Modifier.size(width = 96.dp, height = 126.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AVATARS.forEachIndexed { i, res ->
                    val selected = i == avatar
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .size(58.dp)
                            .background(Cream, CircleShape)
                            .then(
                                if (selected) Modifier.border(3.dp, Gold, CircleShape)
                                else Modifier.border(1.5.dp, Color(0xFFE7E2D4), CircleShape),
                            )
                            .clickable { avatar = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(res), null, Modifier.size(44.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("مشخصات", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Muted)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Column {
                    Text("نام نمایشی", fontSize = 11.sp, color = Muted)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        cursorBrush = SolidColor(Gold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (name.isEmpty()) Text("مثلاً زهرا", fontSize = 14.sp, color = Muted)
                                inner()
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Column {
                    Text(email?.let { "ایمیل" } ?: "شماره موبایل", fontSize = 11.sp, color = Muted)
                    Text(
                        email ?: mobile.ifBlank { "۰۹۱۲ ۳۴۷ ۰۰۲۱" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("سطح تو", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Muted)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مسیر پیشرفت", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "${faNum(learned)} واژه آموخته‌ای؛ مقصد بعدی: سطح $nextLevel",
                            fontSize = 11.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        Modifier
                            .background(GoldLight, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "سطح $levelPill",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Ink,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            ClayButton(
                text = "ذخیرهٔ تغییرات",
                onClick = {
                    scope.launch {
                        runCatching { container.appPrefs.setDisplayName(name) }
                        runCatching { container.appPrefs.setAvatarIndex(avatar) }
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

private val AVATARS = listOf(
    R.drawable.ki_pavatar,
    R.drawable.ki_person,
    R.drawable.ki_navy,
)
