package com.example.nextin.data.places

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class LocationProvider(private val context: Context) {
    fun getCurrentLocation(onSuccess: (Double, Double) -> Unit, onFailure: (String) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            onFailure("Location permission is required")
            return
        }
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(priority, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location != null) onSuccess(location.latitude, location.longitude)
                    else onFailure("Location unavailable. Enable location services and retry.")
                }
                .addOnFailureListener { onFailure(it.localizedMessage ?: "Could not get location") }
        } catch (e: SecurityException) {
            onFailure("Location permission was revoked")
        }
    }
}
