package com.example.nextin.ui.staff

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nextin.data.model.*
import com.example.nextin.domain.queue.GracePeriod
import com.example.nextin.domain.queue.QueuePolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDashboardScreen(
    snapshot: QueueSnapshot,
    error: String?,
    nowMillis: Long,
    onKeep: (String, String) -> Unit,
    onSettings: (String, BusinessSettings) -> Boolean,
    onAddWalkIn: (String, String, Int) -> Boolean,
    onCall: (String, String?, String?) -> Unit,
    onSeat: (String, String) -> Unit,
    onRemove: (String, String) -> Unit,
    onRelease: (String, String) -> Unit,
    onQueueOpen: (String, Boolean) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    busy: Boolean = false
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    val business = snapshot.businesses.firstOrNull { it.id == selectedId }
        ?: snapshot.businesses.firstOrNull()
    var tableId by rememberSaveable(business?.id) { mutableStateOf<String?>(null) }
    var showWalkIn by rememberSaveable(business?.id) { mutableStateOf(false) }
    var removeId by rememberSaveable(business?.id) { mutableStateOf<String?>(null) }
    var overrideId by rememberSaveable(business?.id) { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Staff Dashboard") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Staff demo · Local device only", style = MaterialTheme.typography.labelLarge)
                Text("Staff authentication is not connected yet.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.businesses.forEach { current ->
                        FilterChip(
                            selected = current.id == business?.id,
                            onClick = { selectedId = current.id; onClearError() },
                            enabled = !busy,
                            label = { Text(current.name) }
                        )
                    }
                }
            }
            if (business == null) {
                item { Text("No businesses available.") }
            } else {
                val line = QueuePolicy.waiting(business.id, snapshot.entries)
                val called = snapshot.entries.filter {
                    it.businessId == business.id && it.status in setOf(QueueStatus.CALLED, QueueStatus.EXPIRED)
                }
                val suggested = QueuePolicy.suggest(business, snapshot.entries, tableId)
                val seatedToday = snapshot.entries.count {
                    it.businessId == business.id && it.status == QueueStatus.SEATED &&
                            it.completedAtMillis?.let { time ->
                                Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
                            } == true
                }
                item {
                    Text(business.name, style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { onClearError(); showSettings = true }, enabled = !busy) { Text("Settings") }
                    Text(if (business.type == BusinessType.OTHER) "FCFS · Individual customers" else
                        "${business.settings.seatingMode} · Skip limit ${business.settings.skipLimit}")
                    Text("${line.size} waiting · ${called.size} called · $seatedToday seated today")
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Accepting arrivals", Modifier.weight(1f))
                        Switch(
                            checked = business.queueOpen,
                            onCheckedChange = { onQueueOpen(business.id, it) },
                            enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Accepting arrivals at ${business.name}" }
                        )
                    }
                }
                if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
                if (business.type == BusinessType.RESTAURANT) {
                    item {
                        Text("Tables", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = tableId == null, onClick = { tableId = null }, label = { Text("Any open") })
                            business.tables.forEach { table ->
                                FilterChip(
                                    selected = tableId == table.id,
                                    onClick = { tableId = table.id },
                                    label = { Text("${table.id} · ${table.capacity} seats · ${if (table.occupiedByEntryId == null) "open" else "busy"}") }
                                )
                            }
                        }
                        val selectedTable = business.tables.firstOrNull { it.id == tableId }
                        if (selectedTable?.occupiedByEntryId != null) {
                            val reserved = snapshot.entries.any {
                                it.id == selectedTable.occupiedByEntryId &&
                                        it.status in setOf(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED)
                            }
                            OutlinedButton(
                                onClick = { onRelease(business.id, selectedTable.id) },
                                enabled = !reserved && !busy
                            ) { Text("Mark table open") }
                            if (reserved) Text("This table is reserved for an active party.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Suggested next", style = MaterialTheme.typography.titleLarge)
                            if (suggested == null) Text(if (line.isEmpty()) "No waiting parties." else "No eligible party for the selected open tables.")
                            else Text("${suggested.name} · Party of ${suggested.partySize}")
                            Button(onClick = { onCall(business.id, tableId, null) }, enabled = suggested != null && !busy) {
                                Text("Call next")
                            }
                        }
                    }
                }
                if (called.isNotEmpty()) item { Text("Called parties", style = MaterialTheme.typography.titleLarge) }
                items(called, key = { "called-${it.id}" }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            Text("Party of ${entry.partySize} · ${entry.status}${entry.tableId?.let { " · $it" } ?: ""}")
                            Text(if (entry.confirmedArrival) "Arrival confirmed" else "Awaiting arrival")
                            if (entry.status == QueueStatus.CALLED && !entry.confirmedArrival) {
                                val seconds = (GracePeriod.remainingMillis(entry, business, nowMillis) ?: 0L) / 1000
                                Text("%d:%02d remaining".format(seconds / 60, seconds % 60))
                            }
                            if (entry.status == QueueStatus.EXPIRED) {
                                Text("Grace period ended; choose Keep, Seat, or Remove.")
                                OutlinedButton(onClick = { onKeep(business.id, entry.id) }, enabled = !busy) { Text("Keep") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onSeat(business.id, entry.id) }, enabled = !busy) { Text("Seat") }
                                TextButton(onClick = { removeId = entry.id }, enabled = !busy) { Text("Remove") }
                            }
                        }
                    }
                }
                item {
                    Text("Waiting list", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { onClearError(); showWalkIn = true }, enabled = business.queueOpen && !busy) {
                        Text("Add walk-in")
                    }
                }
                if (line.isEmpty()) item { Text("No one is waiting.") }
                items(line, key = { "waiting-${it.id}" }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${line.indexOf(entry) + 1}. ${entry.name}", style = MaterialTheme.typography.titleMedium)
                            Text("Party of ${entry.partySize} · ${entry.source} · ${entry.skipCount} skips")
                            if (business.type == BusinessType.RESTAURANT && entry.skipCount >= business.settings.skipLimit) Text("Priority for a fitting table")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { overrideId = entry.id }, enabled = !busy) { Text("Call override") }
                                TextButton(onClick = { removeId = entry.id }, enabled = !busy) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSettings && business != null) {
        SettingsDialog(business, error, busy, onDismiss = { showSettings = false }, onSave = { settings ->
            if (onSettings(business.id, settings)) showSettings = false
        })
    }
    if (showWalkIn && business != null) {
        WalkInDialog(business, error, busy, onDismiss = { showWalkIn = false }, onAdd = { name, size ->
            if (onAddWalkIn(business.id, name, size)) showWalkIn = false
        })
    }
    if (removeId != null && business != null) {
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text("Remove this party?") },
            text = { Text("Their queue spot and reserved table will be released.") },
            confirmButton = { TextButton(onClick = { removeId?.let { onRemove(business.id, it) }; removeId = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text("Cancel") } })
    }
    if (overrideId != null && business != null) {
        AlertDialog(onDismissRequest = { overrideId = null }, title = { Text("Override the suggestion?") },
            text = { Text("The selected party must still fit an open table. The current table selection applies.") },
            confirmButton = { TextButton(onClick = { overrideId?.let { onCall(business.id, tableId, it) }; overrideId = null }) { Text("Call party") } },
            dismissButton = { TextButton(onClick = { overrideId = null }) { Text("Cancel") } })
    }
}

