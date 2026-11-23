package com.example.nextin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.nextin.navigation.AppNavigation
import com.example.nextin.notifications.QueueNotifier
import com.example.nextin.ui.theme.NextInTheme

class MainActivity : ComponentActivity() {
    private val openMyQueue = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readOpenMyQueue(intent)
        setContent {
            NextInTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        openMyQueue = openMyQueue.value,
                        onMyQueueOpened = { openMyQueue.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOpenMyQueue(intent)
    }

    private fun readOpenMyQueue(intent: Intent?) {
        if (intent?.getBooleanExtra(QueueNotifier.EXTRA_OPEN_MY_QUEUE, false) == true) {
            openMyQueue.value = true
            intent.removeExtra(QueueNotifier.EXTRA_OPEN_MY_QUEUE)
        }
    }
}
