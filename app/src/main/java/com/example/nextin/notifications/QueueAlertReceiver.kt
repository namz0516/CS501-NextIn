package com.example.nextin.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.nextin.data.cloud.CloudQueueCodec
import com.example.nextin.data.cloud.CloudQueueStore
import com.example.nextin.domain.queue.AlertDecision
import com.example.nextin.domain.queue.AlertKind
import com.example.nextin.domain.queue.AlertSchedule
import com.example.nextin.domain.queue.QueueAlert
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class QueueAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlertScheduler.ACTION_QUEUE_ALERT) return
        val entryId = intent.getStringExtra(AlertScheduler.EXTRA_ENTRY_ID) ?: return
        val kind = intent.getStringExtra(AlertScheduler.EXTRA_KIND)
            ?.let { name -> AlertKind.entries.firstOrNull { it.name == name } } ?: return
        val businessId = intent.getStringExtra(AlertScheduler.EXTRA_BUSINESS_ID) ?: return
        val businessName = intent.getStringExtra(AlertScheduler.EXTRA_BUSINESS_NAME) ?: "the business"
        val calledAt = intent.getLongExtra(AlertScheduler.EXTRA_CALLED_AT, -1L)
        val offset = intent.getLongExtra(AlertScheduler.EXTRA_CLOCK_OFFSET, 0L)
        if (calledAt < 0L) return

        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(TIMEOUT_MILLIS) {
                    handle(appContext, entryId, kind, businessId, businessName, calledAt, offset)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        entryId: String,
        kind: AlertKind,
        businessId: String,
        businessName: String,
        calledAt: Long,
        offset: Long
    ) {
        val preferences = AlertPreferences(context)
        val key = AlertSchedule.key(entryId, calledAt)
        val alreadySent = when (kind) {
            AlertKind.REMINDER -> key in preferences.sentReminders()
            AlertKind.EXPIRED -> key in preferences.sentExpiries()
        }
        if (alreadySent) return

        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis() + offset

        val decision = try {
            val entryDocument = firestore.collection("queueEntries").document(entryId).get().await()
            if (!entryDocument.exists()) return
            val entry = CloudQueueCodec.entry(entryDocument)
            if (entry.customerUid != uid) return
            val grace = firestore.collection("businesses").document(businessId).get().await()
                .getLong("gracePeriodMinutes")?.toInt() ?: return
            AlertSchedule.decide(kind, entry, grace, calledAt, now)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            AlertDecision.Show(if (kind == AlertKind.REMINDER) 60L else 0L)
        }

        when (decision) {
            is AlertDecision.Skip -> Unit
            is AlertDecision.Reschedule -> AlertScheduler(context).schedule(
                entryId, businessId, businessName, calledAt, kind, decision.atMillis, offset
            )
            is AlertDecision.Show -> {
                val notifier = QueueNotifier(context)
                when (kind) {
                    AlertKind.REMINDER -> {
                        preferences.saveSentReminders(preferences.sentReminders() + key)
                        notifier.show(QueueAlert.Reminder(entryId, businessName, decision.secondsLeft, key))
                    }
                    AlertKind.EXPIRED -> {
                        preferences.markExpirySent(key)
                        notifier.show(QueueAlert.Expired(entryId, businessName))
                        try {
                            CloudQueueStore(firestore, auth).expire(entryId)
                        } catch (exception: Exception) {
                            if (exception is CancellationException) throw exception
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 9_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
