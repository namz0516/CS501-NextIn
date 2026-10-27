package com.example.nextin.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerHomeScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("NextIn") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        })
    }) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Find a Business", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("Business listings will appear here.")
            Spacer(Modifier.height(24.dp))
            Text("Browse nearby businesses and check estimated wait times.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
