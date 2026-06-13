package com.campusbite.app.ui.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.R
import com.campusbite.app.data.model.Order
import com.campusbite.app.data.model.Shop
import com.campusbite.app.data.repository.OrderRepository
import com.campusbite.app.data.repository.SlotAvailabilityRepository
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.RefundStatusValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SlotUiState(
    val slots: List<String> = emptyList(),
    val message: String = "",
    val isLoading: Boolean = false
)

sealed class OrderState {
    object Idle : OrderState()
    object Loading : OrderState()
    data class Success(val orderId: String) : OrderState()
    data class Error(val message: String) : OrderState()
}

data class PagedOrderState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val slotAvailabilityRepository: SlotAvailabilityRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _slotUiState = MutableStateFlow(SlotUiState())
    val slotUiState: StateFlow<SlotUiState> = _slotUiState

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    private val _currentOrder = MutableStateFlow<Order?>(null)
    val currentOrder: StateFlow<Order?> = _currentOrder

    private val _activeOrder = MutableStateFlow<Order?>(null)
    val activeOrder: StateFlow<Order?> = _activeOrder

    private val _activeOrders = MutableStateFlow<List<Order>>(emptyList())
    val activeOrders: StateFlow<List<Order>> = _activeOrders

    private val _selectedShop = MutableStateFlow<Shop?>(null)
    val selectedShop: StateFlow<Shop?> = _selectedShop

    private val _userOrders = MutableStateFlow<List<Order>>(emptyList())
    val userOrders: StateFlow<List<Order>> = _userOrders

    private val _studentHistoryState = MutableStateFlow(PagedOrderState())
    val studentHistoryState: StateFlow<PagedOrderState> = _studentHistoryState
    private var studentLastDoc: DocumentSnapshot? = null

    private val _shopHistoryState = MutableStateFlow(PagedOrderState())
    val shopHistoryState: StateFlow<PagedOrderState> = _shopHistoryState
    private var shopLastDoc: DocumentSnapshot? = null

    private var activeOrderListener: ListenerRegistration? = null
    private var currentOrderListener: ListenerRegistration? = null
    private var userOrdersListener: ListenerRegistration? = null
    private var shopAvailabilityListener: ListenerRegistration? = null

    private val lastKnownOrderStates = mutableMapOf<String, String>()
    private val lastNotifiedOrderStates = mutableMapOf<String, String>()

    private val activeStatuses = listOf(
        OrderStatusValue.PENDING,
        OrderStatusValue.ACCEPTED,
        OrderStatusValue.PREPARING,
        OrderStatusValue.READY
    )

    private data class ShopTimeWindow(
        val openingDateTime: LocalDateTime,
        val closingDateTime: LocalDateTime
    )

    fun listenToShopAvailability(shopId: String) {
        shopAvailabilityListener?.remove()

        if (shopId.isBlank()) {
            _selectedShop.value = null
            _slotUiState.value = SlotUiState(message = "Shop ID is missing.")
            return
        }

        viewModelScope.launch {
            try {
                val actualDoc = getShopDocumentByIdOrField(shopId)

                if (actualDoc == null || !actualDoc.exists()) {
                    _selectedShop.value = null
                    _slotUiState.value = SlotUiState(message = "Shop details not found.")
                    return@launch
                }

                shopAvailabilityListener = firestore.collection("shops")
                    .document(actualDoc.id)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("OrderVM", "Shop availability listener failed", error)
                            _selectedShop.value = null
                            _slotUiState.value = SlotUiState(message = "Failed to check shop availability.")
                            return@addSnapshotListener
                        }

                        if (snapshot == null || !snapshot.exists()) {
                            _selectedShop.value = null
                            _slotUiState.value = SlotUiState(message = "Shop details not found.")
                            return@addSnapshotListener
                        }

                        val shop = buildShopFromSnapshot(snapshot)
                        _selectedShop.value = shop

                        if (!isShopAcceptingOrders(snapshot, shop)) {
                            Log.w(
                                "OrderVM",
                                "Shop not accepting orders: docId=${snapshot.id}, " +
                                        "fieldShopId=${snapshot.getString("shopId")}, " +
                                        "isOpen=${shop.isOpen}, " +
                                        "isApproved=${shop.isApproved}, " +
                                        "isBlocked=${shop.isBlocked}, " +
                                        "isDeleted=${shop.isDeleted}, " +
                                        "isVisible=${snapshot.getBoolean("isVisible")}"
                            )

                            _slotUiState.value = SlotUiState(
                                message = "This shop is currently not accepting orders."
                            )
                            return@addSnapshotListener
                        }

                        if (!isWithinWorkingHours(shop)) {
                            _slotUiState.value = SlotUiState(message = getClosedMessage(shop))
                        }
                    }
            } catch (e: Exception) {
                Log.e("OrderVM", "Failed to start shop availability listener", e)
                _selectedShop.value = null
                _slotUiState.value = SlotUiState(message = "Failed to check shop availability.")
            }
        }
    }

    fun placeOrder(order: Order) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading

            try {
                if (order.shopId.isBlank()) {
                    _orderState.value = OrderState.Error("Shop ID is missing.")
                    return@launch
                }

                if (order.items.isEmpty()) {
                    _orderState.value = OrderState.Error("Cart is empty.")
                    return@launch
                }

                if (order.pickupSlot.isBlank()) {
                    _orderState.value = OrderState.Error("Please select a pickup slot.")
                    return@launch
                }

                val shopDoc = getShopDocumentByIdOrField(order.shopId)

                if (shopDoc == null || !shopDoc.exists()) {
                    Log.e("OrderVM", "Shop not found for shopId=${order.shopId}")
                    _orderState.value = OrderState.Error("Shop not found.")
                    return@launch
                }

                val shop = buildShopFromSnapshot(shopDoc)
                val canonicalShopId = shopDoc.id
                val maxOrdersPerSlot = getMaxOrdersPerSlot(shopDoc)
                val closedSlots = getClosedSlots(shopDoc)

                Log.d(
                    "OrderVM",
                    "placeOrder shop check: incomingShopId=${order.shopId}, " +
                            "canonicalShopId=$canonicalShopId, " +
                            "fieldShopId=${shopDoc.getString("shopId")}, " +
                            "isOpen=${shop.isOpen}, " +
                            "isApproved=${shop.isApproved}, " +
                            "isBlocked=${shop.isBlocked}, " +
                            "isDeleted=${shop.isDeleted}, " +
                            "isVisible=${shopDoc.getBoolean("isVisible")}"
                )

                if (!isShopAcceptingOrders(shopDoc, shop)) {
                    _orderState.value = OrderState.Error("This shop is currently not accepting orders.")
                    return@launch
                }

                if (!isWithinWorkingHours(shop)) {
                    _orderState.value = OrderState.Error("This shop is currently closed.")
                    return@launch
                }

                Log.d("OrderVM_DEBUG", "STEP 1: Checking slot availability started")

                val slotAvailability = try {
                    slotAvailabilityRepository.getSlotAvailability(
                        shopId = canonicalShopId,
                        date = order.pickupDate,
                        slot = order.pickupSlot,
                        maxOrders = maxOrdersPerSlot
                    )
                } catch (e: Exception) {
                    Log.e("OrderVM_DEBUG", "STEP 1 FAILED: slotAvailability permission/error", e)
                    _orderState.value = OrderState.Error(
                        "Slot check failed: ${e.message}"
                    )
                    return@launch
                }

                Log.d("OrderVM_DEBUG", "STEP 1 PASSED: Slot check success")

                val isSlotClosed =
                    slotAvailability.isClosed || closedSlots.contains(order.pickupSlot)

                val isSlotFull =
                    slotAvailability.orderCount >= slotAvailability.maxOrders

                if (isSlotClosed || isSlotFull) {
                    _orderState.value = OrderState.Error(
                        "This pickup slot is no longer available. Please select another slot."
                    )
                    return@launch
                }

                val normalizedItems = order.items.map { item ->
                    item.copy(shopId = canonicalShopId)
                }

                val finalOrder = order.copy(
                    shopId = canonicalShopId,
                    items = normalizedItems
                )

                Log.d(
                    "OrderVM",
                    "Writing order: orderShopId=${finalOrder.shopId}, " +
                            "itemShopIds=${finalOrder.items.map { it.shopId }.distinct()}"
                )
                Log.d("OrderVM_DEBUG", "STEP 2: Order write started")

                val result = orderRepository.placeOrder(finalOrder)
                if (result.isSuccess) {
                    Log.d("OrderVM_DEBUG", "STEP 2 PASSED: Order write success")
                } else {
                    Log.e(
                        "OrderVM_DEBUG",
                        "STEP 2 FAILED: Order write failed",
                        result.exceptionOrNull()
                    )
                }

                if (result.isSuccess) {
                    val orderId = result.getOrNull().orEmpty()
                    _orderState.value = OrderState.Success(orderId)
                    listenToOrderById(orderId)
                    listenToActiveOrder()
                } else {
                    _orderState.value = OrderState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to place order"
                    )
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Exception while placing order", e)
                _orderState.value = OrderState.Error(e.message ?: "Failed to place order")
            }
        }
    }

    fun cancelOrderByShopkeeper(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String
    ) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading

            val result = orderRepository.cancelOrderByShopkeeper(
                orderId = orderId,
                paymentReceivedType = paymentReceivedType,
                cancelReason = cancelReason
            )

            _orderState.value = if (result.isSuccess) {
                OrderState.Success(orderId)
            } else {
                OrderState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to cancel order"
                )
            }
        }
    }

    fun markRefundSettled(
        orderId: String,
        refundReferenceId: String,
        refundNote: String
    ) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading

            val result = orderRepository.markRefundSettled(
                orderId = orderId,
                refundReferenceId = refundReferenceId,
                refundNote = refundNote
            )

            _orderState.value = if (result.isSuccess) {
                OrderState.Success(orderId)
            } else {
                OrderState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to mark refund settled"
                )
            }
        }
    }

    fun listenToOrderById(orderId: String) {
        if (orderId.isBlank()) return

        currentOrderListener?.remove()

        currentOrderListener = orderRepository.listenToOrder(orderId) { order ->
            if (order == null) {
                _currentOrder.value = null
                return@listenToOrder
            }

            val previousStateKey = lastKnownOrderStates[order.orderId]
                ?: _currentOrder.value
                    ?.takeIf { it.orderId == order.orderId }
                    ?.let { getOrderStateKey(it) }

            val newStateKey = getOrderStateKey(order)
            _currentOrder.value = order

            if (previousStateKey != null && previousStateKey != newStateKey) {
                notifyForOrderStateChange(
                    order = order,
                    previousStateKey = previousStateKey
                )
            }

            lastKnownOrderStates[order.orderId] = newStateKey
        }
    }

    fun listenToActiveOrder() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listenToActiveOrder(uid)
    }

    fun listenToActiveOrder(userId: String) {
        if (userId.isBlank()) return

        activeOrderListener?.remove()

        val today = LocalDate.now().toString()

        activeOrderListener = firestore.collection("orders")
            .whereEqualTo("studentId", userId)
            .whereIn("status", activeStatuses)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("OrderVM", "Active order listener error", error)
                    _activeOrder.value = null
                    _activeOrders.value = emptyList()
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    _activeOrder.value = null
                    _activeOrders.value = emptyList()
                    return@addSnapshotListener
                }

                val activeOrders = snapshot.documents
                    .mapNotNull { doc ->
                        try {
                            Order.from(doc)
                        } catch (e: Exception) {
                            Log.e("OrderVM", "Failed to parse active order: ${doc.id}", e)
                            null
                        }
                    }
                    .filter { order ->
                        order.pickupDate == today
                    }
                    .sortedByDescending { it.createdAt }

                activeOrders.forEach { order ->
                    if (order.orderId.isNotBlank()) {
                        val previousStateKey = lastKnownOrderStates[order.orderId]
                        val newStateKey = getOrderStateKey(order)

                        if (previousStateKey != null && previousStateKey != newStateKey) {
                            notifyForOrderStateChange(
                                order = order,
                                previousStateKey = previousStateKey
                            )
                        }

                        lastKnownOrderStates[order.orderId] = newStateKey
                    }
                }

                _activeOrders.value = activeOrders
                _activeOrder.value = activeOrders.firstOrNull()
            }
    }

    fun clearActiveOrder() {
        _activeOrder.value = null
        _activeOrders.value = emptyList()
    }

    fun loadUserOrders(userId: String) {
        if (userId.isBlank()) {
            _userOrders.value = emptyList()
            return
        }

        userOrdersListener?.remove()

        userOrdersListener = firestore.collection("orders")
            .whereEqualTo("studentId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("OrderVM", "User orders listener error", error)
                    _userOrders.value = emptyList()
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    _userOrders.value = emptyList()
                    return@addSnapshotListener
                }

                val orders = snapshot.documents.mapNotNull { doc ->
                    try {
                        Order.from(doc)
                    } catch (e: Exception) {
                        Log.e("OrderVM", "Failed to parse user order: ${doc.id}", e)
                        null
                    }
                }

                Log.d("OrderVM", "User orders updated: ${orders.size}")
                _userOrders.value = orders
            }
    }

    fun loadStudentOrdersFirstPage(userId: String) {
        if (userId.isBlank()) return

        studentLastDoc = null
        _studentHistoryState.value = PagedOrderState(isLoading = true)

        viewModelScope.launch {
            val result = orderRepository.getStudentOrdersPaged(
                userId = userId,
                lastVisible = null
            )

            result.fold(
                onSuccess = { (orders, lastDoc) ->
                    studentLastDoc = lastDoc

                    _studentHistoryState.value = PagedOrderState(
                        orders = orders,
                        isLoading = false,
                        hasMore = orders.size >= OrderRepository.PAGE_SIZE.toInt(),
                        error = null
                    )
                },
                onFailure = { e ->
                    _studentHistoryState.value = PagedOrderState(
                        orders = emptyList(),
                        isLoading = false,
                        hasMore = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun loadMoreStudentOrders(userId: String) {
        val current = _studentHistoryState.value

        if (!current.hasMore || current.isLoading || userId.isBlank()) return

        _studentHistoryState.value = current.copy(isLoading = true)

        viewModelScope.launch {
            val result = orderRepository.getStudentOrdersPaged(
                userId = userId,
                lastVisible = studentLastDoc
            )

            result.fold(
                onSuccess = { (newOrders, lastDoc) ->
                    studentLastDoc = lastDoc

                    _studentHistoryState.value = current.copy(
                        orders = current.orders + newOrders,
                        isLoading = false,
                        hasMore = newOrders.size >= OrderRepository.PAGE_SIZE.toInt(),
                        error = null
                    )
                },
                onFailure = { e ->
                    _studentHistoryState.value = current.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun loadShopOrdersFirstPage(shopId: String) {
        if (shopId.isBlank()) return

        shopLastDoc = null
        _shopHistoryState.value = PagedOrderState(isLoading = true)

        viewModelScope.launch {
            val result = orderRepository.getShopOrdersPaged(
                shopId = shopId,
                lastVisible = null
            )

            result.fold(
                onSuccess = { (orders, lastDoc) ->
                    shopLastDoc = lastDoc

                    _shopHistoryState.value = PagedOrderState(
                        orders = orders,
                        isLoading = false,
                        hasMore = orders.size >= OrderRepository.PAGE_SIZE.toInt(),
                        error = null
                    )
                },
                onFailure = { e ->
                    _shopHistoryState.value = PagedOrderState(
                        orders = emptyList(),
                        isLoading = false,
                        hasMore = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun loadMoreShopOrders(shopId: String) {
        val current = _shopHistoryState.value

        if (!current.hasMore || current.isLoading || shopId.isBlank()) return

        _shopHistoryState.value = current.copy(isLoading = true)

        viewModelScope.launch {
            val result = orderRepository.getShopOrdersPaged(
                shopId = shopId,
                lastVisible = shopLastDoc
            )

            result.fold(
                onSuccess = { (newOrders, lastDoc) ->
                    shopLastDoc = lastDoc

                    _shopHistoryState.value = current.copy(
                        orders = current.orders + newOrders,
                        isLoading = false,
                        hasMore = newOrders.size >= OrderRepository.PAGE_SIZE.toInt(),
                        error = null
                    )
                },
                onFailure = { e ->
                    _shopHistoryState.value = current.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun resetState() {
        _orderState.value = OrderState.Idle
    }

    fun loadShop(shopId: String) {
        viewModelScope.launch {
            try {
                if (shopId.isBlank()) {
                    _selectedShop.value = null
                    return@launch
                }

                val shopDoc = getShopDocumentByIdOrField(shopId)

                _selectedShop.value = if (shopDoc != null && shopDoc.exists()) {
                    buildShopFromSnapshot(shopDoc)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Failed to load shop", e)
                _selectedShop.value = null
            }
        }
    }

    fun setSelectedShop(shop: Shop?) {
        _selectedShop.value = shop
    }

    fun loadAvailableSlots(
        shopId: String,
        cartPrepTimeMinutes: Int
    ) {
        viewModelScope.launch {
            _slotUiState.value = SlotUiState(isLoading = true)

            try {
                if (shopId.isBlank()) {
                    _slotUiState.value = SlotUiState(message = "Shop ID is missing.")
                    return@launch
                }

                val shopDoc = getShopDocumentByIdOrField(shopId)

                if (shopDoc == null || !shopDoc.exists()) {
                    _slotUiState.value = SlotUiState(message = "Shop details not found.")
                    return@launch
                }

                val shop = buildShopFromSnapshot(shopDoc)
                val canonicalShopId = shopDoc.id
                val maxOrdersPerSlot = getMaxOrdersPerSlot(shopDoc)
                val closedSlots = getClosedSlots(shopDoc)

                _selectedShop.value = shop

                if (!isShopAcceptingOrders(shopDoc, shop)) {
                    Log.w(
                        "OrderVM",
                        "Cannot load slots. Shop not accepting orders: docId=${shopDoc.id}, " +
                                "fieldShopId=${shopDoc.getString("shopId")}, " +
                                "isOpen=${shop.isOpen}, " +
                                "isApproved=${shop.isApproved}, " +
                                "isBlocked=${shop.isBlocked}, " +
                                "isDeleted=${shop.isDeleted}, " +
                                "isVisible=${shopDoc.getBoolean("isVisible")}"
                    )

                    _slotUiState.value = SlotUiState(
                        message = "This shop is currently not accepting orders."
                    )
                    return@launch
                }

                val now = LocalDateTime.now()
                val timeWindow = getShopTimeWindow(shop, now)
                val openingDateTime = timeWindow.openingDateTime
                val closingDateTime = timeWindow.closingDateTime

                if (now.isBefore(openingDateTime) || !now.isBefore(closingDateTime)) {
                    _slotUiState.value = SlotUiState(message = getClosedMessage(shop))
                    return@launch
                }

                val displayFormatter = DateTimeFormatter.ofPattern("hh:mm a")
                val today = LocalDate.now().toString()
                val earliestTime = now.plusMinutes(cartPrepTimeMinutes.toLong())

                var slot = roundToNextSlotDateTime(
                    time = if (earliestTime.isAfter(openingDateTime)) {
                        earliestTime
                    } else {
                        openingDateTime
                    },
                    intervalMinutes = 15
                )

                val maxWindowEnd = now.plusHours(3)

                val endTime = if (closingDateTime.isBefore(maxWindowEnd)) {
                    closingDateTime
                } else {
                    maxWindowEnd
                }

                val generatedSlots = mutableListOf<String>()

                while (slot.isBefore(endTime)) {
                    generatedSlots.add(slot.toLocalTime().format(displayFormatter))
                    slot = slot.plusMinutes(15)
                }

                if (generatedSlots.isEmpty()) {
                    _slotUiState.value = SlotUiState(
                        message = "No pickup slots are available right now. Please try again later."
                    )
                    return@launch
                }

                val availableSlots = mutableListOf<String>()

                for (slotText in generatedSlots) {
                    val availability = slotAvailabilityRepository.getSlotAvailability(
                        shopId = canonicalShopId,
                        date = today,
                        slot = slotText,
                        maxOrders = maxOrdersPerSlot
                    )

                    val closed = closedSlots.contains(slotText) || availability.isClosed
                    val hasCapacity = availability.orderCount < availability.maxOrders

                    if (!closed && hasCapacity) {
                        availableSlots.add(slotText)
                    }
                }

                _slotUiState.value = SlotUiState(
                    slots = availableSlots,
                    message = when {
                        availableSlots.isEmpty() -> {
                            "All pickup slots are full or temporarily unavailable."
                        }

                        closingDateTime.isBefore(maxWindowEnd) -> {
                            "Slots are shown only until shop closing time: ${formatShopTime(shop.closingTime)}."
                        }

                        else -> ""
                    }
                )
            } catch (e: Exception) {
                Log.e("OrderVM", "Slot loading failed", e)
                _slotUiState.value = SlotUiState(
                    message = "Failed to load pickup slots. ${e.message}"
                )
            }
        }
    }

    private suspend fun getShopDocumentByIdOrField(shopId: String): DocumentSnapshot? {
        val directDoc = firestore.collection("shops")
            .document(shopId)
            .get()
            .await()

        if (directDoc.exists()) {
            return directDoc
        }

        return firestore.collection("shops")
            .whereEqualTo("shopId", shopId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
    }

    private fun buildShopFromSnapshot(snapshot: DocumentSnapshot): Shop {
        return Shop(
            shopId = snapshot.id,
            name = snapshot.getString("name") ?: "",
            description = snapshot.getString("description") ?: "",
            imageUrl = snapshot.getString("imageUrl") ?: "",
            isOpen = snapshot.getBoolean("isOpen") ?: false,
            isApproved = snapshot.getBoolean("isApproved") ?: false,
            isBlocked = snapshot.getBoolean("isBlocked") ?: false,
            isDeleted = snapshot.getBoolean("isDeleted") ?: false,
            openingTime = snapshot.getString("openingTime") ?: "08:00",
            closingTime = snapshot.getString("closingTime") ?: "21:00",
            upiId = snapshot.getString("upiId") ?: "",
            phone = snapshot.getString("phone")
                ?: snapshot.getString("ownerPhone")
                ?: ""
        )
    }
    private fun isShopAcceptingOrders(
        snapshot: DocumentSnapshot,
        shop: Shop
    ): Boolean {
        val isVisible = snapshot.getBoolean("isVisible") ?: true

        return shop.isApproved &&
                shop.isOpen &&
                !shop.isBlocked &&
                !shop.isDeleted &&
                isVisible
    }

    private fun getMaxOrdersPerSlot(snapshot: DocumentSnapshot): Int {
        val value = snapshot.getLong("maxOrdersPerSlot")?.toInt()
        return if (value != null && value > 0) value else 5
    }

    private fun getClosedSlots(snapshot: DocumentSnapshot): List<String> {
        return (snapshot.get("closedSlots") as? List<*>)
            ?.filterIsInstance<String>()
            ?: emptyList()
    }
    private fun getOrderStateKey(order: Order): String {
        return "${order.status.lowercase()}|${order.refundStatus.lowercase()}"
    }

    private fun notifyForOrderStateChange(
        order: Order,
        previousStateKey: String?
    ) {
        val orderId = order.orderId

        if (orderId.isBlank()) {
            return
        }

        val newStateKey = getOrderStateKey(order)

        if (previousStateKey == null || previousStateKey == newStateKey) {
            return
        }

        if (lastNotifiedOrderStates[orderId] == newStateKey) {
            return
        }

        val status = order.status.lowercase()
        val refundStatus = order.refundStatus.lowercase()

        when {
            status == OrderStatusValue.PREPARING -> {
                showOrderStatusNotification(
                    orderId = orderId,
                    title = "Order Accepted",
                    message = "Your order has been accepted and is being prepared."
                )
            }

            status == OrderStatusValue.READY -> {
                showOrderStatusNotification(
                    orderId = orderId,
                    title = "Order Ready",
                    message = "Your food is ready for pickup."
                )
            }

            status == OrderStatusValue.CANCELLED &&
                    refundStatus == RefundStatusValue.REFUND_PENDING -> {
                showOrderStatusNotification(
                    orderId = orderId,
                    title = "Order Cancelled - Refund Pending",
                    message = "Your payment was received by the shopkeeper. Refund will be settled manually."
                )
            }

            status == OrderStatusValue.CANCELLED &&
                    refundStatus == RefundStatusValue.REFUNDED -> {
                showOrderStatusNotification(
                    orderId = orderId,
                    title = "Refund Settled",
                    message = "The shopkeeper has marked your refund as settled."
                )
            }

            status == OrderStatusValue.CANCELLED -> {
                showOrderStatusNotification(
                    orderId = orderId,
                    title = "Order Cancelled",
                    message = "Your order was cancelled because payment was not received."
                )
            }
        }

        lastNotifiedOrderStates[orderId] = newStateKey
    }

    private fun showOrderStatusNotification(
        orderId: String,
        title: String,
        message: String
    ) {
        val channelId = "order_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Order Updates",
                NotificationManager.IMPORTANCE_HIGH
            )

            appContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (canPost) {
            NotificationManagerCompat.from(appContext)
                .notify("${orderId}_$title".hashCode(), notification)
        }
    }

    fun setError(message: String) {
        _orderState.value = OrderState.Error(message)
    }

    private fun getShopTimeWindow(
        shop: Shop,
        now: LocalDateTime = LocalDateTime.now()
    ): ShopTimeWindow {
        val openingTime = parseShopTime(
            value = shop.openingTime,
            fallback = LocalTime.of(8, 0)
        )

        val closingTime = parseShopTime(
            value = shop.closingTime,
            fallback = LocalTime.of(21, 0)
        )

        var openingDateTime = LocalDateTime.of(now.toLocalDate(), openingTime)
        var closingDateTime = LocalDateTime.of(now.toLocalDate(), closingTime)

        val isOvernight = !closingTime.isAfter(openingTime)

        if (isOvernight) {
            if (now.toLocalTime().isBefore(closingTime)) {
                openingDateTime = openingDateTime.minusDays(1)
            } else {
                closingDateTime = closingDateTime.plusDays(1)
            }
        }

        return ShopTimeWindow(
            openingDateTime = openingDateTime,
            closingDateTime = closingDateTime
        )
    }

    private fun isWithinWorkingHours(shop: Shop): Boolean {
        val now = LocalDateTime.now()
        val window = getShopTimeWindow(shop, now)

        return !now.isBefore(window.openingDateTime) &&
                now.isBefore(window.closingDateTime)
    }

    private fun parseShopTime(
        value: String,
        fallback: LocalTime
    ): LocalTime {
        return try {
            val cleanValue = value.trim().replace("\"", "")

            LocalTime.parse(
                cleanValue.ifBlank {
                    fallback.format(DateTimeFormatter.ofPattern("HH:mm"))
                },
                DateTimeFormatter.ofPattern("HH:mm")
            )
        } catch (e: Exception) {
            fallback
        }
    }

    private fun roundToNextSlotDateTime(
        time: LocalDateTime,
        intervalMinutes: Int
    ): LocalDateTime {
        val remainder = time.minute % intervalMinutes
        val minutesToAdd = if (remainder == 0) {
            0
        } else {
            intervalMinutes - remainder
        }

        return time
            .plusMinutes(minutesToAdd.toLong())
            .withSecond(0)
            .withNano(0)
    }

    private fun formatShopTime(timeValue: String): String {
        return try {
            val cleanValue = timeValue.trim().replace("\"", "")

            LocalTime.parse(
                cleanValue,
                DateTimeFormatter.ofPattern("HH:mm")
            ).format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) {
            timeValue.trim().replace("\"", "")
        }
    }

    private fun getClosedMessage(shop: Shop): String {
        return "This shop is currently closed. Orders are accepted between " +
                "${formatShopTime(shop.openingTime)} - ${formatShopTime(shop.closingTime)}."
    }

    override fun onCleared() {
        super.onCleared()
        activeOrderListener?.remove()
        currentOrderListener?.remove()
        userOrdersListener?.remove()
        shopAvailabilityListener?.remove()
    }
}
