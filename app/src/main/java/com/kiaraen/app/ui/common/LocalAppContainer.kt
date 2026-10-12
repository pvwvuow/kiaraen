package com.kiaraen.app.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.kiaraen.app.core.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
