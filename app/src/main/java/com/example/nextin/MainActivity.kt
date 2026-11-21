package com.example.nextin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.nextin.navigation.AppNavigation
import com.example.nextin.ui.theme.NextInTheme
import com.google.android.libraries.places.api.Places

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

        setContent {
            NextInTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppNavigation() }
            }
        }
    }
}
