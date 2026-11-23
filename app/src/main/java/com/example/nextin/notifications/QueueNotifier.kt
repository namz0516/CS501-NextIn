package com.example.nextin.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.nextin.MainActivity
import com.example.nextin.R
import com.example.nextin.domain.queue.QueueAlert

class QueueNotifier(context: Context) {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    init {
        createChannel()
    }

    fun canNotify(): Boolean = canNotify(context)

    fun show(alert: QueueAlert) {
        if (alert is QueueAlert.Clear) {
            manager.cancel(notificationId(alert.entryId))
            return
        }
        if (!canNotify()) return

        val (title, text) = when (alert) {
            is QueueAlert.Called -> "It's your turn at ${alert.businessName}" to
                    "Confirm your arrival within ${alert.graceMinutes} ${if (alert.graceMinutes == 1) "minute" else "minutes"}."
            is QueueAlert.Reminder -> "${alert.secondsLeft} seconds left to confirm" to
                    "Confirm your arrival at ${alert.businessName} before the grace period ends."
            is QueueAlert.Expired -> "Grace period ended" to
                    "Please see staff at ${alert.businessName} about your spot."
            is QueueAlert.Removed -> "Removed from the queue" to
                    "Staff at ${alert.businessName} removed your entry."
            is QueueAlert.Clear -> return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nextin)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openMyQueueIntent())
            .build()

        try {
            manager.notify(notificationId(alert.entryId), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Queue updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when it's your turn and when your grace period is ending."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun openMyQueueIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_MY_QUEUE, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_MY_QUEUE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(entryId: String): Int = entryId.hashCode()

    companion object {
        const val CHANNEL_ID = "queue_updates"
        const val EXTRA_OPEN_MY_QUEUE = "com.example.nextin.OPEN_MY_QUEUE"
        private const val REQUEST_OPEN_MY_QUEUE = 1001

        fun needsRuntimePermission(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun permissionGranted(context: Context): Boolean =
            !needsRuntimePermission() ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED

        fun canNotify(context: Context): Boolean {
            if (!permissionGranted(context)) return false
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
