package com.example.nextin.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextin.data.local.NextInDatabase
import com.example.nextin.data.local.RoomQueueStore
import com.example.nextin.data.model.*
import com.example.nextin.data.repository.LocalQueueRepository
import com.example.nextin.data.places.NearbyRestaurant
import com.example.nextin.data.sample.SampleBusinessData
import com.example.nextin.wear.PhoneQueueSync
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

    //phone synch object
    private val wearSync = PhoneQueueSync(application)

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
                            if (mutableState.value != repo.state.value) {
                                mutableState.value = repo.state.value
                                //during init
                                wearSync.publish(repo.state.value)
                            }
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
                        val snapshot = if (saved == null) {
                            QueueSnapshot(SampleBusinessData.businesses)
                        } else {
                            val missing = SampleBusinessData.businesses.filter { sample ->
                                sample.id == "orca-sightseeing" && saved.businesses.none { it.id == sample.id }
                            }
                            if (missing.isEmpty()) saved else saved.copy(businesses = saved.businesses + missing)
                        }
                        if (saved == null || snapshot != saved) store.save(snapshot)
                        LocalQueueRepository(
                            initialBusinesses = snapshot.businesses,
                            initialEntries = snapshot.entries,
                            persist = store::save
                        ).also { it.expireCalls() }
                    }
                    repository = repo
                    mutableState.value = repo.state.value
                    //after queue ops
                    wearSync.publish(repo.state.value)
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

    /** Google Places metadata + an explicitly simulated local NextIn queue. */
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
        execute(onSuccess = { onSuccess(id) }) { it.registerDemoRestaurant(business) }
    }

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
                    //during expiry checks
                    wearSync.publish(repo.state.value)
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