package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.Order
import com.campusbite.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    private val _currentOrder = MutableStateFlow<Order?>(null)
    val currentOrder: StateFlow<Order?> = _currentOrder

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
}

sealed class OrderState {
    object Idle : OrderState()
    object Loading : OrderState()
    data class Success(val orderId: String) : OrderState()
    data class Error(val message: String) : OrderState()
}