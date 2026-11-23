package com.example.nextin.navigation

import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nextin.ui.auth.StaffAuthViewModel
import com.example.nextin.ui.auth.StaffLoginScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        val staffAuth: StaffAuthViewModel = viewModel()
        val businessId by staffAuth.businessId.collectAsStateWithLifecycle()
        val signingIn by staffAuth.busy.collectAsStateWithLifecycle()
        val loginError by staffAuth.error.collectAsStateWithLifecycle()
        val exitStaff: () -> Unit = {
            if (!queueViewModel.busy.value) {
                queueViewModel.stopStaff()
                staffAuth.signOut()
                navController.popBackStack()
            }
        }

        // Keep listener startup separate from destination cleanup.
        DisposableEffect(Unit) {
            onDispose { queueViewModel.stopStaff() }
        }
        val assignedBusiness = businessId
        LaunchedEffect(assignedBusiness) {
            if (assignedBusiness == null) queueViewModel.stopStaff()
            else queueViewModel.startStaff(assignedBusiness)
        }

        if (businessId == null) {
            StaffLoginScreen(
                busy = signingIn,
                error = loginError,
                onSignIn = { email, password -> staffAuth.signIn(email, password) },
                onBack = exitStaff
            )
            return@composable
        }
        BackHandler(onBack = exitStaff)
        val snapshot by queueViewModel.staffState.collectAsStateWithLifecycle()
        val now by queueViewModel.now.collectAsStateWithLifecycle()
        val busy by queueViewModel.busy.collectAsStateWithLifecycle()
        val error by queueViewModel.error.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { queueViewModel.clearError() }

        val staffReady by queueViewModel.staffReady.collectAsStateWithLifecycle()
        if (!staffReady) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Connecting to your business…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { businessId?.let { queueViewModel.startStaff(it) } }) { Text("Retry") }
                TextButton(onClick = exitStaff) { Text("Back") }
            }
            return@composable
        }

        StaffDashboardScreen(
            snapshot = snapshot.copy(
                businesses = snapshot.businesses.filter { it.id == businessId },
                entries = snapshot.entries.filter { it.businessId == businessId }
            ),
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
            onBack = exitStaff
        )
    }
}