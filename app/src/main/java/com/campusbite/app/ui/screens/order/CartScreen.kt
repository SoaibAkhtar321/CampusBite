package com.campusbite.app.ui.screens.order

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.OrderState
import com.campusbite.app.ui.viewmodel.OrderViewModel
import com.campusbite.app.data.model.Order



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onNavigateBack: () -> Unit,
    onOrderPlaced: (String) -> Unit,
    cartViewModel: CartViewModel = hiltViewModel(),
    orderViewModel: OrderViewModel = hiltViewModel()
) {
    val cartItems by cartViewModel.cartItems.collectAsState()
    val orderState by orderViewModel.orderState.collectAsState()
    var selectedSlot by remember { mutableStateOf("") }
    var selectedPayment by remember { mutableStateOf("Cash on Delivery") }

// Navigate when order placed successfully
    LaunchedEffect(orderState) {
        if (orderState is OrderState.Success) {
            val orderId = (orderState as OrderState.Success).orderId
            cartViewModel.clearCart()
            orderViewModel.resetState()
            onOrderPlaced(orderId)
        }
    }
    val timeSlots = listOf("12:15 PM", "12:30 PM", "1:00 PM", "1:15 PM", "1:30 PM")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Cart", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cart items
                items(cartItems) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "₹${item.price.toInt()} x ${item.quantity}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "₹${(item.price * item.quantity).toInt()}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange
                            )
                        }
                    }
                }

                // Pickup slot
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Select Pickup Slot",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    timeSlots.forEach { slot ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                colors = RadioButtonDefaults.colors(selectedColor = Orange)
                            )
                            Text(text = slot, fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                // Payment method
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Payment Method",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf("Cash on Delivery", "Online Payment").forEach { method ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPayment == method,
                                onClick = { selectedPayment = method },
                                colors = RadioButtonDefaults.colors(selectedColor = Orange)
                            )
                            Text(text = method, fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                // Total
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total Amount",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "₹${cartViewModel.totalPrice.toInt()}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Orange
                        )
                    }
                }
            }

            // Place order button
            Button(
                onClick = {
                    val order = Order(
                        shopId = cartViewModel.currentShopId.value ?: "",
                        items = cartItems,
                        totalPrice = cartViewModel.totalPrice,
                        status = "pending",
                        pickupSlot = selectedSlot,
                        paymentMethod = selectedPayment
                    )
                    orderViewModel.placeOrder(order)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = selectedSlot.isNotEmpty() && orderState !is OrderState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                if (orderState is OrderState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (selectedSlot.isEmpty()) "Select a pickup slot first"
                        else "Place Order • ₹${cartViewModel.totalPrice.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}