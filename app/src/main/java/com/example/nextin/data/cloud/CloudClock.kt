package com.example.nextin.data.cloud

import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class CloudClock {
    private var anchorMillis = System.currentTimeMillis()
    private var anchorElapsed = SystemClock.elapsedRealtime()
    fun now(): Long = anchorMillis + SystemClock.elapsedRealtime() - anchorElapsed

    suspend fun synchronize(firestore: FirebaseFirestore, auth: FirebaseAuth) {
        val uid = requireNotNull(auth.currentUser).uid
        val reference = firestore.collection("customerClocks").document(uid)
        val started = SystemClock.elapsedRealtime()
        firestore.runTransaction { tx ->
            tx.get(reference)
            tx.set(reference, mapOf("time" to FieldValue.serverTimestamp()))
            Unit
        }.await()
        val timestamp = requireNotNull(reference.get(Source.SERVER).await().getTimestamp("time"))
        val finished = SystemClock.elapsedRealtime()
        // Estimate network transit time; server rules remain authoritative at the boundary.
        anchorMillis = timestamp.toDate().time + (finished - started) / 2
        anchorElapsed = finished
    }
}
