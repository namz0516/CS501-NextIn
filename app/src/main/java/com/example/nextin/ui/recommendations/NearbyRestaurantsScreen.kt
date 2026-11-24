package com.example.nextin.ui.recommendations

import com.example.nextin.data.places.NearbyRestaurant
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyRestaurantsScreen(
    onBack: () -> Unit,
    onDemoQueue: (NearbyRestaurant) -> Unit,
    queueBusy: Boolean,
    queueError: String?,
    model: RecommendationsViewModel = viewModel()
) {
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val showNearby by model.showNearby.collectAsStateWithLifecycle(initialValue = true)
    // Remember denial within this screen so searches do not repeatedly prompt.
    var searchPermissionAsked by rememberSaveable { mutableStateOf(false) }
    var searchWithoutLocation by rememberSaveable { mutableStateOf(false) }
    val searchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        searchPermissionAsked = true
        val granted = grants.values.any { it }
        searchWithoutLocation = !granted
        model.searchAnywhere(searchQuery, useLocation = granted)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) model.refresh()
    }
    fun requestRestaurants() {
        val permitted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (permitted) model.refresh()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun searchRestaurants() {
        val permitted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        when {
            permitted -> { searchWithoutLocation = false; model.searchAnywhere(searchQuery, useLocation = true) }
            searchPermissionAsked -> { searchWithoutLocation = true; model.searchAnywhere(searchQuery, useLocation = false) }
            else -> searchLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Find places") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Search for a place")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !searchMode,
                        onClick = { searchMode = false; model.clearResults() },
                        label = { Text("Near me") }
                    )
                    FilterChip(
                        selected = searchMode,
                        onClick = { searchMode = true; model.clearResults() },
                        label = { Text("Search anywhere") }
                    )
                }
            }
            if (!searchMode) item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Show recommendations")
                    Switch(checked = showNearby, onCheckedChange = model::setShowNearby)
                }
            }
            queueError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (searchMode || showNearby) {
                if (searchMode) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Restaurant name") },
                            placeholder = { Text("e.g. Sharetea") },
                            singleLine = true
                        )
                    }
                    if (searchWithoutLocation) item {
                        Text("Location unavailable or denied. Search still works, but results are not sorted by distance.", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        Button(
                            onClick = { searchRestaurants() },
                            enabled = !state.loading && searchQuery.isNotBlank()
                        ) { Text("Search businesses") }
                    }
                } else {
                    item { Button(onClick = { requestRestaurants() }, enabled = !state.loading) {
                        Text(if (state.restaurants.isEmpty()) "Find places near me" else "Refresh nearby businesses")
                    } }
                }
                if (state.loading) item { CircularProgressIndicator() }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                if (!state.loading && state.error == null && state.restaurants.isEmpty() && searchMode) {
                    item { Text("Enter a search above to find businesses") }
                }
                items(state.restaurants, key = { it.id }) { restaurant ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(restaurant.name, style = MaterialTheme.typography.titleMedium)
                            Text(restaurant.address, style = MaterialTheme.typography.bodyMedium)
                            if (state.userLatitude != null && state.userLongitude != null) {
                                val meters = FloatArray(1)
                                Location.distanceBetween(
                                    state.userLatitude!!, state.userLongitude!!,
                                    restaurant.latitude, restaurant.longitude, meters
                                )
                                val distanceLabel = if (meters[0] < 1000f) {
                                    "${meters[0].toInt()} m away"
                                } else {
                                    String.format(Locale.getDefault(), "%.1f km away", meters[0] / 1000f)
                                }
                                Text(distanceLabel, style = MaterialTheme.typography.labelMedium)
                            }
                            Text("Demo queue only — not the restaurant's actual waitlist", style = MaterialTheme.typography.labelSmall)
                            Button(onClick = { onDemoQueue(restaurant) }, enabled = !queueBusy) {
                                Text("Open NextIn demo queue")
                            }
                        }
                    }
                }
                if (state.restaurants.isNotEmpty()) item {
                    Text("Restaurant information provided by Google Places", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
