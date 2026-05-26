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

    private val _openingTime = MutableStateFlow("08:00")
    val openingTime = _openingTime.asStateFlow()

    private val _closingTime = MutableStateFlow("21:00")
    val closingTime = _closingTime.asStateFlow()

    private val _maxOrdersPerSlot = MutableStateFlow(5)
    val maxOrdersPerSlot = _maxOrdersPerSlot.asStateFlow()

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
                    fetchShopDetails(shopId)
                }
            }
            .addOnFailureListener {
                _message.value = it.message ?: "Failed to load profile"
            }
    }

    private fun fetchShopDetails(shopId: String) {
        firestore.collection("shops")
            .document(shopId)
            .get()
            .addOnSuccessListener { doc ->
                _upiId.value = doc.getString("upiId") ?: ""
                _openingTime.value = doc.getString("openingTime") ?: "08:00"
                _closingTime.value = doc.getString("closingTime") ?: "21:00"
                _maxOrdersPerSlot.value =
                    doc.getLong("maxOrdersPerSlot")?.toInt() ?: 5
            }
            .addOnFailureListener {
                _message.value = it.message ?: "Failed to load shop details"
            }
    }

    fun updateShopSettings(
        newUpiId: String,
        newOpeningTime: String,
        newClosingTime: String,
        newMaxOrdersPerSlot: String
    ) {
        val shopId = _userProfile.value?.shopId.orEmpty()

        if (shopId.isBlank()) {
            _message.value = "Shop ID not found"
            return
        }

        val cleanUpiId = newUpiId.trim()
        val cleanOpeningTime = newOpeningTime.trim().replace("\"", "")
        val cleanClosingTime = newClosingTime.trim().replace("\"", "")
        val cleanMaxOrders = newMaxOrdersPerSlot.trim().toIntOrNull()

        if (cleanUpiId.isBlank() || !cleanUpiId.contains("@")) {
            _message.value = "Enter a valid UPI ID"
            return
        }

        if (!isValidTime(cleanOpeningTime) || !isValidTime(cleanClosingTime)) {
            _message.value = "Use valid time format like 08:00 or 21:00"
            return
        }

        if (cleanMaxOrders == null || cleanMaxOrders <= 0) {
            _message.value = "Max orders per slot must be greater than 0"
            return
        }

        val updates = mapOf(
            "upiId" to cleanUpiId,
            "openingTime" to cleanOpeningTime,
            "closingTime" to cleanClosingTime,
            "maxOrdersPerSlot" to cleanMaxOrders
        )

        firestore.collection("shops")
            .document(shopId)
            .update(updates)
            .addOnSuccessListener {
                _upiId.value = cleanUpiId
                _openingTime.value = cleanOpeningTime
                _closingTime.value = cleanClosingTime
                _maxOrdersPerSlot.value = cleanMaxOrders
                _message.value = "Shop settings updated successfully"
            }
            .addOnFailureListener {
                _message.value = it.message ?: "Failed to update shop settings"
            }
    }

    private fun isValidTime(time: String): Boolean {
        return Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time)
    }

    fun clearMessage() {
        _message.value = null
    }
}