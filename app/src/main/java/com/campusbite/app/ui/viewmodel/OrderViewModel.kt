package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.Order
import com.campusbite.app.data.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.campusbite.app.data.model.Shop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
import okhttp3.internal.notify
import java.util.jar.Manifest


@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    private val _currentOrder = MutableStateFlow<Order?>(null)
    val currentOrder: StateFlow<Order?> = _currentOrder
    private val _userOrders = MutableStateFlow<List<Order>>(emptyList())
    val userOrders: StateFlow<List<Order>> = _userOrders
    private val _availableSlots = MutableStateFlow<List<String>>(emptyList())
    val availableSlots: StateFlow<List<String>> = _availableSlots

    private val _selectedShop = MutableStateFlow<Shop?>(null)
    val selectedShop: StateFlow<Shop?> = _selectedShop
    private val _isLoadingSlots = MutableStateFlow(false)
    val isLoadingSlots: StateFlow<Boolean> = _isLoadingSlots
    private val _slotMessage = MutableStateFlow("")
    val slotMessage: StateFlow<String> = _slotMessage

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
//    fun listenToOrderById(orderId: String) {
//        orderRepository.listenToOrder(orderId) { order ->
//            _currentOrder.value = order
//        }
//    }
    fun loadUserOrders() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("orders")
            .whereEqualTo("studentId", uid)
            .addSnapshotListener { snapshot, _ ->

                val list = snapshot?.documents?.mapNotNull {
                    it.toObject(Order::class.java)
                } ?: emptyList()

                _userOrders.value = list.sortedByDescending { it.createdAt }
            }
    }
    fun loadAvailableSlots(
        shopId: String,
        cartPrepTimeMinutes: Int
    ) {
        viewModelScope.launch {

            _isLoadingSlots.value = true
            _slotMessage.value = ""

            try {

                android.util.Log.d("SLOT_DEBUG", "======================")
                android.util.Log.d("SLOT_DEBUG", "shopId = $shopId")

                val shopSnapshot = firestore.collection("shops")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()

                val shop = shopSnapshot.documents
                    .firstOrNull()
                    ?.toObject(Shop::class.java)

                android.util.Log.d("SLOT_DEBUG", "shop = $shop")
                android.util.Log.d("SLOT_DEBUG", "isOpen = ${shop?.isOpen}")
                android.util.Log.d(
                    "SLOT_DEBUG",
                    "maxOrdersPerSlot = ${shop?.maxOrdersPerSlot}"
                )
                android.util.Log.d(
                    "SLOT_DEBUG",
                    "closedSlots = ${shop?.closedSlots}"
                )

                if (shop == null || !shop.isOpen) {

                    android.util.Log.d(
                        "SLOT_DEBUG",
                        "Shop closed OR null"
                    )

                    _selectedShop.value = shop
                    _availableSlots.value = emptyList()

                    _slotMessage.value =
                        "This shop is currently closed."

                    return@launch
                }

                _selectedShop.value = shop

                val displayFormatter =
                    DateTimeFormatter.ofPattern("hh:mm a")

                val closingFormatter =
                    DateTimeFormatter.ofPattern("HH:mm")

                val today = LocalDate.now().toString()

                val now = java.time.LocalDateTime.now()

                val earliestTime =
                    now.plusMinutes(cartPrepTimeMinutes.toLong())

                val generatedSlots = mutableListOf<String>()

                var slot =
                    roundToNextSlotDateTime(earliestTime, 15)

                val maxWindowEnd = now.plusHours(3)

                val shopClosingDateTime =
                    getShopClosingDateTime(
                        now = now,
                        closingTime = shop.closingTime,
                        formatter = closingFormatter
                    )

                val endTime =
                    if (shopClosingDateTime.isBefore(maxWindowEnd)) {
                        shopClosingDateTime
                    } else {
                        maxWindowEnd
                    }

                android.util.Log.d("CLOSING_DEBUG", "===================")
                android.util.Log.d("CLOSING_DEBUG", "now = $now")
                android.util.Log.d("CLOSING_DEBUG", "earliestTime = $earliestTime")
                android.util.Log.d("CLOSING_DEBUG", "initial slot = $slot")
                android.util.Log.d("CLOSING_DEBUG", "shopClosingDateTime = $shopClosingDateTime")
                android.util.Log.d("CLOSING_DEBUG", "endTime = $endTime")
                android.util.Log.d("CLOSING_DEBUG", "closingTime = ${shop.closingTime}")

                while (
                    slot.isBefore(endTime) &&
                    slot.plusMinutes(cartPrepTimeMinutes.toLong()).isBefore(endTime)
                ) {

                    android.util.Log.d(
                        "CLOSING_DEBUG",
                        "Adding slot = $slot"
                    )

                    generatedSlots.add(
                        slot.toLocalTime()
                            .format(displayFormatter)
                    )

                    slot = slot.plusMinutes(15)
                }

                android.util.Log.d(
                    "CLOSING_DEBUG",
                    "generatedSlots = $generatedSlots"
                )

                if (generatedSlots.isEmpty()) {

                    _availableSlots.value = emptyList()

                    _slotMessage.value =
                        "Shop closing time is ${
                            formatClosingTime(shop.closingTime)
                        }. No further pickup slots are available."

                    return@launch
                }

                android.util.Log.d(
                    "SLOT_DEBUG",
                    "generatedSlots = $generatedSlots"
                )

                val available = mutableListOf<String>()

                for (slotText in generatedSlots) {

                    val count = firestore.collection("orders")
                        .whereEqualTo("shopId", shopId)
                        .whereEqualTo("pickupDate", today)
                        .whereEqualTo("pickupSlot", slotText)
                        .get()
                        .await()
                        .size()

                    android.util.Log.d(
                        "SLOT_DEBUG",
                        "slot = $slotText | count = $count | max = ${shop.maxOrdersPerSlot} | closed = ${shop.closedSlots.contains(slotText)}"
                    )

                    if (
                        !shop.closedSlots.contains(slotText) &&
                        count < shop.maxOrdersPerSlot
                    ) {
                        available.add(slotText)
                    }
                }

                android.util.Log.d(
                    "SLOT_DEBUG",
                    "availableSlots = $available"
                )

                _availableSlots.value = available

                _slotMessage.value = when {

                    available.isEmpty() -> {
                        "Shop closing time is ${
                            formatClosingTime(shop.closingTime)
                        }. No further pickup slots are available."
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

            } catch (e: Exception) {

                android.util.Log.e(
                    "SLOT_DEBUG",
                    "ERROR = ${e.message}",
                    e
                )

                e.printStackTrace()

                _availableSlots.value = emptyList()

                _slotMessage.value =
                    "Failed to load pickup slots."

            } finally {

                _isLoadingSlots.value = false
            }
        }
    }

    private fun roundToNextSlotDateTime(
        time: java.time.LocalDateTime,
        intervalMinutes: Int
    ): java.time.LocalDateTime {
        val minute = time.minute
        val remainder = minute % intervalMinutes
        val minutesToAdd = if (remainder == 0) 0 else intervalMinutes - remainder

        return time
            .plusMinutes(minutesToAdd.toLong())
            .withSecond(0)
            .withNano(0)
    }
    private fun getShopClosingDateTime(
        now: java.time.LocalDateTime,
        closingTime: String,
        formatter: DateTimeFormatter
    ): java.time.LocalDateTime {

        val closingLocalTime = try {
            java.time.LocalTime.parse(closingTime, formatter)
        } catch (e: Exception) {
            java.time.LocalTime.of(23, 59)
        }

        var closingDateTime = java.time.LocalDateTime.of(
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

            val time = java.time.LocalTime.parse(closingTime, inputFormatter)
            time.format(outputFormatter)
        } catch (e: Exception) {
            closingTime
        }
    }
}

sealed class OrderState {
    object Idle : OrderState()
    object Loading : OrderState()
    data class Success(val orderId: String) : OrderState()
    data class Error(val message: String) : OrderState()
}