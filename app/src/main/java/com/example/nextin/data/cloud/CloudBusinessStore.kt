package com.example.nextin.data.cloud

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.RestaurantTable
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class CloudBusinessStore(private val firestore: FirebaseFirestore) {
    fun observeBusinesses(
        onChange: (List<Business>, Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = firestore.collection("businesses")
        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) { onError(error); return@addSnapshotListener }
            if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
            try {
                onChange(snapshot.documents.map { CloudQueueCodec.business(it) }, !snapshot.metadata.isFromCache)
            } catch (exception: Exception) { onError(exception) }
        }

    fun observeTables(
        businessId: String,
        onChange: (List<RestaurantTable>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = firestore.collection("businesses").document(businessId)
        .collection("tables").addSnapshotListener { snapshot, error ->
            if (error != null) { onError(error); return@addSnapshotListener }
            if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
            try { onChange(snapshot.documents.map(CloudQueueCodec::table)) }
            catch (exception: Exception) { onError(exception) }
        }
}
