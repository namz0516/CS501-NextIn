package com.example.nextin.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nextin.domain.queue.GracePeriod
import com.example.nextin.data.model.Business
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueStatusScreen(
    business: Business?,
    entry: QueueEntry?,
    position: Int?,
    nowMillis: Long,
    onConfirm: () -> Unit,
    estimatedWaitMinutes: Int?,
    error: String?,
    onLeave: () -> Unit,
    onBrowse: () -> Unit,
    onBack: () -> Unit
) {
    var confirmLeave by rememberSaveable(entry?.id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My queue") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (entry == null || business == null) {
                Text(
                    text = "No active queue",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("Choose a business to join a queue.")
                Button(onClick = onBrowse) { Text("Browse businesses") }
            } else {
                Text(
                    text = business.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("Name: ${entry.name}")
                Text("Party size: ${entry.partySize}")

                val status = when (entry.status) {
                    QueueStatus.WAITING -> "Waiting"
                    QueueStatus.CALLED -> "Called"
                    QueueStatus.EXPIRED -> "Expired"
                    QueueStatus.SEATED -> "Seated"
                    QueueStatus.LEFT -> "Left"
                    QueueStatus.REMOVED -> "Removed"
                }
                Text("Status: $status")

                if (entry.status == QueueStatus.CALLED) {
                    if (entry.confirmedArrival) {
                        Text("Arrival confirmed. Please check in with staff.")
                    } else {
                        val remaining = GracePeriod.remainingMillis(entry, business, nowMillis) ?: 0L
                        val seconds = remaining / 1000
                        Text("Confirm within %d:%02d".format(seconds / 60, seconds % 60))
                        Button(onClick = onConfirm, enabled = remaining > 0) {
                            Text("I'm here / on my way")
                        }
                    }
                }
                if (entry.status == QueueStatus.EXPIRED) {
                    Text("The grace period ended. Please contact staff; your spot has not been automatically removed.")
                }

                if (position != null) {
                    Text("Approximate position: #$position")
                }

                if (entry.status == QueueStatus.WAITING) {
                    Text(estimatedWaitMinutes?.let { "Approximate wait: ~$it min" } ?: "Estimated wait unavailable")
                }
                Text(
                    text = "Restaurant seating can depend on party size and open tables.",
                    style = MaterialTheme.typography.bodySmall
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (entry.status in setOf(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED)) {
                    Button(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Leave queue")
                    }
                } else {
                    Text("No active queue")
                    Button(onClick = onBrowse) { Text("Browse businesses") }
                }
            }
        }
    }

    if (confirmLeave && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this queue?") },
            text = { Text("Your spot will be released. Rejoining puts you at the end of the line.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onLeave()
                    }
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text("Stay")
                }
            }
        )
    }
}