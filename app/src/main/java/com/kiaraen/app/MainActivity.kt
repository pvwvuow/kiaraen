package com.kiaraen.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.kiaraen.app.core.AppContainer
import com.kiaraen.app.ui.AppRoot
import com.kiaraen.app.ui.theme.KiaraTheme

class MainActivity : ComponentActivity() {

    val container: AppContainer by lazy { (application as KiaraApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 → Android 15 is ALWAYS edge-to-edge. Opt in explicitly on
        // every version (consistent look) with dark status-bar icons, because
        // the design is light-only (cream background).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            // Persian-first UI → right-to-left everywhere
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KiaraTheme {
                    // One inset boundary for the whole app: status bar, nav bar,
                    // display cutout AND the keyboard. Consumed here, so inner
                    // Scaffold/NavigationBar don't pad twice.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .safeDrawingPadding(),
                    ) {
                        AppRoot(container)
                    }
                }
            }
        }
    }
}
