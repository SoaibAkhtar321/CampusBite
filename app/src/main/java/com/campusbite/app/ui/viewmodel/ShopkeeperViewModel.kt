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
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ShopkeeperSalesSummary(
    val todayOrders: Int = 0,
    val todaySales: Double = 0.0,
    val monthOrders: Int = 0,
    val monthSales: Double = 0.0,
    val lifetimeOrders: Int = 0,
    val lifetimeSales: Double = 0.0,
    val pendingPaymentOrders: Int = 0,
    val cancelledOrders: Int = 0
)

private data class ShopkeeperAnalyticsSnapshot(
    val verifiedOrders: Int = 0,
    val verifiedSales: Double = 0.0,
    val cancelledOrders: Int = 0
)

@HiltViewModel
class ShopkeeperViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders

    private val _salesSummary = MutableStateFlow(ShopkeeperSalesSummary())
    val salesSummary: StateFlow<ShopkeeperSalesSummary> = _salesSummary

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _shopOpen = MutableStateFlow(true)
    val shopOpen: StateFlow<Boolean> = _shopOpen

    private val _closedSlots = MutableStateFlow<List<String>>(emptyList())
    val closedSlots: StateFlow<List<String>> = _closedSlots

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private var shopId: String = ""

    private var menuItemsListener: ListenerRegistration? = null
    private var activeOrdersListener: ListenerRegistration? = null
    private var refundPendingOrdersListener: ListenerRegistration? = null
    private var shopControlsListener: ListenerRegistration? = null

    private var todayAnalyticsListener: ListenerRegistration? = null
    private var monthAnalyticsListener: ListenerRegistration? = null
    private var lifetimeAnalyticsListener: ListenerRegistration? = null

    private var todayAnalytics = ShopkeeperAnalyticsSnapshot()
    private var monthAnalytics = ShopkeeperAnalyticsSnapshot()
    private var lifetimeAnalytics = ShopkeeperAnalyticsSnapshot()

    private var activeOrdersCache: List<Order> = emptyList()
    private var refundPendingOrdersCache: List<Order> = emptyList()

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
                    listenToAnalytics()
                    loadMenuItems(shopId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to load shopkeeper data."
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
                    _message.value = error.message ?: "Failed to load shop controls."
                    return@addSnapshotListener
                }

                if (doc == null || !doc.exists()) {
                    return@addSnapshotListener
                }

                _shopOpen.value = doc.getBoolean("isOpen") ?: true

                _closedSlots.value =
                    doc.get("closedSlots") as? List<String> ?: emptyList()
            }
    }

    private fun listenToOrders() {
        listenToActiveOrders()
        listenToRefundPendingOrders()
    }

    private fun listenToActiveOrders() {
        if (shopId.isBlank()) return

        activeOrdersListener?.remove()

        val activeStatuses = listOf(
            OrderStatusValue.PENDING,
            OrderStatusValue.PREPARING,
            OrderStatusValue.READY
        )

        activeOrdersListener = firestore.collection("orders")
            .whereEqualTo("shopId", shopId)
            .whereIn("status", activeStatuses)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    activeOrdersCache = emptyList()
                    mergeOrders()
                    _message.value = error.message ?: "Failed to load active orders."
                    updateSalesSummary()
                    return@addSnapshotListener
                }

                val activeOrders = snapshot?.documents
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
                    knownOrderIds.addAll(activeOrders.map { it.orderId })
                    hasLoadedInitialOrders = true
                } else {
                    val newPendingOrders = activeOrders.filter { order ->
                        order.status.lowercase() == OrderStatusValue.PENDING &&
                                order.orderId !in knownOrderIds
                    }

                    newPendingOrders.forEach { order ->
                        showNewOrderNotification(order)
                    }

                    knownOrderIds.clear()
                    knownOrderIds.addAll(activeOrders.map { it.orderId })
                }

                activeOrdersCache = activeOrders
                mergeOrders()
                updateSalesSummary()
            }
    }

    private fun listenToRefundPendingOrders() {
        if (shopId.isBlank()) return

        refundPendingOrdersListener?.remove()

        refundPendingOrdersListener = firestore.collection("orders")
            .whereEqualTo("shopId", shopId)
            .whereEqualTo("status", OrderStatusValue.CANCELLED)
            .whereEqualTo("refundStatus", RefundStatusValue.REFUND_PENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    refundPendingOrdersCache = emptyList()
                    mergeOrders()
                    _message.value = error.message ?: "Failed to load refund pending orders."
                    updateSalesSummary()
                    return@addSnapshotListener
                }

                refundPendingOrdersCache = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    ?.sortedByDescending { it.cancelledAt }
                    ?: emptyList()

                mergeOrders()
                updateSalesSummary()
            }
    }

    private fun mergeOrders() {
        _orders.value = (refundPendingOrdersCache + activeOrdersCache)
            .distinctBy { it.orderId }
            .sortedWith(
                compareByDescending<Order> {
                    it.status.lowercase() == OrderStatusValue.CANCELLED &&
                            it.refundStatus.lowercase() == RefundStatusValue.REFUND_PENDING
                }.thenBy { it.createdAt }
            )
    }

    private fun listenToAnalytics() {
        if (shopId.isBlank()) return

        todayAnalyticsListener?.remove()
        monthAnalyticsListener?.remove()
        lifetimeAnalyticsListener?.remove()

        val today = LocalDate.now().toString()
        val currentMonth = YearMonth.now().toString()

        todayAnalyticsListener = dailyAnalyticsRef(shopId, today)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                todayAnalytics = snapshot.toShopkeeperAnalyticsSnapshot()
                updateSalesSummary()
            }

        monthAnalyticsListener = monthlyAnalyticsRef(shopId, currentMonth)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                monthAnalytics = snapshot.toShopkeeperAnalyticsSnapshot()
                updateSalesSummary()
            }

        lifetimeAnalyticsListener = lifetimeAnalyticsRef(shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                lifetimeAnalytics = snapshot.toShopkeeperAnalyticsSnapshot()
                updateSalesSummary()
            }
    }

    private fun updateSalesSummary() {
        val today = LocalDate.now().toString()
        val currentMonth = YearMonth.now().toString()

        val activeOrders = activeOrdersCache

        val pendingTodayOrders = activeOrders.count { order ->
            order.pickupDate == today &&
                    order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        val pendingMonthOrders = activeOrders.count { order ->
            order.pickupDate.startsWith(currentMonth) &&
                    order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        val pendingPaymentOrders = activeOrders.count { order ->
            order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        _salesSummary.value = ShopkeeperSalesSummary(
            todayOrders = todayAnalytics.verifiedOrders + pendingTodayOrders,
            todaySales = todayAnalytics.verifiedSales,
            monthOrders = monthAnalytics.verifiedOrders + pendingMonthOrders,
            monthSales = monthAnalytics.verifiedSales,
            lifetimeOrders = lifetimeAnalytics.verifiedOrders,
            lifetimeSales = lifetimeAnalytics.verifiedSales,
            pendingPaymentOrders = pendingPaymentOrders,
            cancelledOrders = lifetimeAnalytics.cancelledOrders
        )
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
                _message.value = e.message ?: "Failed to update shop status."
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
                _message.value = e.message ?: "Failed to update slot."
            }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: String) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            try {
                val orderRef = firestore.collection("orders").document(orderId)

                firestore.runTransaction { transaction ->
                    val orderDoc = transaction.get(orderRef)

                    if (!orderDoc.exists()) throw IllegalStateException("Order not found.")

                    val orderShopId = orderDoc.getString("shopId").orEmpty()
                    if (orderShopId != shopId) throw IllegalStateException("You cannot update this order.")

                    val oldPaymentStatus = orderDoc.getString("paymentStatus")?.lowercase().orEmpty()
                    val oldStatus = orderDoc.getString("status")?.lowercase().orEmpty()

                    if (oldStatus == OrderStatusValue.CANCELLED) {
                        throw IllegalStateException("Cancelled order cannot be updated.")
                    }

                    val updates = mutableMapOf<String, Any>(
                        "status" to newStatus,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    val shouldVerifyPayment =
                        newStatus == OrderStatusValue.PREPARING &&
                                oldPaymentStatus !in listOf("verified", PaymentStatusValue.PAID)

                    if (shouldVerifyPayment) updates["paymentStatus"] = PaymentStatusValue.PAID

                    transaction.update(orderRef, updates)

                    if (shouldVerifyPayment) {
                        val pickupDate = orderDoc.getString("pickupDate")
                            ?.takeIf { it.isNotBlank() }
                            ?: LocalDate.now().toString()

                        val month = pickupDate.take(7)

                        val totalPrice = orderDoc.getDouble("totalPrice")
                            ?: orderDoc.getLong("totalPrice")?.toDouble()
                            ?: 0.0

                        incrementVerifiedAnalytics(transaction, shopId, pickupDate, month, totalPrice)
                    }

                    null
                }.await()
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to update order."
            }
        }
    }

    fun cancelOrderByShopkeeper(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String
    ) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            try {
                val cleanPaymentType = paymentReceivedType.trim().lowercase()
                val cleanReason = cancelReason.trim()

                if (cleanPaymentType !in listOf(PaymentReceivedType.NONE, PaymentReceivedType.PARTIAL, PaymentReceivedType.FULL)) {
                    throw IllegalStateException("Invalid payment received type.")
                }

                val paymentReceivedByShopkeeper = cleanPaymentType in listOf(
                    PaymentReceivedType.PARTIAL,
                    PaymentReceivedType.FULL
                )

                if (paymentReceivedByShopkeeper && cleanReason.isBlank()) {
                    throw IllegalStateException("Please select a cancellation reason.")
                }

                val orderRef = firestore.collection("orders").document(orderId)

                firestore.runTransaction { transaction ->
                    val orderDoc = transaction.get(orderRef)

                    if (!orderDoc.exists()) throw IllegalStateException("Order not found.")

                    val orderShopId = orderDoc.getString("shopId").orEmpty()
                    if (orderShopId != shopId) throw IllegalStateException("You cannot cancel this order.")

                    val oldStatus = orderDoc.getString("status")?.lowercase().orEmpty()

                    if (oldStatus == OrderStatusValue.CANCELLED || oldStatus == OrderStatusValue.PICKED_UP) {
                        throw IllegalStateException("This order cannot be cancelled.")
                    }

                    val paymentStatus = when (cleanPaymentType) {
                        PaymentReceivedType.PARTIAL -> PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED
                        PaymentReceivedType.FULL -> PaymentStatusValue.PAID
                        else -> PaymentStatusValue.PAYMENT_NOT_RECEIVED
                    }

                    val refundStatus = if (paymentReceivedByShopkeeper) {
                        RefundStatusValue.REFUND_PENDING
                    } else {
                        RefundStatusValue.NONE
                    }

                    val finalCancelReason = when {
                        cleanReason.isNotBlank() -> cleanReason
                        cleanPaymentType == PaymentReceivedType.NONE -> "Payment not received"
                        else -> "Order cancelled after payment received"
                    }

                    val now = System.currentTimeMillis()

                    transaction.update(
                        orderRef,
                        mapOf(
                            "status" to OrderStatusValue.CANCELLED,
                            "paymentStatus" to paymentStatus,
                            "cancelReason" to finalCancelReason,
                            "cancelledBy" to "shopkeeper",
                            "cancelledAt" to now,
                            "paymentReceivedByShopkeeper" to paymentReceivedByShopkeeper,
                            "paymentReceivedType" to cleanPaymentType,
                            "refundStatus" to refundStatus,
                            "refundReferenceId" to "",
                            "refundSettledAt" to 0L,
                            "refundNote" to "",
                            "updatedAt" to now
                        )
                    )

                    val pickupDate = orderDoc.getString("pickupDate")
                        ?.takeIf { it.isNotBlank() }
                        ?: LocalDate.now().toString()

                    val month = pickupDate.take(7)

                    incrementCancelledAnalytics(transaction, shopId, pickupDate, month)

                    null
                }.await()

                _message.value = if (paymentReceivedByShopkeeper) {
                    "Order cancelled. Refund is now pending."
                } else {
                    "Order cancelled because payment was not received."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to cancel order."
            }
        }
    }

    fun markRefundSettled(orderId: String, refundReferenceId: String, refundNote: String) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            try {
                val cleanRefundReferenceId = refundReferenceId.trim()
                val cleanRefundNote = refundNote.trim()

                if (cleanRefundReferenceId.isBlank()) {
                    throw IllegalStateException("Refund reference ID is required.")
                }

                val orderRef = firestore.collection("orders").document(orderId)

                firestore.runTransaction { transaction ->
                    val orderDoc = transaction.get(orderRef)

                    if (!orderDoc.exists()) throw IllegalStateException("Order not found.")

                    val orderShopId = orderDoc.getString("shopId").orEmpty()
                    if (orderShopId != shopId) throw IllegalStateException("You cannot update this refund.")

                    val refundStatus = orderDoc.getString("refundStatus")?.lowercase().orEmpty()
                    if (refundStatus != RefundStatusValue.REFUND_PENDING) {
                        throw IllegalStateException("This order is not pending refund.")
                    }

                    val now = System.currentTimeMillis()

                    transaction.update(
                        orderRef,
                        mapOf(
                            "paymentStatus" to PaymentStatusValue.REFUNDED,
                            "refundStatus" to RefundStatusValue.REFUNDED,
                            "refundReferenceId" to cleanRefundReferenceId,
                            "refundNote" to cleanRefundNote,
                            "refundSettledAt" to now,
                            "updatedAt" to now
                        )
                    )

                    null
                }.await()

                _message.value = "Refund marked as settled."
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to mark refund settled."
            }
        }
    }

    fun cancelOrderForPaymentNotReceived(orderId: String) {
        cancelOrderByShopkeeper(
            orderId = orderId,
            paymentReceivedType = PaymentReceivedType.NONE,
            cancelReason = "Payment not received"
        )
    }

    private fun incrementVerifiedAnalytics(
        transaction: com.google.firebase.firestore.Transaction,
        shopId: String,
        pickupDate: String,
        month: String,
        amount: Double
    ) {
        val dailyRef = dailyAnalyticsRef(shopId, pickupDate)
        val monthlyRef = monthlyAnalyticsRef(shopId, month)
        val lifetimeRef = lifetimeAnalyticsRef(shopId)

        val update = mapOf(
            "verifiedOrders" to FieldValue.increment(1),
            "verifiedSales" to FieldValue.increment(amount),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        transaction.set(dailyRef, update, SetOptions.merge())
        transaction.set(monthlyRef, update, SetOptions.merge())
        transaction.set(lifetimeRef, update, SetOptions.merge())
    }

    private fun incrementCancelledAnalytics(
        transaction: com.google.firebase.firestore.Transaction,
        shopId: String,
        pickupDate: String,
        month: String
    ) {
        val dailyRef = dailyAnalyticsRef(shopId, pickupDate)
        val monthlyRef = monthlyAnalyticsRef(shopId, month)
        val lifetimeRef = lifetimeAnalyticsRef(shopId)

        val update = mapOf(
            "cancelledOrders" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        transaction.set(dailyRef, update, SetOptions.merge())
        transaction.set(monthlyRef, update, SetOptions.merge())
        transaction.set(lifetimeRef, update, SetOptions.merge())
    }

    private fun dailyAnalyticsRef(shopId: String, date: String): DocumentReference {
        return firestore.collection("shopAnalytics")
            .document(shopId)
            .collection("daily")
            .document(date)
    }

    private fun monthlyAnalyticsRef(shopId: String, month: String): DocumentReference {
        return firestore.collection("shopAnalytics")
            .document(shopId)
            .collection("monthly")
            .document(month)
    }

    private fun lifetimeAnalyticsRef(shopId: String): DocumentReference {
        return firestore.collection("shopAnalytics")
            .document(shopId)
            .collection("lifetime")
            .document("summary")
    }

    private fun DocumentSnapshot?.toShopkeeperAnalyticsSnapshot(): ShopkeeperAnalyticsSnapshot {
        if (this == null || !exists()) return ShopkeeperAnalyticsSnapshot()

        return ShopkeeperAnalyticsSnapshot(
            verifiedOrders = getLong("verifiedOrders")?.toInt() ?: 0,
            verifiedSales = getDouble("verifiedSales")
                ?: getLong("verifiedSales")?.toDouble()
                ?: 0.0,
            cancelledOrders = getLong("cancelledOrders")?.toInt() ?: 0
        )
    }

    fun clearMessage() {
        _message.value = ""
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

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Order Received 🔔")
            .setContentText(itemSummary.ifBlank { "You have a new order" })
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
                    _message.value = error.message ?: "Failed to load menu items."
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
                val itemId = firestore.collection("menuItems").document().id

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
                _message.value = e.message ?: "Failed to add menu item."
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
                _message.value = e.message ?: "Failed to update menu item."
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
                _message.value = e.message ?: "Failed to delete menu item."
            }
        }
    }

    fun toggleMenuItemAvailability(itemId: String, currentItem: MenuItem) {
        if (itemId.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("menuItems")
                    .document(itemId)
                    .update("isAvailable", !currentItem.isAvailable)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to update item availability."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        activeOrdersListener?.remove()
        refundPendingOrdersListener?.remove()
        shopControlsListener?.remove()
        menuItemsListener?.remove()

        todayAnalyticsListener?.remove()
        monthAnalyticsListener?.remove()
        lifetimeAnalyticsListener?.remove()
    }
}
