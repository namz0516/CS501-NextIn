package com.example.nextin.data.local

import com.example.nextin.data.model.QueueSnapshot

/** Blocking operations: call these from Dispatchers.IO, never the UI thread. */
class RoomQueueStore(private val database: NextInDatabase) {
    private val dao = database.queueDao()

    fun load(): QueueSnapshot? {
        var result: QueueSnapshot? = null
        database.runInTransaction {
            val businesses = dao.businesses()
            if (businesses.isNotEmpty()) {
                val tables = dao.tables()
                result = QueueSnapshot(
                    businesses = businesses.map { row ->
                        row.toModel(tables.filter { it.businessId == row.id }.map { it.toModel() })
                    },
                    entries = dao.entries().map { it.toModel() }
                )
            }
        }
        return result
    }

    fun save(snapshot: QueueSnapshot) {
        database.runInTransaction {
            dao.clearEntries()
            dao.clearTables()
            dao.clearBusinesses()
            dao.insertBusinesses(snapshot.businesses.mapIndexed { index, business -> business.toEntity(index) })
            dao.insertTables(snapshot.businesses.flatMap { business ->
                business.tables.mapIndexed { index, table -> table.toEntity(business.id, index) }
            })
            dao.insertEntries(snapshot.entries.mapIndexed { index, entry -> entry.toEntity(index) })
        }
    }
}