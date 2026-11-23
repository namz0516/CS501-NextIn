package com.example.nextin.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object CloudSessions {
    private var staffConfigured = false

    @Synchronized
    fun staffApp(context: Context): FirebaseApp =
        FirebaseApp.getApps(context).firstOrNull { it.name == "staff-session" }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "staff-session")

    @Synchronized
    fun staffFirestore(context: Context): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance(staffApp(context.applicationContext))
        if (!staffConfigured) {
            // The SDK owns its singleton; this object stores no Android Context.
            // Staff names stay in memory across this process only.
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false).build()
            staffConfigured = true
        }
        return firestore
    }
}
