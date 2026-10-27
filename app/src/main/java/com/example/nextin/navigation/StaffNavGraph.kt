package com.example.nextin.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.nextin.ui.staff.StaffDashboardScreen

fun NavGraphBuilder.staffNavGraph(navController: NavHostController) {
    composable(Routes.STAFF) {
        StaffDashboardScreen(onBack = { navController.popBackStack() })
    }
}
