package com.neko.neuecode.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.repository.AuthRepository
import com.neko.neuecode.domain.model.SessionState
import com.neko.neuecode.ui.screen.academic.JwxtAcademicScreen
import com.neko.neuecode.ui.screen.intranet.IntranetVpnScreen
import com.neko.neuecode.ui.screen.paycode.ECodeWebViewScreen
import com.neko.neuecode.ui.screen.paycode.PayCodeScreen
import com.neko.neuecode.ui.screen.personal.PersonalScreen
import com.neko.neuecode.ui.screen.recharge.RechargeScreen
import com.neko.neuecode.ui.screen.schedule.JwxtScheduleScreen
import com.neko.neuecode.ui.theme.panel

private data class BottomBarDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val bottomBarDestinations = listOf(
    BottomBarDestination(MainDestinations.PAY, MainDestinations.LABEL_PAY, Icons.Outlined.QrCode2, Icons.Filled.QrCode2),
    BottomBarDestination(
        MainDestinations.SCHEDULE,
        MainDestinations.LABEL_SCHEDULE,
        Icons.Outlined.CalendarMonth,
        Icons.Filled.CalendarMonth,
    ),
    BottomBarDestination(MainDestinations.ME, MainDestinations.LABEL_ME, Icons.Outlined.Person, Icons.Filled.Person),
)

/**
 * Between bottom-bar tabs the slide follows tab order (pay → schedule → me),
 * whatever the back stack does; secondary screens keep push/pop direction.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection(
    default: AnimatedContentTransitionScope.SlideDirection,
): AnimatedContentTransitionScope.SlideDirection {
    val from = MainDestinations.bottomBar.indexOf(initialState.destination.route)
    val to = MainDestinations.bottomBar.indexOf(targetState.destination.route)
    if (from < 0 || to < 0 || from == to) return default
    return if (to > from) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
}

@Composable
fun MainAppScreen(
    sessionState: SessionState.Authenticated,
    cookieJar: PersistentCookieJar,
    userPreferences: UserPreferences,
    authRepository: AuthRepository,
    initialStartRoute: String = MainDestinations.resolveStartRoute(null),
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || MainDestinations.isBottomBar(currentRoute)
    val graphStart = when {
        initialStartRoute == MainDestinations.widgetExamRoute -> MainDestinations.ME
        else -> MainDestinations.resolveStartRoute(initialStartRoute)
    }
    LaunchedEffect(initialStartRoute) {
        if (initialStartRoute == MainDestinations.widgetExamRoute) {
            navController.navigate(MainDestinations.widgetExamRoute)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.panel) {
                    bottomBarDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = graphStart,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                fadeIn(tween(220)) + slideIntoContainer(
                    slideDirection(AnimatedContentTransitionScope.SlideDirection.Start),
                    tween(280),
                )
            },
            exitTransition = {
                fadeOut(tween(180)) + slideOutOfContainer(
                    slideDirection(AnimatedContentTransitionScope.SlideDirection.Start),
                    tween(240),
                )
            },
            popEnterTransition = {
                fadeIn(tween(220)) + slideIntoContainer(
                    slideDirection(AnimatedContentTransitionScope.SlideDirection.End),
                    tween(280),
                )
            },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutOfContainer(
                    slideDirection(AnimatedContentTransitionScope.SlideDirection.End),
                    tween(240),
                )
            },
        ) {
            composable(MainDestinations.PAY) {
                PayCodeScreen(
                    onOpenPayCode = {
                        if (com.neko.neuecode.domain.ecode.EcodeModuleAvailability.shouldOpenPayCodeWebView()) {
                            navController.navigate(MainDestinations.openPayCodeRoute)
                        }
                    },
                    onOpenRecharge = {
                        if (com.neko.neuecode.domain.ecode.EcodeModuleAvailability.shouldOpenRecharge()) {
                            navController.navigate(MainDestinations.RECHARGE)
                        }
                    },
                )
            }

            composable(MainDestinations.SCHEDULE) {
                JwxtScheduleScreen(
                    onOpenIntranet = { navController.navigate(MainDestinations.INTRANET) },
                )
            }

            composable(MainDestinations.ME) {
                PersonalScreen(
                    sessionState = sessionState,
                    cookieJar = cookieJar,
                    userPreferences = userPreferences,
                    authRepository = authRepository,
                    onLogout = onLogout,
                    onOpenIntranet = { navController.navigate(MainDestinations.INTRANET) },
                    onOpenScores = { navController.navigate(MainDestinations.academicRoute(MainDestinations.SCORES)) },
                    onOpenExams = { navController.navigate(MainDestinations.academicRoute(MainDestinations.EXAMS)) },
                )
            }

            composable(MainDestinations.RECHARGE) {
                RechargeScreen(onBack = { navController.popBackStack() })
            }

            composable(MainDestinations.ECODE_WEBVIEW) {
                ECodeWebViewScreen(onBack = { navController.popBackStack() })
            }

            composable(MainDestinations.INTRANET) {
                IntranetVpnScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = MainDestinations.ACADEMIC,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) {
                JwxtAcademicScreen(
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(MainDestinations.ME) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenIntranet = { navController.navigate(MainDestinations.INTRANET) },
                )
            }
        }
    }
}
