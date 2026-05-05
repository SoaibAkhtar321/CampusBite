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
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.OrderState
import com.campusbite.app.ui.viewmodel.OrderViewModel
import java.time.LocalDate

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
    val availableSlots by orderViewModel.availableSlots.collectAsState()
    val selectedShop by orderViewModel.selectedShop.collectAsState()
    val isLoadingSlots by orderViewModel.isLoadingSlots.collectAsState()
    val currentShopId by cartViewModel.currentShopId.collectAsState()

    var selectedSlot by remember { mutableStateOf("") }
    var selectedPayment by remember { mutableStateOf("Cash on Delivery") }

    val shopId = currentShopId ?: ""
    val cartPrepTime = cartItems.maxOfOrNull { it.prepTimeMinutes } ?: 0

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
            val orderId = (orderState as OrderState.Success).orderId
            cartViewModel.clearCart()
            orderViewModel.resetState()
            onOrderPlaced(orderId)
        }
    }

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

                                Text(
                                    text = "Prep time: ${item.prepTimeMinutes} min",
                                    fontSize = 11.sp,
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

                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedShop?.isOpen == false) {
                        Text(
                            text = "This shop is currently not accepting orders.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        "Select Pickup Slot",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "Loading available slots...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        availableSlots.isEmpty() -> {
                            Text(
                                text = "No pickup slots available right now.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        else -> {
                            availableSlots.forEach { slot ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedSlot == slot,
                                        onClick = { selectedSlot = slot },
                                        colors = RadioButtonDefaults.colors(selectedColor = Orange)
                                    )

                                    Text(
                                        text = slot,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }

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
                        text = when {
                            selectedShop?.isOpen == false -> "Shop is closed"
                            isLoadingSlots -> "Loading slots..."
                            availableSlots.isEmpty() -> "No slots available"
                            selectedSlot.isEmpty() -> "Select a pickup slot first"
                            else -> "Place Order • ₹${cartViewModel.totalPrice.toInt()}"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}