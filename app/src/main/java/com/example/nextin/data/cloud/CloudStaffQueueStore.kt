package com.example.nextin.data.cloud

import com.example.nextin.data.model.*
import com.example.nextin.data.repository.LocalQueueRepository
import com.google.firebase.firestore.*
import kotlinx.coroutines.tasks.await

// Reuses the tested queue rules against a fresh, transactionally locked cloud snapshot.
class CloudStaffQueueStore(
    private val firestore: FirebaseFirestore,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun addWalkIn(id: String, name: String, size: Int): String {
        require(name.trim().length in 1..80) { "Enter a name of 1–80 characters." }
        return requireNotNull(change(id) { it.addWalkIn(id, name, size).id })
    }
    suspend fun call(id: String, tableId: String?, entryId: String?) {
        change(id) { it.callParty(id, tableId, entryId); null }
    }
    suspend fun seat(id: String, entryId: String) {
        change(id) { it.seat(id, entryId); null }
    }
    suspend fun remove(id: String, entryId: String) {
        change(id) { it.remove(id, entryId); null }
    }
    suspend fun releaseTable(id: String, tableId: String) {
        change(id) { it.releaseTable(id, tableId); null }
    }
    suspend fun keepExpired(id: String, entryId: String) {
        change(id) { it.keepExpired(id, entryId); null }
    }
    suspend fun expireCalls(id: String) {
        change(id) { it.expireCalls(nowMillis()); null }
    }
    suspend fun setQueueOpen(id: String, open: Boolean) {
        change(id) { it.setQueueOpen(id, open); null }
    }
    suspend fun updateSettings(id: String, settings: BusinessSettings) {
        change(id) { it.updateSettings(id, settings); null }
    }

    private suspend fun change(
        businessId: String,
        action: (LocalQueueRepository) -> String?
    ): String? {
        val businessRef = firestore.collection("businesses").document(businessId)
        val queueRef = firestore.collection("queuePublic").document(businessId)
        return firestore.runTransaction { transaction ->
            val savedBusiness = transaction.get(businessRef)
            val savedQueue = transaction.get(queueRef)
            require(savedBusiness.getLong("schemaVersion") == 1L && savedQueue.exists()) {
                "This business needs cloud setup before staff can serve its queue."
            }
            val tableIds = (savedBusiness.get("tableIds") as? List<*>)
                .orEmpty().map { requireNotNull(it as? String) }
            val savedTables = tableIds.map {
                transaction.get(businessRef.collection("tables").document(it))
            }
            val publicEntries = CloudQueueCodec.publicEntries(savedQueue)
            // Occupied seated entries are read too, so reopening a table can check its status.
            val ids = (publicEntries.keys + savedTables.mapNotNull {
                it.getString("occupiedByEntryId")
            }).toSet()
            val savedEntries = ids.associateWith {
                transaction.get(firestore.collection("queueEntries").document(it))
            }
            val entries = savedEntries.values.map(CloudQueueCodec::entry)
            val business = CloudQueueCodec.business(savedBusiness, savedTables.map(CloudQueueCodec::table))
            val repo = LocalQueueRepository(
                initialBusinesses = listOf(business),
                initialEntries = entries,
                nowMillis = nowMillis
            )
            // No UI changes or external side effects occur in a retryable transaction.
            val result = action(repo)
            val next = repo.state.value
            val nextBusiness = next.businesses.single()
            val changedEntries = next.entries.filter { updated ->
                entries.firstOrNull { it.id == updated.id } != updated
            }
            if (changedEntries.isEmpty() && nextBusiness == business) return@runTransaction result

            val nextPublic = publicEntries.toMutableMap()
            changedEntries.forEach { entry ->
                val data = CloudQueueCodec.privateData(entry, savedEntries[entry.id])
                transaction.set(firestore.collection("queueEntries").document(entry.id), data)
                if (CloudQueueCodec.isActive(entry)) nextPublic[entry.id] = CloudQueueCodec.publicData(data)
                else nextPublic.remove(entry.id)
            }
            nextBusiness.tables.forEach { table ->
                if (business.tables.first { it.id == table.id } != table) {
                    transaction.update(businessRef.collection("tables").document(table.id),
                        "occupiedByEntryId", table.occupiedByEntryId)
                }
            }
            if (nextBusiness.settings != business.settings || nextBusiness.queueOpen != business.queueOpen) {
                transaction.update(businessRef, mapOf(
                    "queueOpen" to nextBusiness.queueOpen,
                    "maxPartySize" to nextBusiness.settings.maxPartySize,
                    "gracePeriodMinutes" to nextBusiness.settings.gracePeriodMinutes,
                    "skipLimit" to nextBusiness.settings.skipLimit,
                    "averageServiceMinutes" to nextBusiness.settings.averageServiceMinutes,
                    "seatingMode" to nextBusiness.settings.seatingMode.name
                ))
            }
            // Every join/leave/call updates this document, serializing competing devices.
            transaction.update(queueRef, mapOf(
                "entries" to nextPublic, "revision" to (savedQueue.getLong("revision")!! + 1)
            ))
            result
        }.await()
    }
}
