package com.example.nextin.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextin.data.local.NextInDatabase
import com.example.nextin.data.local.RoomQueueStore
import com.example.nextin.data.model.*
import com.example.nextin.data.repository.LocalQueueRepository
import com.example.nextin.data.sample.SampleBusinessData
import com.example.nextin.domain.queue.QueueAlerts
import com.example.nextin.domain.queue.ServerClock
import com.example.nextin.notifications.QueueNotifier
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    // Connects to Firebase Authentication.
    private val auth = FirebaseAuth.getInstance()
    // Stores the signed-in customer's ID for queue operations.
    private var customerUid: String = ""
    private val store = RoomQueueStore(NextInDatabase.getInstance(application))
    private var repository: LocalQueueRepository? = null
    private val mutex = Mutex()
    private val clock = ServerClock()
    private val notifier = QueueNotifier(application)
    private val sentReminders = mutableSetOf<String>()

    private val mutableState = MutableStateFlow(QueueSnapshot())
    val state: StateFlow<QueueSnapshot> = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()
    private val mutableNow = MutableStateFlow(clock.now())
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
                mutableNow.value = clock.now()
                mutex.withLock {
                    val repo = repository
                    if (repo != null) {
                        try {
                            withContext(Dispatchers.IO) { repo.expireCalls(mutableNow.value) }
                            publish(repo.state.value)
                            sendReminderIfDue()
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
                    // Reuse the saved account, or create an anonymous account.
                    val user = auth.currentUser
                        ?: auth.signInAnonymously().await().user
                    // Stop initialization if Firebase did not return a user.
                    customerUid = requireNotNull(user) {
                        "Customer sign-in failed. Please retry."
                    }.uid
                    // Continue loading the existing local queue database.
                    val repo = withContext(Dispatchers.IO) {
                        val saved = store.load()
                        val snapshot = saved ?: QueueSnapshot(SampleBusinessData.businesses)
                        if (saved == null) store.save(snapshot)
                        LocalQueueRepository(
                            initialBusinesses = snapshot.businesses,
                            nowMillis = clock::now,
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
                    mutableError.value = "Could not start NextIn: ${exception.message}"
                } finally {
                    mutableLoading.value = false
                }
            }
        }
    }

    fun activeEntry(snapshot: QueueSnapshot): QueueEntry? {
        return snapshot.entries.firstOrNull {
            it.customerUid == customerUid && it.status in setOf(
                QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED
            )
        }
    }

    fun currentEntry(snapshot: QueueSnapshot): QueueEntry? {
        return activeEntry(snapshot) ?: snapshot.entries.lastOrNull { it.customerUid == customerUid }
    }

    fun join(businessId: String, name: String, size: Int, onSuccess: () -> Unit) =
        execute(onSuccess) { it.join(businessId, customerUid, name, size) }

    fun leave(entryId: String) = execute { it.leave(entryId, customerUid) }

    fun addWalkIn(id: String, name: String, size: Int, onSuccess: () -> Unit) =
        execute(onSuccess) { it.addWalkIn(id, name, size) }

    fun callParty(id: String, tableId: String?, entryId: String? = null) =
        execute { it.callParty(id, tableId, entryId) }

    fun seat(id: String, entryId: String) = execute { it.seat(id, entryId) }
    fun remove(id: String, entryId: String) = execute { it.remove(id, entryId) }
    fun releaseTable(id: String, tableId: String) = execute { it.releaseTable(id, tableId) }
    fun setQueueOpen(id: String, open: Boolean) = execute { it.setQueueOpen(id, open) }
    fun confirmArrival(entryId: String) = execute { it.confirmArrival(entryId, customerUid) }
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
                    publish(repo.state.value)
                    onSuccess()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    repository?.let { publish(it.state.value) }
                    mutableError.value = exception.message ?: "Could not save this queue change."
                } finally {
                    mutableBusy.value = false
                }
            }
        }
    }

    fun clearError() { mutableError.value = null }

    fun syncServerTime(serverMillis: Long, requestStartedAt: Long, responseReceivedAt: Long) {
        clock.sync(serverMillis, requestStartedAt, responseReceivedAt)
        mutableNow.value = clock.now()
    }

    private fun publish(next: QueueSnapshot) {
        val previous = mutableState.value
        mutableState.value = next
        if (!mutableReady.value || previous == next) return
        QueueAlerts.detect(previous, next, customerUid).forEach(notifier::show)
    }

    private fun sendReminderIfDue() {
        val reminder = QueueAlerts.reminder(
            mutableState.value, customerUid, mutableNow.value, sentReminders
        ) ?: return
        sentReminders += reminder.key
        notifier.show(reminder)
    }

}