package com.example.nextin.ui.recommendations

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    model: RecommendationsViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val showNearby by model.showNearby.collectAsStateWithLifecycle(initialValue = true)
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
    Scaffold(topBar = { TopAppBar(title = { Text("Nearby restaurants") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Discover real restaurants near your location. These restaurants are not necessarily connected to NextIn queues.")
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Show recommendations")
                    Switch(checked = showNearby, onCheckedChange = model::setShowNearby)
                }
            }
            if (showNearby) {
                item { Button(onClick = { requestRestaurants() }, enabled = !state.loading) {
                    Text(if (state.restaurants.isEmpty()) "Find restaurants near me" else "Refresh nearby restaurants")
                } }
                if (state.loading) item { CircularProgressIndicator() }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                items(state.restaurants, key = { it.id }) { restaurant ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(restaurant.name, style = MaterialTheme.typography.titleMedium)
                            Text(restaurant.address, style = MaterialTheme.typography.bodyMedium)
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
