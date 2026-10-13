package com.kiaraen.app.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kiaraen.app.BuildConfig
import com.kiaraen.app.core.ReminderScheduler
import com.kiaraen.app.core.TtsPlayer
import com.kiaraen.app.data.local.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The approved kiara design, pixel for pixel: the prototype page (built from
 * web/kiara-app.html by the `prepareKiaraWeb` Gradle task) runs full screen in
 * a WebView. Everything the page cannot do itself comes from [Bridge]:
 * English speech, real reminder notifications, share / rate, haptics and a
 * file backup of the learner's saved state. Android back walks the page's own
 * screens first and only then closes the app.
 */
class KiaraWebHost(private val activity: ComponentActivity) {

    companion object {
        const val ENTRY = "web/index.html"
        private const val URL = "file:///android_asset/$ENTRY"
        private const val BACKUP = "kiara-web-state.json"
        val BG: Int = Color.parseColor("#F7F6F3")

        /** True when the build packed the web UI (and we are not in a JVM unit test). */
        fun isAvailable(context: Context): Boolean {
            if (Build.FINGERPRINT == "robolectric") return false
            return runCatching { context.assets.open(ENTRY).close(); true }.getOrDefault(false)
        }
    }

    private var webView: WebView? = null
    private var pageReady = false
    private var insetTopCss = 0f
    private var onPermission: ((Boolean) -> Unit)? = null
    private val prefs by lazy { AppPrefs(activity.applicationContext) }

    // must be registered while the activity is being created
    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermission?.invoke(granted)
            onPermission = null
        }

    /** Builds the screen. Returns false (→ native UI) if this phone has no usable WebView. */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun attach(savedState: Bundle?): Boolean {
        val wv = try {
            WebView(activity)
        } catch (t: Throwable) {
            return false // WebView missing or updating right now
        }
        webView = wv
        wv.setBackgroundColor(BG)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            textZoom = 100 // the design is drawn to scale; system font size must not break it
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowContentAccess = false
            allowFileAccess = false // android_asset pages still load
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = false
        }
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.isLongClickable = false
        wv.setOnLongClickListener { true }
        wv.addJavascriptInterface(Bridge(), "KiaraNative")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                openExternal(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageReady = true
                pushInsets()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // the page renderer was killed (low memory on weak phones): start over, don't crash
                (view.parent as? ViewGroup)?.removeView(view)
                runCatching { view.destroy() }
                webView = null
                activity.recreate()
                return true
            }
        }

        val root = FrameLayout(activity).apply { setBackgroundColor(BG) }
        root.addView(
            wv,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        // Draw under the status bar (the design has its own status row there),
        // keep clear of the navigation bar and side cutouts.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val side = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            v.setPadding(side.left, 0, side.right, side.bottom)
            insetTopCss = top / v.resources.displayMetrics.density
            pushInsets()
            WindowInsetsCompat.CONSUMED
        }
        activity.setContentView(root)

        val restored = savedState != null && wv.restoreState(savedState) != null
        if (!restored) wv.loadUrl(URL)

        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = webView
                    if (view == null || !pageReady) {
                        activity.finish()
                        return
                    }
                    view.evaluateJavascript("(window.kiaraBack && window.kiaraBack()) ? 1 : 0") { r ->
                        if (r != "1") activity.finish()
                    }
                }
            },
        )
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    webView?.onResume()
                    if (pageReady) webView?.evaluateJavascript("window.KL && KL.onResume && KL.onResume()", null)
                }

                override fun onPause(owner: LifecycleOwner) {
                    webView?.onPause()
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    runCatching { webView?.destroy() }
                    webView = null
                }
            },
        )
        TtsPlayer.warmUp(activity.applicationContext)
        return true
    }

    fun saveState(out: Bundle) {
        webView?.saveState(out)
    }

    private fun pushInsets() {
        if (!pageReady) return
        webView?.evaluateJavascript("window.kiaraSetInsetTop && kiaraSetInsetTop($insetTopCss)", null)
    }

    private fun openExternal(uri: Uri) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun withNotificationPermission(cb: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            cb(true)
            return
        }
        onPermission = cb
        runCatching { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            .onFailure {
                onPermission = null
                cb(false)
            }
    }

    /** Called by the page (window.KiaraNative) on a background thread. */
    inner class Bridge {
        private val app: Context get() = activity.applicationContext

        @JavascriptInterface
        fun speak(text: String?, rate: Double) {
            val t = text?.trim().orEmpty()
            if (t.isEmpty()) return
            val r = when {
                rate < 0.8 -> "slow"
                rate > 1.2 -> "fast"
                else -> "normal"
            }
            activity.runOnUiThread { TtsPlayer.speakWord(app, t, r) }
        }

        @JavascriptInterface
        fun setReminder(on: Boolean, hour: Int, minute: Int, days: String?, smart: Boolean, fromUser: Boolean) {
            val set = days.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            activity.lifecycleScope.launch(Dispatchers.IO) {
                runCatching { prefs.setReminder(on, hour, minute, set, smart) }
                runCatching { ReminderScheduler.reschedule(app, on) }
            }
            if (on && fromUser) activity.runOnUiThread { withNotificationPermission { } }
        }

        @JavascriptInterface
        fun testReminder() {
            activity.runOnUiThread {
                withNotificationPermission { ok -> if (ok) ReminderScheduler.postTest(app) }
            }
        }

        @JavascriptInterface
        fun share(text: String?) {
            activity.runOnUiThread {
                runCatching {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text.orEmpty())
                    activity.startActivity(Intent.createChooser(send, null))
                }
            }
        }

        @JavascriptInterface
        fun rate() {
            activity.runOnUiThread {
                val id = activity.packageName
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
                } catch (e: ActivityNotFoundException) {
                    openExternal(Uri.parse("https://play.google.com/store/apps/details?id=$id"))
                } catch (t: Throwable) {
                    // nothing to open on this phone
                }
            }
        }

        @JavascriptInterface
        fun haptic(kind: String?) {
            activity.runOnUiThread {
                val c = when (kind) {
                    "ok" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                    "bad" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                    else -> HapticFeedbackConstants.KEYBOARD_TAP
                }
                runCatching {
                    webView?.performHapticFeedback(c)
                    webView?.playSoundEffect(SoundEffectConstants.CLICK)
                }
            }
        }

        @JavascriptInterface
        fun appVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun saveBackup(json: String?) {
            if (json.isNullOrEmpty()) return
            runCatching {
                val dir = activity.filesDir
                val tmp = File(dir, "$BACKUP.tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(File(dir, BACKUP))) tmp.delete()
            }
        }

        @JavascriptInterface
        fun loadBackup(): String =
            runCatching { File(activity.filesDir, BACKUP).takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
    }
}
