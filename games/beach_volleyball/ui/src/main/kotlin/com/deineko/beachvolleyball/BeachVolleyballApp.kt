package com.deineko.beachvolleyball

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deineko.beachvolleyball.ui.ConnectRole
import com.deineko.beachvolleyball.ui.ConnectScreen
import com.deineko.beachvolleyball.ui.LockOrientation
import com.deineko.beachvolleyball.ui.MatchMode
import com.deineko.beachvolleyball.ui.MatchScreen
import com.deineko.beachvolleyball.ui.ModeSelectScreen
import com.deineko.connect.NearbyGameConnection

private const val ROUTE_MODE = "mode"
private const val ROUTE_CONNECT_HOST = "connect_host"
private const val ROUTE_CONNECT_JOIN = "connect_join"
private const val ROUTE_MATCH_PC = "match_pc"
private const val ROUTE_MATCH_HOST = "match_host"
private const val ROUTE_MATCH_CLIENT = "match_client"

@Composable
fun BeachVolleyballApp(onExitToHub: () -> Unit) {
    LockOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

    val context = LocalContext.current
    val connection = remember { NearbyGameConnection(context) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_MODE) {
        composable(ROUTE_MODE) {
            LaunchedEffect(Unit) { connection.stop() }
            ModeSelectScreen(
                onPlayPc = { navController.navigate(ROUTE_MATCH_PC) },
                onHost = { navController.navigate(ROUTE_CONNECT_HOST) },
                onJoin = { navController.navigate(ROUTE_CONNECT_JOIN) },
                onExitToHub = onExitToHub,
            )
        }
        composable(ROUTE_CONNECT_HOST) {
            ConnectScreen(
                role = ConnectRole.HOST,
                connection = connection,
                onConnected = { navController.navigate(ROUTE_MATCH_HOST) { popUpTo(ROUTE_MODE) } },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(ROUTE_CONNECT_JOIN) {
            ConnectScreen(
                role = ConnectRole.JOIN,
                connection = connection,
                onConnected = { navController.navigate(ROUTE_MATCH_CLIENT) { popUpTo(ROUTE_MODE) } },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(ROUTE_MATCH_PC) {
            MatchScreen(
                mode = MatchMode.VsPc,
                connection = null,
                onExit = { navController.popBackStack(ROUTE_MODE, false) },
            )
        }
        composable(ROUTE_MATCH_HOST) {
            MatchScreen(
                mode = MatchMode.Host,
                connection = connection,
                onExit = { navController.popBackStack(ROUTE_MODE, false) },
            )
        }
        composable(ROUTE_MATCH_CLIENT) {
            MatchScreen(
                mode = MatchMode.Client,
                connection = connection,
                onExit = { navController.popBackStack(ROUTE_MODE, false) },
            )
        }
    }
}
