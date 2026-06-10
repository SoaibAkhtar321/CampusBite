package com.campusbite.app.ui.screens.shopkeeper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.OrderViewModel
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatusPickedUp  = Color(0xFF1565C0)
private val StatusCancelled = Color(0xFFD32F2F)
private val StatusRefunded  = Color(0xFF2E7D32)
private val OrangeSoft      = Orange.copy(alpha = 0.12f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopkeeperOrderHistoryScreen(
    shopId: String,
    onNavigateBack: () -> Unit,
    onNavigateToOrderStatus: (String) -> Unit = {},
    viewModel: OrderViewModel = hiltViewModel()
) {
    val historyState by viewModel.shopHistoryState.collectAsState()

    LaunchedEffect(shopId) {
        if (shopId.isNotBlank()) {
            viewModel.loadShopOrdersFirstPage(shopId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Order History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header card
            item {
                ShopHistoryHeader(totalLoaded = historyState.orders.size)
            }

            // Error state
            historyState.error?.let { errorMsg ->
                if (historyState.orders.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "⚠️", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMsg.ifBlank { "Failed to load order history" },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = {
                                    if (shopId.isNotBlank()) viewModel.loadShopOrdersFirstPage(shopId)
                                }) {
                                    Text("Retry", color = Orange)
                                }
                            }
                        }
                    }
                }
            }

            // First page loading spinner
            if (historyState.isLoading && historyState.orders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Orange)
                    }
                }
            }

            // Empty state
            if (!historyState.isLoading && historyState.orders.isEmpty() && historyState.error == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 42.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "📋", fontSize = 42.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No completed orders yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Picked up and cancelled orders will appear here.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Order cards
            items(
                items = historyState.orders,
                key = { order -> order.orderId }
            ) { order ->
                ShopkeeperOrderCard(
                    order = order,
                    onViewDetails = { onNavigateToOrderStatus(order.orderId) }
                )
            }

            // Load more / spinner / end marker
            if (historyState.orders.isNotEmpty()) {
                item {
                    when {
                        historyState.isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Orange,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        historyState.hasMore -> {
                            OutlinedButton(
                                onClick = {
                                    if (shopId.isNotBlank()) viewModel.loadMoreShopOrders(shopId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Orange)
                            ) {
                                Text(
                                    text = "Load more orders",
                                    color = Orange,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "All orders loaded",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopHistoryHeader(totalLoaded: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Orange)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(color = Color.White.copy(alpha = 0.18f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Order History",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (totalLoaded == 0) "Picked up & cancelled orders"
                    else "$totalLoaded orders loaded",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun ShopkeeperOrderCard(
    order: Order,
    onViewDetails: () -> Unit
) {
    val isCancelled  = order.status.lowercase() == OrderStatusValue.CANCELLED
    val isPickedUp   = order.status.lowercase() == OrderStatusValue.PICKED_UP
    val isRefundPending = order.refundStatus.lowercase() == RefundStatusValue.REFUND_PENDING
    val isRefunded   = order.refundStatus.lowercase() == RefundStatusValue.REFUNDED

    val statusColor = when {
        isCancelled && isRefunded       -> StatusRefunded
        isCancelled && isRefundPending  -> StatusCancelled
        isCancelled                     -> StatusCancelled
        isPickedUp                      -> StatusPickedUp
        else                            -> Orange
    }

    val statusLabel = when {
        isCancelled && isRefunded       -> "Refunded"
        isCancelled && isRefundPending  -> "Refund Pending"
        isCancelled                     -> "Cancelled"
        isPickedUp                      -> "Picked Up"
        else                            -> order.status.replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {

            // Header row: order ID + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order #${order.orderId.takeLast(6).uppercase()}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formatOrderTime(order.createdAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Student info — key for verification
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Student",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = order.studentName.ifBlank { "Unknown" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Pickup Slot",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = order.pickupSlot.ifBlank { "—" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Orange
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Items preview
            val visibleItems   = order.items.take(3)
            val remainingCount = order.items.size - visibleItems.size

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                visibleItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(7.dp), color = OrangeSoft) {
                            Text(
                                text = "×${item.quantity}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "₹${(item.price * item.quantity).toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (remainingCount > 0) {
                    Text(
                        text = "+$remainingCount more item${if (remainingCount == 1) "" else "s"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            // Payment info
            val paymentLabel = when (order.paymentStatus.lowercase()) {
                PaymentStatusValue.PAID                     -> "Paid ✓"
                PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED -> "Partial payment"
                PaymentStatusValue.PAYMENT_NOT_RECEIVED     -> "Not received"
                PaymentStatusValue.REFUNDED                 -> "Refunded"
                else                                        -> order.paymentStatus.ifBlank { "—" }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = paymentLabel,
                    fontSize = 12.sp,
                    color = if (order.paymentStatus.lowercase() == PaymentStatusValue.PAID)
                        StatusRefunded else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "₹${order.totalPrice.toInt()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Orange
                )
            }

            // Cancel reason if present
            if (order.cancelReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reason: ${order.cancelReason}",
                    fontSize = 11.sp,
                    color = StatusCancelled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Refund reference if settled
            if (isRefunded && order.refundReferenceId.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ref: ${order.refundReferenceId}",
                    fontSize = 11.sp,
                    color = StatusRefunded,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // View details button — lets shopkeeper pull up the full OrderStatusScreen
            // so they can verify items, total, and student info when student comes to collect
            TextButton(
                onClick = onViewDetails,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "View full order details →",
                    color = Orange,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun formatOrderTime(createdAt: Long): String {
    if (createdAt <= 0L) return "Time not available"
    return try {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(createdAt))
    } catch (e: Exception) {
        "Time not available"
    }
}
