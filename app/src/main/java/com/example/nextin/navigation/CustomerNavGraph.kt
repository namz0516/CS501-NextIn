package com.example.nextin.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.nextin.ui.customer.BusinessDetailsScreen
import com.example.nextin.ui.customer.CustomerHomeScreen
import com.example.nextin.ui.queue.QueueViewModel

fun NavGraphBuilder.customerNavGraph(
    navController: NavHostController,
    queueViewModel: QueueViewModel
) {
    composable(Routes.CUSTOMER) {
        val snapshot by queueViewModel.state
            .collectAsStateWithLifecycle()

        CustomerHomeScreen(
            businesses = snapshot.businesses,
            onBusinessClick = { businessId ->
                navController.navigate(
                    Routes.businessDetails(businessId)
                )
            },
            onBack = {
                navController.popBackStack()
            }
        )
    }

    composable(
        route = Routes.BUSINESS_DETAILS,
        arguments = listOf(
            navArgument("businessId") {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val snapshot by queueViewModel.state
            .collectAsStateWithLifecycle()

        val businessId = backStackEntry.arguments
            ?.getString("businessId")

        val business = snapshot.businesses.firstOrNull {
            it.id == businessId
        }

        BusinessDetailsScreen(
            business = business,
            onBack = {
                navController.popBackStack()
            }
        )
    }
}