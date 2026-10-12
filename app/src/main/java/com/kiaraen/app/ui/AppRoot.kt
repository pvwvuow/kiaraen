package com.kiaraen.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    const val AUTH = "auth"
    const val MAIN = "main"
    const val QUIZ = "quiz/{quizRef}"
    fun quiz(ref: String) = "quiz/$ref"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "خانه", Icons.Filled.Home),
    Tab("levels", "سطح‌ها", Icons.Filled.MenuBook),
    Tab("collections", "مجموعه‌ها", Icons.Filled.CollectionsBookmark),
    Tab("progress", "پیشرفت", Icons.Filled.Insights),
    Tab("profile", "من", Icons.Filled.Person),
)

@Composable
fun AppRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val navController = rememberNavController()
        // AuthScreen redirects automatically when a stored session exists.
        val start = Routes.AUTH

        NavHost(navController = navController, startDestination = start) {
            composable(Routes.AUTH) {
                AuthScreen(
                    onAuthenticated = {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.MAIN) {
                MainTabs(navController)
            }
            composable(Routes.QUIZ) { entry ->
                QuizScreen(
                    quizRef = entry.arguments?.getString("quizRef") ?: "",
                    onExit = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun MainTabs(navController: androidx.navigation.NavHostController) {
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onOpenQuiz = { ref -> navController.navigate(Routes.quiz(ref)) },
                    onOpenLevels = { navController.navigate("levels") },
                )
            }
            composable("levels") { LevelsScreen(onOpenQuiz = { ref -> navController.navigate(Routes.quiz(ref)) }) }
            composable("collections") { CollectionsScreen(onOpenQuiz = { ref -> navController.navigate(Routes.quiz(ref)) }) }
            composable("progress") { ProgressScreen() }
            composable("profile") {
                ProfileScreen(
                    onGoAuth = { navController.navigate(Routes.AUTH) },
                    onSignedOut = {
                        navController.navigate(Routes.AUTH) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
