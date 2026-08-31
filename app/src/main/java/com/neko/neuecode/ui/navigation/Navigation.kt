package com.neko.neuecode.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.repository.AuthRepository
import com.neko.neuecode.domain.model.SessionState
import com.neko.neuecode.ui.screen.intranet.IntranetVpnScreen
import com.neko.neuecode.ui.screen.paycode.ECodeWebViewScreen
import com.neko.neuecode.ui.screen.paycode.PayCodeScreen
import com.neko.neuecode.ui.screen.personal.PersonalScreen
import com.neko.neuecode.ui.screen.recharge.RechargeScreen
import com.neko.neuecode.ui.screen.schedule.JwxtScheduleScreen

private data class BottomBarDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomBarDestinations = listOf(
    BottomBarDestination(MainDestinations.PAY, MainDestinations.LABEL_PAY, Icons.Default.QrCode),
    BottomBarDestination(MainDestinations.SCHEDULE, MainDestinations.LABEL_SCHEDULE, Icons.Default.DateRange),
    BottomBarDestination(MainDestinations.ME, MainDestinations.LABEL_ME, Icons.Default.Person),
)

@Composable
fun MainAppScreen(
    sessionState: SessionState.Authenticated,
    cookieJar: PersistentCookieJar,
    userPreferences: UserPreferences,
    authRepository: AuthRepository,
    initialStartRoute: String = MainDestinations.PAY,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || MainDestinations.isBottomBar(currentRoute)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomBarDestinations.forEach { destination ->
                        NavigationBarItem(
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            selected = currentRoute == destination.route,
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
            startDestination = MainDestinations.resolveStartRoute(initialStartRoute),
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                fadeIn(tween(220)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(280),
                )
            },
            exitTransition = {
                fadeOut(tween(180)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(240),
                )
            },
            popEnterTransition = {
                fadeIn(tween(220)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(280),
                )
            },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(240),
                )
            },
        ) {
            composable(MainDestinations.PAY) {
                PayCodeScreen(
                    onOpenPayCode = { navController.navigate(MainDestinations.openPayCodeRoute) },
                    onOpenRecharge = { navController.navigate(MainDestinations.RECHARGE) },
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
        }
    }
}
