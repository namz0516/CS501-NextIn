package com.example.nextin.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextin.data.cloud.*
import com.example.nextin.data.local.NextInDatabase
import com.example.nextin.data.local.RoomQueueStore
import com.example.nextin.data.model.*
import com.example.nextin.data.repository.LocalQueueRepository
import com.example.nextin.data.places.NearbyRestaurant
import com.example.nextin.wear.PhoneQueueSync
import com.example.nextin.data.sample.SampleBusinessData
import com.example.nextin.domain.queue.AlertSchedule
import com.example.nextin.domain.queue.GracePeriod
import com.example.nextin.domain.queue.QueueAlert
import com.example.nextin.domain.queue.QueueAlerts
import com.example.nextin.notifications.AlertPreferences
import com.example.nextin.notifications.AlertScheduler
import com.example.nextin.notifications.QueueNotifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val customerCloud = CloudQueueStore(firestore, auth)
    private val clock = CloudClock()
    private val notifier = QueueNotifier(application)
    private val alertPreferences = AlertPreferences(application)
    private val alertScheduler = AlertScheduler(application)
    private var scheduledOffset: Long? = null
    private var scheduledExact: Boolean? = null
    private val operationMutex = Mutex()
    private val wearSync = PhoneQueueSync(application)
    private var demoRepository: LocalQueueRepository? = null

    private fun isDemoBusiness(id: String): Boolean =
        id.startsWith("google-demo:")
    private var customerUid = ""
    private var cache: RoomQueueStore? = null
    private val cacheWrites = Channel<Pair<RoomQueueStore, QueueSnapshot>>(Channel.CONFLATED)
    private val listeners = mutableListOf<ListenerRegistration>()
    private var staffListener: ListenerRegistration? = null
    private var staffBusinessId: String? = null
    private var staffGeneration = 0
    private var initialization: Job? = null
    private var expiryJob: Job? = null
    private var clockJob: Job? = null
    private var clockSynchronized = false
    private var lastClockAttempt = 0L
    private var lastExpiryAttempt = 0L
    private var businesses = SampleBusinessData.businesses.map { it.copy(queueOpen = false) }
    private val tables = mutableMapOf<String, List<RestaurantTable>>()
    private var ownEntries = emptyList<QueueEntry>()
    private var publicEntries = emptyList<QueueEntry>()
    private var staffEntries = emptyList<QueueEntry>()
    private var businessOnline = false
    private var publicOnline = false
    private var customerOnline = false
    private var staffOnline = false

    private val mutableState = MutableStateFlow(QueueSnapshot())
    val state = mutableState.asStateFlow()
    private val mutableStaffState = MutableStateFlow(QueueSnapshot())
    val staffState = mutableStaffState.asStateFlow()
    private val mutableStaffReady = MutableStateFlow(false)
    val staffReady = mutableStaffReady.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableNow = MutableStateFlow(clock.now())
    val now = mutableNow.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading = mutableLoading.asStateFlow()
    private val mutableConnected = MutableStateFlow(false)
    val connected = mutableConnected.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener {
        if (customerUid.isNotEmpty() && it.currentUser?.uid != customerUid) {
            initialization?.cancel()
            stopListeners()
            customerUid = ""
            cache = null
            demoRepository = null
            wearSync.publish(QueueSnapshot(), "")
            ownEntries = emptyList()
            publicEntries = emptyList()
            mutableState.value = QueueSnapshot()
            mutableReady.value = false
            mutableLoading.value = false
            initialize()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        viewModelScope.launch(Dispatchers.IO) {
            for ((store, snapshot) in cacheWrites) {
                try { store.save(snapshot) }
                catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    mutableError.value = "Cloud data is current, but the device cache could not be saved."
                }
            }
        }
        initialize()
        viewModelScope.launch {
            while (isActive) {
                mutableNow.value = clock.now()
                checkExpiry()

                if (mutableReady.value && !mutableBusy.value) {
                    operationMutex.withLock {
                        val repo = demoRepository
                        if (repo != null) {
                            try {
                                val previous = repo.state.value
                                withContext(Dispatchers.IO) {
                                    repo.expireCalls()
                                }
                                if (previous != repo.state.value) publish()
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                mutableError.value =
                                    "Could not update demo queue expiry."
                            }
                        }
                    }
                }
                sendReminderIfDue()
                if (mutableReady.value && scheduledExact != null &&
                    scheduledExact != AlertScheduler.exactAllowed(getApplication())) {
                    syncAlarms(state.value)
                }
                delay(1000)
            }
        }
    }

    fun initialize() {
        if (mutableLoading.value || mutableReady.value) return
        mutableLoading.value = true
        clearError()
        initialization = viewModelScope.launch {
            try {
                val user = auth.currentUser ?: auth.signInAnonymously().await().user
                customerUid = requireNotNull(user) { "Customer sign-in failed." }.uid
                val store = RoomQueueStore(NextInDatabase.forCustomer(getApplication(), customerUid))
                val saved = withContext(Dispatchers.IO) { store.load() }
                val demoStore = RoomQueueStore(
                    NextInDatabase.forCustomer(
                        getApplication(), "local-demo:" + customerUid
                    )
                )

                demoRepository = withContext(Dispatchers.IO) {
                    val demoSaved = demoStore.load()
                    LocalQueueRepository(
                        initialBusinesses = demoSaved?.businesses.orEmpty(),
                        initialEntries = demoSaved?.entries.orEmpty(),
                        persist = demoStore::save
                    ).also { it.expireCalls() }
                }

                cache = store
                businesses = saved?.businesses ?: SampleBusinessData.businesses.map { it.copy(queueOpen = false) }
                ownEntries = saved?.entries.orEmpty().filter { it.customerUid == customerUid }
                publicEntries = saved?.entries.orEmpty().filter {
                    it.customerUid != customerUid && CloudQueueCodec.isActive(it)
                }.map { it.copy(customerUid = null, name = "Waiting party") }
                // Publish restored data before allowing navigation to render it.
                publish()
                mutableReady.value = true
                listen()
            } catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) {
                mutableError.value = "Could not start NextIn: " + exception.message
            } finally { mutableLoading.value = false }
        }
    }

    private fun listen() {
        val listeningUid = customerUid
        val store = CloudBusinessStore(firestore)
        listeners += store.observeBusinesses({ value, online ->
            businesses = value.sortedBy { business ->
                SampleBusinessData.businesses.indexOfFirst { it.id == business.id }
            }
            businessOnline = online
            publish()
        }, { customerError(it) })
        SampleBusinessData.businesses.forEach { business ->
            listeners += store.observeTables(business.id, { value ->
                tables[business.id] = value
                publish()
            }, { customerError(it) })
        }
        listeners += customerCloud.observePublic({ value, online ->
            publicEntries = value
            publicOnline = online
            publish()
        }, { customerError(it) })
        listeners += customerCloud.observeCustomer({ value, online ->
            if (customerUid == listeningUid && auth.currentUser?.uid == listeningUid) {
                ownEntries = value.filter { it.customerUid == listeningUid }
                customerOnline = online
                publish()
            }
        }, { if (customerUid == listeningUid) customerError(it) })
    }

    private fun customerError(exception: Exception) {
        customerOnline = false
        mutableConnected.value = false
        mutableError.value = "Cloud sync failed: " + exception.message
    }

    private fun publish() {
        val allBusinesses = businesses.map { it.copy(tables = tables[it.id] ?: it.tables) }
        // Own private records override anonymous projections with the same entry ID.
        val entries = (publicEntries.associateBy { it.id } +
            ownEntries.filter { it.customerUid == customerUid }.associateBy { it.id })
            .values.sortedWith(compareBy<QueueEntry> { it.joinedAtMillis }.thenBy { it.id })
        val cloudSnapshot = QueueSnapshot(allBusinesses, entries)
        val demo = demoRepository?.state?.value ?: QueueSnapshot()

        val snapshot = QueueSnapshot(
            businesses = allBusinesses + demo.businesses,
            entries = (entries + demo.entries).sortedWith(
                compareBy<QueueEntry> { it.joinedAtMillis }.thenBy { it.id }
            )
        )
        val previous = mutableState.value
        mutableState.value = snapshot
        wearSync.publish(snapshot, customerUid)
        if (mutableReady.value && previous != snapshot) {
            QueueAlerts.detect(previous, snapshot, customerUid).forEach { showAlert(it, snapshot) }
        }
        if (mutableReady.value) syncAlarms(snapshot)
        cache?.let { cacheWrites.trySend(it to cloudSnapshot) }
        mutableConnected.value = businessOnline && publicOnline && customerOnline
        val id = staffBusinessId
        mutableStaffState.value = QueueSnapshot(
            allBusinesses.filter { it.id == id }, staffEntries
        )
        mutableStaffReady.value = id != null && staffOnline && businessOnline &&
            publicOnline && tables.containsKey(id)
        if (mutableConnected.value && !clockSynchronized && clockJob?.isActive != true &&
            clock.now() - lastClockAttempt > 30000) {
            lastClockAttempt = clock.now()
            clockJob = viewModelScope.launch {
                try {
                    clock.synchronize(firestore, auth)
                    clockSynchronized = true
                }
                catch (exception: Exception) { if (exception is CancellationException) throw exception }
            }
        }
    }

    fun startStaff(businessId: String) {
        stopStaff()
        staffBusinessId = businessId
        val generation = staffGeneration
        val db = CloudSessions.staffFirestore(getApplication())
        val staffAuth = FirebaseAuth.getInstance(CloudSessions.staffApp(getApplication()))
        staffListener = CloudQueueStore(db, staffAuth).observeStaff(businessId, { entries, online ->
            if (generation == staffGeneration) {
                staffEntries = entries
                staffOnline = online
                publish()
            }
        }, { exception ->
            if (generation == staffGeneration) {
                staffEntries = emptyList()
                staffOnline = false
                publish()
                mutableError.value = "Staff sync failed: " + exception.message
            }
        })
    }

    fun stopStaff() {
        staffGeneration++
        staffListener?.remove()
        staffListener = null
        staffBusinessId = null
        staffEntries = emptyList()
        staffOnline = false
        mutableStaffReady.value = false
        mutableStaffState.value = QueueSnapshot()
    }

    fun activeEntry(snapshot: QueueSnapshot): QueueEntry? = snapshot.entries.firstOrNull {
        it.customerUid == customerUid && CloudQueueCodec.isActive(it)
    }

    fun currentEntry(snapshot: QueueSnapshot): QueueEntry? =
        activeEntry(snapshot) ?: snapshot.entries.lastOrNull { it.customerUid == customerUid }

    fun join(
        businessId: String,
        name: String,
        size: Int,
        onSuccess: () -> Unit
    ) {
        if (activeEntry(state.value) != null) {
            mutableError.value =
                "You already have an active queue. Leave it before joining another."
            return
        }

        if (isDemoBusiness(businessId)) {
            executeDemo(onSuccess = onSuccess) {
                it.join(businessId, customerUid, name, size)
            }
        } else {
            execute(onSuccess = onSuccess) {
                val id = customerCloud.join(businessId, name, size)
                withTimeout(15000) {
                    state.first { snapshot ->
                        snapshot.entries.any { it.id == id }
                    }
                }
            }
        }
    }

    private fun isDemoEntry(entryId: String): Boolean =
        demoRepository?.state?.value?.entries?.any {
            it.id == entryId
        } == true

    fun leave(entryId: String) {
        if (isDemoEntry(entryId)) {
            executeDemo { it.leave(entryId, customerUid) }
        } else {
            execute { customerCloud.leave(entryId) }
        }
    }

    fun confirmArrival(entryId: String) {
        if (isDemoEntry(entryId)) {
            executeDemo { it.confirmArrival(entryId, customerUid) }
        } else {
            execute { customerCloud.confirmArrival(entryId) }
        }
    }

    fun registerDemoRestaurant(restaurant: NearbyRestaurant, onSuccess: (String) -> Unit) {
        val id = "google-demo:${restaurant.id}"
        val business = Business(
            id = id,
            name = restaurant.name,
            address = restaurant.address,
            description = "NextIn demonstration queue. Not operated by this restaurant.",
            latitude = restaurant.latitude,
            longitude = restaurant.longitude,
            type = when (restaurant.primaryType) {
                "restaurant", "meal_takeaway", "meal_delivery" ->
                    BusinessType.RESTAURANT

                "cafe", "coffee_shop", "tea_house",
                "bubble_tea_store", "beverage_store",
                "juice_shop" ->
                    BusinessType.CAFE

                "movie_theater" ->
                    BusinessType.CINEMA

                "store", "clothing_store", "shopping_mall",
                "convenience_store", "book_store", "shoe_store",
                "electronics_store", "supermarket" ->
                    BusinessType.SHOP

                "tourist_attraction", "travel_agency" ->
                    BusinessType.TOURS_AND_ATTRACTIONS

                "beauty_salon", "hair_salon", "nail_salon" ->
                    BusinessType.SALON_AND_BEAUTY

                else -> BusinessType.OTHER
            },
            queueOpen = true,
            settings = BusinessSettings(
                seatingMode = SeatingMode.FCFS,
                maxPartySize = 8,
                averageServiceMinutes = 25,
                gracePeriodMinutes = 5
            ),
            tables = listOf(2, 4, 6, 8).mapIndexed { index, capacity ->
                RestaurantTable(id = "${id}:table:${index}", capacity = capacity)
            }
        )
        executeDemo(onSuccess = { onSuccess(id) }) {
            it.registerDemoRestaurant(business)
        }
    }


    private fun executeDemo(
        onSuccess: () -> Unit = {},
        action: (LocalQueueRepository) -> Unit
    ) {
        if (mutableBusy.value || !mutableReady.value) return

        mutableBusy.value = true
        clearError()

        viewModelScope.launch {
            try {
                operationMutex.withLock {
                    val repo = requireNotNull(demoRepository)
                    withContext(Dispatchers.IO) {
                        action(repo)
                    }
                    publish()
                }
                onSuccess()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                publish()
                mutableError.value =
                    exception.message ?: "Could not update the demo queue."
            } finally {
                mutableBusy.value = false
            }
        }
    }

    private fun staffStore(id: String): CloudStaffQueueStore {
        require(staffBusinessId == id && mutableStaffReady.value) { "Reconnect or sign in to this business." }
        return CloudStaffQueueStore(CloudSessions.staffFirestore(getApplication()), clock::now)
    }

    fun addWalkIn(id: String, name: String, size: Int, onSuccess: () -> Unit) =
        execute(onSuccess = onSuccess) { staffStore(id).addWalkIn(id, name, size) }
    fun callParty(id: String, tableId: String?, entryId: String? = null) =
        execute { staffStore(id).call(id, tableId, entryId) }
    fun seat(id: String, entryId: String) = execute { staffStore(id).seat(id, entryId) }
    fun remove(id: String, entryId: String) = execute { staffStore(id).remove(id, entryId) }
    fun releaseTable(id: String, tableId: String) = execute { staffStore(id).releaseTable(id, tableId) }
    fun setQueueOpen(id: String, open: Boolean) = execute { staffStore(id).setQueueOpen(id, open) }
    fun keepExpired(id: String, entryId: String) = execute { staffStore(id).keepExpired(id, entryId) }
    fun updateSettings(id: String, settings: BusinessSettings, onSuccess: () -> Unit) =
        execute(onSuccess = onSuccess) { staffStore(id).updateSettings(id, settings) }

    private fun execute(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (mutableBusy.value || !mutableReady.value) return
        if (!mutableConnected.value) {
            mutableError.value = "You are viewing saved data. Reconnect before changing the queue."
            return
        }
        mutableBusy.value = true
        clearError()
        viewModelScope.launch {
            try {
                operationMutex.withLock { action() }
                onSuccess()
            } catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) {
                mutableError.value = exception.message ?: "The cloud change could not be completed."
            } finally { mutableBusy.value = false }
        }
    }

    private fun checkExpiry() {
        if (!mutableConnected.value || expiryJob?.isActive == true ||
            clock.now() - lastExpiryAttempt < 5000) return
        val id = staffBusinessId
        val snapshot = if (id != null && mutableStaffReady.value) staffState.value else state.value
        val expired = snapshot.entries.firstOrNull { entry ->
            val business = snapshot.businesses.firstOrNull { it.id == entry.businessId }
            !isDemoBusiness(entry.businessId) &&
                (id != null || entry.customerUid == customerUid) &&
                business != null && GracePeriod.hasExpired(entry, business, clock.now())
        } ?: return
        if (id == null && expired.customerUid != customerUid) return
        lastExpiryAttempt = clock.now()
        expiryJob = viewModelScope.launch {
            try {
                operationMutex.withLock {
                    if (id != null && id == staffBusinessId && mutableStaffReady.value) {
                        staffStore(id).expireCalls(id)
                    } else if (expired.customerUid == customerUid) customerCloud.expire(expired.id)
                }
            } catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { /* Server deadline and connectivity win; retry later. */ }
        }
    }

    fun clearError() { mutableError.value = null }

    private companion object {
        const val OFFSET_TOLERANCE_MILLIS = 2_000L
    }

    private fun showAlert(alert: QueueAlert, snapshot: QueueSnapshot) {
        if (alert is QueueAlert.Expired) {
            val calledAt = snapshot.entries.firstOrNull { it.id == alert.entryId }?.calledAtMillis
            val key = AlertSchedule.key(alert.entryId, calledAt)
            if (key in alertPreferences.sentExpiries()) return
            alertPreferences.markExpirySent(key)
        }
        notifier.show(alert)
    }

    private fun syncAlarms(snapshot: QueueSnapshot) {
        val offset = clock.now() - System.currentTimeMillis()
        val exact = AlertScheduler.exactAllowed(getApplication())
        val offsetChanged = scheduledOffset?.let { abs(it - offset) > OFFSET_TOLERANCE_MILLIS } ?: true
        val reschedule = offsetChanged || scheduledExact != exact
        val saved = alertPreferences.scheduledAlarms()
        val planned = mutableMapOf<String, String>()
        snapshot.entries.filter {
            it.customerUid == customerUid &&
                !isDemoBusiness(it.businessId)
        }.forEach { entry ->
            val business = snapshot.businesses.firstOrNull { it.id == entry.businessId } ?: return@forEach
            val grace = business.settings.gracePeriodMinutes
            val times = AlertSchedule.times(entry, grace)
            if (times.isEmpty()) return@forEach
            val signature = "${entry.calledAtMillis}:$grace"
            planned[entry.id] = signature
            if (!reschedule && saved[entry.id] == signature) return@forEach
            times.forEach { time ->
                alertScheduler.schedule(
                    entry.id, business.id, business.name, requireNotNull(entry.calledAtMillis),
                    time.kind, time.atMillis, offset
                )
            }
        }
        (saved.keys - planned.keys).forEach(alertScheduler::cancel)
        if (planned != saved) alertPreferences.saveScheduledAlarms(planned)
        scheduledOffset = offset
        scheduledExact = exact
    }

    private fun sendReminderIfDue() {
        if (!mutableReady.value) return
        val snapshot = mutableState.value
        val sent = alertPreferences.sentReminders()
        val reminder = QueueAlerts.reminder(
            snapshot, customerUid, mutableNow.value, sent
        ) ?: return
        val called = snapshot.entries
            .filter { it.status == QueueStatus.CALLED }
            .map { AlertSchedule.key(it.id, it.calledAtMillis) }
            .toSet()
        alertPreferences.saveSentReminders(sent.filter { it in called }.toSet() + reminder.key)
        notifier.show(reminder)
    }

    private fun stopListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
        stopStaff()
        businessOnline = false
        publicOnline = false
        customerOnline = false
        tables.clear()
        mutableConnected.value = false
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        stopListeners()
        cacheWrites.close()
        super.onCleared()
    }
}
