package com.campusbite.app.ui.screens.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.OrderState
import com.campusbite.app.ui.viewmodel.OrderViewModel
import java.time.LocalDate
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onNavigateBack: () -> Unit,
    onOrderPlaced: (String) -> Unit,
    cartViewModel: CartViewModel = hiltViewModel(),
    orderViewModel: OrderViewModel = hiltViewModel()
) {

    val cartItems by cartViewModel.cartItems.collectAsState()
    val currentShopId by cartViewModel.currentShopId.collectAsState()

    val orderState by orderViewModel.orderState.collectAsState()
    val selectedShop by orderViewModel.selectedShop.collectAsState()
    val slotUiState by orderViewModel.slotUiState.collectAsState()
    val availableSlots = slotUiState.slots
    val isLoadingSlots = slotUiState.isLoading
    val slotMessage = slotUiState.message

    var selectedSlot by remember { mutableStateOf("") }
    var selectedPayment by remember {
        mutableStateOf("Cash on Delivery")
    }

    val shopId = currentShopId ?: ""
    val cartPrepTime =
        cartItems.maxOfOrNull { it.prepTimeMinutes } ?: 0

    LaunchedEffect(shopId, cartPrepTime, cartItems.size) {

        if (shopId.isNotEmpty() && cartItems.isNotEmpty()) {

            selectedSlot = ""

            orderViewModel.loadAvailableSlots(
                shopId = shopId,
                cartPrepTimeMinutes = cartPrepTime
            )
        }
    }

    LaunchedEffect(orderState) {

        if (orderState is OrderState.Success) {

            val orderId =
                (orderState as OrderState.Success).orderId

            cartViewModel.clearCart()
            orderViewModel.resetState()

            onOrderPlaced(orderId)
        }
    }

    Scaffold(
        topBar = {

            TopAppBar(
                title = {
                    Text(
                        text = "Your Cart",
                        fontWeight = FontWeight.Bold
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = onNavigateBack
                    ) {

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                items(cartItems) { item ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {

                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )

                                Text(
                                    text = "₹${item.price.toInt()} x ${item.quantity}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )

                                Text(
                                    text = "Prep time: ${item.prepTimeMinutes} min",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "₹${(item.price * item.quantity).toInt()}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange
                            )
                        }
                    }
                }

                item {

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Select Pickup Slot",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when {

                        isLoadingSlots -> {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Orange
                                )

                                Spacer(
                                    modifier = Modifier.width(8.dp)
                                )

                                Text(
                                    text = "Loading available slots...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        availableSlots.isNotEmpty() -> {

                            FlowRow(
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp),

                                verticalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {

                                availableSlots.forEach { slot ->

                                    FilterChip(
                                        selected =
                                            selectedSlot == slot,

                                        onClick = {
                                            selectedSlot = slot
                                        },

                                        label = {
                                            Text(slot)
                                        }
                                    )
                                }
                            }

                            if (slotMessage.isNotBlank()) {

                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                Text(
                                    text = slotMessage,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        slotMessage.isNotBlank() -> {

                            Text(
                                text = slotMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                item {

                    Spacer(modifier = Modifier.height(8.dp))

                    if (orderState is OrderState.Error) {

                        Text(
                            text = (orderState as OrderState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )
                    }

                    Text(
                        text = "Payment Method",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        "Cash on Delivery",
                        "Online Payment"
                    ).forEach { method ->

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            RadioButton(
                                selected =
                                    selectedPayment == method,

                                onClick = {
                                    selectedPayment = method
                                },

                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Orange
                                )
                            )

                            Text(
                                text = method,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                item {

                    Spacer(modifier = Modifier.height(8.dp))

                    Divider()

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        Text(
                            text = "Total Amount",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "₹${cartViewModel.totalPrice.toInt()}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Orange
                        )
                    }
                }
            }

            Button(
                onClick = {

                    val order = Order(
                        shopId = shopId,
                        items = cartItems,
                        totalPrice = cartViewModel.totalPrice,
                        status = "pending",
                        pickupSlot = selectedSlot,
                        pickupDate = LocalDate.now().toString(),
                        paymentMethod = selectedPayment
                    )

                    orderViewModel.placeOrder(order)
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                enabled =
                    selectedShop?.isOpen == true &&
                            selectedSlot.isNotEmpty() &&
                            availableSlots.isNotEmpty() &&
                            orderState !is OrderState.Loading,

                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange
                )
            ) {

                if (orderState is OrderState.Loading) {

                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )

                } else {

                    Text(
                        text = when {

                            selectedShop?.isOpen == false -> {
                                "Shop is closed"
                            }

                            isLoadingSlots -> {
                                "Loading slots..."
                            }

                            availableSlots.isEmpty() -> {
                                "Slots unavailable"
                            }

                            selectedSlot.isEmpty() -> {
                                "Select a pickup slot first"
                            }

                            else -> {
                                "Place Order • ₹${cartViewModel.totalPrice.toInt()}"
                            }
                        },

                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}