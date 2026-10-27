package com.example.nextin.ui.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(onCustomerClick: () -> Unit, onStaffClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NextIn", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Skip the wait, not the line.", fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onCustomerClick, modifier = Modifier.fillMaxWidth()) { Text("Continue as Customer") }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onStaffClick, modifier = Modifier.fillMaxWidth()) { Text("Business Staff") }
    }
}
