package com.example.nextin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.nextin.navigation.AppNavigation
import com.example.nextin.notifications.QueueNotifier
import com.example.nextin.ui.theme.NextInTheme
import com.google.android.libraries.places.api.Places
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions

class MainActivity : ComponentActivity() {

    private val openMyQueue = mutableStateOf(false)
    private val pendingJoinBusiness = mutableStateOf<String?>(null)

    // Link that opens the app
    private fun receiveJoinLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "nextin" &&
            uri.host == "join" &&
            uri.pathSegments.size == 1) {
            pendingJoinBusiness.value = uri.pathSegments.first()
        }
    }

    // Handle notification navigation
    private fun readOpenMyQueue(intent: Intent?) {
        if (intent?.getBooleanExtra(
                QueueNotifier.EXTRA_OPEN_MY_QUEUE, false
            ) == true) {
            openMyQueue.value = true
            intent.removeExtra(QueueNotifier.EXTRA_OPEN_MY_QUEUE)
        }
    }

    // Handle links and notifications when the app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveJoinLink(intent)
        readOpenMyQueue(intent)
    }

    // Open QR scanner
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
                    Toast.makeText(
                        this,
                        "This QR code is not a supported NextIn queue.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Could not scan QR: ${it.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        receiveJoinLink(intent)

        if (savedInstanceState == null) {
            readOpenMyQueue(intent)
        }

        // Initialize Google Places SDK
        if (BuildConfig.PLACES_API_KEY.isNotBlank() &&
            !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                applicationContext,
                BuildConfig.PLACES_API_KEY
            )
        }

        setContent {
            NextInTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        openMyQueue = openMyQueue.value,
                        onMyQueueOpened = { openMyQueue.value = false },
                        pendingJoinBusinessId = pendingJoinBusiness.value,
                        onJoinLinkHandled = { pendingJoinBusiness.value = null },
                        onScanQr = { scanQueueQr() }
                    )
                }
            }
        }
    }
}
