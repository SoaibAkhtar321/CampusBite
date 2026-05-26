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
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems

    private var shopId: String = ""
    private var menuItemsListener: ListenerRegistration? = null
    private var ordersListener: ListenerRegistration? = null
    private var shopControlsListener: ListenerRegistration? = null

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

                val userDoc = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                shopId = userDoc.getString("shopId").orEmpty()

                if (shopId.isNotBlank()) {
                    loadShopControls()
                    listenToOrders()
                    loadMenuItems(shopId)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadShopControls() {
        if (shopId.isBlank()) return

        shopControlsListener?.remove()

        shopControlsListener = firestore.collection("shops")
            .document(shopId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                if (doc == null || !doc.exists()) return@addSnapshotListener

                _shopOpen.value = doc.getBoolean("isOpen") ?: true

                _closedSlots.value =
                    doc.get("closedSlots") as? List<String> ?: emptyList()
            }
    }

    private fun listenToOrders() {
        if (shopId.isBlank()) return

        ordersListener?.remove()

        ordersListener = firestore.collection("orders")
            .whereEqualTo("shopId", shopId)
            .whereNotEqualTo("status", "picked_up")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    _orders.value = emptyList()
                    return@addSnapshotListener
                }

                val orderList = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()

                if (!hasLoadedInitialOrders) {
                    knownOrderIds.clear()
                    knownOrderIds.addAll(orderList.map { it.orderId })
                    hasLoadedInitialOrders = true
                } else {
                    val newPendingOrders = orderList.filter { order ->
                        order.status == "pending" &&
                                order.orderId !in knownOrderIds
                    }

                    newPendingOrders.forEach { order ->
                        showNewOrderNotification(order)
                    }

                    knownOrderIds.clear()
                    knownOrderIds.addAll(orderList.map { it.orderId })
                }

                _orders.value = orderList
            }
    }

    fun toggleShopOpen(isOpen: Boolean) {
        if (shopId.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shopId)
                    .update("isOpen", isOpen)
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleSlot(slot: String) {
        if (shopId.isBlank()) return

        viewModelScope.launch {
            try {
                val shopRef = firestore.collection("shops")
                    .document(shopId)

                if (_closedSlots.value.contains(slot)) {
                    shopRef.update(
                        "closedSlots",
                        FieldValue.arrayRemove(slot)
                    ).await()
                } else {
                    shopRef.update(
                        "closedSlots",
                        FieldValue.arrayUnion(slot)
                    ).await()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateOrderStatus(
        orderId: String,
        newStatus: String
    ) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    "status" to newStatus
                )

                if (newStatus == "preparing") {
                    updates["paymentStatus"] = "verified"
                }

                firestore.collection("orders")
                    .document(orderId)
                    .update(updates)
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showNewOrderNotification(order: Order) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) return
        }

        val channelId = "staff_order_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Staff Order Updates",
                NotificationManager.IMPORTANCE_HIGH
            )

            val notificationManager = appContext.getSystemService(
                NotificationManager::class.java
            )

            notificationManager.createNotificationChannel(channel)
        }

        val itemSummary = order.items.joinToString {
            "${it.name} x${it.quantity}"
        }

        val notification = NotificationCompat.Builder(
            appContext,
            channelId
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Order Received 🔔")
            .setContentText(
                itemSummary.ifBlank {
                    "You have a new order"
                }
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify(order.orderId.hashCode(), notification)

        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun loadMenuItems(shopId: String) {
        if (shopId.isBlank()) {
            _menuItems.value = emptyList()
            return
        }

        menuItemsListener?.remove()

        menuItemsListener = firestore.collection("menuItems")
            .whereEqualTo("shopId", shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    _menuItems.value = emptyList()
                    return@addSnapshotListener
                }

                _menuItems.value = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(MenuItem::class.java)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    ?.sortedBy { it.name }
                    ?: emptyList()
            }
    }

    fun addMenuItem(menuItem: MenuItem) {
        viewModelScope.launch {
            try {
                val itemId = firestore.collection("menuItems")
                    .document()
                    .id

                val newItem = menuItem.copy(
                    itemId = itemId,
                    shopId = shopId
                )

                firestore.collection("menuItems")
                    .document(itemId)
                    .set(newItem)
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateMenuItem(menuItem: MenuItem) {
        if (menuItem.itemId.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("menuItems")
                    .document(menuItem.itemId)
                    .set(menuItem)
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMenuItem(itemId: String) {
        if (itemId.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("menuItems")
                    .document(itemId)
                    .delete()
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleMenuItemAvailability(
        itemId: String,
        currentItem: MenuItem
    ) {
        if (itemId.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("menuItems")
                    .document(itemId)
                    .update(
                        "isAvailable",
                        !currentItem.isAvailable
                    )
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ordersListener?.remove()
        shopControlsListener?.remove()
        menuItemsListener?.remove()
    }
}