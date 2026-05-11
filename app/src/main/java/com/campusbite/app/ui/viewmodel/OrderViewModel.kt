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
import com.campusbite.app.data.model.Shop
import com.campusbite.app.data.repository.OrderRepository
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

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // ── Slot UI State ─────────────────────────────────────────────────────────
    data class SlotUiState(
        val slots: List<String> = emptyList(),
        val message: String = "",
        val isLoading: Boolean = false
    )

    private val _slotUiState = MutableStateFlow(SlotUiState())
    val slotUiState: StateFlow<SlotUiState> = _slotUiState

    // ── Order state ───────────────────────────────────────────────────────────
    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    // ── Current order (tracked by ID after placing) ───────────────────────────
    private val _currentOrder = MutableStateFlow<Order?>(null)
    val currentOrder: StateFlow<Order?> = _currentOrder

    // ── Active order (latest in-progress order for the logged-in student) ─────
    private val _activeOrder = MutableStateFlow<Order?>(null)
    val activeOrder: StateFlow<Order?> = _activeOrder
    private var activeOrderListener: ListenerRegistration? = null

    // ── Selected shop ─────────────────────────────────────────────────────────
    private val _selectedShop = MutableStateFlow<Shop?>(null)
    val selectedShop: StateFlow<Shop?> = _selectedShop

    // ── Order history ─────────────────────────────────────────────────────────
    private val _userOrders = MutableStateFlow<List<Order>>(emptyList())
    val userOrders: StateFlow<List<Order>> = _userOrders

    // ─────────────────────────────────────────────────────────────────────────
    // Place order
    // ─────────────────────────────────────────────────────────────────────────
    fun placeOrder(order: Order) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading
            try {
                val shopSnapshot = firestore.collection("shops")
                    .whereEqualTo("shopId", order.shopId)
                    .get()
                    .await()

                val shopDoc = shopSnapshot.documents.firstOrNull()
                val isOpen = shopDoc?.getBoolean("isOpen") ?: false

                if (!isOpen) {
                    _orderState.value = OrderState.Error("This shop is currently not accepting orders.")
                    return@launch
                }

                val result = orderRepository.placeOrder(order)
                if (result.isSuccess) {
                    val orderId = result.getOrNull() ?: ""
                    _orderState.value = OrderState.Success(orderId)
                    listenToOrderById(orderId)
                } else {
                    _orderState.value = OrderState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to place order"
                    )
                }
            } catch (e: Exception) {
                _orderState.value = OrderState.Error(e.message ?: "Failed to place order")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Listen to a specific order by ID (used on OrderStatusScreen)
    // ─────────────────────────────────────────────────────────────────────────
    fun listenToOrderById(orderId: String) {
        orderRepository.listenToOrder(orderId) { order ->
            val previousStatus = _currentOrder.value?.status
            _currentOrder.value = order
            if (previousStatus != "ready" && order?.status == "ready") {
                showOrderReadyNotification(orderId)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Listen to the latest active order for a student (HomeScreen banner &
    // ProfileScreen). No orderBy → no composite index needed. Sort client-side.
    // ─────────────────────────────────────────────────────────────────────────
    fun listenToActiveOrder(userId: String) {
        activeOrderListener?.remove()

        activeOrderListener = firestore.collection("orders")
            .whereEqualTo("studentId", userId)
            .whereIn("status", listOf("pending", "accepted", "preparing", "ready"))
            // ← NO .orderBy() — that combo requires a Firestore composite index
            //   which silently causes the query to return nothing if missing.
            //   We sort client-side instead.
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _activeOrder.value = null
                    return@addSnapshotListener
                }
                _activeOrder.value = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try { doc.toObject(Order::class.java) } catch (e: Exception) { null }
                    }
                    ?.maxByOrNull { it.createdAt } // latest active order
            }
    }

    fun clearActiveOrder() {
        _activeOrder.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load order history — NO orderBy to avoid composite index requirement.
    // Sort client-side by createdAt descending.
    // ─────────────────────────────────────────────────────────────────────────
    fun loadUserOrders(userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("orders")
                    .whereEqualTo("studentId", userId)
                    // ← removed: .orderBy("createdAt", Query.Direction.DESCENDING)
                    //   Firestore requires a composite index for whereEqualTo + orderBy
                    //   on different fields, and without it the query silently fails.
                    .get()
                    .await()

                _userOrders.value = snapshot.documents
                    .mapNotNull { doc ->
                        try { doc.toObject(Order::class.java) } catch (e: Exception) { null }
                    }
                    .sortedByDescending { it.createdAt } // sort client-side
            } catch (e: Exception) {
                e.printStackTrace()
                _userOrders.value = emptyList()
            }
        }
    }

    fun resetState() {
        _orderState.value = OrderState.Idle
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pickup slot loading
    // ─────────────────────────────────────────────────────────────────────────
    fun loadAvailableSlots(shopId: String, cartPrepTimeMinutes: Int) {
        viewModelScope.launch {
            _slotUiState.value = SlotUiState(isLoading = true)
            try {
                val shopSnapshot = firestore.collection("shops")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()

                val shop = shopSnapshot.documents.firstOrNull()?.toObject(Shop::class.java)

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
                val closingFormatter = DateTimeFormatter.ofPattern("HH:mm")
                val today = LocalDate.now().toString()
                val now = LocalDateTime.now()
                val earliestTime = now.plusMinutes(cartPrepTimeMinutes.toLong())
                var slot = roundToNextSlotDateTime(earliestTime, 15)
                val maxWindowEnd = now.plusHours(3)
                val shopClosingDateTime = getShopClosingDateTime(now, shop.closingTime, closingFormatter)
                val endTime = if (shopClosingDateTime.isBefore(maxWindowEnd)) shopClosingDateTime else maxWindowEnd

                val generatedSlots = mutableListOf<String>()
                while (slot.isBefore(endTime)) {
                    generatedSlots.add(slot.toLocalTime().format(displayFormatter))
                    slot = slot.plusMinutes(15)
                }

                if (generatedSlots.isEmpty()) {
                    _slotUiState.value = SlotUiState(
                        message = "Shop closing time is ${formatClosingTime(shop.closingTime)}. No further pickup slots are available."
                    )
                    return@launch
                }

                val available = mutableListOf<String>()
                for (slotText in generatedSlots) {
                    val count = firestore.collection("orders")
                        .whereEqualTo("shopId", shopId)
                        .whereEqualTo("pickupDate", today)
                        .whereEqualTo("pickupSlot", slotText)
                        .get()
                        .await()
                        .size()
                    if (!shop.closedSlots.contains(slotText) && count < shop.maxOrdersPerSlot) {
                        available.add(slotText)
                    }
                }

                _slotUiState.value = SlotUiState(
                    slots = available,
                    message = when {
                        available.isEmpty() -> "All pickup slots are full, closed, or unavailable right now."
                        shopClosingDateTime.isBefore(maxWindowEnd) ->
                            "Shop closing time is ${formatClosingTime(shop.closingTime)}. Slots after that are unavailable."
                        else -> ""
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _slotUiState.value = SlotUiState(message = "Failed to load pickup slots.")
            } finally {
                if (_slotUiState.value.isLoading) {
                    _slotUiState.value = _slotUiState.value.copy(isLoading = false)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun roundToNextSlotDateTime(time: LocalDateTime, intervalMinutes: Int): LocalDateTime {
        val remainder = time.minute % intervalMinutes
        val minutesToAdd = if (remainder == 0) 0 else intervalMinutes - remainder
        return time.plusMinutes(minutesToAdd.toLong()).withSecond(0).withNano(0)
    }

    private fun getShopClosingDateTime(
        now: LocalDateTime,
        closingTime: String,
        formatter: DateTimeFormatter
    ): LocalDateTime {
        val closingLocalTime = try {
            LocalTime.parse(closingTime, formatter)
        } catch (e: Exception) {
            LocalTime.of(23, 59)
        }
        var closingDateTime = LocalDateTime.of(now.toLocalDate(), closingLocalTime)
        if (closingDateTime.isBefore(now)) closingDateTime = closingDateTime.plusDays(1)
        return closingDateTime
    }

    private fun formatClosingTime(closingTime: String): String {
        return try {
            val time = LocalTime.parse(closingTime, DateTimeFormatter.ofPattern("HH:mm"))
            time.format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) {
            closingTime
        }
    }

    private fun showOrderReadyNotification(orderId: String) {
        val channelId = "order_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Order Updates", NotificationManager.IMPORTANCE_HIGH)
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Order Ready 🎉")
            .setContentText("Your food is ready for pickup.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(appContext).notify(orderId.hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(appContext).notify(orderId.hashCode(), notification)
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeOrderListener?.remove()
    }
}

// ─── Slot UI State (top-level for external use) ───────────────────────────────
data class SlotUiState(
    val slots: List<String> = emptyList(),
    val message: String = "",
    val isLoading: Boolean = false
)

// ─── Order state sealed class ─────────────────────────────────────────────────
sealed class OrderState {
    object Idle : OrderState()
    object Loading : OrderState()
    data class Success(val orderId: String) : OrderState()
    data class Error(val message: String) : OrderState()
}
