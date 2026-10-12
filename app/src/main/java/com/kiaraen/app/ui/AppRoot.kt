package com.kiaraen.app.ui

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.home.HomeScreen
import com.kiaraen.app.ui.kiara.KiaraBottomBar
import com.kiaraen.app.ui.levels.LevelsScreen
import com.kiaraen.app.ui.profile.ProfileScreen
import com.kiaraen.app.ui.progress.ProgressScreen
import com.kiaraen.app.ui.quiz.QuizScreen
import com.kiaraen.app.ui.words.CollectionDetailScreen
import com.kiaraen.app.ui.words.WordsScreen

object Routes {
    /** fresh=true → opened on purpose (profile CTA): never auto-skip for guests. */
    const val AUTH = "auth?fresh={fresh}"
    const val MAIN = "main"
    const val QUIZ = "quiz/{quizRef}"
    const val COLLECTION = "collection/{id}"
    const val LEVELS = "levels?level={level}"
    fun auth(fresh: Boolean) = "auth?fresh=$fresh"
    fun quiz(ref: String) = "quiz/" + Uri.encode(ref)
    fun collection(id: Long) = "collection/$id"
    fun levels(level: String? = null) = if (level == null) "levels" else "levels?level=$level"
}

/** The four approved tabs — bottom bar shows only on these routes. */
private val TAB_ROUTES = setOf("home", "words", "progress", "profile")

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
 *   main  → home | words | ... + details (tabsNav, its OWN controller)
 *
 * ⚠️ Never hand the root controller to the inner NavHost: a NavController
 * can drive exactly one NavHost. Sharing it made the inner host call
 * setViewModelStore() after setGraph() → IllegalStateException (v0.2.1 crash).
 */
@Composable
fun AppRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val rootNav = rememberNavController()

        NavHost(navController = rootNav, startDestination = Routes.AUTH) {
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

    // The approved design is RTL (Persian-first).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val backStack by tabsNav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route
        val showBar = current in TAB_ROUTES

        Box {
            NavHost(
                navController = tabsNav,
                startDestination = "home",
                modifier = Modifier.padding(bottom = if (showBar) 92.dp else 0.dp),
            ) {
                composable("home") {
                    HomeScreen(
                        onOpenQuiz = openQuiz,
                        onOpenCollection = { id ->
                            tabsNav.navigate(Routes.collection(id)) { launchSingleTop = true }
                        },
                        onSeeAllCollections = { selectTab("words") },
                    )
                }
                composable("words") {
                    WordsScreen(
                        onOpenCollection = { id ->
                            tabsNav.navigate(Routes.collection(id)) { launchSingleTop = true }
                        },
                        onOpenLevel = { levelId ->
                            tabsNav.navigate(Routes.levels(levelId)) { launchSingleTop = true }
                        },
                    )
                }
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
                composable(
                    route = Routes.COLLECTION,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    CollectionDetailScreen(
                        collectionId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { tabsNav.popBackStack() },
                        onOpenQuiz = openQuiz,
                    )
                }
                composable(
                    route = Routes.LEVELS,
                    arguments = listOf(
                        navArgument("level") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    LevelsScreen(
                        onOpenQuiz = openQuiz,
                        directLevel = entry.arguments?.getString("level")?.takeUnless { it.isEmpty() },
                    )
                }
            }

            if (showBar) {
                KiaraBottomBar(
                    currentRoute = current,
                    onSelect = { selectTab(it) },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter),
                )
            }
        }
    }
}

