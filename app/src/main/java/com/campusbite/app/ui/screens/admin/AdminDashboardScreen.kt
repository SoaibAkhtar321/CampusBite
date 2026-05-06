package com.campusbite.app.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.AdminViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val shopOpen by viewModel.shopOpen.collectAsState()
    val closedSlots by viewModel.closedSlots.collectAsState()



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Panel", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = Orange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = "Shop Controls",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween,
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Column {
                                    Text(
                                        text = if (shopOpen)
                                            "Accepting Orders"
                                        else
                                            "Shop Closed",

                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text =
                                            "Toggle customer ordering",
                                        fontSize = 12.sp
                                    )
                                }

                                Switch(
                                    checked = shopOpen,
                                    onCheckedChange = {
                                        viewModel.toggleShopOpen(it)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Quick Slot Controls",
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val sampleSlots = listOf(
                                "06:00 PM",
                                "06:15 PM",
                                "06:30 PM",
                                "06:45 PM"
                            )

                            sampleSlots.forEach { slot ->

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween,
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Text(slot)

                                    Button(
                                        onClick = {
                                            viewModel.toggleSlot(slot)
                                        },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor =
                                                    if (closedSlots.contains(slot))
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        Orange
                                            )
                                    ) {

                                        Text(
                                            if (closedSlots.contains(slot))
                                                "Closed"
                                            else
                                                "Open"
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                if (orders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No orders yet",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(orders) { order ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Order #${order.orderId.take(6)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                StatusChip(status = order.status)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            order.items.forEach { item ->
                                Text(
                                    "${item.name} x${item.quantity}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Pickup: ${order.pickupSlot}",
                                    fontSize = 12.sp,
                                    color = Orange
                                )
                                Text(
                                    "Total: Rs.${order.totalPrice.toInt()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            when (order.status) {
                                "pending" -> {
                                    Button(
                                        onClick = { viewModel.updateOrderStatus(order.orderId, "preparing") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                                    ) {
                                        Text("Accept & Prepare")
                                    }
                                }
                                "preparing" -> {
                                    Button(
                                        onClick = { viewModel.updateOrderStatus(order.orderId, "ready") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                                    ) {
                                        Text("Mark as Ready")
                                    }
                                }
                                "ready" -> {

                                    Column {

                                        Text(
                                            "Ready for pickup",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            onClick = {
                                                viewModel.updateOrderStatus(
                                                    order.orderId,
                                                    "picked_up"
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Orange
                                            )
                                        ) {
                                            Text("Mark as Picked Up")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val color = when (status) {
        "pending" -> MaterialTheme.colorScheme.error
        "preparing" -> Orange
        "ready" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = status.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}