package com.example.nextin.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.nextin.domain.queue.AlertKind

class AlertScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)

    fun schedule(
        entryId: String,
        businessId: String,
        businessName: String,
        calledAtMillis: Long,
        kind: AlertKind,
        atServerMillis: Long,
        clockOffsetMillis: Long
    ) {
        val manager = alarmManager ?: return
        val intent = alertIntent(entryId, kind)
            .putExtra(EXTRA_BUSINESS_ID, businessId)
            .putExtra(EXTRA_BUSINESS_NAME, businessName)
            .putExtra(EXTRA_CALLED_AT, calledAtMillis)
            .putExtra(EXTRA_CLOCK_OFFSET, clockOffsetMillis)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = atServerMillis - clockOffsetMillis
        try {
            if (exactAllowed(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(entryId: String) {
        val manager = alarmManager ?: return
        AlertKind.entries.forEach { kind ->
            val pending = PendingIntent.getBroadcast(
                context, 0, alertIntent(entryId, kind),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return@forEach
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun alertIntent(entryId: String, kind: AlertKind): Intent =
        Intent(context, QueueAlertReceiver::class.java)
            .setAction(ACTION_QUEUE_ALERT)
            .setData(Uri.Builder().scheme(URI_SCHEME).authority(kind.name.lowercase()).appendPath(entryId).build())
            .putExtra(EXTRA_ENTRY_ID, entryId)
            .putExtra(EXTRA_KIND, kind.name)

    companion object {
        fun exactAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        }

        const val ACTION_QUEUE_ALERT = "com.example.nextin.QUEUE_ALERT"
        const val EXTRA_ENTRY_ID = "entryId"
        const val EXTRA_KIND = "kind"
        const val EXTRA_BUSINESS_ID = "businessId"
        const val EXTRA_BUSINESS_NAME = "businessName"
        const val EXTRA_CALLED_AT = "calledAt"
        const val EXTRA_CLOCK_OFFSET = "clockOffset"
        private const val URI_SCHEME = "nextin-alert"
    }
}
