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
    data class SlotUiState(
        val slots: List<String> = emptyList(),
        val message: String = "",
        val isLoading: Boolean = false
    )

    private val _slotUiState = MutableStateFlow(SlotUiState())
    val slotUiState: StateFlow<SlotUiState> = _slotUiState
    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    private val _currentOrder = MutableStateFlow<Order?>(null)
    val currentOrder: StateFlow<Order?> = _currentOrder

    private val _selectedShop = MutableStateFlow<Shop?>(null)
    val selectedShop: StateFlow<Shop?> = _selectedShop




    private val _userOrders = MutableStateFlow<List<Order>>(emptyList())
    val userOrders: StateFlow<List<Order>> = _userOrders

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
                    _orderState.value = OrderState.Error(
                        "This shop is currently not accepting orders."
                    )
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
                _orderState.value = OrderState.Error(
                    e.message ?: "Failed to place order"
                )
            }
        }
    }

    fun listenToOrderById(orderId: String) {
        orderRepository.listenToOrder(orderId) { order ->
            val previousStatus = _currentOrder.value?.status

            _currentOrder.value = order

            if (
                previousStatus != "ready" &&
                order?.status == "ready"
            ) {
                showOrderReadyNotification(orderId)
            }
        }
    }

    fun resetState() {
        _orderState.value = OrderState.Idle
    }

    fun loadAvailableSlots(
        shopId: String,
        cartPrepTimeMinutes: Int
    ) {
        viewModelScope.launch {

            _slotUiState.value = SlotUiState(isLoading = true)

            try {
                val shopSnapshot = firestore.collection("shops")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()

                val shop = shopSnapshot.documents
                    .firstOrNull()
                    ?.toObject(Shop::class.java)

                if (shop == null) {
                    _selectedShop.value = null

                    _slotUiState.value = SlotUiState(
                        message = "Shop details not found."
                    )

                    return@launch
                }

                if (!shop.isOpen) {
                    _selectedShop.value = shop

                    _slotUiState.value = SlotUiState(
                        message = "This shop is currently closed."
                    )

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

                val shopClosingDateTime = getShopClosingDateTime(
                    now = now,
                    closingTime = shop.closingTime,
                    formatter = closingFormatter
                )

                val endTime = if (shopClosingDateTime.isBefore(maxWindowEnd)) {
                    shopClosingDateTime
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
                        message = "Shop closing time is ${
                            formatClosingTime(shop.closingTime)
                        }. No further pickup slots are available."
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

                    if (
                        !shop.closedSlots.contains(slotText) &&
                        count < shop.maxOrdersPerSlot
                    ) {
                        available.add(slotText)
                    }
                }

                _slotUiState.value = SlotUiState(
                    slots = available,
                    message = when {
                        available.isEmpty() -> {
                            "All pickup slots are full, closed, or unavailable right now."
                        }

                        shopClosingDateTime.isBefore(maxWindowEnd) -> {
                            "Shop closing time is ${
                                formatClosingTime(shop.closingTime)
                            }. Slots after that are unavailable."
                        }

                        else -> {
                            ""
                        }
                    }
                )

            } catch (e: Exception) {
                e.printStackTrace()

                _slotUiState.value = SlotUiState(
                    message = "Failed to load pickup slots."
                )

            } finally {
                if (_slotUiState.value.isLoading) {
                    _slotUiState.value = _slotUiState.value.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun roundToNextSlotDateTime(
        time: LocalDateTime,
        intervalMinutes: Int
    ): LocalDateTime {
        val minute = time.minute
        val remainder = minute % intervalMinutes
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

        var closingDateTime = LocalDateTime.of(
            now.toLocalDate(),
            closingLocalTime
        )

        if (closingDateTime.isBefore(now)) {
            closingDateTime = closingDateTime.plusDays(1)
        }

        return closingDateTime
    }

    private fun formatClosingTime(closingTime: String): String {
        return try {
            val inputFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val outputFormatter = DateTimeFormatter.ofPattern("hh:mm a")

            val time = LocalTime.parse(closingTime, inputFormatter)
            time.format(outputFormatter)

        } catch (e: Exception) {
            closingTime
        }
    }
    fun loadUserOrders(userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("orders")
                    .whereEqualTo("studentId", userId)
                    .get()
                    .await()

                val orders = snapshot.documents.mapNotNull {
                    it.toObject(Order::class.java)
                }.sortedByDescending {
                    it.createdAt
                }

                _userOrders.value = orders

            } catch (e: Exception) {
                e.printStackTrace()
                _userOrders.value = emptyList()
            }
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

            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Order Ready 🎉")
            .setContentText("Your food is ready for pickup.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(appContext)
                    .notify(orderId.hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(appContext)
                .notify(orderId.hashCode(), notification)
        }
    }
}
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
