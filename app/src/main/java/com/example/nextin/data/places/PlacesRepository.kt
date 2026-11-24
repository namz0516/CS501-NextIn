
package com.example.nextin.data.places

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.net.SearchByTextRequest

class PlacesRepository(context: Context) {

    private val placesClient =
        Places.createClient(context.applicationContext)

    //Search for businesses using a text query
    fun searchRestaurantsAnywhere(
        query: String,
        latitude: Double?,
        longitude: Double?,
        onSuccess: (List<NearbyRestaurant>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
            Place.Field.PRIMARY_TYPE
        )
        val builder = SearchByTextRequest.builder(query, fields)
            .setMaxResultCount(20)
        if (latitude != null && longitude != null) {
            builder.setLocationBias(CircularBounds.newInstance(LatLng(latitude, longitude), 20000.0))
        }
        val request = builder.build()

        //converting google's businesses to our format
        placesClient.searchByText(request)
            .addOnSuccessListener { response ->
                val results = response.places.mapNotNull { place ->
                    val location = place.location ?: return@mapNotNull null
                    val id = place.id ?: return@mapNotNull null
                    NearbyRestaurant(
                        id = id,
                        name = place.displayName.orEmpty(),
                        address = place.formattedAddress.orEmpty(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        primaryType = place.primaryType
                    )
                }
                // Rank name relevance before distance. "Share Tea" and "Sharetea"
                // normalize to the same name, so unrelated tea shops rank lower.
                val sorted = results.sortedWith(
                    compareBy<NearbyRestaurant> { nameMatchRank(query, it.name) }
                        .thenBy { restaurant ->
                            if (latitude != null && longitude != null) {
                                distanceMeters(latitude, longitude, restaurant.latitude, restaurant.longitude)
                            } else Double.POSITIVE_INFINITY
                        }
                )
                onSuccess(sorted)
            }
            .addOnFailureListener(onFailure)
    }


    private fun normalizeName(value: String): String =
        value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun nameMatchRank(query: String, name: String): Int {
        val q = normalizeName(query)
        val n = normalizeName(name)
        return when {
            q.isEmpty() -> 3
            n == q -> 0
            n.contains(q) -> 1
            else -> 2
        }
    }

    private fun distanceMeters(lat: Double, lng: Double, otherLat: Double, otherLng: Double): Double {
        val output = FloatArray(1)
        Location.distanceBetween(lat, lng, otherLat, otherLng, output)
        return output[0].toDouble()
    }

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
            Place.Field.LOCATION,
            Place.Field.PRIMARY_TYPE
        )

        val request = SearchNearbyRequest.builder(bounds, fields)
            .setIncludedTypes(
                listOf(
                    "restaurant",
                    "cafe",
                    "coffee_shop",
                    "movie_theater",
                    "clothing_store",
                    "store",
                    "shopping_mall",
                    "beauty_salon",
                    "hair_salon",
                    "nail_salon",
                    "tourist_attraction"
                )
            )
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
                        longitude = location.longitude,
                        primaryType = place.primaryType
                    )
                }

                onSuccess(restaurants)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
}
