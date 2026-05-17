package ru.meeweel.network_protocols_app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme
import ru.meeweel.network_protocols_app.screen.by_protocols.ByProtocolsScreen
import ru.meeweel.network_protocols_app.screen.by_scenarios.ByScenariosScreen
import ru.meeweel.network_protocols_app.screen.full_test.FullTestScreen
import ru.meeweel.network_protocols_app.screen.home.HomeScreen

@Composable
fun NetworkProtocolsNavHost() {
    val navController = rememberNavController()

    NavHost(
        modifier = Modifier
            .background(color = NpTheme.colorScheme.background)
            .systemBarsPadding(),
        navController = navController,
        startDestination = NetworkProtocolsDestination.Home,
    ) {
        composable<NetworkProtocolsDestination.Home> {
            HomeScreen(navController = navController)
        }
        composable<NetworkProtocolsDestination.FullTest> {
            FullTestScreen(navController = navController)
        }
        composable<NetworkProtocolsDestination.ByProtocols> {
            ByProtocolsScreen(navController = navController)
        }
        composable<NetworkProtocolsDestination.ByScenarios> {
            ByScenariosScreen(navController = navController)
        }
    }
}
