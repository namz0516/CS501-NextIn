package com.example.nextin.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextin.data.local.NextInDatabase
import com.example.nextin.data.local.RoomQueueStore
import com.example.nextin.data.model.*
import com.example.nextin.data.repository.LocalQueueRepository
import com.example.nextin.data.sample.SampleBusinessData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RoomQueueStore(NextInDatabase.getInstance(application))
    private var repository: LocalQueueRepository? = null
    private val mutex = Mutex()

    private val mutableState = MutableStateFlow(QueueSnapshot())
    val state: StateFlow<QueueSnapshot> = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()
    private val mutableNow = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = mutableNow.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = mutableReady.asStateFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()

    init {
        initialize()
        viewModelScope.launch {
            while (isActive) {
                mutableNow.value = System.currentTimeMillis()
                mutex.withLock {
                    val repo = repository
                    if (repo != null) {
                        try {
                            withContext(Dispatchers.IO) { repo.expireCalls(mutableNow.value) }
                            mutableState.value = repo.state.value
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            mutableError.value = exception.message ?: "Could not save queue expiry."
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    fun initialize() {
        if (mutableReady.value || mutableLoading.value) return
        mutableLoading.value = true
        clearError()
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val repo = withContext(Dispatchers.IO) {
                        val saved = store.load()
                        val snapshot = saved ?: QueueSnapshot(SampleBusinessData.businesses)
                        if (saved == null) store.save(snapshot)
                        LocalQueueRepository(
                            initialBusinesses = snapshot.businesses,
                            initialEntries = snapshot.entries,
                            persist = store::save
                        ).also { it.expireCalls() }
                    }
                    repository = repo
                    mutableState.value = repo.state.value
                    mutableReady.value = true
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    mutableError.value = "Could not load saved queues: ${exception.message}"
                } finally {
                    mutableLoading.value = false
                }
            }
        }
    }

    fun activeEntry(snapshot: QueueSnapshot): QueueEntry? {
        return snapshot.entries.firstOrNull {
            it.customerUid == DEMO_CUSTOMER_UID && it.status in setOf(
                QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED
            )
        }
    }

    fun currentEntry(snapshot: QueueSnapshot): QueueEntry? {
        return activeEntry(snapshot) ?: snapshot.entries.lastOrNull { it.customerUid == DEMO_CUSTOMER_UID }
    }

    fun join(businessId: String, name: String, size: Int, onSuccess: () -> Unit) =
        execute(onSuccess) { it.join(businessId, DEMO_CUSTOMER_UID, name, size) }

    fun leave(entryId: String) = execute { it.leave(entryId, DEMO_CUSTOMER_UID) }

    fun addWalkIn(id: String, name: String, size: Int, onSuccess: () -> Unit) =
        execute(onSuccess) { it.addWalkIn(id, name, size) }

    fun callParty(id: String, tableId: String?, entryId: String? = null) =
        execute { it.callParty(id, tableId, entryId) }

    fun seat(id: String, entryId: String) = execute { it.seat(id, entryId) }
    fun remove(id: String, entryId: String) = execute { it.remove(id, entryId) }
    fun releaseTable(id: String, tableId: String) = execute { it.releaseTable(id, tableId) }
    fun setQueueOpen(id: String, open: Boolean) = execute { it.setQueueOpen(id, open) }
    fun confirmArrival(entryId: String) = execute { it.confirmArrival(entryId, DEMO_CUSTOMER_UID) }
    fun keepExpired(id: String, entryId: String) = execute { it.keepExpired(id, entryId) }
    fun updateSettings(id: String, settings: BusinessSettings, onSuccess: () -> Unit) =
        execute(onSuccess) { it.updateSettings(id, settings) }

    private fun execute(
        onSuccess: () -> Unit = {},
        action: (LocalQueueRepository) -> Unit
    ) {
        if (mutableBusy.value || !mutableReady.value) return
        mutableBusy.value = true
        clearError()
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val repo = requireNotNull(repository)
                    withContext(Dispatchers.IO) { action(repo) }
                    mutableState.value = repo.state.value
                    onSuccess()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    repository?.let { mutableState.value = it.state.value }
                    mutableError.value = exception.message ?: "Could not save this queue change."
                } finally {
                    mutableBusy.value = false
                }
            }
        }
    }

    fun clearError() { mutableError.value = null }

    companion object {
        private const val DEMO_CUSTOMER_UID = "demo-customer"
    }
}