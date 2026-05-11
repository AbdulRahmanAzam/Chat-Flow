package com.chatflow.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatflow.app.data.AppContainer
import com.chatflow.app.ui.screens.*

sealed class Route(val path: String) {
    data object Onboard : Route("onboard")
    data object Home : Route("home")
    data object Chat : Route("chat/{cid}") { fun build(cid: String) = "chat/$cid" }
    data object Peers : Route("peers")
    data object Settings : Route("settings")
    data object NewChannel : Route("new-channel")
}

@Composable
fun ChatFlowNav(container: AppContainer) {
    val nav = rememberNavController()
    val nickname by container.prefs.nickname.collectAsState(initial = "")

    val startDestination = if (nickname.isBlank()) Route.Onboard.path else Route.Home.path

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Route.Onboard.path) {
            OnboardingScreen(container) { nav.navigate(Route.Home.path) {
                popUpTo(Route.Onboard.path) { inclusive = true }
            } }
        }
        composable(Route.Home.path) {
            HomeScreen(container,
                onOpenChat = { cid -> nav.navigate(Route.Chat.build(cid)) },
                onOpenPeers = { nav.navigate(Route.Peers.path) },
                onOpenSettings = { nav.navigate(Route.Settings.path) },
                onNewChannel = { nav.navigate(Route.NewChannel.path) }
            )
        }
        composable(
            Route.Chat.path,
            arguments = listOf(navArgument("cid") { type = NavType.StringType })
        ) { backStack ->
            val cid = backStack.arguments?.getString("cid") ?: return@composable
            ChatScreen(container, conversationId = cid, onBack = { nav.popBackStack() })
        }
        composable(Route.Peers.path) {
            PeersScreen(container,
                onBack = { nav.popBackStack() },
                onOpenChat = { cid -> nav.navigate(Route.Chat.build(cid)) })
        }
        composable(Route.Settings.path) {
            SettingsScreen(container, onBack = { nav.popBackStack() })
        }
        composable(Route.NewChannel.path) {
            NewChannelScreen(container, onDone = { channel ->
                nav.navigate(Route.Chat.build("#$channel")) {
                    popUpTo(Route.Home.path)
                }
            }, onBack = { nav.popBackStack() })
        }
    }
}
