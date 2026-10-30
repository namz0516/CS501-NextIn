package com.example.nextin.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.nextin.data.sample.SampleBusinessData
import com.example.nextin.ui.customer.BusinessDetailsScreen
import com.example.nextin.ui.customer.CustomerHomeScreen

fun NavGraphBuilder.customerNavGraph(
    navController: NavHostController
) {
    composable(Routes.CUSTOMER) {
        CustomerHomeScreen(
            businesses = SampleBusinessData.businesses,
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
        val businessId = backStackEntry.arguments
            ?.getString("businessId")

        val business = SampleBusinessData.businesses
            .firstOrNull { it.id == businessId }

        BusinessDetailsScreen(
            business = business,
            onBack = {
                navController.popBackStack()
            }
        )
    }
}