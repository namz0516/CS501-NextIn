package com.example.nextin.data.cloud

import com.example.nextin.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.tasks.await

class CloudQueueStore(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    init {
        require(firestore.app.name == auth.app.name) { "Firebase sessions must match." }
    }

    suspend fun join(businessId: String, name: String, partySize: Int): String {
        val uid = requireNotNull(auth.currentUser) { "Sign in before joining." }.uid
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 80) { "Enter a name of 1–80 characters." }
        require(partySize in 1..8) { "Party size must be between 1 and 8." }
        val businessRef = firestore.collection("businesses").document(businessId)
        val queueRef = firestore.collection("queuePublic").document(businessId)
        val customerRef = firestore.collection("customerQueues").document(uid)
        val entryRef = firestore.collection("queueEntries").document()

        firestore.runTransaction { transaction ->
            val business = transaction.get(businessRef)
            val queue = transaction.get(queueRef)
            val customer = transaction.get(customerRef)
            val previous = customer.getString("entryId")?.let {
                transaction.get(firestore.collection("queueEntries").document(it))
            }
            require(business.getLong("schemaVersion") == 1L && queue.exists()) {
                "Staff must sign in once to finish setting up this business."
            }
            require(business.getBoolean("queueOpen") == true) { "This queue is closed." }
            require(partySize <= requireNotNull(business.getLong("maxPartySize"))) {
                "This party is larger than the business limit."
            }
            require(previous?.getString("status") !in setOf("WAITING", "CALLED", "EXPIRED")) {
                "You already have an active queue. Leave it before joining another."
            }
            val entry = QueueEntry(
                id = entryRef.id, businessId = businessId, customerUid = uid,
                name = cleanName, partySize = partySize, joinedAtMillis = System.currentTimeMillis()
            )
            val data = CloudQueueCodec.privateData(entry)
            val publicEntries = CloudQueueCodec.publicEntries(queue).toMutableMap()
            publicEntries[entry.id] = CloudQueueCodec.publicData(data)

            // Ownership, private data, and anonymous position data commit together.
            transaction.set(entryRef, data)
            transaction.set(customerRef, mapOf("entryId" to entry.id, "businessId" to businessId))
            transaction.update(queueRef, mapOf(
                "entries" to publicEntries, "revision" to (queue.getLong("revision")!! + 1)
            ))
            entry.id
        }.await()
        return entryRef.id
    }

    suspend fun leave(entryId: String) = changeOwnEntry(entryId, "leave")
    suspend fun confirmArrival(entryId: String) = changeOwnEntry(entryId, "confirm")
    suspend fun expire(entryId: String) = changeOwnEntry(entryId, "expire")

    private suspend fun changeOwnEntry(entryId: String, action: String) {
        val uid = requireNotNull(auth.currentUser) { "Customer sign-in is required." }.uid
        val reference = firestore.collection("queueEntries").document(entryId)
        firestore.runTransaction { transaction ->
            val saved = transaction.get(reference)
            val entry = CloudQueueCodec.entry(saved)
            require(entry.customerUid == uid) { "You can only change your own entry." }
            val queueRef = firestore.collection("queuePublic").document(entry.businessId)
            val queue = transaction.get(queueRef)
            val business = transaction.get(firestore.collection("businesses").document(entry.businessId))
            val tableRef = if (action == "leave") entry.tableId?.let {
                firestore.collection("businesses").document(entry.businessId).collection("tables").document(it)
            } else null
            val table = tableRef?.let { transaction.get(it) }
            val next = when (action) {
                "leave" -> {
                    require(CloudQueueCodec.isActive(entry)) { "This entry is already completed." }
                    if (tableRef != null) require(table?.getString("occupiedByEntryId") == entryId) {
                        "The reservation changed. Please contact staff."
                    }
                    entry.copy(status = QueueStatus.LEFT, completedAtMillis = System.currentTimeMillis())
                }
                "confirm" -> {
                    require(entry.status == QueueStatus.CALLED) { "This party is not currently called." }
                    if (entry.confirmedArrival) return@runTransaction Unit
                    require((business.getLong("gracePeriodMinutes") ?: 0L) in 1L..30L) { "Invalid grace period." }
                    // Server rules, rather than the phone clock, enforce the deadline.
                    entry.copy(confirmedArrival = true)
                }
                else -> {
                    if (entry.status != QueueStatus.CALLED || entry.confirmedArrival) return@runTransaction Unit
                    entry.copy(status = QueueStatus.EXPIRED)
                }
            }
            val data = CloudQueueCodec.privateData(next, saved)
            val publicEntries = CloudQueueCodec.publicEntries(queue).toMutableMap()
            if (CloudQueueCodec.isActive(next)) publicEntries[entryId] = CloudQueueCodec.publicData(data)
            else publicEntries.remove(entryId)
            transaction.set(reference, data)
            transaction.update(queueRef, mapOf(
                "entries" to publicEntries, "revision" to (queue.getLong("revision")!! + 1)
            ))
            if (tableRef != null) transaction.update(tableRef, "occupiedByEntryId", null)
            Unit
        }.await()
    }

    fun observeCustomer(
        onChange: (List<QueueEntry>, Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val uid = requireNotNull(auth.currentUser).uid
        return observe(firestore.collection("queueEntries").whereEqualTo("customerUid", uid), onChange, onError)
    }

    fun observeStaff(
        businessId: String,
        onChange: (List<QueueEntry>, Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = observe(
        firestore.collection("queueEntries").whereEqualTo("businessId", businessId), onChange, onError
    )

    fun observePublic(
        onChange: (List<QueueEntry>, Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = firestore.collection("queuePublic")
        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) { onError(error); return@addSnapshotListener }
            if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
            try {
                val entries = snapshot.documents.flatMap { document ->
                    CloudQueueCodec.publicEntries(document).map { (id, data) ->
                        CloudQueueCodec.entry(id, data, true)
                    }
                }
                onChange(entries, !snapshot.metadata.isFromCache)
            } catch (exception: Exception) { onError(exception) }
        }

    private fun observe(
        query: Query,
        onChange: (List<QueueEntry>, Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) { onError(error); return@addSnapshotListener }
        if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
        try {
            onChange(snapshot.documents.map(CloudQueueCodec::entry)
                .sortedWith(compareBy<QueueEntry> { it.joinedAtMillis }.thenBy { it.id }),
                !snapshot.metadata.isFromCache)
        } catch (exception: Exception) { onError(exception) }
    }
}
