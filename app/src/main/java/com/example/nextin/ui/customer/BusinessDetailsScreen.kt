package com.example.nextin.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDetailsScreen(
    business: Business?,
    estimatedWaitMinutes: Int?,
    onJoin: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business details") },
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
            if (business == null) {
                Text(
                    text = "Business not found",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("Go back and select another business.")
            } else {
                Text(
                    text = business.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = when (business.type) {
                        BusinessType.RESTAURANT ->
                            business.cuisine ?: "Restaurant"
                        BusinessType.OTHER -> "Walk-in service"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (business.queueOpen) {
                                "QUEUE OPEN"
                            } else {
                                "QUEUE CLOSED"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = if (!business.queueOpen) {
                                "Check back later"
                            } else {
                                estimatedWaitMinutes?.let { "~$it min" }
                                    ?: "Wait unavailable"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = if (business.queueOpen) {
                                "Estimated wait for 1 person. Your wait may vary with party size and queue changes."
                            } else {
                                "This business is not accepting new queue entries."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                DetailsSection(
                    title = "Location",
                    body = business.address
                )

                DetailsSection(
                    title = "About this business",
                    body = business.description
                )

                if (business.type == BusinessType.RESTAURANT) {
                    DetailsSection(
                        title = "Bringing a group?",
                        body = "Choose a party size from 1 to " +
                                "${business.settings.maxPartySize} on the next screen."
                    )
                } else {
                    DetailsSection(
                        title = "Individual service",
                        body = "Each queue entry is for one customer."
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                DetailsSection(
                    title = "How it works",
                    body = "Join the queue, follow your position in My queue, " +
                            "and confirm your arrival when staff calls you."
                )

                Button(
                    onClick = onJoin,
                    enabled = business.queueOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(
                        text = if (business.queueOpen) {
                            "Join queue"
                        } else {
                            "Queue closed"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Text(
                    text = "Demo mode • Queue data is saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailsSection(
    title: String,
    body: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}