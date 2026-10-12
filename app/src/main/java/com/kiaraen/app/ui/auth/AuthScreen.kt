package com.kiaraen.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.data.repository.NotConfiguredException
import com.kiaraen.app.data.sync.SyncWorker
import com.kiaraen.app.ui.common.KHead
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.launch

/** Auth modes — mirrors the approved design (download/kiara-app-login-preview.png). */
private enum class AuthMode { LOGIN, SIGNUP, FORGOT }

/**
 * Kiara auth gate: ورود / ثبت‌نام / فراموشی رمز + «ادامه بدون حساب».
 * Without Supabase keys everything except guest mode explains itself politely.
 *
 * [skipGuestRedirect] = opened on purpose from the «من» tab: a remembered
 * guest must SEE the form here instead of being bounced straight back.
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    skipGuestRedirect: Boolean = false,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var checking by remember { mutableStateOf(true) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun switchTo(next: AuthMode) {
        mode = next
        error = null
        info = null
    }

    suspend fun enter() {
        runCatching { container.appPrefs.setGuestMode(false) }
        runCatching { SyncWorker.enqueueOneShot(context, pull = true) }
        onAuthenticated()
    }

    LaunchedEffect(Unit) {
        // A cold-start network/storage hiccup here must show the login form,
        // never kill the app (guests can always continue offline).
        val restored = runCatching { container.authRepository.restoreSession() }.getOrNull()
        val rememberedGuest = !skipGuestRedirect &&
            runCatching { container.appPrefs.isGuest() }.getOrDefault(false)
        when {
            restored != null -> enter()
            rememberedGuest -> onAuthenticated()      // remembered guest → straight in
            else -> checking = false
        }
    }

    fun fail(t: Throwable) {
        val msg = t.message.orEmpty()
        error = when {
            t is NotConfiguredException ->
                "اتصال به سرور هنوز تنظیم نشده؛ فعلاً با «ادامه بدون حساب» وارد شو"
            msg.contains("Invalid login credentials", true) ->
                "ایمیل یا رمز عبور اشتباه است"
            msg.contains("already registered", true) ->
                "این ایمیل قبلاً ثبت شده؛ وارد شو"
            msg.contains("Email not confirmed", true) ->
                "اول ایمیلت را تأیید کن"
            msg.contains("rate limit", true) ->
                "چند تلاش ناموفق؛ کمی بعد دوباره امتحان کن"
            msg.contains("Unable to resolve host", true) ||
                msg.contains("timeout", true) ||
                msg.contains("Failed to connect", true) ||
                t is java.io.IOException ->
                "اینترنت وصل نیست یا سرور در دسترس نیست؛ دوباره امتحان کن"
            else -> "خطا: $msg"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (checking) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                color = GoldDark,
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))

            // back arrow (forgot-password only) — RTL: row start = top-right
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (mode == AuthMode.FORGOT) {
                    Text(
                        "→",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        modifier = Modifier
                            .clickable { switchTo(AuthMode.LOGIN) }
                            .padding(8.dp),
                    )
                }
            }

            // decoded once, downsampled, off the main thread (weak phones)
            KHead(size = 104.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "kiara",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                when (mode) {
                    AuthMode.LOGIN -> "خوش برگشتی!"
                    AuthMode.SIGNUP -> "کیارات رو بساز"
                    AuthMode.FORGOT -> "رمزت رو دوباره بگیر"
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (mode) {
                    AuthMode.LOGIN -> "ادامه یادگیری از همین‌جا"
                    AuthMode.SIGNUP -> "یه شروع تازه برای کلمه‌های تازه"
                    AuthMode.FORGOT -> "لینک بازیابی به ایمیلت ارسال می‌شه"
                },
                fontSize = 14.sp,
                color = Muted,
            )
            Spacer(Modifier.height(26.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = Line,
                cursorColor = GoldDark,
                focusedLabelColor = GoldDark,
            )

            if (mode == AuthMode.SIGNUP) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام") },
                    placeholder = { Text("نامت رو بنویس", color = Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("ایمیل") },
                placeholder = { Text("you@example.com", color = Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Next,
                ),
                colors = fieldColors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            if (mode != AuthMode.FORGOT) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("رمز عبور") },
                    placeholder = { Text("••••••••", color = Muted) },
                    singleLine = true,
                    visualTransformation =
                        if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showPass) "پنهان" else "نمایش",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showPass) GoldDark else Muted,
                            modifier = Modifier
                                .clickable { showPass = !showPass }
                                .padding(horizontal = 10.dp),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                    ),
                    colors = fieldColors,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == AuthMode.SIGNUP) {
                    Text(
                        "حداقل ۸ کاراکتر",
                        fontSize = 12.sp,
                        color = Muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (mode == AuthMode.LOGIN) {
                Text(
                    "رمزت رو فراموش کردی؟",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { switchTo(AuthMode.FORGOT) }
                        .padding(vertical = 6.dp),
                )
            }

            if (mode == AuthMode.SIGNUP) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable { terms = !terms },
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(
                                if (terms) Gold else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(6.dp),
                            )
                            .border(
                                1.5.dp,
                                if (terms) GoldDark else Line,
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (terms) Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "قوانین استفاده و حریم خصوصی را می‌پذیرم",
                        fontSize = 13.sp,
                        color = Ink,
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            info?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = GoldDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(18.dp))

            if (busy) {
                CircularProgressIndicator(color = GoldDark)
            } else {
                Button(
                    onClick = {
                        busy = true; error = null; info = null
                        scope.launch {
                            try {
                                when (mode) {
                                    AuthMode.LOGIN -> {
                                        if (email.isBlank() || password.isBlank()) {
                                            error = "ایمیل و رمز عبور رو بنویس"
                                        } else {
                                            container.authRepository.signIn(email.trim(), password)
                                                .onSuccess { enter() }
                                                .onFailure { fail(it) }
                                        }
                                    }
                                    AuthMode.SIGNUP -> {
                                        when {
                                            name.isBlank() -> error = "نامت رو بنویس"
                                            email.isBlank() -> error = "ایمیلت رو بنویس"
                                            password.length < 8 -> error = "رمز حداقل ۸ کاراکتر باشه"
                                            !terms -> error = "قوانین رو بپذیر تا ادامه بدیم"
                                            else -> container.authRepository
                                                .signUp(email.trim(), password, name.trim())
                                                .onSuccess {
                                                    // "Confirm email" ON → account exists but has
                                                    // no session yet: explain instead of entering
                                                    // the app half-signed-in.
                                                    val uid = runCatching {
                                                        container.authRepository.currentUserId()
                                                    }.getOrNull()
                                                    if (uid != null) {
                                                        enter()
                                                    } else {
                                                        password = ""
                                                        switchTo(AuthMode.LOGIN)
                                                        info = "حساب ساخته شد؛ لینک تأیید به ایمیلت رفت. بعد از تأیید همین‌جا وارد شو"
                                                    }
                                                }
                                                .onFailure { fail(it) }
                                        }
                                    }
                                    AuthMode.FORGOT -> {
                                        if (email.isBlank()) {
                                            error = "ایمیلت رو بنویس"
                                        } else {
                                            container.authRepository.resetPassword(email.trim())
                                                .onSuccess {
                                                    info = "لینک بازیابی به ایمیلت اومد"
                                                }
                                                .onFailure { fail(it) }
                                        }
                                    }
                                }
                            } catch (c: kotlinx.coroutines.CancellationException) {
                                throw c
                            } catch (t: Throwable) {
                                fail(t)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink,
                        contentColor = Color(0xFFFFFDF7),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(
                        when (mode) {
                            AuthMode.LOGIN -> "ورود"
                            AuthMode.SIGNUP -> "ساخت حساب"
                            AuthMode.FORGOT -> "ارسال لینک بازیابی"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                if (mode != AuthMode.FORGOT) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        HorizontalDivider(Modifier.weight(1f), color = Line)
                        Text(
                            "یا",
                            fontSize = 13.sp,
                            color = Muted,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        HorizontalDivider(Modifier.weight(1f), color = Line)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { info = "ورود با گوگل به‌زودی فعال می‌شه" },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = Ink,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    ) {
                        Text(
                            if (mode == AuthMode.LOGIN) "ورود با گوگل" else "ثبت‌نام با گوگل",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    if (mode == AuthMode.LOGIN) {
                        // was plain text in 0.2.1 → the signup form was unreachable
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { switchTo(AuthMode.SIGNUP) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "حساب نداری؟ ",
                                fontSize = 14.sp,
                                color = Muted,
                            )
                            Text(
                                "ثبت‌نام کن",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "ادامه بدون حساب",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        runCatching { container.appPrefs.setGuestMode(true) }
                                        onAuthenticated()
                                    }
                                }
                                .padding(10.dp),
                        )
                    } else {
                        Text(
                            "قبلاً حساب ساختی؟",
                            fontSize = 14.sp,
                            color = Muted,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "وارد شو",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable { switchTo(AuthMode.LOGIN) }
                                .padding(10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
