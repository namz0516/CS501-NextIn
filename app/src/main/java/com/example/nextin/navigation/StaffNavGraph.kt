package com.example.nextin.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.nextin.ui.queue.QueueViewModel
import com.example.nextin.ui.staff.StaffDashboardScreen

fun NavGraphBuilder.staffNavGraph(
    navController: NavHostController,
    queueViewModel: QueueViewModel
) {
    composable(Routes.STAFF) {
        val snapshot by queueViewModel.state.collectAsStateWithLifecycle()
        val now by queueViewModel.now.collectAsStateWithLifecycle()
        val busy by queueViewModel.busy.collectAsStateWithLifecycle()
        val error by queueViewModel.error.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { queueViewModel.clearError() }

        StaffDashboardScreen(
            snapshot = snapshot,
            error = error,
            busy = busy,
            nowMillis = now,
            onKeep = { id, entry -> queueViewModel.keepExpired(id, entry) },
            onSettings = { id, settings, done -> queueViewModel.updateSettings(id, settings, done) },
            onAddWalkIn = { id, name, size, done -> queueViewModel.addWalkIn(id, name, size, done) },
            onCall = { id, table, entry -> queueViewModel.callParty(id, table, entry) },
            onSeat = { id, entry -> queueViewModel.seat(id, entry) },
            onRemove = { id, entry -> queueViewModel.remove(id, entry) },
            onRelease = { id, table -> queueViewModel.releaseTable(id, table) },
            onQueueOpen = { id, open -> queueViewModel.setQueueOpen(id, open) },
            onClearError = { queueViewModel.clearError() },
            onBack = { navController.popBackStack() }
        )
    }
}