package com.example.nextin.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

private data class QueueStatusUi(
    val business: String = "",
    val status: String = "NONE",
    val ahead: Int = 0,
    val updatedAt: Long = 0L
)

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val queue = mutableStateOf(QueueStatusUi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val value by queue
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("NextIn", textAlign = TextAlign.Center)
                    when (value.status) {
                        "WAITING" -> {
                            Text(value.business, textAlign = TextAlign.Center)
                            Text("Position #${value.ahead + 1}", textAlign = TextAlign.Center)
                            Text("${value.ahead} ahead of you", textAlign = TextAlign.Center)
                        }
                        "CALLED" -> {
                            Text(value.business, textAlign = TextAlign.Center)
                            Text("Your turn!", textAlign = TextAlign.Center)
                        }
                        "EXPIRED" -> Text("Your call expired", textAlign = TextAlign.Center)
                        else -> Text("Join a demo queue on your phone", textAlign = TextAlign.Center)
                    }
                    if (value.updatedAt > 0) {
                        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                            .format(java.util.Date(value.updatedAt))
                        Text("Last synced $time", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dataClient.addListener(this)
        dataClient.dataItems.addOnSuccessListener { buffer ->
            try {
                for (item in buffer) {
                    if (item.uri.path == "/nextin/queue-status") {
                        read(item)
                    }
                }
            } finally {
                buffer.release()
            }
        }
    }

    override fun onStop() {
        dataClient.removeListener(this)
        super.onStop()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/nextin/queue-status") {
                read(event.dataItem)
            }
        }
    }

    private fun read(item: com.google.android.gms.wearable.DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        queue.value = QueueStatusUi(
            business = map.getString("business").orEmpty(),
            status = map.getString("status") ?: "NONE",
            ahead = map.getInt("ahead"),
            updatedAt = map.getLong("updatedAt")
        )
    }
}
