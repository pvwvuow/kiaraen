package com.kiaraen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.kiaraen.app.core.AppContainer
import com.kiaraen.app.ui.AppRoot
import com.kiaraen.app.ui.theme.KiaraTheme

class MainActivity : ComponentActivity() {

    val container: AppContainer by lazy { (application as KiaraApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Persian-first UI → right-to-left everywhere
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KiaraTheme {
                    AppRoot(container)
                }
            }
        }
    }
}
