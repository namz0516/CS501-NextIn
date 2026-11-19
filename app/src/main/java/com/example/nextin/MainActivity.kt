package com.example.nextin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.nextin.navigation.AppNavigation
import com.example.nextin.ui.theme.NextInTheme
import android.util.Log
import com.google.android.libraries.places.api.Places
import com.example.nextin.data.places.PlacesRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //to initialize google places sdk when nextin starts
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                applicationContext,
                BuildConfig.PLACES_API_KEY
            )
        }

        //temporary test
        if (Places.isInitialized()) {
            val repository = PlacesRepository(applicationContext)

            repository.searchNearbyRestaurants(
                //vancouver coordinates for now
                latitude = 49.2827,
                longitude = -123.1207,
                onSuccess = { restaurants ->
                    Log.d("NextInPlaces", "Found ${restaurants.size} restaurants")

                    restaurants.forEach { restaurant ->
                        Log.d(
                            "NextInPlaces",
                            "${restaurant.name} - ${restaurant.address}"
                        )
                    }
                },
                onFailure = { exception ->
                    Log.e("NextInPlaces", "Search failed", exception)
                }
            )
        }

        setContent {
            NextInTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppNavigation() }
            }
        }
    }
}
