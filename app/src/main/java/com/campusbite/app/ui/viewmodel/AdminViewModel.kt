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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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

                if (newStatus.lowercase() == "ready") {
                    val order = _orders.value.find { it.orderId == orderId }

                    if (order != null) {
                        sendNotificationToStudent(order)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendNotificationToStudent(order: Order) {
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users")
                    .document(order.studentId)
                    .get()
                    .await()

                val token = userDoc.getString("fcmToken")

                if (!token.isNullOrEmpty()) {
                    sendNotification(token)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendNotification(token: String) {
        val client = OkHttpClient()

        val notification = JSONObject().apply {
            put("title", "Order Ready 🎉")
            put("body", "Your order is ready for pickup")
        }

        val json = JSONObject().apply {
            put("to", token)
            put("notification", notification)
        }

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://fcm.googleapis.com/fcm/send")
            .post(body)
            .addHeader("Authorization", "key=YOUR_SERVER_KEY")
            .addHeader("Content-Type", "application/json")
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    println("FCM Response: ${response.body?.string()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}