package com.kiaraen.app.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.BuildConfig
import com.kiaraen.app.R
import com.kiaraen.app.data.remote.SupabaseProvider
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.launch

/**
 * «من» tab: identity + account actions.
 * Guest → big ورود/ثبت‌نام CTA; signed-in → email + خروج.
 * The version string comes straight from BuildConfig so it never drifts.
 */
@Composable
fun ProfileScreen(
    onGoAuth: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val userId = rememberUserId()

    var email by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        if (SupabaseProvider.isConfigured && userId != "local") {
            runCatching { container.authRepository.userEmail() }.getOrNull()
                ?.let { email = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .size(110.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.khead),
                contentDescription = null,
                modifier = Modifier.size(92.dp).clip(CircleShape),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            email ?: "مهمان",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
        )
        Text(
            if (userId == "local") "پیشرفتت روی همین دستگاه ذخیره می‌شه"
            else "حسابت متصله؛ پیشرفتت همه‌جا همگام می‌شه",
            fontSize = 13.sp,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(26.dp))

        if (userId == "local") {
            Button(
                onClick = onGoAuth,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = Color(0xFFFFFDF7),
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("ورود یا ثبت‌نام", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "با ساخت حساب، همین پیشرفت منتقل می‌شه",
                fontSize = 12.sp,
                color = Muted,
            )
        } else {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        container.authRepository.signOut()
                        container.appPrefs.setGuestMode(false)
                        busy = false
                        onSignedOut()
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    "خروج از حساب کاربری",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            Text("وضعیت سرور", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
            Text(
                if (SupabaseProvider.isConfigured) "متصل" else "تنظیم نشده (آفلاین)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (SupabaseProvider.isConfigured) GoldDark else Muted,
            )
        }
        HorizontalDivider(color = Line)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            Text("نسخه برنامه", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
            Text(
                BuildConfig.VERSION_NAME,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Muted,
            )
        }
    }
}
