package com.example.nextin.wear

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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

                val teal = Color(0xFF5EEAD4)
                val muted = Color(0xFF94A3B8)
                val white = Color(0xFFF1F5F9)

                val accent = when (value.status) {
                    "WAITING" -> teal
                    "CALLED" -> Color(0xFF86EFAC)
                    "EXPIRED" -> Color(0xFFFCA5A5)
                    else -> muted
                }

                val statusLabel = when (value.status) {
                    "WAITING" -> "IN QUEUE"
                    "CALLED" -> "READY"
                    "EXPIRED" -> "EXPIRED"
                    else -> "NO ACTIVE QUEUE"
                }

                val headline = when (value.status) {
                    "WAITING" -> "#${value.ahead + 1}"
                    "CALLED" -> "✓"
                    "EXPIRED" -> "!"
                    else -> "—"
                }

                val title = when (value.status) {
                    "WAITING" -> value.business
                    "CALLED" -> "Your turn!"
                    "EXPIRED" -> "Call expired"
                    else -> "Join a queue"
                }

                val subtitle = when (value.status) {
                    "WAITING" -> if (value.ahead == 1)
                        "1 party ahead"
                    else "${value.ahead} parties ahead"
                    "CALLED" -> "Please head over"
                    "EXPIRED" -> "Check your phone"
                    else -> "on your phone"
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101719))
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        3.dp, Alignment.CenterVertically
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NextIn",
                        color = teal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = statusLabel,
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                        modifier = Modifier
                            .background(
                                accent.copy(alpha = 0.12f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 3.dp
                            )
                    )

                    Text(
                        text = headline,
                        color = accent,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = title,
                        color = white,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = subtitle,
                        color = muted,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    if (value.updatedAt > 0) {
                        val time = java.text.DateFormat
                            .getTimeInstance(java.text.DateFormat.SHORT)
                            .format(java.util.Date(value.updatedAt))

                        Text(
                            text = "Synced $time",
                            color = muted,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
