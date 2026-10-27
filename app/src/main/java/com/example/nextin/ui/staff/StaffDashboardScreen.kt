package com.example.nextin.ui.staff

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDashboardScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Staff Dashboard") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        })
    }) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Queue Management", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("Staff queue management will appear here.")
        }
    }
}
