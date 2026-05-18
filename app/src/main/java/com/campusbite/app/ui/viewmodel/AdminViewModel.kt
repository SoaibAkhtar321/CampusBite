package com.campusbite.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AdminShop(
    val docId: String = "",
    val shopId: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val isOpen: Boolean = false,
    val isApproved: Boolean = false
)

data class AdminUser(
    val docId: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "student",
    val shopId: String = "",
    val isApproved: Boolean = true,
    val isBlocked: Boolean = false,
    val createdAt: Long = 0L
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _shops = MutableStateFlow<List<AdminShop>>(emptyList())
    val shops: StateFlow<List<AdminShop>> = _shops.asStateFlow()

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users.asStateFlow()

    private val _pendingShopkeepers = MutableStateFlow<List<AdminUser>>(emptyList())
    val pendingShopkeepers: StateFlow<List<AdminUser>> = _pendingShopkeepers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var shopsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null

    init {
        listenToShops()
        listenToUsers()
    }

    private fun listenToShops() {
        shopsListener?.remove()

        shopsListener = firestore.collection("shops")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AdminViewModel", "Failed to listen to shops", error)
                    _message.value = error.message ?: "Failed to load shops"
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val shopList = snapshot?.documents?.map { doc ->
                    AdminShop(
                        docId = doc.id,
                        shopId = doc.getString("shopId") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        ownerUid = doc.getString("ownerUid")
                            ?: doc.getString("ownerId")
                            ?: "",
                        isOpen = doc.getBoolean("isOpen") ?: false,
                        isApproved = doc.getBoolean("isApproved") ?: false
                    )
                } ?: emptyList()

                _shops.value = shopList.sortedBy { it.name.lowercase() }
                _isLoading.value = false

                Log.d("AdminViewModel", "Loaded shops: ${shopList.size}")
            }
    }

    private fun listenToUsers() {
        usersListener?.remove()

        usersListener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AdminViewModel", "Failed to listen to users", error)
                    _message.value = error.message ?: "Failed to load users"
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val userList = snapshot?.documents?.map { doc ->
                    val role = doc.getString("role") ?: "student"

                    val isApproved = doc.getBoolean("isApproved")
                        ?: (role != "shopkeeper")

                    AdminUser(
                        docId = doc.id,
                        uid = doc.getString("uid") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        email = doc.getString("email") ?: "",
                        phone = doc.getString("phone")
                            ?: doc.getString("phoneNumber")
                            ?: "",
                        role = role,
                        shopId = doc.getString("shopId") ?: "",
                        isApproved = isApproved,
                        isBlocked = doc.getBoolean("isBlocked") ?: false,
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                } ?: emptyList()

                _users.value = userList.sortedWith(
                    compareBy<AdminUser> { it.role }
                        .thenBy { it.name.lowercase() }
                )

                _pendingShopkeepers.value = userList
                    .filter { user ->
                        user.role == "shopkeeper" && !user.isApproved && !user.isBlocked
                    }
                    .sortedByDescending { it.createdAt }

                _isLoading.value = false

                Log.d("AdminViewModel", "Loaded users: ${userList.size}")
                Log.d("AdminViewModel", "Pending shopkeepers: ${_pendingShopkeepers.value.size}")
            }
    }

    fun setShopkeeperApproved(
        userDocId: String,
        approved: Boolean
    ) {
        viewModelScope.launch {
            try {
                if (userDocId.isBlank()) {
                    _message.value = "User ID missing"
                    return@launch
                }

                val userRef = firestore.collection("users").document(userDocId)
                val userSnapshot = userRef.get().await()

                if (!userSnapshot.exists()) {
                    _message.value = "User not found"
                    return@launch
                }

                val uid = userSnapshot.getString("uid") ?: userDocId
                val name = userSnapshot.getString("name") ?: "Unnamed Shop"
                val email = userSnapshot.getString("email") ?: ""
                val phone = userSnapshot.getString("phone")
                    ?: userSnapshot.getString("phoneNumber")
                    ?: ""
                val role = userSnapshot.getString("role") ?: "student"
                val existingShopId = userSnapshot.getString("shopId") ?: ""

                if (role != "shopkeeper") {
                    _message.value = "Only shopkeepers can be approved as shops"
                    return@launch
                }

                if (!approved) {
                    userRef.update(
                        mapOf(
                            "isApproved" to false
                        )
                    ).await()

                    _message.value = "Shopkeeper moved to pending"
                    return@launch
                }

                val finalShopId = if (existingShopId.isNotBlank()) {
                    existingShopId
                } else {
                    generateShopId(name, uid)
                }

                val shopRef = firestore.collection("shops").document(finalShopId)
                val shopSnapshot = shopRef.get().await()

                if (!shopSnapshot.exists()) {
                    val shopData = mapOf(
                        "shopId" to finalShopId,
                        "name" to name,
                        "description" to "",
                        "ownerUid" to uid,
                        "ownerEmail" to email,
                        "ownerPhone" to phone,
                        "isOpen" to false,
                        "isApproved" to true,
                        "openingTime" to "08:00",
                        "closingTime" to "20:00",
                        "maxOrdersPerSlot" to 5,
                        "closedSlots" to emptyList<String>(),
                        "createdAt" to System.currentTimeMillis()
                    )

                    shopRef.set(shopData).await()
                } else {
                    shopRef.update(
                        mapOf(
                            "isApproved" to true,
                            "ownerUid" to uid,
                            "ownerEmail" to email,
                            "ownerPhone" to phone
                        )
                    ).await()
                }

                userRef.update(
                    mapOf(
                        "isApproved" to true,
                        "shopId" to finalShopId
                    )
                ).await()

                _message.value = "Shopkeeper approved and shop created"

                Log.d(
                    "AdminViewModel",
                    "Approved shopkeeper. userDocId=$userDocId, shopId=$finalShopId"
                )

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to approve shopkeeper", e)
                _message.value = e.message ?: "Failed to approve shopkeeper"
            }
        }
    }

    fun setShopApproved(
        shopDocId: String,
        approved: Boolean,
        shopId: String
    ) {
        viewModelScope.launch {
            try {
                if (shopDocId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                firestore.collection("shops")
                    .document(shopDocId)
                    .update("isApproved", approved)
                    .await()

                if (shopId.isNotBlank()) {
                    val usersSnapshot = firestore.collection("users")
                        .whereEqualTo("shopId", shopId)
                        .get()
                        .await()

                    usersSnapshot.documents.forEach { userDoc ->
                        userDoc.reference
                            .update("isApproved", approved)
                            .await()
                    }
                }

                _message.value = if (approved) {
                    "Shop approved successfully"
                } else {
                    "Shop approval removed"
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update shop approval", e)
                _message.value = e.message ?: "Failed to update shop approval"
            }
        }
    }

    fun setShopOpen(
        shopDocId: String,
        open: Boolean
    ) {
        viewModelScope.launch {
            try {
                if (shopDocId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                firestore.collection("shops")
                    .document(shopDocId)
                    .update("isOpen", open)
                    .await()

                _message.value = if (open) {
                    "Shop marked as open"
                } else {
                    "Shop marked as closed"
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update shop open status", e)
                _message.value = e.message ?: "Failed to update shop status"
            }
        }
    }

    fun setUserBlocked(
        userDocId: String,
        blocked: Boolean
    ) {
        viewModelScope.launch {
            try {
                if (userDocId.isBlank()) {
                    _message.value = "User ID missing"
                    return@launch
                }

                firestore.collection("users")
                    .document(userDocId)
                    .update("isBlocked", blocked)
                    .await()

                _message.value = if (blocked) {
                    "User blocked successfully"
                } else {
                    "User unblocked successfully"
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update user block status", e)
                _message.value = e.message ?: "Failed to update user"
            }
        }
    }

    fun setUserRole(
        userDocId: String,
        role: String
    ) {
        viewModelScope.launch {
            try {
                if (userDocId.isBlank()) {
                    _message.value = "User ID missing"
                    return@launch
                }

                val cleanRole = role.trim().lowercase()

                val updates = mutableMapOf<String, Any>(
                    "role" to cleanRole
                )

                when (cleanRole) {
                    "shopkeeper" -> {
                        updates["isApproved"] = false
                        updates["shopId"] = ""
                    }

                    "student" -> {
                        updates["isApproved"] = true
                        updates["shopId"] = ""
                    }

                    "admin" -> {
                        updates["isApproved"] = true
                        updates["shopId"] = ""
                    }
                }

                firestore.collection("users")
                    .document(userDocId)
                    .update(updates)
                    .await()

                _message.value = "User role updated to $cleanRole"

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update user role", e)
                _message.value = e.message ?: "Failed to update role"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun generateShopId(
        name: String,
        uid: String
    ): String {
        val base = name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "shop" }

        return "${base}_${uid.takeLast(5)}"
    }

    override fun onCleared() {
        super.onCleared()
        shopsListener?.remove()
        usersListener?.remove()
    }
}