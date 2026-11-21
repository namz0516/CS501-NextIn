package com.example.nextin.ui.recommendations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextin.BuildConfig
import com.example.nextin.data.places.LocationProvider
import com.example.nextin.data.places.NearbyRestaurant
import com.example.nextin.data.places.PlacesRepository
import com.example.nextin.data.places.RecommendationsPreferences
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RecommendationsState(
    val loading: Boolean = false,
    val restaurants: List<NearbyRestaurant> = emptyList(),
    val error: String? = null
)

class RecommendationsViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val preferences = RecommendationsPreferences(application)
    private val _state = MutableStateFlow(RecommendationsState())
    val state: StateFlow<RecommendationsState> = _state
    val showNearby = preferences.showNearby

    fun setShowNearby(enabled: Boolean) {
        viewModelScope.launch { preferences.setShowNearby(enabled) }
    }

    fun refresh() {
        if (_state.value.loading) return
        if (BuildConfig.PLACES_API_KEY.isBlank() || !Places.isInitialized()) {
            _state.value = RecommendationsState(error = "Google Places is not configured on this device")
            return
        }
        _state.value = RecommendationsState(loading = true)
        locationProvider.getCurrentLocation(
            onSuccess = { latitude, longitude ->
                PlacesRepository(getApplication()).searchNearbyRestaurants(
                    latitude, longitude,
                    onSuccess = { _state.value = RecommendationsState(restaurants = it) },
                    onFailure = { _state.value = RecommendationsState(error = it.localizedMessage ?: "Search failed") }
                )
            },
            onFailure = { _state.value = RecommendationsState(error = it) }
        )
    }
}
