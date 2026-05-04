package com.campusbite.app.ui.screens.order

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.viewmodel.OrderViewModel

@Composable
fun OrderHistoryScreen(viewModel: OrderViewModel = hiltViewModel()) {

    val orders by viewModel.userOrders.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadUserOrders()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(orders) { order ->

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text(
                        text = "Order #${order.orderId.takeLast(5)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Status: ${order.status}",
                        color = when (order.status) {
                            "READY" -> Color.Green
                            "PREPARING" -> Color(0xFFFFA500)
                            else -> Color.Gray
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Total: ₹${order.totalPrice}")

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = order.items.joinToString {
                            "${it.name} x${it.quantity}"
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}