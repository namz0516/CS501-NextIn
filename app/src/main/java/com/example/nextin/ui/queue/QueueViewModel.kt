package com.example.nextin.ui.queue

import androidx.lifecycle.ViewModel
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueSnapshot
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.data.repository.LocalQueueRepository
import com.example.nextin.data.sample.SampleBusinessData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QueueViewModel(
    private val repository: LocalQueueRepository =
        LocalQueueRepository(SampleBusinessData.businesses)
) : ViewModel() {

    val state: StateFlow<QueueSnapshot> = repository.state

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    fun activeEntry(snapshot: QueueSnapshot): QueueEntry? {
        return snapshot.entries.firstOrNull {
            it.customerUid == DEMO_CUSTOMER_UID &&
                    it.status in setOf(
                QueueStatus.WAITING,
                QueueStatus.CALLED,
                QueueStatus.EXPIRED
            )
        }
    }

    fun join(
        businessId: String,
        name: String,
        partySize: Int
    ): QueueEntry? {
        clearError()

        return try {
            repository.join(
                businessId = businessId,
                customerUid = DEMO_CUSTOMER_UID,
                name = name,
                partySize = partySize
            )
        } catch (exception: IllegalArgumentException) {
            mutableError.value =
                exception.message ?: "Could not join the queue."

            null
        }
    }

    fun leave(entryId: String): Boolean {
        clearError()

        return try {
            repository.leave(
                entryId = entryId,
                customerUid = DEMO_CUSTOMER_UID
            )

            true
        } catch (exception: IllegalArgumentException) {
            mutableError.value =
                exception.message ?: "Could not leave the queue."

            false
        }
    }

    fun clearError() {
        mutableError.value = null
    }

    companion object {
        private const val DEMO_CUSTOMER_UID = "demo-customer"
    }
}