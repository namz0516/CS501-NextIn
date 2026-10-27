package com.example.nextin.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nextin.ui.welcome.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val CUSTOMER = "customer"
    const val STAFF = "staff"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onCustomerClick = { navController.navigate(Routes.CUSTOMER) },
                onStaffClick = { navController.navigate(Routes.STAFF) }
            )
        }
        customerNavGraph(navController)
        staffNavGraph(navController)
    }
}
