package com.example.nextin.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.nextin.domain.queue.QueuePolicy
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.ui.customer.BusinessDetailsScreen
import com.example.nextin.ui.customer.CustomerHomeScreen
import com.example.nextin.ui.customer.JoinQueueScreen
import com.example.nextin.ui.customer.QueueStatusScreen
import com.example.nextin.ui.queue.QueueViewModel

fun NavGraphBuilder.customerNavGraph(
    navController: NavHostController,
    queueViewModel: QueueViewModel
) {
    fun openMyQueue() {
        navController.navigate(Routes.MY_QUEUE) {
            popUpTo(Routes.CUSTOMER)
            launchSingleTop = true
        }
    }

    composable(Routes.CUSTOMER) {
        val snapshot by queueViewModel.state.collectAsStateWithLifecycle()

        CustomerHomeScreen(
            businesses = snapshot.businesses,
            entries = snapshot.entries,
            onBusinessClick = { id ->
                navController.navigate(Routes.businessDetails(id))
            },
            onMyQueue = { openMyQueue() },
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Routes.BUSINESS_DETAILS,
        arguments = listOf(navArgument("businessId") { type = NavType.StringType })
    ) { backStackEntry ->
        val snapshot by queueViewModel.state.collectAsStateWithLifecycle()
        val id = backStackEntry.arguments?.getString("businessId")
        val business = snapshot.businesses.firstOrNull { it.id == id }

        BusinessDetailsScreen(
            business = business,
            estimatedWaitMinutes = business?.let {
                QueuePolicy.estimatedWaitMinutes(it, snapshot.entries, 1)
            },
            onJoin = {
                business?.let {
                    navController.navigate(Routes.joinQueue(it.id))
                }
            },
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Routes.JOIN_QUEUE,
        arguments = listOf(navArgument("businessId") { type = NavType.StringType })
    ) { backStackEntry ->
        val snapshot by queueViewModel.state.collectAsStateWithLifecycle()
        val error by queueViewModel.error.collectAsStateWithLifecycle()
        val id = backStackEntry.arguments?.getString("businessId")
        val business = snapshot.businesses.firstOrNull { it.id == id }

        LaunchedEffect(id) { queueViewModel.clearError() }

        JoinQueueScreen(
            business = business,
            hasActiveEntry = queueViewModel.activeEntry(snapshot) != null,
            error = error,
            onJoin = { name, size ->
                val entry = queueViewModel.join(id.orEmpty(), name, size)
                if (entry != null) openMyQueue()
            },
            onMyQueue = { openMyQueue() },
            onBack = { navController.popBackStack() }
        )
    }

    composable(Routes.MY_QUEUE) {
        val snapshot by queueViewModel.state.collectAsStateWithLifecycle()
        val error by queueViewModel.error.collectAsStateWithLifecycle()
        val entry = queueViewModel.currentEntry(snapshot)
        val business = snapshot.businesses.firstOrNull {
            it.id == entry?.businessId
        }

        val waiting = snapshot.entries.filter {
            it.businessId == entry?.businessId && it.status == QueueStatus.WAITING
        }.sortedWith(
            compareBy<QueueEntry> { it.joinedAtMillis }.thenBy { it.id }
        )
        val index = waiting.indexOfFirst { it.id == entry?.id }
        val position = if (index >= 0) index + 1 else null

        LaunchedEffect(Unit) { queueViewModel.clearError() }

        QueueStatusScreen(
            business = business,
            entry = entry,
            position = position,
            estimatedWaitMinutes = if (business != null && entry?.status == QueueStatus.WAITING) {
                QueuePolicy.estimatedWaitMinutes(business, snapshot.entries, entry.partySize, entry.id)
            } else null,
            error = error,
            onLeave = { entry?.let { queueViewModel.leave(it.id) } },
            onBrowse = { navController.popBackStack(Routes.CUSTOMER, false) },
            onBack = { navController.popBackStack() }
        )
    }
}