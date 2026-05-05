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
                val shopDoc = firestore.collection("shops")
                    .document(shopId)
                    .get()
                    .await()

                val shop = shopDoc.toObject(Shop::class.java)

                if (shop == null || !shop.isOpen) {
                    _selectedShop.value = shop
                    _availableSlots.value = emptyList()
                    _isLoadingSlots.value = false
                    return@launch
                }

                _selectedShop.value = shop

                val displayFormatter = DateTimeFormatter.ofPattern("hh:mm a")

                val today = LocalDate.now().toString()
                val now = LocalTime.now()

                val earliestTime = now.plusMinutes(cartPrepTimeMinutes.toLong())

                val generatedSlots = mutableListOf<String>()

                var slot = roundToNextSlot(earliestTime, 15)
                val endTime = now.plusHours(3)

                while (slot.isBefore(endTime)) {
                    generatedSlots.add(slot.format(displayFormatter))
                    slot = slot.plusMinutes(15)
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

                _availableSlots.value = available

            } catch (e: Exception) {
                e.printStackTrace()
                _availableSlots.value = emptyList()
            } finally {
                _isLoadingSlots.value = false
            }
        }
    }

    private fun roundToNextSlot(time: LocalTime, intervalMinutes: Int): LocalTime {
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