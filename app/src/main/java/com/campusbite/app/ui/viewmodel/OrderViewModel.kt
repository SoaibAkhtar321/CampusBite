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

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
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

    fun placeOrder(order: Order) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading

            try {
                Log.d("OrderVM", "placeOrder() called")
                Log.d("OrderVM", "shopId: ${order.shopId}")
                Log.d("OrderVM", "items count: ${order.items.size}")
                Log.d("OrderVM", "pickupSlot: ${order.pickupSlot}")
                Log.d("OrderVM", "totalPrice: ${order.totalPrice}")

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

                val shopDoc = firestore.collection("shops")
                    .document(order.shopId)
                    .get()
                    .await()

                if (!shopDoc.exists()) {
                    _orderState.value = OrderState.Error("Shop not found.")
                    return@launch
                }

                val isOpen = shopDoc.getBoolean("isOpen") ?: false

                if (!isOpen) {
                    _orderState.value = OrderState.Error("This shop is currently not accepting orders.")
                    return@launch
                }

                val result = orderRepository.placeOrder(order)

                if (result.isSuccess) {
                    val orderId = result.getOrNull().orEmpty()

                    Log.d("OrderVM", "Order placed successfully: $orderId")

                    _orderState.value = OrderState.Success(orderId)
                    listenToOrderById(orderId)
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Failed to place order"

                    Log.e("OrderVM", "Order failed: $message")

                    _orderState.value = OrderState.Error(message)
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

        orderRepository.listenToOrder(orderId) { order ->
            val previousStatus = _currentOrder.value?.status

            _currentOrder.value = order

            if (previousStatus != "ready" && order?.status == "ready") {
                showOrderReadyNotification(orderId)
            }
        }
    }

    fun listenToActiveOrder() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listenToActiveOrder(uid)
    }

    fun listenToActiveOrder(userId: String) {
        activeOrderListener?.remove()

        activeOrderListener = firestore.collection("orders")
            .whereEqualTo("studentId", userId)
            .whereIn("status", listOf("pending", "accepted", "preparing", "ready"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("OrderVM", "Active order listener error", error)
                    _activeOrder.value = null
                    return@addSnapshotListener
                }

                val latestOrder = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e("OrderVM", "Failed to parse active order", e)
                            null
                        }
                    }
                    ?.maxByOrNull { it.createdAt }

                _activeOrder.value = latestOrder
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

                val shopDoc = firestore.collection("shops")
                    .document(shopId)
                    .get()
                    .await()

                val shop = shopDoc.toObject(Shop::class.java)

                _selectedShop.value = shop

                Log.d("OrderVM", "Loaded shop: ${shop?.name}, isOpen: ${shop?.isOpen}")

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
                    _selectedShop.value = null
                    _slotUiState.value = SlotUiState(message = "Shop ID is missing.")
                    return@launch
                }

                val shopDoc = firestore.collection("shops")
                    .document(shopId)
                    .get()
                    .await()

                Log.d("OrderVM", "shopDoc exists: ${shopDoc.exists()}")
                Log.d("OrderVM", "shopDoc data: ${shopDoc.data}")

                val shop = shopDoc.toObject(Shop::class.java)

                Log.d("OrderVM", "shop deserialized: ${shop?.name ?: "NULL"}")

                if (shop == null) {
                    _selectedShop.value = null
                    _slotUiState.value = SlotUiState(message = "Shop details not found.")
                    return@launch
                }

                if (!shop.isOpen) {
                    _selectedShop.value = shop
                    _slotUiState.value = SlotUiState(message = "This shop is currently closed.")
                    return@launch
                }

                _selectedShop.value = shop

                val displayFormatter = DateTimeFormatter.ofPattern("hh:mm a")
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

                val today = LocalDate.now().toString()
                val now = LocalDateTime.now()

                val openingTime = parseShopTime(
                    value = shop.openingTime,
                    fallback = LocalTime.of(8, 0),
                    formatter = timeFormatter
                )

                val openingDateTime = LocalDateTime.of(
                    now.toLocalDate(),
                    openingTime
                )

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

                val shopClosingDateTime = getShopClosingDateTime(
                    now = now,
                    closingTime = shop.closingTime,
                    formatter = timeFormatter
                )

                val endTime = if (shopClosingDateTime.isBefore(maxWindowEnd)) {
                    shopClosingDateTime
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
                        message = "Shop opening time is ${formatShopTime(shop.openingTime)}. No pickup slots available yet."
                    )
                    return@launch
                }

                val availableSlots = mutableListOf<String>()

                for (slotText in generatedSlots) {
                    val orderCount = firestore.collection("orders")
                        .whereEqualTo("shopId", shopId)
                        .whereEqualTo("pickupDate", today)
                        .whereEqualTo("pickupSlot", slotText)
                        .get()
                        .await()
                        .size()

                    val isSlotClosedByShop = shop.closedSlots.contains(slotText)
                    val hasCapacity = orderCount < shop.maxOrdersPerSlot

                    if (!isSlotClosedByShop && hasCapacity) {
                        availableSlots.add(slotText)
                    }
                }

                _slotUiState.value = SlotUiState(
                    slots = availableSlots,
                    message = when {
                        availableSlots.isEmpty() -> {
                            "All pickup slots are full, closed, or unavailable right now."
                        }

                        shopClosingDateTime.isBefore(maxWindowEnd) -> {
                            "Shop closing time is ${formatShopTime(shop.closingTime)}. Slots after that are unavailable."
                        }

                        else -> ""
                    },
                    isLoading = false
                )

            } catch (e: Exception) {
                Log.e("OrderVM", "Failed to load pickup slots", e)

                _slotUiState.value = SlotUiState(
                    message = "Failed to load pickup slots.",
                    isLoading = false
                )
            }
        }
    }

    private fun parseShopTime(
        value: String,
        fallback: LocalTime,
        formatter: DateTimeFormatter
    ): LocalTime {
        return try {
            LocalTime.parse(value.ifBlank { fallback.format(formatter) }, formatter)
        } catch (e: Exception) {
            fallback
        }
    }

    private fun roundToNextSlotDateTime(
        time: LocalDateTime,
        intervalMinutes: Int
    ): LocalDateTime {
        val remainder = time.minute % intervalMinutes
        val minutesToAdd = if (remainder == 0) 0 else intervalMinutes - remainder

        return time
            .plusMinutes(minutesToAdd.toLong())
            .withSecond(0)
            .withNano(0)
    }

    private fun getShopClosingDateTime(
        now: LocalDateTime,
        closingTime: String,
        formatter: DateTimeFormatter
    ): LocalDateTime {
        val closingLocalTime = parseShopTime(
            value = closingTime,
            fallback = LocalTime.of(23, 59),
            formatter = formatter
        )

        var closingDateTime = LocalDateTime.of(
            now.toLocalDate(),
            closingLocalTime
        )

        if (closingDateTime.isBefore(now)) {
            closingDateTime = closingDateTime.plusDays(1)
        }

        return closingDateTime
    }

    private fun formatShopTime(timeValue: String): String {
        return try {
            val time = LocalTime.parse(
                timeValue,
                DateTimeFormatter.ofPattern("HH:mm")
            )

            time.format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) {
            timeValue
        }
    }

    private fun showOrderReadyNotification(orderId: String) {
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
            .setContentTitle("Order Ready 🎉")
            .setContentText("Your food is ready for pickup.")
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
                    .notify(orderId.hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(appContext)
                .notify(orderId.hashCode(), notification)
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeOrderListener?.remove()
    }
}