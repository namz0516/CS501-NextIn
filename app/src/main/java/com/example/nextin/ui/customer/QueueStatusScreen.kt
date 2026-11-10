package com.example.nextin.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.domain.queue.GracePeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueStatusScreen(
    business: Business?,
    entry: QueueEntry?,
    position: Int?,
    busy: Boolean,
    nowMillis: Long,
    onConfirm: () -> Unit,
    estimatedWaitMinutes: Int?,
    error: String?,
    onLeave: () -> Unit,
    onBrowse: () -> Unit,
    onBack: () -> Unit
) {
    var confirmLeave by rememberSaveable(entry?.id) {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My queue") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (entry == null || business == null) {
                Text(
                    text = "Your next spot awaits",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Choose a business to join a queue.")
                Button(
                    onClick = onBrowse,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Browse businesses")
                }
            } else {
                val active = entry.status in setOf(
                    QueueStatus.WAITING,
                    QueueStatus.CALLED,
                    QueueStatus.EXPIRED
                )
                val remaining = GracePeriod.remainingMillis(
                    entry,
                    business,
                    nowMillis
                ) ?: 0L
                val seconds = remaining.coerceAtLeast(0L) / 1000

                val heading = when (entry.status) {
                    QueueStatus.WAITING -> "YOUR POSITION"
                    QueueStatus.CALLED -> if (entry.confirmedArrival) {
                        "ARRIVAL CONFIRMED"
                    } else {
                        "YOU'RE NEXT"
                    }
                    QueueStatus.EXPIRED -> "GRACE PERIOD ENDED"
                    QueueStatus.SEATED -> "YOU'RE ALL SET"
                    QueueStatus.LEFT -> "QUEUE LEFT"
                    QueueStatus.REMOVED -> "ENTRY REMOVED"
                }

                val mainValue = when (entry.status) {
                    QueueStatus.WAITING ->
                        position?.let { "#$it" } ?: "Waiting"
                    QueueStatus.CALLED -> if (entry.confirmedArrival) {
                        "See staff"
                    } else {
                        "%d:%02d".format(seconds / 60, seconds % 60)
                    }
                    QueueStatus.EXPIRED -> "See staff"
                    QueueStatus.SEATED -> "Seated"
                    QueueStatus.LEFT -> "Spot released"
                    QueueStatus.REMOVED -> "Closed"
                }

                val message = when (entry.status) {
                    QueueStatus.WAITING ->
                        "Follow your progress here while you wait."
                    QueueStatus.CALLED -> if (entry.confirmedArrival) {
                        "Staff can see your confirmation. Please check in with them."
                    } else {
                        "Confirm before the timer ends and check in with staff."
                    }
                    QueueStatus.EXPIRED ->
                        "Contact staff about your spot. You have not been automatically removed."
                    QueueStatus.SEATED ->
                        "Staff marked your party as seated."
                    QueueStatus.LEFT ->
                        "You can browse businesses and join another queue."
                    QueueStatus.REMOVED ->
                        "Staff removed this entry. Contact the business if you need help."
                }

                Text(
                    text = business.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = mainValue,
                            fontSize = if (
                                entry.status == QueueStatus.WAITING ||
                                (entry.status == QueueStatus.CALLED &&
                                        !entry.confirmedArrival)
                            ) 56.sp else 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (entry.status == QueueStatus.WAITING) {
                    QueueInfoCard(
                        title = "Estimated wait",
                        value = estimatedWaitMinutes?.let {
                            "~$it minutes"
                        } ?: "Unavailable"
                    )
                }

                QueueInfoCard(
                    title = "Your entry",
                    value = "${entry.name} • Party of ${entry.partySize}"
                )

                if (entry.status == QueueStatus.CALLED &&
                    !entry.confirmedArrival
                ) {
                    Button(
                        onClick = onConfirm,
                        enabled = remaining > 0 && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(
                            if (busy) "Saving…" else "I'm here / on my way"
                        )
                    }
                }

                if (error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (active) {
                    OutlinedButton(
                        onClick = { confirmLeave = true },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Leave queue")
                    }
                } else {
                    Button(
                        onClick = onBrowse,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Browse businesses")
                    }
                }

                if (entry.status == QueueStatus.WAITING) {
                    Text(
                        text = if (
                            business.type == BusinessType.RESTAURANT
                        ) {
                            "Wait times are approximate. Seating depends on party size, queue rules and available tables."
                        } else {
                            "Wait times are approximate and can change as staff serves customers."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmLeave && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this queue?") },
            text = {
                Text(
                    "Your spot will be released. Rejoining puts you at the end of the line."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onLeave()
                    },
                    enabled = !busy
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text("Stay")
                }
            }
        )
    }
}

@Composable
private fun QueueInfoCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}