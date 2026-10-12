package com.kiaraen.app.ui

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kiaraen.app.core.AppContainer
import com.kiaraen.app.ui.auth.AuthScreen
import com.kiaraen.app.ui.collections.CollectionsScreen
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.home.HomeScreen
import com.kiaraen.app.ui.levels.LevelsScreen
import com.kiaraen.app.ui.profile.ProfileScreen
import com.kiaraen.app.ui.progress.ProgressScreen
import com.kiaraen.app.ui.quiz.QuizScreen

object Routes {
    /** fresh=true → opened on purpose (profile CTA): never auto-skip for guests. */
    const val AUTH = "auth?fresh={fresh}"
    const val MAIN = "main"
    const val QUIZ = "quiz/{quizRef}"
    fun auth(fresh: Boolean) = "auth?fresh=$fresh"
    fun quiz(ref: String) = "quiz/" + Uri.encode(ref)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "خانه", Icons.Filled.Home),
    Tab("levels", "سطح‌ها", Icons.Filled.MenuBook),
    Tab("collections", "مجموعه‌ها", Icons.Filled.CollectionsBookmark),
    Tab("progress", "پیشرفت", Icons.Filled.Insights),
    Tab("profile", "من", Icons.Filled.Person),
)

/** Navigation's default is a 700 ms crossfade — sluggish on weak phones. */
private const val SCREEN_FADE_MS = 150

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/**
 * NavController must only be touched on the main thread. Auth/sign-out
 * callbacks fire after suspend calls (DataStore, supabase session status)
 * that can resume on a worker thread → hop to main before navigating.
 */
private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}

/**
 * Two-level navigation:
 *   root  → auth | main | quiz          (rootNav)
 *   main  → home | levels | ... tabs    (tabsNav, its OWN controller)
 *
 * ⚠️ Never hand the root controller to the inner NavHost: a NavController
 * can drive exactly one NavHost. Sharing it makes the inner host call
 * setViewModelStore() after setGraph() → IllegalStateException ("ViewModelStore
 * should be set before setGraph call") the moment MAIN opens, and replaces the
 * root graph so quiz/auth routes stop existing. That was the v0.2.1 crash.
 */
@Composable
fun AppRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val rootNav = rememberNavController()

        NavHost(
            navController = rootNav,
            startDestination = Routes.AUTH,
            enterTransition = { fadeIn(tween(SCREEN_FADE_MS)) },
            exitTransition = { fadeOut(tween(SCREEN_FADE_MS)) },
        ) {
            composable(
                route = Routes.AUTH,
                arguments = listOf(
                    navArgument("fresh") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                AuthScreen(
                    skipGuestRedirect = entry.arguments?.getBoolean("fresh") ?: false,
                    onAuthenticated = { onMain { rootNav.openMain() } },
                )
            }
            composable(Routes.MAIN) {
                MainTabs(rootNav)
            }
            composable(Routes.QUIZ) { entry ->
                QuizScreen(
                    quizRef = entry.arguments?.getString("quizRef").orEmpty(),
                    onExit = {
                        // double-tap on «بازگشت» must not pop MAIN as well
                        if (rootNav.currentDestination?.route == Routes.QUIZ) rootNav.popBackStack()
                    },
                )
            }
        }
    }
}

/** MAIN becomes the only root entry (no way "back" into the auth gate). */
private fun NavHostController.openMain() {
    if (currentDestination?.route == Routes.MAIN) return
    navigate(Routes.MAIN) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun MainTabs(rootNav: NavHostController) {
    val container = LocalAppContainer.current
    val tabsNav = rememberNavController()

    // Signed in → everything learned as a guest ("local") belongs to the
    // account right away. Waiting for a network sync made guest progress look
    // lost after login whenever the phone was offline.
    LaunchedEffect(Unit) {
        runCatching {
            val uid = container.authRepository.currentUserId()
            if (uid != null) container.progressRepository.linkLocalData(uid)
        }.onFailure { Log.w("AppRoot", "guest hand-off failed (sync retries it)", it) }
    }

    fun selectTab(route: String) {
        if (tabsNav.currentDestination?.route == route) return
        tabsNav.navigate(route) {
            popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openQuiz: (String) -> Unit = { ref ->
        rootNav.navigate(Routes.quiz(ref)) { launchSingleTop = true }
    }

    Scaffold(
        bottomBar = {
            val backStack by tabsNav.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { selectTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
            }
        },
    ) { padding ->
        // bottom tabs switch instantly: no animation work at all
        NavHost(
            navController = tabsNav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenQuiz = openQuiz,
                    onOpenLevels = { selectTab("levels") },
                )
            }
            composable("levels") { LevelsScreen(onOpenQuiz = openQuiz) }
            composable("collections") { CollectionsScreen(onOpenQuiz = openQuiz) }
            composable("progress") { ProgressScreen() }
            composable("profile") {
                ProfileScreen(
                    onGoAuth = {
                        rootNav.navigate(Routes.auth(fresh = true)) { launchSingleTop = true }
                    },
                    onSignedOut = {
                        onMain {
                            rootNav.navigate(Routes.auth(fresh = false)) {
                                popUpTo(rootNav.graph.id) { inclusive = true }
                            }
                        }
                    },
                )
            }
        }
    }
}
