package com.example.nextin.domain.queue

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.data.model.SeatingMode
import kotlin.math.ceil

object QueuePolicy {

    fun waiting(businessId: String, entries: List<QueueEntry>): List<QueueEntry> {
        return entries.filter {
            it.businessId == businessId && it.status == QueueStatus.WAITING
        }.sortedWith(
            compareBy<QueueEntry> { it.joinedAtMillis }.thenBy { it.id }
        )
    }

    fun suggest(
        business: Business,
        entries: List<QueueEntry>,
        tableId: String? = null
    ): QueueEntry? {
        val line = waiting(business.id, entries)
        if (business.type == BusinessType.OTHER) return line.firstOrNull()

        val openTables = business.tables.filter {
            it.occupiedByEntryId == null && (tableId == null || it.id == tableId)
        }

        if (business.settings.seatingMode == SeatingMode.FCFS) {
            return line.firstOrNull()?.takeIf { party ->
                openTables.any { it.capacity >= party.partySize }
            }
        }

        val eligible = line.filter { party ->
            openTables.any { it.capacity >= party.partySize }
        }

        return eligible.firstOrNull {
            it.skipCount >= business.settings.skipLimit
        } ?: eligible.firstOrNull()
    }

    fun estimatedWaitMinutes(
        business: Business,
        entries: List<QueueEntry>,
        partySize: Int,
        entryId: String? = null
    ): Int? {
        if (partySize < 1) return null
        if (business.type == BusinessType.OTHER && partySize != 1) return null
        if (business.type == BusinessType.RESTAURANT &&
            partySize > business.settings.maxPartySize) return null

        val line = waiting(business.id, entries)
        val ahead = if (entryId == null) {
            line
        } else {
            val index = line.indexOfFirst { it.id == entryId }
            if (index < 0) return null
            line.take(index)
        }

        if (business.type == BusinessType.OTHER) {
            return ahead.size * business.settings.averageServiceMinutes
        }

        val fittingTables = business.tables.filter { it.capacity >= partySize }
        if (fittingTables.isEmpty()) return null

        val competing = ahead.count { party ->
            fittingTables.any { it.capacity >= party.partySize }
        }

        return ceil(
            competing.toDouble() * business.settings.averageServiceMinutes /
                    fittingTables.size
        ).toInt()
    }
}