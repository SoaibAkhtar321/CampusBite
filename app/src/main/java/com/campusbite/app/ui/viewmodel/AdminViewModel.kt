package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val role: String = "student",
    val isBlocked: Boolean = false,
    val shopId: String = ""
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _shops = MutableStateFlow<List<AdminShop>>(emptyList())
    val shops: StateFlow<List<AdminShop>> = _shops

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        listenToShops()
        listenToUsers()
    }

    private fun listenToShops() {
        firestore.collection("shops")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.map { doc ->
                    AdminShop(
                        docId = doc.id,
                        shopId = doc.getString("shopId") ?: "",
                        name = doc.getString("name") ?: "",
                        ownerUid = doc.getString("ownerUid") ?: "",
                        isOpen = doc.getBoolean("isOpen") ?: false,
                        isApproved = doc.getBoolean("isApproved") ?: false
                    )
                } ?: emptyList()
                _shops.value = list
                _isLoading.value = false
            }
    }

    private fun listenToUsers() {
        firestore.collection("users")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.map { doc ->
                    AdminUser(
                        docId = doc.id,
                        uid = doc.getString("uid") ?: "",
                        name = doc.getString("name") ?: "",
                        email = doc.getString("email") ?: "",
                        role = doc.getString("role") ?: "student",
                        isBlocked = doc.getBoolean("isBlocked") ?: false,
                        shopId = doc.getString("shopId") ?: ""
                    )
                } ?: emptyList()
                _users.value = list
                _isLoading.value = false
            }
    }

    fun setShopApproved(shopDocId: String, approved: Boolean) {
        viewModelScope.launch {
            firestore.collection("shops").document(shopDocId)
                .update("isApproved", approved).await()
        }
    }

    fun setShopOpen(shopDocId: String, open: Boolean) {
        viewModelScope.launch {
            firestore.collection("shops").document(shopDocId)
                .update("isOpen", open).await()
        }
    }

    fun setUserBlocked(userDocId: String, blocked: Boolean) {
        viewModelScope.launch {
            firestore.collection("users").document(userDocId)
                .update("isBlocked", blocked).await()
        }
    }

    fun setUserRole(userDocId: String, role: String) {
        viewModelScope.launch {
            firestore.collection("users").document(userDocId)
                .update("role", role).await()
        }
    }
}