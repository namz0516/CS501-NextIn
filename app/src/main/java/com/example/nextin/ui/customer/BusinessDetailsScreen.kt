package com.example.nextin.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDetailsScreen(
    business: Business?,
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (business == null) {
                Text(
                    text = "Business not found",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "Go back and select another business.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = business.name,
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = when (business.type) {
                        BusinessType.RESTAURANT ->
                            business.cuisine ?: "Restaurant"

                        BusinessType.OTHER ->
                            "Walk-in service"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = business.address,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = if (business.queueOpen) {
                        "Queue open"
                    } else {
                        "Queue closed"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (business.queueOpen) {
                        "Estimated wait unavailable"
                    } else {
                        "Not accepting new queue entries"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = business.description,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (business.type == BusinessType.RESTAURANT) {
                    Text(
                        text = "Party size",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Parties of 1–${business.settings.maxPartySize} people",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}