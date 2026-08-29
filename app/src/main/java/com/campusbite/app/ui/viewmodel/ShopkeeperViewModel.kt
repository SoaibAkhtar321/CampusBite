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
import com.campusbite.app.data.repository.OrderActionRepository
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
    private val orderActionRepository: OrderActionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders

    private val _salesSummary = MutableStateFlow(ShopkeeperSalesSummary())
    val salesSummary: StateFlow<ShopkeeperSalesSummary> = _salesSummary

    private val _analyticsState = MutableStateFlow(ShopkeeperAnalyticsState())
    val analyticsState: StateFlow<ShopkeeperAnalyticsState> = _analyticsState

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

    // Tracks whether each live analytics listener has delivered at least one
    // snapshot. Used by loadAnalytics() to skip the redundant direct .get()
    // reads once the listeners (started in listenToAnalytics()) are already
    // keeping todayAnalytics/monthAnalytics/lifetimeAnalytics current — while
    // still falling back to a direct read if a listener hasn't fired yet, so
    // the Analytics screen never has to show zero/stale values while waiting.
    private var hasTodayAnalyticsSnapshot = false
    private var hasMonthAnalyticsSnapshot = false
    private var hasLifetimeAnalyticsSnapshot = false

    private val analyticsListenersReady: Boolean
        get() = hasTodayAnalyticsSnapshot &&
                hasMonthAnalyticsSnapshot &&
                hasLifetimeAnalyticsSnapshot

    private var todayAnalytics = ShopkeeperAnalyticsSnapshot()
    private var monthAnalytics = ShopkeeperAnalyticsSnapshot()
    private var lifetimeAnalytics = ShopkeeperAnalyticsSnapshot()

    private var activeOrdersCache: List<Order> = emptyList()
    private var refundPendingOrdersCache: List<Order> = emptyList()

    private val knownOrderIds = mutableSetOf<String>()
    private var hasLoadedInitialOrders = false

    init {
        loadShopkeeperData()
    }

    private fun loadShopkeeperData() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val uid = auth.currentUser?.uid
                    ?: throw Exception("User not logged in.")

                val userDoc = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                shopId = userDoc.getString("shopId").orEmpty()

                if (shopId.isBlank()) {
                    _message.value = "Shop ID missing. Please login again."
                    return@launch
                }

                loadShopControls()
                listenToOrders()
                listenToAnalytics()
                loadMenuItems(shopId)

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

                if (doc == null || !doc.exists()) return@addSnapshotListener

                _shopOpen.value = doc.getBoolean("isOpen") ?: true

                _closedSlots.value = doc.get("closedSlots") as? List<String>
                    ?: emptyList()
            }
    }

    private fun listenToOrders() {
        listenToActiveOrders()
        listenToRefundPendingOrders()
    }

    private fun listenToActiveOrders() {
        if (shopId.isBlank()) return

        activeOrdersListener?.remove()

        activeOrdersListener = firestore.collection("orders")
            .whereEqualTo("shopId", shopId)
            .whereIn(
                "status",
                listOf(
                    OrderStatusValue.PENDING,
                    OrderStatusValue.PREPARING,
                    OrderStatusValue.READY
                )
            )
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    activeOrdersCache = emptyList()
                    mergeOrders()
                    updateSalesSummary()
                    _message.value = error.message ?: "Failed to load active orders."
                    return@addSnapshotListener
                }

                val activeOrders = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toOrderOrNull() }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()

                handleNewPendingOrderNotifications(activeOrders)

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
                    updateSalesSummary()
                    _message.value = error.message ?: "Failed to load refund pending orders."
                    return@addSnapshotListener
                }

                refundPendingOrdersCache = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toOrderOrNull() }
                    ?.sortedByDescending { it.cancelledAt }
                    ?: emptyList()

                mergeOrders()
                updateSalesSummary()
            }
    }

    private fun DocumentSnapshot.toOrderOrNull(): Order? {
        return try {
            val order = Order.from(this)
            if (order.orderId.isBlank()) {
                order.copy(orderId = id)
            } else {
                order
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun handleNewPendingOrderNotifications(activeOrders: List<Order>) {
        if (!hasLoadedInitialOrders) {
            knownOrderIds.clear()
            knownOrderIds.addAll(activeOrders.map { it.orderId })
            hasLoadedInitialOrders = true
            return
        }

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
                hasTodayAnalyticsSnapshot = true
                updateSalesSummary()
            }

        monthAnalyticsListener = monthlyAnalyticsRef(shopId, currentMonth)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                monthAnalytics = snapshot.toShopkeeperAnalyticsSnapshot()
                hasMonthAnalyticsSnapshot = true
                updateSalesSummary()
            }

        lifetimeAnalyticsListener = lifetimeAnalyticsRef(shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                lifetimeAnalytics = snapshot.toShopkeeperAnalyticsSnapshot()
                hasLifetimeAnalyticsSnapshot = true
                updateSalesSummary()
            }
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            _analyticsState.value = _analyticsState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val finalShopId = resolveShopIdForAnalytics()

                if (finalShopId.isBlank()) {
                    _analyticsState.value = _analyticsState.value.copy(
                        isLoading = false,
                        error = "Shop ID missing. Please login again."
                    )
                    return@launch
                }

                // The 3 analytics documents are already kept current by the
                // live listeners started in listenToAnalytics() (running
                // since this ViewModel was created). Once all 3 have
                // delivered their first snapshot, re-reading them here with
                // one-off .get() calls would just fetch the same data the
                // listeners already pushed into todayAnalytics/monthAnalytics/
                // lifetimeAnalytics — so skip the redundant reads and reuse
                // that in-memory data instead. If a listener hasn't fired yet
                // (e.g. this is called before its first snapshot arrives),
                // fall back to a direct read so the screen never shows a
                // zero/stale value while waiting for the listener.
                if (!analyticsListenersReady) {
                    val today = LocalDate.now().toString()
                    val currentMonth = YearMonth.now().toString()

                    val todaySnapshot = dailyAnalyticsRef(
                        shopId = finalShopId,
                        date = today
                    ).get().await()

                    val monthSnapshot = monthlyAnalyticsRef(
                        shopId = finalShopId,
                        month = currentMonth
                    ).get().await()

                    val lifetimeSnapshot = lifetimeAnalyticsRef(
                        shopId = finalShopId
                    ).get().await()

                    todayAnalytics = todaySnapshot.toShopkeeperAnalyticsSnapshot()
                    monthAnalytics = monthSnapshot.toShopkeeperAnalyticsSnapshot()
                    lifetimeAnalytics = lifetimeSnapshot.toShopkeeperAnalyticsSnapshot()
                }

                updateAnalyticsStateFromCurrentData(
                    isLoading = false,
                    error = null
                )

            } catch (e: Exception) {
                e.printStackTrace()

                _analyticsState.value = _analyticsState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load analytics."
                )
            }
        }
    }

    private suspend fun resolveShopIdForAnalytics(): String {
        if (shopId.isNotBlank()) {
            return shopId
        }

        val uid = auth.currentUser?.uid ?: return ""

        val userDoc = firestore.collection("users")
            .document(uid)
            .get()
            .await()

        shopId = userDoc.getString("shopId").orEmpty()

        return shopId
    }

    private fun updateSalesSummary() {
        val today = LocalDate.now().toString()
        val currentMonth = YearMonth.now().toString()

        val pendingTodayOrders = activeOrdersCache.count { order ->
            order.pickupDate == today &&
                    order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        val pendingMonthOrders = activeOrdersCache.count { order ->
            order.pickupDate.startsWith(currentMonth) &&
                    order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        val pendingPaymentOrders = activeOrdersCache.count { order ->
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

        updateAnalyticsStateFromCurrentData()
    }

    private fun updateAnalyticsStateFromCurrentData(
        isLoading: Boolean = false,
        error: String? = null
    ) {
        val today = LocalDate.now().toString()
        val currentMonth = YearMonth.now().toString()

        val pendingTodayOrders = activeOrdersCache.count { order ->
            order.pickupDate == today &&
                    order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        val pendingMonthOrders = activeOrdersCache.count { order ->
            order.pickupDate.startsWith(currentMonth) &&
                    order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        val pendingPaymentOrders = activeOrdersCache.count { order ->
            order.paymentStatus.lowercase() == PaymentStatusValue.PENDING_VERIFICATION
        }

        _analyticsState.value = ShopkeeperAnalyticsState(
            isLoading = isLoading,
            error = error,

            todaySales = todayAnalytics.verifiedSales,
            todayOrders = todayAnalytics.verifiedOrders + pendingTodayOrders,
            todayVerified = todayAnalytics.verifiedOrders,
            todayCancelled = todayAnalytics.cancelledOrders,
            todayPendingVerification = pendingTodayOrders,

            monthSales = monthAnalytics.verifiedSales,
            monthOrders = monthAnalytics.verifiedOrders + pendingMonthOrders,
            monthVerified = monthAnalytics.verifiedOrders,
            monthCancelled = monthAnalytics.cancelledOrders,
            monthPendingVerification = pendingMonthOrders,

            lifetimeSales = lifetimeAnalytics.verifiedSales,
            lifetimeOrders = lifetimeAnalytics.verifiedOrders,
            lifetimeVerified = lifetimeAnalytics.verifiedOrders,
            lifetimeCancelled = lifetimeAnalytics.cancelledOrders,
            lifetimePendingVerification = pendingPaymentOrders
        )
    }

    fun updateOrderStatus(
        orderId: String,
        newStatus: String
    ) {
        if (orderId.isBlank()) {
            _message.value = "Order ID missing."
            return
        }

        viewModelScope.launch {
            val result = orderActionRepository.updateOrderStatus(
                orderId = orderId,
                newStatus = newStatus
            )

            result.fold(
                onSuccess = {
                    _message.value = when (newStatus.lowercase()) {
                        OrderStatusValue.PREPARING -> "Order accepted and started preparing."
                        OrderStatusValue.READY -> "Order marked as ready."
                        OrderStatusValue.PICKED_UP -> "Order marked as picked up."
                        else -> "Order updated successfully."
                    }
                },
                onFailure = { error ->
                    error.printStackTrace()
                    _message.value = error.message ?: "Failed to update order."
                }
            )
        }
    }

    fun cancelOrderByShopkeeper(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String,
        paymentReceivedAmount: Double = 0.0
    ) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            val cleanPaymentType = paymentReceivedType.trim().lowercase()
            val cleanReason = cancelReason.trim()

            val result = orderActionRepository.cancelOrderByShopkeeper(
                orderId = orderId,
                paymentReceivedType = cleanPaymentType,
                cancelReason = cleanReason,
                paymentReceivedAmount = paymentReceivedAmount
            )

            result.fold(
                onSuccess = {
                    _message.value = when (cleanPaymentType) {
                        PaymentReceivedType.NONE -> {
                            "Order cancelled. No refund required."
                        }

                        PaymentReceivedType.PARTIAL -> {
                            "Order cancelled. Refund pending for ₹${paymentReceivedAmount.toInt()}."
                        }

                        PaymentReceivedType.FULL -> {
                            "Order cancelled. Refund pending."
                        }

                        else -> {
                            "Order cancelled successfully."
                        }
                    }
                },
                onFailure = { error ->
                    error.printStackTrace()
                    _message.value = error.message ?: "Failed to cancel order."
                }
            )
        }
    }

    fun markRefundSettled(
        orderId: String,
        refundReferenceId: String,
        refundNote: String
    ) {
        if (orderId.isBlank()) {
            _message.value = "Order ID missing."
            return
        }

        viewModelScope.launch {
            val result = orderActionRepository.markRefundSettled(
                orderId = orderId,
                refundReferenceId = refundReferenceId.trim(),
                refundNote = refundNote.trim()
            )

            result.fold(
                onSuccess = {
                    _message.value = "Refund marked as settled."
                },
                onFailure = { error ->
                    error.printStackTrace()
                    _message.value = error.message ?: "Failed to mark refund settled."
                }
            )
        }
    }

    fun cancelOrderForPaymentNotReceived(orderId: String) {
        cancelOrderByShopkeeper(
            orderId = orderId,
            paymentReceivedType = PaymentReceivedType.NONE,
            cancelReason = "Payment not received"
        )
    }

    fun toggleShopOpen(isOpen: Boolean) {
        if (shopId.isBlank()) {
            _message.value = "Shop ID missing. Please login again."
            return
        }

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shopId)
                    .update(
                        mapOf(
                            "isOpen" to isOpen,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                _message.value = if (isOpen) "Shop opened" else "Shop closed"

            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to update shop status."
            }
        }
    }

    fun toggleSlot(slot: String) {
        if (shopId.isBlank()) {
            _message.value = "Shop ID missing. Please login again."
            return
        }

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
        if (shopId.isBlank()) {
            _message.value = "Shop ID missing. Please login again."
            return
        }

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

                _message.value = "Menu item added."

            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to add menu item."
            }
        }
    }

    fun updateMenuItem(menuItem: MenuItem) {
        if (menuItem.itemId.isBlank()) {
            _message.value = "Item ID missing."
            return
        }

        viewModelScope.launch {
            try {
                firestore.collection("menuItems")
                    .document(menuItem.itemId)
                    .set(menuItem)
                    .await()

                _message.value = "Menu item updated."

            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to update menu item."
            }
        }
    }

    fun deleteMenuItem(itemId: String) {
        if (itemId.isBlank()) {
            _message.value = "Item ID missing."
            return
        }

        viewModelScope.launch {
            try {
                firestore.collection("menuItems")
                    .document(itemId)
                    .delete()
                    .await()

                _message.value = "Menu item deleted."

            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to delete menu item."
            }
        }
    }

    fun toggleMenuItemAvailability(
        itemId: String,
        currentItem: MenuItem
    ) {
        if (itemId.isBlank()) {
            _message.value = "Item ID missing."
            return
        }

        viewModelScope.launch {
            try {
                firestore.collection("menuItems")
                    .document(itemId)
                    .update(
                        mapOf(
                            "isAvailable" to !currentItem.isAvailable,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

            } catch (e: Exception) {
                e.printStackTrace()
                _message.value = e.message ?: "Failed to update item availability."
            }
        }
    }

    private fun dailyAnalyticsRef(
        shopId: String,
        date: String
    ): DocumentReference {
        return firestore.collection("shopAnalytics")
            .document(shopId)
            .collection("daily")
            .document(date)
    }

    private fun monthlyAnalyticsRef(
        shopId: String,
        month: String
    ): DocumentReference {
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

    private fun showNewOrderNotification(order: Order) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) return
        }

        val channelId = STAFF_ORDER_UPDATES_CHANNEL_ID

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

    fun clearMessage() {
        _message.value = ""
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

    companion object {
        private const val STAFF_ORDER_UPDATES_CHANNEL_ID = "staff_order_updates"
    }
}