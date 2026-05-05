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
@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val firestore: FirebaseFirestore
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

    fun placeOrder(order: Order) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading
            val result = orderRepository.placeOrder(order)
            if (result.isSuccess) {
                val orderId = result.getOrNull() ?: ""
                _orderState.value = OrderState.Success(orderId)
                listenToOrder(orderId)
            } else {
                _orderState.value = OrderState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to place order"
                )
            }
        }
    }

    private fun listenToOrder(orderId: String) {
        orderRepository.listenToOrder(orderId) { order ->
            _currentOrder.value = order
        }
    }

    fun resetState() {
        _orderState.value = OrderState.Idle
    }
    fun listenToOrderById(orderId: String) {
        orderRepository.listenToOrder(orderId) { order ->
            _currentOrder.value = order
        }
    }
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
                    return@launch
                }

                _selectedShop.value = shop

                val displayFormatter =
                    DateTimeFormatter.ofPattern("hh:mm a")

                val today = LocalDate.now().toString()

                android.util.Log.d("SLOT_DEBUG", "today = $today")

                val now = java.time.LocalDateTime.now()

                android.util.Log.d("SLOT_DEBUG", "now = $now")

                val earliestTime =
                    now.plusMinutes(cartPrepTimeMinutes.toLong())

                android.util.Log.d(
                    "SLOT_DEBUG",
                    "earliestTime = $earliestTime"
                )

                val generatedSlots = mutableListOf<String>()

                var slot =
                    roundToNextSlotDateTime(earliestTime, 15)

                val endTime = now.plusHours(3)

                android.util.Log.d(
                    "SLOT_DEBUG",
                    "endTime = $endTime"
                )

                while (slot.isBefore(endTime)) {

                    val formatted =
                        slot.toLocalTime().format(displayFormatter)

                    generatedSlots.add(formatted)

                    slot = slot.plusMinutes(15)
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

            } catch (e: Exception) {

                android.util.Log.e(
                    "SLOT_DEBUG",
                    "ERROR = ${e.message}",
                    e
                )

                e.printStackTrace()
                _availableSlots.value = emptyList()

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
}

sealed class OrderState {
    object Idle : OrderState()
    object Loading : OrderState()
    data class Success(val orderId: String) : OrderState()
    data class Error(val message: String) : OrderState()
}