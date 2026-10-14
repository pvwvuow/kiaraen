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
import com.kiaraen.app.ui.awards.AwardsScreen
import com.kiaraen.app.ui.colls.CollsScreen
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.goal.GoalScreen
import com.kiaraen.app.ui.help.HelpScreen
import com.kiaraen.app.ui.home.HomeScreen
import com.kiaraen.app.ui.kiara.KiaraBottomBar
import com.kiaraen.app.ui.learn.LearnScreen
import com.kiaraen.app.ui.levels.LevelsScreen
import com.kiaraen.app.ui.mistakes.MistakesScreen
import com.kiaraen.app.ui.profile.EditProfileScreen
import com.kiaraen.app.ui.profile.ProfileScreen
import com.kiaraen.app.ui.progress.ProgressScreen
import com.kiaraen.app.ui.quiz.QuizScreen
import com.kiaraen.app.ui.remind.RemindScreen
import com.kiaraen.app.ui.review.ReviewScreen
import com.kiaraen.app.ui.settings.SavedWordsScreen
import com.kiaraen.app.ui.settings.SettingsScreen
import com.kiaraen.app.ui.words.CollectionDetailScreen
import com.kiaraen.app.ui.words.WordsScreen

object Routes {
    /** fresh=true → opened on purpose (profile CTA): never auto-skip for guests. */
    const val AUTH = "auth?fresh={fresh}"
    const val MAIN = "main"
    const val QUIZ = "quiz/{quizRef}"
    const val COLLECTION = "collection/{id}"
    const val LEVELS = "levels?level={level}"
    const val LEARN = "learn/{ref}?mode={mode}"
    const val REVIEW = "review"
    const val AWARDS = "awards"
    const val GOAL = "goal"
    const val REMIND = "remind"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val EDIT = "edit"
    const val COLLS = "colls"
    const val SAVED = "saved"
    const val MISTAKES = "mistakes"

    fun auth(fresh: Boolean) = "auth?fresh=$fresh"
    fun quiz(ref: String) = "quiz/" + Uri.encode(ref)
    fun collection(id: Long) = "collection/$id"
    fun levels(level: String? = null) = if (level == null) "levels" else "levels?level=$level"
    fun learn(ref: String, mode: String = "flash") = "learn/" + Uri.encode(ref) + "?mode=$mode"
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
 *   root  → auth | main | quiz | learn | review      (rootNav)
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
            composable(
                route = Routes.QUIZ,
                arguments = listOf(navArgument("quizRef") { type = NavType.StringType }),
            ) { entry ->
                QuizScreen(
                    quizRef = entry.arguments?.getString("quizRef").orEmpty(),
                    onExit = {
                        // double-tap on «بازگشت» must not pop MAIN as well
                        if (rootNav.currentDestination?.route == Routes.QUIZ) rootNav.popBackStack()
                    },
                )
            }
            composable(
                route = Routes.LEARN,
                arguments = listOf(
                    navArgument("ref") { type = NavType.StringType },
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "flash"
                    },
                ),
            ) { entry ->
                LearnScreen(
                    ref = entry.arguments?.getString("ref").orEmpty(),
                    mode = entry.arguments?.getString("mode") ?: "flash",
                    onExit = { if (rootNav.currentDestination?.route?.startsWith("learn/") == true) rootNav.popBackStack() },
                    onOpenQuiz = { ref -> rootNav.navigate(Routes.quiz(ref)) { launchSingleTop = true } },
                )
            }
            composable(Routes.REVIEW) {
                ReviewScreen(onExit = { if (rootNav.currentDestination?.route == Routes.REVIEW) rootNav.popBackStack() })
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
    val openLearn: (String, String) -> Unit = { ref, mode ->
        rootNav.navigate(Routes.learn(ref, mode)) { launchSingleTop = true }
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
                        onOpenReview = { rootNav.navigate(Routes.REVIEW) { launchSingleTop = true } },
                        onOpenLearn = openLearn,
                        onOpenCollection = { id ->
                            tabsNav.navigate(Routes.collection(id)) { launchSingleTop = true }
                        },
                        onSeeAllCollections = { selectTab("words") },
                        onOpenMistakes = {
                            tabsNav.navigate(Routes.MISTAKES) { launchSingleTop = true }
                        },
                        onOpenProgress = { selectTab("progress") },
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
                        onOpenColls = {
                            tabsNav.navigate(Routes.COLLS) { launchSingleTop = true }
                        },
                        onOpenSaved = {
                            tabsNav.navigate(Routes.SAVED) { launchSingleTop = true }
                        },
                        onOpenHelp = {
                            tabsNav.navigate(Routes.HELP) { launchSingleTop = true }
                        },
                    )
                }
                composable("progress") { ProgressScreen(onOpenQuiz = openQuiz) }
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
                        onOpenGoal = { tabsNav.navigate(Routes.GOAL) { launchSingleTop = true } },
                        onOpenRemind = { tabsNav.navigate(Routes.REMIND) { launchSingleTop = true } },
                        onOpenSettings = { tabsNav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onOpenHelp = { tabsNav.navigate(Routes.HELP) { launchSingleTop = true } },
                        onOpenEdit = { tabsNav.navigate(Routes.EDIT) { launchSingleTop = true } },
                        onOpenAwards = { tabsNav.navigate(Routes.AWARDS) { launchSingleTop = true } },
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
                        onOpenLearn = openLearn,
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
                        onOpenLearn = openLearn,
                        directLevel = entry.arguments?.getString("level")?.takeUnless { it.isEmpty() },
                        onBack = { tabsNav.popBackStack() },
                    )
                }
                composable(Routes.COLLS) {
                    CollsScreen(
                        onBack = { tabsNav.popBackStack() },
                        onOpenCollection = { id ->
                            tabsNav.navigate(Routes.collection(id)) { launchSingleTop = true }
                        },
                    )
                }
                composable(Routes.GOAL) { GoalScreen(onBack = { tabsNav.popBackStack() }) }
                composable(Routes.REMIND) { RemindScreen(onBack = { tabsNav.popBackStack() }) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { tabsNav.popBackStack() },
                        onOpenEditProfile = { tabsNav.navigate(Routes.EDIT) { launchSingleTop = true } },
                        onOpenSavedWords = { tabsNav.navigate(Routes.SAVED) { launchSingleTop = true } },
                        onSignOut = {
                            onMain {
                                rootNav.navigate(Routes.auth(fresh = false)) {
                                    popUpTo(rootNav.graph.id) { inclusive = true }
                                }
                            }
                        },
                        onGoAuth = {
                            rootNav.navigate(Routes.auth(fresh = true)) { launchSingleTop = true }
                        },
                    )
                }
                composable(Routes.HELP) { HelpScreen(onBack = { tabsNav.popBackStack() }) }
                composable(Routes.EDIT) { EditProfileScreen(onBack = { tabsNav.popBackStack() }) }
                composable(Routes.AWARDS) { AwardsScreen(onBack = { tabsNav.popBackStack() }) }
                composable(Routes.SAVED) { SavedWordsScreen(onBack = { tabsNav.popBackStack() }) }
                composable(Routes.MISTAKES) {
                    MistakesScreen(
                        onBack = { tabsNav.popBackStack() },
                        onOpenQuiz = openQuiz,
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
