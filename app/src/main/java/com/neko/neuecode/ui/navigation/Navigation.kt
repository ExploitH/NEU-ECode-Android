package com.neko.neuecode.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.repository.AuthRepository
import com.neko.neuecode.domain.model.SessionState
import com.neko.neuecode.ui.screen.ecode.ECodeScreen
import com.neko.neuecode.ui.screen.personal.PersonalScreen
import com.neko.neuecode.ui.screen.recharge.RechargeScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object ECode : Screen("ecode", "e码通", Icons.Default.QrCode)
    object Recharge : Screen("recharge", "充值", Icons.Default.AccountBalanceWallet)
    object Personal : Screen("personal", "我的", Icons.Default.Person)
}

@Composable
fun MainAppScreen(
    sessionState: SessionState.Authenticated,
    cookieJar: PersistentCookieJar,
    userPreferences: UserPreferences,
    authRepository: AuthRepository,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.ECode) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val screens = listOf(Screen.ECode, Screen.Recharge, Screen.Personal)
                
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = selectedScreen == screen,
                        onClick = {
                            selectedScreen = screen
                            navController.navigate(screen.route) {
                                // Pop up to the start destination
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // Avoid multiple copies
                                launchSingleTop = true
                                // Restore state
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.ECode.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.ECode.route) {
                ECodeScreen()
            }

            composable(Screen.Recharge.route) {
                RechargeScreen()
            }
            
            composable(Screen.Personal.route) {
                PersonalScreen(
                    sessionState = sessionState,
                    cookieJar = cookieJar,
                    userPreferences = userPreferences,
                    authRepository = authRepository,
                    onLogout = onLogout
                )
            }
        }
    }
}
