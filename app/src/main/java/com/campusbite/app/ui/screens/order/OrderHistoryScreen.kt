package com.campusbite.app.ui.screens.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.OrderViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun OrderHistoryScreen(
    viewModel: OrderViewModel = hiltViewModel()
) {
    val userOrders by viewModel.userOrders.collectAsState()

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            viewModel.loadUserOrders(uid)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "My Orders",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (userOrders.isEmpty()) {
            item {
                Text(
                    text = "No orders found.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(userOrders) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Order #${order.orderId.takeLast(5)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Status: ${order.status}",
                            fontSize = 14.sp
                        )

                        Text(
                            text = "Pickup Slot: ${order.pickupSlot}",
                            fontSize = 14.sp
                        )

                        Text(
                            text = "Total: ₹${order.totalPrice.toInt()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Orange
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        order.items.forEach { orderItem ->
                            Text(
                                text = "${orderItem.name} x${orderItem.quantity}",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}