package com.example.nextin

import android.os.Bundle
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.nextin.navigation.AppNavigation
import com.example.nextin.ui.theme.NextInTheme
import com.google.android.libraries.places.api.Places

class MainActivity : ComponentActivity() {
    private val pendingJoinBusiness = mutableStateOf<String?>(null)

    //link that opens app
    private fun receiveJoinLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "nextin" && uri.host == "join") {
            pendingJoinBusiness.value = uri.pathSegments.firstOrNull()
        }
    }

    //handle links when app already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveJoinLink(intent) //gets new business id
    }

    //opens qr
    private fun scanQueueQr() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val uri = barcode.rawValue?.let(android.net.Uri::parse)
                if (uri?.scheme == "nextin" &&
                    uri.host == "join" &&
                    uri.pathSegments.size == 1) {

                    pendingJoinBusiness.value = uri.pathSegments.first()
                } else {
                    Toast.makeText(this, "This QR code is not a supported NextIn queue.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not scan QR: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveJoinLink(intent)
        //to initialize google places sdk when nextin starts
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                applicationContext,
                BuildConfig.PLACES_API_KEY
            )
        }

        setContent {
            NextInTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppNavigation(
                    pendingJoinBusinessId = pendingJoinBusiness.value,
                    onJoinLinkHandled = { pendingJoinBusiness.value = null },
                    onScanQr = { scanQueueQr() }
                ) }
            }
        }
    }
}
