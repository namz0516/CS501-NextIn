package com.example.nextin.ui.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.domain.queue.QueuePolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerHomeScreen(
    businesses: List<Business>,
    entries: List<QueueEntry>,
    onBusinessClick: (String) -> Unit,
    onMyQueue: () -> Unit,
    onNearbyRestaurants: () -> Unit,
    onBack: () -> Unit,
    connected: Boolean = true
) {
    val visibleBusinesses = businesses
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "NextIn",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(onClick = onMyQueue) {
                        Text("My queue")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Find your next spot",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (connected) "Live queue updates"
                            else "Connecting • Showing saved data",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Explore businesses and check the queue before joining.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Less waiting. More freedom.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Join a queue and follow your progress here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                OutlinedButton(onClick = onNearbyRestaurants, modifier = Modifier.fillMaxWidth()) {
                    Text("Explore places nearby")
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Browse businesses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${visibleBusinesses.size} businesses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (visibleBusinesses.isEmpty()) {
                item {
                    Text("Find nearby places to open a NextIn demo queue.")
                }
            }

            items(
                items = visibleBusinesses,
                key = { it.id }
            ) { business ->
                val waitingCount = entries.count {
                    it.businessId == business.id &&
                            it.status == QueueStatus.WAITING
                }

                BusinessListItem(
                    business = business,
                    waitingCount = waitingCount,
                    waitMinutes = QueuePolicy.estimatedWaitMinutes(
                        business,
                        entries,
                        1
                    ),
                    onClick = { onBusinessClick(business.id) }
                )
            }

            item {
                Text(
                    text = "Nearby demo queues are simulated and do not represent actual business wait times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BusinessListItem(
    business: Business,
    waitingCount: Int,
    waitMinutes: Int?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = business.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (business.id.startsWith("google-demo:"))
                            "${business.name} (Demo)"
                        else business.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (business.type) {
                            BusinessType.RESTAURANT -> business.cuisine ?: "Restaurant"
                            BusinessType.CAFE -> "Cafe"
                            BusinessType.CINEMA -> "Cinema"
                            BusinessType.SHOP -> "Shop"
                            BusinessType.TOURS_AND_ATTRACTIONS -> "Tours & Attractions"
                            BusinessType.SALON_AND_BEAUTY -> "Salon & Beauty"
                            BusinessType.OTHER -> "Other Business"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (business.queueOpen) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (business.queueOpen) "OPEN" else "CLOSED",
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 6.dp
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (business.queueOpen) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Text(
                text = business.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$waitingCount waiting",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = if (business.queueOpen) {
                            "Estimated wait for 1 person"
                        } else {
                            "Not accepting new entries"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = if (!business.queueOpen) {
                        "Closed"
                    } else {
                        waitMinutes?.let { "~$it min" } ?: "Unavailable"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "View business →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}