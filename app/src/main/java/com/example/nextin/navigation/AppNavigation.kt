package com.example.nextin.navigation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    const val JOIN_QUEUE = "business/{businessId}/join"
    const val MY_QUEUE = "my_queue"

    fun joinQueue(businessId: String): String {
        return "business/${Uri.encode(businessId)}/join"
    }

    fun businessDetails(businessId: String): String {
        return "business/${Uri.encode(businessId)}"
    }
}

@Composable
fun AppNavigation(
    queueViewModel: QueueViewModel = viewModel()
) {
    val ready by queueViewModel.ready.collectAsStateWithLifecycle()
    val loading by queueViewModel.loading.collectAsStateWithLifecycle()
    val error by queueViewModel.error.collectAsStateWithLifecycle()
    if (!ready) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator()
                Text("Loading saved queues…")
            } else {
                Text(error ?: "Queues could not be loaded.")
                Button(onClick = { queueViewModel.initialize() }) { Text("Retry") }
            }
        }
        return
    }

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

        staffNavGraph(navController, queueViewModel)
    }
}