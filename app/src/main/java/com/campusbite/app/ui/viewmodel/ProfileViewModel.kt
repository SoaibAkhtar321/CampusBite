package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.campusbite.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _upiId = MutableStateFlow("")
    val upiId = _upiId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        fetchUserDetails()
    }

    private fun fetchUserDetails() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                _userProfile.value = user

                val shopId = user?.shopId.orEmpty()

                if (shopId.isNotBlank()) {
                    fetchShopUpiId(shopId)
                }
            }
            .addOnFailureListener {
                _message.value = it.message ?: "Failed to load profile"
            }
    }

    private fun fetchShopUpiId(shopId: String) {
        firestore.collection("shops")
            .document(shopId)
            .get()
            .addOnSuccessListener { doc ->
                _upiId.value = doc.getString("upiId") ?: ""
            }
            .addOnFailureListener {
                _message.value = it.message ?: "Failed to load UPI ID"
            }
    }

    fun updateUpiId(newUpiId: String) {
        val user = _userProfile.value
        val shopId = user?.shopId.orEmpty()

        if (shopId.isBlank()) {
            _message.value = "Shop ID not found"
            return
        }

        val cleanUpiId = newUpiId.trim()

        if (cleanUpiId.isBlank()) {
            _message.value = "UPI ID cannot be empty"
            return
        }

        if (!cleanUpiId.contains("@")) {
            _message.value = "Enter a valid UPI ID"
            return
        }

        firestore.collection("shops")
            .document(shopId)
            .update("upiId", cleanUpiId)
            .addOnSuccessListener {
                _upiId.value = cleanUpiId
                _message.value = "UPI ID updated successfully"
            }
            .addOnFailureListener {
                _message.value = it.message ?: "Failed to update UPI ID"
            }
    }

    fun clearMessage() {
        _message.value = null
    }
}