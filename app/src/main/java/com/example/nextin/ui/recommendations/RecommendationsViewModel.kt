package com.example.nextin.ui.recommendations

import android.app.Application
import android.location.Location
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
    val error: String? = null,
    val userLatitude: Double? = null,
    val userLongitude: Double? = null
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

    /** Search with device coordinates when available; fall back to unbiased text search. */
    fun searchAnywhere(query: String, useLocation: Boolean) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _state.value = RecommendationsState(error = "Enter a restaurant name")
            return
        }
        if (_state.value.loading) return
        if (BuildConfig.PLACES_API_KEY.isBlank() || !Places.isInitialized()) {
            _state.value = RecommendationsState(error = "Google Places is not configured on this device")
            return
        }
        _state.value = RecommendationsState(loading = true)
        if (useLocation) {
            locationProvider.getCurrentLocation(
                onSuccess = { lat, lng -> runTextSearch(trimmed, lat, lng) },
                onFailure = { runTextSearch(trimmed, null, null) }
            )
        } else {
            runTextSearch(trimmed, null, null)
        }
    }

    private fun runTextSearch(query: String, latitude: Double?, longitude: Double?) {
        PlacesRepository(getApplication()).searchRestaurantsAnywhere(
            query, latitude, longitude,
            onSuccess = { _state.value = RecommendationsState(restaurants = it, userLatitude = latitude, userLongitude = longitude) },
            onFailure = { _state.value = RecommendationsState(error = it.localizedMessage ?: "Search failed") }
        )
    }

    fun clearResults() {
        _state.value = RecommendationsState()
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
                    onSuccess = { _state.value = RecommendationsState(restaurants = it, userLatitude = latitude, userLongitude = longitude) },
                    onFailure = { _state.value = RecommendationsState(error = it.localizedMessage ?: "Search failed") }
                )
            },
            onFailure = { _state.value = RecommendationsState(error = it) }
        )
    }
}
