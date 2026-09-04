package com.campusbite.app.ui.screens.order

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatusPending    = Color(0xFFE65100)
private val StatusPreparing  = Color(0xFF1565C0)
private val StatusReady      = Color(0xFF2E7D32)
private val StatusCancelled  = Color(0xFFD32F2F)
private val StatusRefunded   = Color(0xFF6A1B9A)

private val OrangeSoft = Orange.copy(alpha = 0.12f)

@Composable
fun OrderHistoryScreen(
    onNavigateToOrderStatus: (String) -> Unit = {},
    viewModel: OrderViewModel = hiltViewModel()
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val historyState by viewModel.studentHistoryState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Load first page when screen opens
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            viewModel.loadStudentOrdersFirstPage(uid)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OrderHistoryHeader(totalOrders = historyState.orders.size)
            }

            // Error state
            historyState.error?.let { errorMsg ->
                if (historyState.orders.isEmpty()) {
                    item {
                        ErrorCard(message = errorMsg, onRetry = {
                            if (uid.isNotBlank()) viewModel.loadStudentOrdersFirstPage(uid)
                        })
                    }
                }
            }

            // Empty state — only show when not loading and no orders
            if (!historyState.isLoading && historyState.orders.isEmpty() && historyState.error == null) {
                item { EmptyHistoryCard() }
            }

            // Order cards
            items(
                items = historyState.orders,
                key = { order -> order.orderId }
            ) { order ->
                OrderHistoryCard(
                    order = order,
                    onViewDetails = { onNavigateToOrderStatus(order.orderId) }
                )
            }

            // Loading spinner for first page
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

            // Load more button / loading spinner for subsequent pages
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
                                    if (uid.isNotBlank()) viewModel.loadMoreStudentOrders(uid)
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
                            // All orders loaded — show end marker
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
private fun OrderHistoryHeader(totalOrders: Int) {
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
                    text = "My Orders",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (totalOrders == 0) "No orders yet"
                    else "$totalOrders order${if (totalOrders == 1) "" else "s"} loaded",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
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
            Text(text = "🧾", fontSize = 42.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "No orders found", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your completed, cancelled, and refunded orders will appear here.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
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
                text = message.ifBlank { "Failed to load orders" },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry", color = Orange)
            }
        }
    }
}

@Composable
private fun OrderHistoryCard(
    order: Order,
    onViewDetails: () -> Unit
) {
    val statusColor = getStatusColor(order)
    val statusLabel = getStatusLabel(order)
    val paymentText = getPaymentText(order)
    val refundText  = getRefundText(order)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
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
                StatusPill(label = statusLabel, color = statusColor)
            }

            Spacer(modifier = Modifier.height(14.dp))
            OrderItemsPreview(order = order)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            InfoLine(label = "Pickup Slot", value = order.pickupSlot.ifBlank { "Not selected" })
            Spacer(modifier = Modifier.height(6.dp))
            InfoLine(label = "Payment", value = paymentText)

            if (refundText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                InfoLine(
                    label = "Refund",
                    value = refundText,
                    valueColor = when (order.refundStatus.lowercase()) {
                        RefundStatusValue.REFUND_PENDING -> StatusCancelled
                        RefundStatusValue.REFUNDED -> StatusReady
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            if (order.cancelReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                InfoLine(label = "Reason", value = order.cancelReason, valueColor = StatusCancelled)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "₹${order.totalPrice.toInt()}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Orange
                    )
                }
                Button(
                    onClick = onViewDetails,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text(text = "View Details", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OrderItemsPreview(order: Order) {
    val visibleItems   = order.items.take(3)
    val remainingCount = order.items.size - visibleItems.size

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleItems.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    fontWeight = FontWeight.SemiBold,
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
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun getStatusLabel(order: Order): String {
    val status       = order.status.lowercase()
    val refundStatus = order.refundStatus.lowercase()
    return when {
        status == OrderStatusValue.CANCELLED && refundStatus == RefundStatusValue.REFUND_PENDING -> "Refund Pending"
        status == OrderStatusValue.CANCELLED && refundStatus == RefundStatusValue.REFUNDED       -> "Refunded"
        status == OrderStatusValue.CANCELLED  -> "Cancelled"
        status == OrderStatusValue.PICKED_UP  -> "Picked Up"
        status == OrderStatusValue.READY      -> "Ready"
        status == OrderStatusValue.PREPARING  -> "Preparing"
        status == OrderStatusValue.ACCEPTED   -> "Accepted"
        status == OrderStatusValue.PENDING    -> "Pending"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

private fun getStatusColor(order: Order): Color {
    val status       = order.status.lowercase()
    val refundStatus = order.refundStatus.lowercase()
    return when {
        status == OrderStatusValue.CANCELLED && refundStatus == RefundStatusValue.REFUNDED      -> StatusReady
        status == OrderStatusValue.CANCELLED && refundStatus == RefundStatusValue.REFUND_PENDING -> StatusCancelled
        status == OrderStatusValue.CANCELLED  -> StatusCancelled
        status == OrderStatusValue.PICKED_UP  -> StatusRefunded
        status == OrderStatusValue.READY      -> StatusReady
        status == OrderStatusValue.PREPARING  -> StatusPreparing
        else -> StatusPending
    }
}

private fun getPaymentText(order: Order): String {
    return when (order.paymentStatus.lowercase()) {
        "verified", PaymentStatusValue.PAID                -> "Paid"
        PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED        -> "Partial payment received"
        PaymentStatusValue.PAYMENT_NOT_RECEIVED            -> "Payment not received"
        PaymentStatusValue.REFUNDED                        -> "Refunded"
        PaymentStatusValue.PENDING_VERIFICATION            -> "Pending verification"
        else -> order.paymentStatus.ifBlank { "Not available" }
    }
}

private fun getRefundText(order: Order): String {
    return when (order.refundStatus.lowercase()) {
        RefundStatusValue.REFUND_PENDING  -> "Pending manual settlement"
        RefundStatusValue.REFUNDED        ->
            if (order.refundReferenceId.isNotBlank()) "Settled (${order.refundReferenceId})" else "Settled"
        RefundStatusValue.REFUND_DISPUTED -> "Disputed"
        else -> ""
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
