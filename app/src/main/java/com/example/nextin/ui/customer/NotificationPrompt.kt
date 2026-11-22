package com.example.nextin.ui.customer

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.nextin.notifications.QueueNotifier

@Composable
fun NotificationPrompt(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(QueueNotifier.canNotify(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        enabled = QueueNotifier.canNotify(context)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        asked = true
        enabled = QueueNotifier.canNotify(context)
    }

    if (enabled || dismissed) return

    val openSettings = !QueueNotifier.needsRuntimePermission() || asked

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Get an alert when it's your turn",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "We'll notify you when staff calls you and before your grace period ends. This is optional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (openSettings) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(if (openSettings) "Open settings" else "Turn on notifications")
                }
                TextButton(onClick = { dismissed = true }) {
                    Text("Not now")
                }
            }
        }
    }
}
