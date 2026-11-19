
package com.example.nextin.data.places

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.net.SearchNearbyRequest

class PlacesRepository(context: Context) {

    private val placesClient =
        Places.createClient(context.applicationContext)

    fun searchNearbyRestaurants(
        latitude: Double,
        longitude: Double,
        onSuccess: (List<NearbyRestaurant>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val center = LatLng(latitude, longitude)

        val bounds = CircularBounds.newInstance(
            center,
            1500.0
        )

        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION
        )

        val request = SearchNearbyRequest.builder(bounds, fields)
            .setIncludedTypes(listOf("restaurant"))
            .setMaxResultCount(10)
            .build()

        placesClient.searchNearby(request)
            .addOnSuccessListener { response ->
                val restaurants = response.places.mapNotNull { place ->
                    val location = place.location
                        ?: return@mapNotNull null

                    NearbyRestaurant(
                        id = place.id.orEmpty(),
                        name = place.displayName.orEmpty(),
                        address = place.formattedAddress.orEmpty(),
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }

                onSuccess(restaurants)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
}
