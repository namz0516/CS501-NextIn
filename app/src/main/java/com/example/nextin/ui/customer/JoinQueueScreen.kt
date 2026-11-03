package com.example.nextin.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinQueueScreen(
    business: Business?,
    hasActiveEntry: Boolean,
    busy: Boolean,
    error: String?,
    onJoin: (String, Int) -> Unit,
    onMyQueue: () -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable(business?.id) {
        mutableStateOf("")
    }
    var partySize by rememberSaveable(business?.id) {
        mutableStateOf("1")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join the queue") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !busy
                    ) {
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (business == null) {
                Text("Business not found. Please go back.")
            } else {
                val canJoin =
                    business.queueOpen && !hasActiveEntry && !busy

                Text(
                    text = "Save your spot",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Enter your details to join this business’s queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = business.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = business.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (hasActiveEntry) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "You already have a spot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Leave your current queue before joining another."
                            )
                            OutlinedButton(
                                onClick = onMyQueue,
                                enabled = !busy
                            ) {
                                Text("View my queue")
                            }
                        }
                    }
                }

                if (!business.queueOpen) {
                    Text(
                        text = "This queue is currently closed.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "Your details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    supportingText = {
                        Text("Staff will use this name to identify your party.")
                    },
                    singleLine = true,
                    enabled = canJoin,
                    modifier = Modifier.fillMaxWidth()
                )

                if (business.type == BusinessType.RESTAURANT) {
                    OutlinedTextField(
                        value = partySize,
                        onValueChange = { partySize = it },
                        label = { Text("Party size") },
                        supportingText = {
                            Text(
                                "Include yourself. Maximum " +
                                        "${business.settings.maxPartySize} people."
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        enabled = canJoin,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "This entry is for one customer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (error != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Button(
                    onClick = {
                        val size =
                            if (business.type == BusinessType.RESTAURANT) {
                                partySize.toIntOrNull() ?: 0
                            } else {
                                1
                            }

                        onJoin(name, size)
                    },
                    enabled = canJoin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(
                        text = if (busy) {
                            "Saving your spot…"
                        } else {
                            "Confirm & join"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onBack,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }

                Text(
                    text = "Demo mode • Your queue entry is saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}