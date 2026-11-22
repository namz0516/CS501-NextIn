package com.example.nextin.ui.customer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.app.ActivityCompat
import com.example.nextin.notifications.AlertPreferences
import com.example.nextin.notifications.QueueNotifier

@Composable
fun NotificationPrompt(entryId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val preferences = remember { AlertPreferences(context) }
    var enabled by remember { mutableStateOf(QueueNotifier.canNotify(context)) }
    var dismissed by remember(entryId) { mutableStateOf(preferences.promptDismissedFor(entryId)) }
    var blocked by remember { mutableStateOf(permissionBlocked(activity, preferences)) }

    LifecycleResumeEffect(Unit) {
        enabled = QueueNotifier.canNotify(context)
        blocked = permissionBlocked(activity, preferences)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        preferences.markPermissionAsked()
        enabled = QueueNotifier.canNotify(context)
        blocked = permissionBlocked(activity, preferences)
    }

    if (enabled || dismissed) return

    val openSettings = !QueueNotifier.needsRuntimePermission() || blocked ||
            QueueNotifier.permissionGranted(context)

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
                TextButton(onClick = {
                    preferences.dismissPrompt(entryId)
                    dismissed = true
                }) {
                    Text("Not now")
                }
            }
        }
    }
}

private fun permissionBlocked(activity: Activity?, preferences: AlertPreferences): Boolean {
    if (!QueueNotifier.needsRuntimePermission() || activity == null) return false
    return preferences.permissionAsked() &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}
