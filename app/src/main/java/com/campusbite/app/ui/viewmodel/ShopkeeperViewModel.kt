package com.campusbite.app.ui.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.R
import com.campusbite.app.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
class ShopkeeperViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _shopOpen = MutableStateFlow(true)
    val shopOpen: StateFlow<Boolean> = _shopOpen

    private val _closedSlots = MutableStateFlow<List<String>>(emptyList())
    val closedSlots: StateFlow<List<String>> = _closedSlots

    private var shopId: String = ""
    private var shopDocId: String = ""

    private val knownOrderIds = mutableSetOf<String>()
    private var hasLoadedInitialOrders = false

    init { loadShopIdAndOrders() }

    private fun loadShopIdAndOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val userDoc = firestore.collection("users").document(uid).get().await()
                shopId = userDoc.getString("shopId") ?: ""
                if (shopId.isNotEmpty()) {
                    loadShopControls()
                    listenToOrders()
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    private fun loadShopControls() {
        firestore.collection("shops")
            .whereEqualTo("shopId", shopId)
            .addSnapshotListener { snapshot, _ ->
                val doc = snapshot?.documents?.firstOrNull()
                if (doc != null) {
                    shopDocId = doc.id
                    _shopOpen.value = doc.getBoolean("isOpen") ?: true
                    _closedSlots.value = doc.get("closedSlots") as? List<String> ?: emptyList()
                }
            }
    }

    private fun listenToOrders() {
        firestore.collection("orders")
            .whereEqualTo("shopId", shopId)
            .whereNotEqualTo("status", "picked_up")
            .addSnapshotListener { snapshot, _ ->
                val orderList = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(Order::class.java) } catch (e: Exception) { null }
                } ?: emptyList()

                val sortedOrders = orderList.sortedBy { it.createdAt }

                if (!hasLoadedInitialOrders) {
                    knownOrderIds.clear()
                    knownOrderIds.addAll(sortedOrders.map { it.orderId })
                    hasLoadedInitialOrders = true
                } else {
                    val newPending = sortedOrders.filter {
                        it.status == "pending" && it.orderId !in knownOrderIds
                    }
                    newPending.forEach { showNewOrderNotification(it) }
                    knownOrderIds.clear()
                    knownOrderIds.addAll(sortedOrders.map { it.orderId })
                }

                _orders.value = sortedOrders
                _isLoading.value = false
            }
    }

    fun toggleShopOpen(isOpen: Boolean) {
        if (shopDocId.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("shops").document(shopDocId)
                    .update("isOpen", isOpen).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleSlot(slot: String) {
        if (shopDocId.isEmpty()) return
        viewModelScope.launch {
            try {
                val docRef = firestore.collection("shops").document(shopDocId)
                if (_closedSlots.value.contains(slot))
                    docRef.update("closedSlots", FieldValue.arrayRemove(slot)).await()
                else
                    docRef.update("closedSlots", FieldValue.arrayUnion(slot)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                firestore.collection("orders").document(orderId)
                    .update("status", newStatus).await()
                if (newStatus.lowercase() == "ready")
                    _orders.value.find { it.orderId == orderId }?.let { sendNotificationToStudent(it) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun sendNotificationToStudent(order: Order) {
        viewModelScope.launch {
            try {
                val token = firestore.collection("users").document(order.studentId)
                    .get().await().getString("fcmToken")
                if (!token.isNullOrEmpty()) sendNotification(token)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showNewOrderNotification(order: Order) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val channelId = "staff_order_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Staff Order Updates",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
        }

        val itemSummary = order.items.joinToString { "${it.name} x${it.quantity}" }
        try {
            NotificationManagerCompat.from(appContext).notify(
                order.orderId.hashCode(),
                NotificationCompat.Builder(appContext, channelId)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("New Order Received 🔔")
                    .setContentText(itemSummary.ifEmpty { "You have a new order" })
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun sendNotification(token: String) {
        val json = JSONObject().apply {
            put("to", token)
            put("notification", JSONObject().apply {
                put("title", "Order Ready 🎉")
                put("body", "Your order is ready for pickup")
            })
        }
        val request = Request.Builder()
            .url("https://fcm.googleapis.com/fcm/send")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Authorization", "key=YOUR_SERVER_KEY")
            .build()
        Thread {
            try { OkHttpClient().newCall(request).execute() } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}