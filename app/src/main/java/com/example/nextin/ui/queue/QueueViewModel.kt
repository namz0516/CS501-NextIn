package com.example.nextin.ui.queue

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.nextin.data.model.BusinessSettings
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

    private val mutableNow = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = mutableNow.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                mutableNow.value = System.currentTimeMillis()
                try {
                    repository.expireCalls(mutableNow.value)
                } catch (exception: IllegalArgumentException) {
                    mutableError.value = exception.message
                }
                delay(1000)
            }
        }
    }

    fun confirmArrival(entryId: String): Boolean =
        staffAction { repository.confirmArrival(entryId, DEMO_CUSTOMER_UID) }

    fun keepExpired(businessId: String, entryId: String): Boolean =
        staffAction { repository.keepExpired(businessId, entryId) }

    fun updateSettings(businessId: String, settings: BusinessSettings): Boolean =
        staffAction { repository.updateSettings(businessId, settings) }

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

    fun currentEntry(snapshot: QueueSnapshot): QueueEntry? {
        return activeEntry(snapshot) ?: snapshot.entries.lastOrNull {
            it.customerUid == DEMO_CUSTOMER_UID
        }
    }

    fun addWalkIn(businessId: String, name: String, size: Int): Boolean =
        staffAction { repository.addWalkIn(businessId, name, size) }

    fun callParty(businessId: String, tableId: String?, entryId: String? = null): Boolean =
        staffAction { repository.callParty(businessId, tableId, entryId) }

    fun seat(businessId: String, entryId: String): Boolean =
        staffAction { repository.seat(businessId, entryId) }

    fun remove(businessId: String, entryId: String): Boolean =
        staffAction { repository.remove(businessId, entryId) }

    fun releaseTable(businessId: String, tableId: String): Boolean =
        staffAction { repository.releaseTable(businessId, tableId) }

    fun setQueueOpen(businessId: String, open: Boolean): Boolean =
        staffAction { repository.setQueueOpen(businessId, open) }

    private fun staffAction(action: () -> Unit): Boolean {
        clearError()
        return try {
            action()
            true
        } catch (exception: IllegalArgumentException) {
            mutableError.value = exception.message ?: "Could not update this queue."
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