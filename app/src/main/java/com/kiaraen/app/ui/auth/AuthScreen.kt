package com.kiaraen.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kiaraen.app.data.sync.SyncWorker
import com.kiaraen.app.ui.common.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Email/password auth against Supabase GoTrue.
 * A stored session from a previous run redirects straight to MAIN.
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val uid = container.authRepository.restoreSession()
        if (uid != null) {
            SyncWorker.enqueueOneShot(context, pull = true)
            onAuthenticated()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("کیارا انگلیش", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("ایمیل") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("رمز عبور") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))

        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        container.authRepository.signIn(email.trim(), password)
                            .onSuccess {
                                SyncWorker.enqueueOneShot(context, pull = true)
                                onAuthenticated()
                            }
                            .onFailure { error = "ورود ناموفق بود: ${it.message}" }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ورود") }
            TextButton(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        container.authRepository.signUp(email.trim(), password, null)
                            .onSuccess {
                                SyncWorker.enqueueOneShot(context, pull = true)
                                onAuthenticated()
                            }
                            .onFailure { error = "ثبت‌نام ناموفق بود: ${it.message}" }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ساخت حساب جدید") }
        }
    }
}
