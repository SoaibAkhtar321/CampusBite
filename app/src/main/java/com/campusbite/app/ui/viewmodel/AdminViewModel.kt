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
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.campusbite.app.R
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class AdminViewModel @Inject constructor(
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

    private val knownOrderIds = mutableSetOf<String>()
    private var hasLoadedInitialOrders = false

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
                    loadShopControls()
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

                val sortedOrders = orderList.sortedBy { it.createdAt }

                if (!hasLoadedInitialOrders) {
                    knownOrderIds.clear()
                    knownOrderIds.addAll(sortedOrders.map { it.orderId })
                    hasLoadedInitialOrders = true
                } else {
                    val newPendingOrders = sortedOrders.filter { order ->
                        order.status == "pending" &&
                                order.orderId !in knownOrderIds
                    }

                    newPendingOrders.forEach { order ->
                        showNewOrderNotification(order)
                    }

                    knownOrderIds.clear()
                    knownOrderIds.addAll(sortedOrders.map { it.orderId })
                }

                _orders.value = sortedOrders
                _isLoading.value = false
            }
    }
    private fun loadShopControls() {

        firestore.collection("shops")
            .whereEqualTo("shopId", shopId)
            .addSnapshotListener { snapshot, _ ->

                val shop = snapshot?.documents
                    ?.firstOrNull()

                _shopOpen.value =
                    shop?.getBoolean("isOpen") ?: true

                _closedSlots.value =
                    shop?.get("closedSlots") as? List<String>
                        ?: emptyList()
            }
    }

    fun toggleShopOpen(isOpen: Boolean) {

        viewModelScope.launch {

            try {

                val shopDoc = firestore.collection("shops")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()

                shopDoc?.reference
                    ?.update("isOpen", isOpen)
                    ?.await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleSlot(slot: String) {

        viewModelScope.launch {

            try {

                val shopDoc = firestore.collection("shops")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()

                val current =
                    _closedSlots.value.toMutableList()

                if (current.contains(slot)) {
                    current.remove(slot)
                } else {
                    current.add(slot)
                }

                shopDoc?.reference
                    ?.update("closedSlots", current)
                    ?.await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
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
    private fun showNewOrderNotification(order: Order) {
        val channelId = "staff_order_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Staff Order Updates",
                NotificationManager.IMPORTANCE_HIGH
            )

            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val itemSummary = order.items.joinToString {
            "${it.name} x${it.quantity}"
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Order Received")
            .setContentText(itemSummary.ifEmpty { "You have a new order" })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("New order received: $itemSummary")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(appContext)
                    .notify(order.orderId.hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(appContext)
                .notify(order.orderId.hashCode(), notification)
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