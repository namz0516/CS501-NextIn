package com.example.nextin.ui.customer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.nextin.notifications.AlertPreferences
import com.example.nextin.notifications.AlertScheduler
import com.example.nextin.notifications.QueueNotifier

@Composable
fun ExactAlarmPrompt(entryId: String, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val preferences = remember { AlertPreferences(context) }
    var notificationsOn by remember { mutableStateOf(QueueNotifier.canNotify(context)) }
    var exactAllowed by remember { mutableStateOf(AlertScheduler.exactAllowed(context)) }
    var dismissed by remember(entryId) { mutableStateOf(preferences.alarmPromptDismissedFor(entryId)) }

    LifecycleResumeEffect(Unit) {
        notificationsOn = QueueNotifier.canNotify(context)
        exactAllowed = AlertScheduler.exactAllowed(context)
        onPauseOrDispose { }
    }

    if (!notificationsOn || exactAllowed || dismissed) return

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
                text = "Get grace period alerts on time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Allow alarms so we can remind you before your grace period ends, even when NextIn is closed. Without it, these alerts can arrive late.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val packageUri = "package:${context.packageName}".toUri()
                    val request = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(request)
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Text("Allow alarms")
                }
                TextButton(onClick = {
                    preferences.dismissAlarmPrompt(entryId)
                    dismissed = true
                }) {
                    Text("Not now")
                }
            }
        }
    }
}
