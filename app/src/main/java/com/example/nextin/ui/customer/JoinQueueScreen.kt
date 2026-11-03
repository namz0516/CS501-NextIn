package com.example.nextin.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    var name by rememberSaveable(business?.id) { mutableStateOf("") }
    var partySize by rememberSaveable(business?.id) { mutableStateOf("1") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join queue") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (business == null) {
                Text("Business not found. Please go back.")
            } else {
                val canJoin = business.queueOpen && !hasActiveEntry && !busy

                Text(
                    text = business.name,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (hasActiveEntry) {
                    Text("You already have an active queue. Leave it before joining another.")
                    OutlinedButton(onClick = onMyQueue) {
                        Text("View my queue")
                    }
                }

                if (!business.queueOpen) {
                    Text("This queue is closed.")
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    enabled = canJoin,
                    modifier = Modifier.fillMaxWidth()
                )

                if (business.type == BusinessType.RESTAURANT) {
                    OutlinedTextField(
                        value = partySize,
                        onValueChange = { partySize = it },
                        label = { Text("Party size (1–${business.settings.maxPartySize})") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        enabled = canJoin,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("This business accepts one customer per entry.")
                }

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val size = if (business.type == BusinessType.RESTAURANT) {
                            partySize.toIntOrNull() ?: 0
                        } else {
                            1
                        }
                        onJoin(name, size)
                    },
                    enabled = canJoin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Saving…" else "Join queue")
                }

                Text(
                    text = "Demo mode: this queue is saved on this device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}