package com.spendwise.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spendwise.R
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.ui.api.SessionEvents
import com.spendwise.ui.navigation.Routes
import com.spendwise.ui.screens.budget.BudgetScreen
import com.spendwise.ui.screens.chatbot.ChatSessionsScreen
import com.spendwise.ui.screens.chatbot.ChatThreadScreen
import com.spendwise.ui.screens.dashboard.DashboardScreen
import com.spendwise.ui.screens.emis.EmisScreen
import com.spendwise.ui.screens.onboarding.BackfillScreen
import com.spendwise.ui.screens.onboarding.ConsentScreen
import com.spendwise.ui.screens.onboarding.PermissionsScreen
import com.spendwise.ui.screens.onboarding.QuestionnaireScreen
import com.spendwise.ui.screens.onboarding.SignUpScreen
import com.spendwise.ui.screens.settings.SettingsScreen
import com.spendwise.ui.screens.transactions.TransactionDetailScreen
import com.spendwise.ui.screens.transactions.TransactionsScreen
import com.spendwise.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var session: DeviceSessionStore

    @Inject lateinit var sessionEvents: SessionEvents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpendWiseTheme {
                SpendWiseApp(
                    startDestination = resolveStartDestination(),
                    sessionEvents = sessionEvents,
                )
            }
        }
    }

    /**
     * Resumes the onboarding wizard at the first incomplete step (docs/user_flows.md
     * Onboarding), or goes straight to the dashboard for a fully onboarded session.
     */
    private fun resolveStartDestination(): String = when {
        session.getUserJwt() == null -> Routes.SIGN_UP
        session.getDeviceApiKey() == null -> Routes.CONSENT
        !hasSmsPermission() -> Routes.PERMISSIONS
        !session.isQuestionnaireDone() -> Routes.QUESTIONNAIRE
        !session.isBackfillDone() -> Routes.BACKFILL
        else -> Routes.DASHBOARD
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
}

private data class BottomNavItem(val route: String, val icon: ImageVector, val label: String)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.DASHBOARD, Icons.Filled.Home, "Home"),
    BottomNavItem(Routes.TRANSACTIONS, Icons.AutoMirrored.Filled.List, "Transactions"),
    BottomNavItem(Routes.BUDGET, Icons.Filled.AccountBalanceWallet, "Budget"),
    BottomNavItem(Routes.EMIS, Icons.Filled.Autorenew, "EMIs"),
    BottomNavItem(Routes.CHATBOT, Icons.AutoMirrored.Filled.Chat, "Chat"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendWiseApp(startDestination: String, sessionEvents: SessionEvents) {
    val navController = rememberNavController()

    val expired by sessionEvents.sessionExpired.collectAsState()
    LaunchedEffect(expired) {
        if (expired) {
            navController.navigate(Routes.SIGN_UP) { popUpTo(0) { inclusive = true } }
            sessionEvents.acknowledgeSessionExpired()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The transactions destination is registered with an optional `?category={categoryId}`
    // suffix, so tab identity is the route pattern's path segment only.
    val currentTabRoute = currentRoute?.substringBefore('?')
    val isMainRoute = currentRoute != null && currentRoute.startsWith("main/")
    val isPrimaryTab = remember(currentTabRoute) { bottomNavItems.any { it.route == currentTabRoute } }

    Scaffold(
        topBar = {
            if (isPrimaryTab) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isMainRoute) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentTabRoute == item.route,
                            onClick = { navController.navigateToTab(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            // --- Onboarding wizard (E9-S1) ---
            composable(Routes.SIGN_UP) {
                SignUpScreen(onSignedUp = {
                    navController.navigate(Routes.CONSENT) { popUpTo(0) { inclusive = true } }
                })
            }
            composable(Routes.CONSENT) {
                ConsentScreen(onConsented = {
                    navController.navigate(Routes.PERMISSIONS) { popUpTo(0) { inclusive = true } }
                })
            }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onPermissionsResolved = {
                    navController.navigate(Routes.QUESTIONNAIRE) { popUpTo(0) { inclusive = true } }
                })
            }
            composable(Routes.QUESTIONNAIRE) {
                QuestionnaireScreen(onQuestionnaireDone = {
                    navController.navigate(Routes.BACKFILL) { popUpTo(0) { inclusive = true } }
                })
            }
            composable(Routes.BACKFILL) {
                BackfillScreen(onBackfillComplete = {
                    navController.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
                })
            }

            // --- Main app (E9-S2) ---
            composable(Routes.DASHBOARD) {
                DashboardScreen(onCategoryClick = { categoryId ->
                    navController.navigate(Routes.transactionsByCategory(categoryId))
                })
            }
            composable(
                route = Routes.TRANSACTIONS_BY_CATEGORY,
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                TransactionsScreen(onTransactionClick = { id ->
                    navController.navigate(Routes.transactionDetail(id))
                })
            }
            composable(Routes.TRANSACTION_DETAIL) {
                TransactionDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BUDGET) { BudgetScreen() }
            composable(Routes.EMIS) { EmisScreen() }
            composable(Routes.CHATBOT) {
                ChatSessionsScreen(onSessionClick = { sessionId ->
                    navController.navigate(Routes.chatThread(sessionId))
                })
            }
            composable(Routes.CHAT_THREAD) {
                ChatThreadScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onLoggedOut = {
                    navController.navigate(Routes.SIGN_UP) { popUpTo(0) { inclusive = true } }
                })
            }
        }
    }
}

/** Standard bottom-nav behavior: single top, restore tab state, pop to the graph start. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
