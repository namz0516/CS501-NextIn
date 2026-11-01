package com.example.nextin.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nextin.ui.queue.QueueViewModel
import com.example.nextin.ui.welcome.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val CUSTOMER = "customer"
    const val STAFF = "staff"

    const val BUSINESS_DETAILS = "business/{businessId}"

    fun businessDetails(businessId: String): String {
        return "business/${Uri.encode(businessId)}"
    }
}

@Composable
fun AppNavigation(
    queueViewModel: QueueViewModel = viewModel()
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onCustomerClick = {
                    navController.navigate(Routes.CUSTOMER)
                },
                onStaffClick = {
                    navController.navigate(Routes.STAFF)
                }
            )
        }

        customerNavGraph(
            navController = navController,
            queueViewModel = queueViewModel
        )

        staffNavGraph(navController)
    }
}