@Composable
private fun WalkInDialog(
    business: Business, error: String?, busy: Boolean,
    onDismiss: () -> Unit, onAdd: (String, Int) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add walk-in") },
        text = {
            Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (business.type == BusinessType.RESTAURANT) {
                    OutlinedTextField(size, { size = it }, label = { Text("Party size") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, if (business.type == BusinessType.RESTAURANT) size.toIntOrNull() ?: 0 else 1) }, enabled = !busy) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsDialog(
    business: Business, error: String?, busy: Boolean,
    onDismiss: () -> Unit, onSave: (BusinessSettings) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(business.settings.seatingMode.name) }
    var service by rememberSaveable { mutableStateOf(business.settings.averageServiceMinutes.toString()) }
    var grace by rememberSaveable { mutableStateOf(business.settings.gracePeriodMinutes.toString()) }
    var skip by rememberSaveable { mutableStateOf(business.settings.skipLimit.toString()) }
    var maximum by rememberSaveable { mutableStateOf(business.settings.maxPartySize.toString()) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Queue settings") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (business.type == BusinessType.RESTAURANT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeatingMode.entries.forEach { value ->
                            FilterChip(selected = mode == value.name, onClick = { mode = value.name }, label = { Text(if (value == SeatingMode.FCFS) "FCFS" else "Table-fit") })
                        }
                    }
                    SettingsField("Skip limit (1–10)", skip) { skip = it }
                    SettingsField("Maximum party size (1–8)", maximum) { maximum = it }
                }
                SettingsField("Average service minutes (1–240)", service) { service = it }
                SettingsField("Grace period minutes (1–30)", grace) { grace = it }
                Text("Changing grace duration also changes deadlines for existing calls.", style = MaterialTheme.typography.bodySmall)
                (inputError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val serviceNumber = service.toIntOrNull()
                val graceNumber = grace.toIntOrNull()
                val skipNumber = skip.toIntOrNull()
                val maxNumber = maximum.toIntOrNull()
                if (serviceNumber == null || graceNumber == null || skipNumber == null || maxNumber == null) {
                    inputError = "Enter whole numbers for each setting."
                } else {
                    inputError = null
                    onSave(BusinessSettings(SeatingMode.valueOf(mode), maxNumber, skipNumber, serviceNumber, graceNumber))
                }
            }, enabled = !busy) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}