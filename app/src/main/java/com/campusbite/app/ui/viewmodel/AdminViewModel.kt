package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var shopId: String = ""

    init {
        loadShopIdAndOrders()
    }

    private fun loadShopIdAndOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val userDoc = firestore.collection("users").document(uid).get().await()
                shopId = userDoc.getString("shopId") ?: ""
                if (shopId.isNotEmpty()) {
                    listenToOrders()
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    private fun listenToOrders() {
        firestore.collection("orders")
            .whereEqualTo("shopId", shopId)
            .whereNotEqualTo("status", "picked_up")
            .addSnapshotListener { snapshot, _ ->
                val orderList = snapshot?.documents?.mapNotNull {
                    it.toObject(Order::class.java)
                } ?: emptyList()
                _orders.value = orderList.sortedBy { it.createdAt }
                _isLoading.value = false
            }
    }


    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                firestore.collection("orders")
                    .document(orderId)
                    .update("status", newStatus)
                    .await()
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}