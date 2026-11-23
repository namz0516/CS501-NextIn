package com.example.nextin.data.cloud

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class CloudStaffSetup(private val firestore: FirebaseFirestore) {
    suspend fun initializeBusiness(business: Business) {
        val reference = firestore.collection("businesses").document(business.id)
        val publicRef = firestore.collection("queuePublic").document(business.id)
        // Upgrade any entries created during the earlier data-layer setup.
        val publicExists = publicRef.get(Source.SERVER).await().exists()
        val legacyEntries = if (publicExists) emptyMap() else firestore.collection("queueEntries")
            .whereEqualTo("businessId", business.id).get(Source.SERVER).await().documents
            .filter { CloudQueueCodec.isActive(CloudQueueCodec.entry(it)) }
            .associate { it.id to CloudQueueCodec.publicData(requireNotNull(it.data)) }
        val largestTable = business.tables.maxByOrNull { it.capacity }
        val largestCapacity = largestTable?.capacity ?: 1

        firestore.runTransaction { transaction ->
            val saved = transaction.get(reference)
            val publicQueue = transaction.get(publicRef)
            require(saved.exists()) { "Create the business document in Firestore first." }

            // Repeated logins must never reset occupied tables or business settings.
            val version = saved.getLong("schemaVersion")
            if (version != null) {
                require(version == 1L) { "This business uses an unsupported cloud schema." }
                if (!publicQueue.exists()) {
                    transaction.set(publicRef, mapOf("entries" to legacyEntries, "revision" to 0))
                }
                return@runTransaction Unit
            }

            val tableReferences = business.tables.map {
                reference.collection("tables").document(it.id)
            }
            val existingTables = tableReferences.map { transaction.get(it) }
            require(existingTables.none { it.exists() }) {
                "Cloud tables already exist without a configured business. Ask the project owner to check setup."
            }

            val maximum = saved.getLong("maxPartySize")
                ?: business.settings.maxPartySize.toLong()
            val grace = saved.getLong("gracePeriodMinutes")
                ?: business.settings.gracePeriodMinutes.toLong()
            require(maximum in 1L..largestCapacity.toLong()) {
                "The maximum party size must fit an available table or service spot."
            }
            require(grace in 1L..30L) { "The grace period must be between 1 and 30 minutes." }
            require(business.type != BusinessType.RESTAURANT || largestTable != null) {
                "Restaurants need at least one table."
            }

            // Preserve previously configured availability, limits, and revision.
            transaction.update(reference, mapOf(
                "queueOpen" to requireNotNull(saved.getBoolean("queueOpen")),
                "maxPartySize" to maximum,
                "gracePeriodMinutes" to grace,
                "type" to business.type.name,
                "seatingMode" to business.settings.seatingMode.name,
                "skipLimit" to business.settings.skipLimit,
                "averageServiceMinutes" to business.settings.averageServiceMinutes,
                "tableIds" to business.tables.map { it.id },
                "largestTableId" to largestTable?.id,
                "maxTableCapacity" to largestCapacity,
                "revision" to (saved.getLong("revision") ?: 0L),
                "schemaVersion" to 1
            ))

            if (!publicQueue.exists()) {
                transaction.set(publicRef, mapOf("entries" to legacyEntries, "revision" to 0))
            }

            business.tables.forEachIndexed { index, table ->
                // Initial cloud tables start free; local demo reservations are not uploaded.
                transaction.set(tableReferences[index], mapOf(
                    "capacity" to table.capacity,
                    "occupiedByEntryId" to null
                ))
            }
            Unit
        }.await()
    }
}
