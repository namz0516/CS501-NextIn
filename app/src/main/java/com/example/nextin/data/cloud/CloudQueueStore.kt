package com.example.nextin.data.cloud

import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueSource
import com.example.nextin.data.model.QueueStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CloudQueueStore(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    init {
        require(firestore.app.name == auth.app.name) {
            "Firestore and Authentication must use the same Firebase session."
        }
    }

    suspend fun join(businessId: String, name: String, partySize: Int): String {
        val uid = requireNotNull(auth.currentUser) { "Sign in before joining." }.uid
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 80) {
            "Enter a name between 1 and 80 characters."
        }
        require(partySize in 1..8) { "Party size must be between 1 and 8." }

        val businessRef = firestore.collection("businesses").document(businessId)
        val customerRef = firestore.collection("customerQueues").document(uid)
        val entryRef = firestore.collection("queueEntries").document()

        firestore.runTransaction { transaction ->
            // Read all required records before writing anything.
            val business = transaction.get(businessRef)
            val customer = transaction.get(customerRef)
            val previousId = customer.getString("entryId")
            val previous = previousId?.let {
                transaction.get(firestore.collection("queueEntries").document(it))
            }

            require(business.getBoolean("queueOpen") == true) { "This queue is closed." }
            val maximum = requireNotNull(business.getLong("maxPartySize")) {
                "The business has no maxPartySize setting."
            }
            require(partySize.toLong() <= maximum) {
                "This business accepts parties of up to $maximum."
            }
            require(previous?.getString("status") !in ACTIVE_STATUSES) {
                "You already have an active queue. Leave it before joining another."
            }

            transaction.set(entryRef, mapOf(
                "businessId" to businessId,
                "customerUid" to uid,
                "name" to cleanName,
                "partySize" to partySize,
                "source" to QueueSource.APP.name,
                "status" to QueueStatus.WAITING.name,
                "joinedAt" to FieldValue.serverTimestamp(),
                "skipCount" to 0,
                "calledAt" to null,
                "confirmedArrival" to false,
                "tableId" to null,
                "completedAt" to null
            ))
            transaction.set(customerRef, mapOf(
                "entryId" to entryRef.id,
                "businessId" to businessId
            ))
            entryRef.id
        }.await()

        return entryRef.id
    }

    suspend fun leave(entryId: String) {
        val uid = requireNotNull(auth.currentUser) {
            "Sign in before leaving a queue."
        }.uid
        val reference = firestore.collection("queueEntries").document(entryId)

        firestore.runTransaction { transaction ->
            val entry = transaction.get(reference)
            require(entry.getString("customerUid") == uid) {
                "You can only leave your own queue."
            }
            require(entry.getString("status") in ACTIVE_STATUSES) {
                "This queue entry is already completed."
            }

            val businessId = requireNotNull(entry.getString("businessId"))
            val tableId = entry.getString("tableId")
            val tableRef = tableId?.let {
                firestore.collection("businesses").document(businessId)
                    .collection("tables").document(it)
            }
            // Read the reservation before any transaction writes.
            val table = tableRef?.let { transaction.get(it) }
            if (tableRef != null) {
                require(table?.getString("occupiedByEntryId") == entryId) {
                    "The table reservation has changed. Ask staff for help."
                }
            }

            transaction.update(reference, mapOf(
                "status" to QueueStatus.LEFT.name,
                "completedAt" to FieldValue.serverTimestamp()
            ))
            if (tableRef != null) {
                transaction.update(tableRef, "occupiedByEntryId", null)
            }
            Unit
        }.await()
    }

    suspend fun confirmArrival(entryId: String) {
        val uid = requireNotNull(auth.currentUser) {
            "Sign in before confirming arrival."
        }.uid
        val reference = firestore.collection("queueEntries").document(entryId)

        firestore.runTransaction { transaction ->
            val entry = transaction.get(reference)
            require(entry.getString("customerUid") == uid) {
                "You can only confirm your own arrival."
            }
            require(entry.getString("status") == QueueStatus.CALLED.name) {
                "This party is not currently called."
            }
            requireNotNull(entry.getTimestamp("calledAt")) {
                "The call has no saved start time."
            }
            val businessId = requireNotNull(entry.getString("businessId"))
            val business = transaction.get(
                firestore.collection("businesses").document(businessId)
            )
            val grace = business.getLong("gracePeriodMinutes")
            require(grace != null && grace in 1L..30L) {
                "The business needs a valid gracePeriodMinutes setting."
            }

            // Firestore rules check the deadline using server time.
            // Repeated confirmation is harmless once it has succeeded.
            if (entry.getBoolean("confirmedArrival") != true) {
                transaction.update(reference, "confirmedArrival", true)
            }
            Unit
        }.await()
    }

    fun observeCustomer(
        onChange: (List<QueueEntry>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val uid = requireNotNull(auth.currentUser) { "Customer sign-in is required." }.uid
        return observe(
            firestore.collection("queueEntries").whereEqualTo("customerUid", uid),
            onChange, onError
        )
    }

    fun observeStaff(
        businessId: String,
        onChange: (List<QueueEntry>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return observe(
            firestore.collection("queueEntries").whereEqualTo("businessId", businessId),
            onChange, onError
        )
    }

    private fun observe(
        query: Query,
        onChange: (List<QueueEntry>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null || snapshot.metadata.hasPendingWrites()) {
                return@addSnapshotListener
            }
            try {
                val entries = snapshot.documents.map(::toEntry)
                    .sortedWith(compareBy<QueueEntry> { it.joinedAtMillis }.thenBy { it.id })
                onChange(entries)
            } catch (exception: Exception) {
                onError(exception)
            }
        }
    }

    private fun toEntry(document: DocumentSnapshot): QueueEntry {
        return QueueEntry(
            id = document.id,
            businessId = requireNotNull(document.getString("businessId")),
            customerUid = document.getString("customerUid"),
            name = requireNotNull(document.getString("name")),
            partySize = requireNotNull(document.getLong("partySize")).toInt(),
            source = QueueSource.valueOf(requireNotNull(document.getString("source"))),
            status = QueueStatus.valueOf(requireNotNull(document.getString("status"))),
            joinedAtMillis = requireNotNull(document.getTimestamp("joinedAt")).toDate().time,
            skipCount = requireNotNull(document.getLong("skipCount")).toInt(),
            calledAtMillis = document.getTimestamp("calledAt")?.toDate()?.time,
            confirmedArrival = document.getBoolean("confirmedArrival") == true,
            tableId = document.getString("tableId"),
            completedAtMillis = document.getTimestamp("completedAt")?.toDate()?.time
        )
    }

    companion object {
        private val ACTIVE_STATUSES = setOf("WAITING", "CALLED", "EXPIRED")
    }
}
