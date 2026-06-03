package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    private val activeStatuses = listOf(
        "pending",
        "accepted",
        "preparing",
        "ready"
    )

    init {
        fetchUserDetails()
    }

    private fun fetchUserDetails() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _message.value = error.message ?: "Failed to load profile"
                    return@addSnapshotListener
                }

                val user = snapshot?.toObject(User::class.java)
                _userProfile.value = user

                val shopId = user?.shopId.orEmpty()

                if (shopId.isNotBlank()) {
                    fetchShopDetails(shopId)
                }
            }
    }

    private fun fetchShopDetails(shopId: String) {
        viewModelScope.launch {
            try {
                val shopRef = getShopReferenceByIdOrField(shopId) ?: return@launch

                val doc = shopRef.get().await()

                _upiId.value = doc.getString("upiId") ?: ""
                _openingTime.value = doc.getString("openingTime") ?: "08:00"
                _closingTime.value = doc.getString("closingTime") ?: "21:00"
                _maxOrdersPerSlot.value = doc.getLong("maxOrdersPerSlot")?.toInt() ?: 5
            } catch (e: Exception) {
                _message.value = e.message ?: "Failed to load shop details"
            }
        }
    }

    fun updateStudentProfile(
        newName: String,
        newPhone: String
    ) {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: run {
                    _message.value = "User not logged in"
                    return@launch
                }

                val cleanName = newName.trim()
                val cleanPhone = newPhone.trim()

                val validationError = validateBasicProfile(
                    name = cleanName,
                    phone = cleanPhone
                )

                if (validationError != null) {
                    _message.value = validationError
                    return@launch
                }

                firestore.collection("users")
                    .document(uid)
                    .update(
                        mapOf(
                            "name" to cleanName,
                            "phone" to cleanPhone
                        )
                    )
                    .await()

                updateActiveOrdersForStudent(
                    studentId = uid,
                    studentName = cleanName,
                    studentPhone = cleanPhone
                )

                _message.value = "Profile updated successfully"
            } catch (e: Exception) {
                _message.value = e.message ?: "Failed to update profile"
            }
        }
    }

    fun updateShopkeeperProfile(
        newName: String,
        newPhone: String,
        newShopName: String
    ) {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: run {
                    _message.value = "User not logged in"
                    return@launch
                }

                val currentUser = _userProfile.value ?: run {
                    _message.value = "Profile not loaded"
                    return@launch
                }

                val shopId = currentUser.shopId.trim()

                if (shopId.isBlank()) {
                    _message.value = "Shop ID not found"
                    return@launch
                }

                val cleanName = newName.trim()
                val cleanPhone = newPhone.trim()
                val cleanShopName = newShopName.trim()

                val validationError = validateBasicProfile(
                    name = cleanName,
                    phone = cleanPhone
                )

                if (validationError != null) {
                    _message.value = validationError
                    return@launch
                }

                if (cleanShopName.isBlank()) {
                    _message.value = "Shop name is required"
                    return@launch
                }

                val shopRef = getShopReferenceByIdOrField(shopId)

                if (shopRef == null) {
                    _message.value = "Shop not found"
                    return@launch
                }

                val batch = firestore.batch()

                val userRef = firestore.collection("users")
                    .document(uid)

                batch.update(
                    userRef,
                    mapOf(
                        "name" to cleanName,
                        "phone" to cleanPhone
                    )
                )

                batch.update(
                    shopRef,
                    mapOf(
                        "name" to cleanShopName,
                        "phone" to cleanPhone,
                        "ownerPhone" to cleanPhone
                    )
                )

                val activeOrdersSnapshot = firestore.collection("orders")
                    .whereEqualTo("shopId", shopId)
                    .whereIn("status", activeStatuses)
                    .get()
                    .await()

                activeOrdersSnapshot.documents.forEach { orderDoc ->
                    batch.update(
                        orderDoc.reference,
                        mapOf(
                            "shopName" to cleanShopName,
                            "shopkeeperPhone" to cleanPhone
                        )
                    )
                }

                batch.commit().await()

                _userProfile.value = currentUser.copy(
                    name = cleanName,
                    phone = cleanPhone
                )

                _message.value = "Profile updated successfully"
            } catch (e: Exception) {
                _message.value = e.message ?: "Failed to update profile"
            }
        }
    }

    fun updateShopSettings(
        newUpiId: String,
        newOpeningTime: String,
        newClosingTime: String,
        newMaxOrdersPerSlot: String
    ) {
        viewModelScope.launch {
            try {
                val shopId = _userProfile.value?.shopId.orEmpty()

                if (shopId.isBlank()) {
                    _message.value = "Shop ID not found"
                    return@launch
                }

                val cleanUpiId = newUpiId.trim()
                val cleanOpeningTime = newOpeningTime.trim().replace("\"", "")
                val cleanClosingTime = newClosingTime.trim().replace("\"", "")
                val cleanMaxOrders = newMaxOrdersPerSlot.trim().toIntOrNull()

                if (cleanUpiId.isBlank() || !cleanUpiId.contains("@")) {
                    _message.value = "Enter a valid UPI ID"
                    return@launch
                }

                if (!isValidTime(cleanOpeningTime) || !isValidTime(cleanClosingTime)) {
                    _message.value = "Use valid time format like 08:00 or 21:00"
                    return@launch
                }

                if (cleanMaxOrders == null || cleanMaxOrders <= 0) {
                    _message.value = "Max orders per slot must be greater than 0"
                    return@launch
                }

                val shopRef = getShopReferenceByIdOrField(shopId)

                if (shopRef == null) {
                    _message.value = "Shop not found"
                    return@launch
                }

                shopRef.update(
                    mapOf(
                        "upiId" to cleanUpiId,
                        "openingTime" to cleanOpeningTime,
                        "closingTime" to cleanClosingTime,
                        "maxOrdersPerSlot" to cleanMaxOrders
                    )
                ).await()

                _upiId.value = cleanUpiId
                _openingTime.value = cleanOpeningTime
                _closingTime.value = cleanClosingTime
                _maxOrdersPerSlot.value = cleanMaxOrders

                _message.value = "Shop settings updated successfully"
            } catch (e: Exception) {
                _message.value = e.message ?: "Failed to update shop settings"
            }
        }
    }

    private suspend fun updateActiveOrdersForStudent(
        studentId: String,
        studentName: String,
        studentPhone: String
    ) {
        val snapshot = firestore.collection("orders")
            .whereEqualTo("studentId", studentId)
            .whereIn("status", activeStatuses)
            .get()
            .await()

        if (snapshot.isEmpty) return

        val batch = firestore.batch()

        snapshot.documents.forEach { orderDoc ->
            batch.update(
                orderDoc.reference,
                mapOf(
                    "studentName" to studentName,
                    "studentPhone" to studentPhone
                )
            )
        }

        batch.commit().await()
    }

    private suspend fun getShopReferenceByIdOrField(
        shopId: String
    ): DocumentReference? {
        val directRef = firestore.collection("shops")
            .document(shopId)

        val directDoc = directRef.get().await()

        if (directDoc.exists()) {
            return directRef
        }

        val queryDoc = firestore.collection("shops")
            .whereEqualTo("shopId", shopId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        return queryDoc?.reference
    }

    private fun validateBasicProfile(
        name: String,
        phone: String
    ): String? {
        return when {
            name.isBlank() ->
                "Name is required"

            phone.isBlank() ->
                "Phone number is required"

            phone.length != 10 ->
                "Phone number must be 10 digits"

            phone.first() !in listOf('6', '7', '8', '9') ->
                "Phone number must start with 6, 7, 8, or 9"

            phone in fakePhoneNumbers() ->
                "Enter a valid phone number"

            phone.groupingBy { it }.eachCount().any { it.value >= 8 } ->
                "Enter a valid phone number"

            hasLongRepeatedSequence(phone) ->
                "Enter a valid phone number"

            else -> null
        }
    }

    private fun fakePhoneNumbers(): Set<String> {
        return setOf(
            "1234567890",
            "9876543210",
            "0123456789",
            "0000000000",
            "1111111111",
            "2222222222",
            "3333333333",
            "4444444444",
            "5555555555",
            "6666666666",
            "7777777777",
            "8888888888",
            "9999999999"
        )
    }

    private fun hasLongRepeatedSequence(
        phone: String
    ): Boolean {
        var repeatCount = 1

        for (i in 1 until phone.length) {
            if (phone[i] == phone[i - 1]) {
                repeatCount++

                if (repeatCount >= 6) {
                    return true
                }
            } else {
                repeatCount = 1
            }
        }

        return false
    }

    private fun isValidTime(time: String): Boolean {
        return Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time)
    }

    fun closeShopBeforeLogout(
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val shopId = _userProfile.value?.shopId.orEmpty()

                if (shopId.isBlank()) {
                    onComplete()
                    return@launch
                }

                val shopRef = getShopReferenceByIdOrField(shopId)

                if (shopRef != null) {
                    shopRef.update(
                        mapOf(
                            "isOpen" to false,
                            "lastActiveAt" to System.currentTimeMillis()
                        )
                    ).await()
                }

                _message.value = "Shop closed successfully"
                onComplete()
            } catch (e: Exception) {
                _message.value = e.message ?: "Failed to close shop"
                onComplete()
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}