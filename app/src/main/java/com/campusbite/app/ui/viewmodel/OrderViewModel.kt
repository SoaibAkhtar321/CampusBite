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
import com.campusbite.app.data.repository.SlotAvailabilityRepository

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

    private val _selectedShop = MutableStateFlow<Shop?>(null)
    val selectedShop: StateFlow<Shop?> = _selectedShop

    private val _userOrders = MutableStateFlow<List<Order>>(emptyList())
    val userOrders: StateFlow<List<Order>> = _userOrders

    private var activeOrderListener: ListenerRegistration? = null
    private var currentOrderListener: ListenerRegistration? = null
    private var shopAvailabilityListener: ListenerRegistration? = null

    private data class ShopTimeWindow(
        val openingDateTime: LocalDateTime,
        val closingDateTime: LocalDateTime
    )

    fun listenToShopAvailability(shopId: String) {
        shopAvailabilityListener?.remove()

        if (shopId.isBlank()) {
            _selectedShop.value = null
            _slotUiState.value = SlotUiState(
                slots = emptyList(),
                message = "Shop ID is missing.",
                isLoading = false
            )
            return
        }

        viewModelScope.launch {
            try {
                val actualDoc = getShopDocumentByIdOrField(shopId)

                if (actualDoc == null || !actualDoc.exists()) {
                    _selectedShop.value = null
                    _slotUiState.value = SlotUiState(
                        slots = emptyList(),
                        message = "Shop details not found.",
                        isLoading = false
                    )
                    return@launch
                }

                shopAvailabilityListener = firestore.collection("shops")
                    .document(actualDoc.id)
                    .addSnapshotListener { snapshot, error ->

                        if (error != null) {
                            Log.e("OrderVM", "Shop availability listener failed", error)

                            _selectedShop.value = null
                            _slotUiState.value = SlotUiState(
                                slots = emptyList(),
                                message = "Failed to check shop availability.",
                                isLoading = false
                            )
                            return@addSnapshotListener
                        }

                        if (snapshot == null || !snapshot.exists()) {
                            _selectedShop.value = null
                            _slotUiState.value = SlotUiState(
                                slots = emptyList(),
                                message = "Shop details not found.",
                                isLoading = false
                            )
                            return@addSnapshotListener
                        }

                        val shop = buildShopFromSnapshot(snapshot)

                        val isAcceptingOrders =
                            shop.isOpen &&
                                    !shop.isBlocked &&
                                    !shop.isDeleted

                        _selectedShop.value = shop

                        if (!isAcceptingOrders) {
                            _slotUiState.value = SlotUiState(
                                slots = emptyList(),
                                message = "This shop is currently not accepting orders.",
                                isLoading = false
                            )
                            return@addSnapshotListener
                        }

                        if (!isWithinWorkingHours(shop)) {
                            _slotUiState.value = SlotUiState(
                                slots = emptyList(),
                                message = getClosedMessage(shop),
                                isLoading = false
                            )
                        }
                    }

            } catch (e: Exception) {
                Log.e("OrderVM", "Failed to start shop availability listener", e)

                _selectedShop.value = null
                _slotUiState.value = SlotUiState(
                    slots = emptyList(),
                    message = "Failed to check shop availability.",
                    isLoading = false
                )
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
                    _orderState.value = OrderState.Error("Shop not found.")
                    return@launch
                }

                val shop = buildShopFromSnapshot(shopDoc)

                val isAcceptingOrders =
                    shop.isOpen &&
                            !shop.isBlocked &&
                            !shop.isDeleted

                if (!isAcceptingOrders) {
                    _orderState.value = OrderState.Error(
                        "This shop is currently not accepting orders."
                    )
                    return@launch
                }

                if (!isWithinWorkingHours(shop)) {
                    _orderState.value = OrderState.Error(
                        "This shop is currently closed."
                    )
                    return@launch
                }

                val slotAvailability = slotAvailabilityRepository.getSlotAvailability(
                    shopId = order.shopId,
                    date = order.pickupDate,
                    slot = order.pickupSlot,
                    maxOrders = shop.maxOrdersPerSlot
                )

                val isSlotClosed =
                    slotAvailability.isClosed ||
                            shop.closedSlots.contains(order.pickupSlot)

                val isSlotFull =
                    slotAvailability.orderCount >= slotAvailability.maxOrders

                if (isSlotClosed || isSlotFull) {
                    _orderState.value = OrderState.Error(
                        "This pickup slot is no longer available. Please select another slot."
                    )
                    return@launch
                }

                val result = orderRepository.placeOrder(order)

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

                _orderState.value = OrderState.Error(
                    e.message ?: "Failed to place order"
                )
            }
        }
    }

    fun listenToOrderById(orderId: String) {
        if (orderId.isBlank()) return

        currentOrderListener?.remove()

        currentOrderListener = orderRepository.listenToOrder(orderId) { order ->
            val previousStatus = _currentOrder.value?.status
            val newStatus = order?.status

            _currentOrder.value = order

            if (previousStatus == null || newStatus == null) {
                return@listenToOrder
            }

            if (previousStatus != newStatus) {
                when (newStatus) {
                    "preparing" -> {
                        showOrderStatusNotification(
                            orderId = orderId,
                            title = "Order Accepted 🍳",
                            message = "Your order has been accepted and is being prepared."
                        )
                    }

                    "ready" -> {
                        showOrderStatusNotification(
                            orderId = orderId,
                            title = "Order Ready 🎉",
                            message = "Your food is ready for pickup."
                        )
                    }
                }
            }
        }
    }

    fun listenToActiveOrder() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listenToActiveOrder(uid)
    }

    fun listenToActiveOrder(userId: String) {
        if (userId.isBlank()) return

        activeOrderListener?.remove()

        activeOrderListener = firestore.collection("orders")
            .whereEqualTo("studentId", userId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.e("OrderVM", "Active order listener error", error)
                    _activeOrder.value = null
                    return@addSnapshotListener
                }

                val allOrders = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e("OrderVM", "Failed to parse order", e)
                            null
                        }
                    }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()

                _userOrders.value = allOrders

                val activeStatuses = listOf(
                    "pending",
                    "accepted",
                    "preparing",
                    "ready"
                )

                _activeOrder.value = allOrders.firstOrNull { order ->
                    order.status.lowercase() in activeStatuses
                }
            }
    }

    fun clearActiveOrder() {
        _activeOrder.value = null
    }

    fun loadUserOrders(userId: String) {
        viewModelScope.launch {
            try {
                if (userId.isBlank()) {
                    _userOrders.value = emptyList()
                    return@launch
                }

                val snapshot = firestore.collection("orders")
                    .whereEqualTo("studentId", userId)
                    .get()
                    .await()

                _userOrders.value = snapshot.documents
                    .mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e("OrderVM", "Failed to parse user order", e)
                            null
                        }
                    }
                    .sortedByDescending { it.createdAt }

            } catch (e: Exception) {
                Log.e("OrderVM", "Failed to load user orders", e)
                _userOrders.value = emptyList()
            }
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

                _selectedShop.value =
                    if (shopDoc != null && shopDoc.exists()) {
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
            _slotUiState.value = SlotUiState(
                slots = emptyList(),
                message = "",
                isLoading = true
            )

            try {
                if (shopId.isBlank()) {
                    _selectedShop.value = null
                    _slotUiState.value = SlotUiState(
                        slots = emptyList(),
                        message = "Shop ID is missing.",
                        isLoading = false
                    )
                    return@launch
                }

                val shopDoc = getShopDocumentByIdOrField(shopId)

                if (shopDoc == null || !shopDoc.exists()) {
                    _selectedShop.value = null
                    _slotUiState.value = SlotUiState(
                        slots = emptyList(),
                        message = "Shop details not found.",
                        isLoading = false
                    )
                    return@launch
                }

                val shop = buildShopFromSnapshot(shopDoc)
                _selectedShop.value = shop

                val isAcceptingOrders =
                    shop.isOpen &&
                            !shop.isBlocked &&
                            !shop.isDeleted

                if (!isAcceptingOrders) {
                    _slotUiState.value = SlotUiState(
                        slots = emptyList(),
                        message = "This shop is currently not accepting orders.",
                        isLoading = false
                    )
                    return@launch
                }

                val now = LocalDateTime.now()
                val timeWindow = getShopTimeWindow(
                    shop = shop,
                    now = now
                )

                val openingDateTime = timeWindow.openingDateTime
                val closingDateTime = timeWindow.closingDateTime

                val isWithinWorkingHours =
                    !now.isBefore(openingDateTime) &&
                            now.isBefore(closingDateTime)

                if (!isWithinWorkingHours) {
                    _slotUiState.value = SlotUiState(
                        slots = emptyList(),
                        message = getClosedMessage(shop),
                        isLoading = false
                    )
                    return@launch
                }

                val displayFormatter = DateTimeFormatter.ofPattern("hh:mm a")
                val today = LocalDate.now().toString()

                val earliestTime = now.plusMinutes(
                    cartPrepTimeMinutes.toLong()
                )

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
                    generatedSlots.add(
                        slot.toLocalTime().format(displayFormatter)
                    )

                    slot = slot.plusMinutes(15)
                }

                if (generatedSlots.isEmpty()) {
                    _slotUiState.value = SlotUiState(
                        slots = emptyList(),
                        message = "No pickup slots are available right now. Please try again later.",
                        isLoading = false
                    )
                    return@launch
                }

                val availableSlots = mutableListOf<String>()

                for (slotText in generatedSlots) {
                    val slotAvailability =
                        slotAvailabilityRepository.getSlotAvailability(
                            shopId = shop.shopId,
                            date = today,
                            slot = slotText,
                            maxOrders = shop.maxOrdersPerSlot
                        )

                    val isSlotClosedByShop =
                        shop.closedSlots.contains(slotText) || slotAvailability.isClosed

                    val hasCapacity =
                        slotAvailability.orderCount < slotAvailability.maxOrders

                    if (!isSlotClosedByShop && hasCapacity) {
                        availableSlots.add(slotText)
                    }
                }

                _slotUiState.value = SlotUiState(
                    slots = availableSlots,
                    message = when {
                        availableSlots.isEmpty() ->
                            "All pickup slots are full or temporarily unavailable."

                        closingDateTime.isBefore(maxWindowEnd) ->
                            "Slots are shown only until shop closing time: ${
                                formatShopTime(shop.closingTime)
                            }."

                        else -> ""
                    },
                    isLoading = false
                )

            }  catch (e: Exception) {
            Log.e("OrderVM", "Slot loading failed", e)

            _slotUiState.value = SlotUiState(
                slots = emptyList(),
                message = "Failed to load pickup slots. ${e.message}",
                isLoading = false
            )

            }
        }
    }

    private suspend fun getShopDocumentByIdOrField(
        shopId: String
    ): DocumentSnapshot? {
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

    private fun buildShopFromSnapshot(
        snapshot: DocumentSnapshot
    ): Shop {
        return Shop(
            shopId = snapshot.getString("shopId") ?: snapshot.id,
            name = snapshot.getString("name") ?: "",
            description = snapshot.getString("description") ?: "",
            imageUrl = snapshot.getString("imageUrl") ?: "",

            isOpen = snapshot.getBoolean("isOpen") ?: false,
            isApproved = snapshot.getBoolean("isApproved") ?: false,
            isBlocked = snapshot.getBoolean("isBlocked") ?: false,
            isDeleted = snapshot.getBoolean("isDeleted") ?: false,

            openingTime = snapshot.getString("openingTime") ?: "08:00",
            closingTime = snapshot.getString("closingTime") ?: "21:00",

            maxOrdersPerSlot =
                snapshot.getLong("maxOrdersPerSlot")?.toInt() ?: 5,

            closedSlots =
                (snapshot.get("closedSlots") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?: emptyList(),

            upiId = snapshot.getString("upiId") ?: "",

            phone = snapshot.getString("phone")
                ?: snapshot.getString("ownerPhone")
                ?: "",

            ownerUid = snapshot.getString("ownerUid") ?: "",
            ownerEmail = snapshot.getString("ownerEmail") ?: "",
            ownerPhone = snapshot.getString("ownerPhone") ?: "",

            createdAt = snapshot.getLong("createdAt") ?: 0L
        )
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

        var openingDateTime = LocalDateTime.of(
            now.toLocalDate(),
            openingTime
        )

        var closingDateTime = LocalDateTime.of(
            now.toLocalDate(),
            closingTime
        )

        val isOvernightTiming =
            !closingTime.isAfter(openingTime)

        if (isOvernightTiming) {
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

        val timeWindow = getShopTimeWindow(
            shop = shop,
            now = now
        )

        return !now.isBefore(timeWindow.openingDateTime) &&
                now.isBefore(timeWindow.closingDateTime)
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

        val minutesToAdd =
            if (remainder == 0) {
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

            val time = LocalTime.parse(
                cleanValue,
                DateTimeFormatter.ofPattern("HH:mm")
            )

            time.format(
                DateTimeFormatter.ofPattern("hh:mm a")
            )
        } catch (e: Exception) {
            timeValue.trim().replace("\"", "")
        }
    }

    private fun getClosedMessage(shop: Shop): String {
        return "This shop is currently closed. Orders are accepted between ${
            formatShopTime(shop.openingTime)
        } - ${
            formatShopTime(shop.closingTime)
        }."
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

            val notificationManager = appContext.getSystemService(
                NotificationManager::class.java
            )

            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                NotificationManagerCompat.from(appContext)
                    .notify("${orderId}_$title".hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(appContext)
                .notify("${orderId}_$title".hashCode(), notification)
        }
    }

    fun setError(message: String) {
        _orderState.value = OrderState.Error(message)
    }

    override fun onCleared() {
        super.onCleared()
        activeOrderListener?.remove()
        currentOrderListener?.remove()
        shopAvailabilityListener?.remove()
    }
}
