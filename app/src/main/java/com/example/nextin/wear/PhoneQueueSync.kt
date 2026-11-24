package com.example.nextin.wear

import android.content.Context
import android.util.Log
import com.example.nextin.data.model.QueueSnapshot
import com.example.nextin.data.model.QueueStatus
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** The phone is the source of truth, the watch only receives a summary. */
class PhoneQueueSync(context: Context) {
    private val client = Wearable.getDataClient(context.applicationContext)
    private var lastPayload: String? = null

    //look through all entries and display the active or expired status one
    fun publish(snapshot: QueueSnapshot) {
        val entry = snapshot.entries.firstOrNull {
            it.customerUid == "demo-customer" && it.status in setOf(
                QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED
            )
        }
        val business = snapshot.businesses.firstOrNull { it.id == entry?.businessId }
        //counts waiting customers before selected customer at the business
        val ahead = if (entry == null) 0 else snapshot.entries.count {
            it.businessId == entry.businessId && it.status == QueueStatus.WAITING &&
                (it.joinedAtMillis < entry.joinedAtMillis ||
                    (it.joinedAtMillis == entry.joinedAtMillis && it.id < entry.id))
        }
        val status = entry?.status?.name ?: "NONE"
        val name = business?.name ?: ""
        val payload = "$status|$name|$ahead|${entry?.id.orEmpty()}"
        if (payload == lastPayload) return
        lastPayload = payload

        //phone sending info
        val request = PutDataMapRequest.create("/nextin/queue-status").apply {
            dataMap.putString("status", status)
            dataMap.putString("business", name)
            dataMap.putInt("ahead", ahead)
            dataMap.putLong("updatedAt", System.currentTimeMillis())
            dataMap.putString("entryId", entry?.id.orEmpty())
        }.asPutDataRequest().setUrgent()
        client.putDataItem(request).addOnFailureListener { error ->
            lastPayload = null //to avoid repeatedly sending identical info
            Log.w("NextInWearSync", "Unable to sync queue status", error)
        }
    }
}
