package com.example.nextin.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.nextin.ui.auth.StaffAuthViewModel
import com.example.nextin.ui.auth.StaffLoginScreen
import com.example.nextin.ui.queue.QueueViewModel
import com.example.nextin.ui.staff.StaffDashboardScreen

fun NavGraphBuilder.staffNavGraph(
    navController: NavHostController,
    queueViewModel: QueueViewModel
) {
    composable(Routes.STAFF) {
        // This ViewModel belongs to the staff navigation destination.
        val staffAuth: StaffAuthViewModel = viewModel()

        val businessId by staffAuth.businessId.collectAsStateWithLifecycle()
        val signingIn by staffAuth.busy.collectAsStateWithLifecycle()
        val loginError by staffAuth.error.collectAsStateWithLifecycle()

        // Both the screen's Back button and Android Back sign out staff.
        val exitStaff: () -> Unit = {
            staffAuth.signOut()
            navController.popBackStack()
        }

        // Show login until credentials and assignment checks succeed.
        if (businessId == null) {
            StaffLoginScreen(
                busy = signingIn,
                error = loginError,
                onSignIn = { email, password ->
                    staffAuth.signIn(email, password)
                },
                onBack = exitStaff
            )

            return@composable
        }

        BackHandler(onBack = exitStaff)

        val snapshot by queueViewModel.state.collectAsStateWithLifecycle()
        val now by queueViewModel.now.collectAsStateWithLifecycle()
        val busy by queueViewModel.busy.collectAsStateWithLifecycle()
        val error by queueViewModel.error.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            queueViewModel.clearError()
        }

        StaffDashboardScreen(
            // Display only this staff member's assigned business and entries.
            snapshot = snapshot.copy(
                businesses = snapshot.businesses.filter {
                    it.id == businessId
                },
                entries = snapshot.entries.filter {
                    it.businessId == businessId
                }
            ),
            error = error,
            busy = busy,
            nowMillis = now,
            onKeep = { id, entry ->
                queueViewModel.keepExpired(id, entry)
            },
            onSettings = { id, settings, done ->
                queueViewModel.updateSettings(id, settings, done)
            },
            onAddWalkIn = { id, name, size, done ->
                queueViewModel.addWalkIn(id, name, size, done)
            },
            onCall = { id, table, entry ->
                queueViewModel.callParty(id, table, entry)
            },
            onSeat = { id, entry ->
                queueViewModel.seat(id, entry)
            },
            onRemove = { id, entry ->
                queueViewModel.remove(id, entry)
            },
            onRelease = { id, table ->
                queueViewModel.releaseTable(id, table)
            },
            onQueueOpen = { id, open ->
                queueViewModel.setQueueOpen(id, open)
            },
            onClearError = {
                queueViewModel.clearError()
            },
            onBack = exitStaff
        )
    }
}