package com.example.nextin.ui.auth

import android.app.Application
import com.example.nextin.data.cloud.CloudStaffSetup
import com.example.nextin.data.cloud.CloudSessions
import com.example.nextin.data.sample.SampleBusinessData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StaffAuthViewModel(application: Application) : AndroidViewModel(application) {

    // Separate staff authentication preserves the customer's anonymous account.
    // Both sessions still connect to the same Firebase project.
    private val staffApp = CloudSessions.staffApp(application)
    private val auth = FirebaseAuth.getInstance(staffApp)
    private val firestore = CloudSessions.staffFirestore(application)

    // A non-null business ID means login and assignment checks succeeded.
    private var assignmentListener: ListenerRegistration? = null
    private var sessionGeneration = 0

    private val mutableBusinessId = MutableStateFlow<String?>(null)
    val businessId = mutableBusinessId.asStateFlow()

    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun signIn(email: String, password: String) {
        // Prevent repeated login requests.
        if (mutableBusy.value) return

        mutableError.value = null

        if (email.isBlank() || password.isBlank()) {
            mutableError.value = "Enter your email and password."
            return
        }

        assignmentListener?.remove()
        assignmentListener = null
        sessionGeneration++
        mutableBusy.value = true
        mutableBusinessId.value = null

        viewModelScope.launch {
            try {
                // Firebase checks the email and password.
                val user = requireNotNull(
                    auth.signInWithEmailAndPassword(
                        email.trim(),
                        password
                    ).await().user
                ) {
                    "Sign-in did not return an account."
                }

                // Fetch this user's assignment directly from the server.
                val assignment = firestore
                    .collection("staff")
                    .document(user.uid)
                    .get(Source.SERVER)
                    .await()

                val assignedBusiness = assignment.getString("businessId")

                // A valid account must also have an active staff assignment.
                require(
                    assignment.getBoolean("active") == true &&
                            !assignedBusiness.isNullOrBlank()
                ) {
                    "This account has no active staff assignment."
                }

                // Create cloud business settings and tables only on the first login.
                val business = requireNotNull(
                    SampleBusinessData.businesses.firstOrNull { it.id == assignedBusiness }
                ) { "The assigned business is not configured in this app." }
                CloudStaffSetup(firestore).initializeBusiness(business)

                // Publishing this value opens the assigned dashboard.
                mutableBusinessId.value = assignedBusiness
                watchAssignment(user.uid, assignedBusiness!!)

            } catch (exception: CancellationException) {
                // Preserve normal coroutine cancellation.
                throw exception

            } catch (exception: Exception) {
                mutableError.value =
                    exception.message ?: "Staff sign-in failed. Please retry."

            } finally {
                // Failed assignment checks must not leave a staff login active.
                if (mutableBusinessId.value == null) {
                    auth.signOut()
                }

                mutableBusy.value = false
            }
        }
    }

    private fun watchAssignment(uid: String, assignedBusiness: String) {
        val generation = sessionGeneration
        assignmentListener = firestore.collection("staff").document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { document, error ->
                if (generation != sessionGeneration || auth.currentUser?.uid != uid) {
                    return@addSnapshotListener
                }
                if (error != null || (document != null && !document.metadata.isFromCache &&
                    (document.getBoolean("active") != true ||
                     document.getString("businessId") != assignedBusiness))) {
                    sessionGeneration++
                    assignmentListener?.remove()
                    assignmentListener = null
                    mutableBusinessId.value = null
                    mutableError.value = "Staff access changed. Sign in again or contact the project owner."
                    auth.signOut()
                }
            }
    }

    override fun onCleared() {
        sessionGeneration++
        assignmentListener?.remove()
        super.onCleared()
    }

    fun signOut() {
        if (mutableBusy.value) return
        sessionGeneration++
        assignmentListener?.remove()
        assignmentListener = null

        mutableBusinessId.value = null
        mutableError.value = null

        // This signs out staff without signing out the customer.
        auth.signOut()
    }
}
