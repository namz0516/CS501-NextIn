package com.example.nextin.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.nextin.ui.customer.CustomerHomeScreen

fun NavGraphBuilder.customerNavGraph(navController: NavHostController) {
    composable(Routes.CUSTOMER) {
        CustomerHomeScreen(onBack = { navController.popBackStack() })
    }
}
