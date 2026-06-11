package com.campusbite.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.AdminShopReportSummary
import com.campusbite.app.ui.viewmodel.AdminViewModel
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.RefundStatusValue

private val DangerRed = Color(0xFFD32F2F)
private val SuccessGreen = Color(0xFF2E7D32)
private val MutedGrey = Color(0xFF616161)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShopReportScreen(
    shopId: String,
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
){
    val reportState by viewModel.shopReportState.collectAsState()

    var cancelDialogOrder by remember {
        mutableStateOf<Order?>(null)
    }

    var refundDialogOrder by remember {
        mutableStateOf<Order?>(null)
    }

    LaunchedEffect(shopId) {
        viewModel.loadShopReport(shopId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = reportState.shop?.name ?: "Shop Report",
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
                }
            )
        }
    ) { innerPadding ->

        when {
            reportState.isLoading && reportState.shop == null -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Orange
                    )
                }
            }

            reportState.error.isNotBlank() && reportState.shop == null -> {
                ErrorState(
                    modifier = Modifier.padding(innerPadding),
                    message = reportState.error,
                    shopId = shopId,
                    onRetry = {
                        viewModel.loadShopReport(shopId)
                    }
                )
            }

            else -> {
                val refundPendingOrders = reportState.recentOrders.filter { order ->
                    order.refundStatus.lowercase() == RefundStatusValue.REFUND_PENDING ||
                            order.paymentStatus.lowercase() == "refund_pending"
                }

                val cancelledOrders = reportState.recentOrders.filter { order ->
                    order.status.lowercase() == OrderStatusValue.CANCELLED
                }

                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        reportState.shop?.let { shop ->
                            ShopHeaderCard(
                                name = shop.name,
                                shopId = shop.shopId.ifBlank { shop.docId },
                                isOpen = shop.isOpen,
                                isApproved = shop.isApproved,
                                isBlocked = shop.isBlocked
                            )
                        }
                    }

                    item {
                        AnalyticsSummaryCard(
                            summary = reportState.summary
                        )
                    }

                    if (reportState.error.isNotBlank()) {
                        item {
                            ErrorRow(
                                message = reportState.error
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Refund Pending Orders (${refundPendingOrders.size})"
                        )
                    }

                    if (refundPendingOrders.isEmpty()) {
                        item {
                            EmptySmallText("No refund pending orders")
                        }
                    } else {
                        items(
                            items = refundPendingOrders,
                            key = { order ->
                                "refund_${order.orderId}"
                            }
                        ) { order ->
                            AdminOrderCard(
                                order = order,
                                showActions = true,
                                onCancel = {
                                    cancelDialogOrder = order
                                },
                                onSettle = {
                                    refundDialogOrder = order
                                }
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Cancelled Orders (${cancelledOrders.size})"
                        )
                    }

                    if (cancelledOrders.isEmpty()) {
                        item {
                            EmptySmallText("No cancelled orders")
                        }
                    } else {
                        items(
                            items = cancelledOrders,
                            key = { order ->
                                "cancelled_${order.orderId}"
                            }
                        ) { order ->
                            AdminOrderCard(
                                order = order,
                                showActions = false,
                                onCancel = {
                                    cancelDialogOrder = order
                                },
                                onSettle = {
                                    refundDialogOrder = order
                                }
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Recent Orders (${reportState.recentOrders.size})"
                        )
                    }

                    if (reportState.recentOrders.isEmpty()) {
                        item {
                            EmptySmallText("No recent orders")
                        }
                    } else {
                        items(
                            items = reportState.recentOrders,
                            key = { order ->
                                "recent_${order.orderId}"
                            }
                        ) { order ->
                            AdminOrderCard(
                                order = order,
                                showActions = true,
                                compact = true,
                                onCancel = {
                                    cancelDialogOrder = order
                                },
                                onSettle = {
                                    refundDialogOrder = order
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    cancelDialogOrder?.let { order ->
        CancelOrderDialog(
            order = order,
            onDismiss = {
                cancelDialogOrder = null
            },
            onConfirm = { paymentReceivedType, reason ->
                viewModel.cancelOrderByAdmin(
                    orderId = order.orderId,
                    paymentReceivedType = paymentReceivedType,
                    cancelReason = reason
                )

                cancelDialogOrder = null
            }
        )
    }

    refundDialogOrder?.let { order ->
        RefundSettleDialog(
            order = order,
            onDismiss = {
                refundDialogOrder = null
            },
            onConfirm = { referenceId, note ->
                viewModel.markRefundSettledByAdmin(
                    orderId = order.orderId,
                    refundReferenceId = referenceId,
                    refundNote = note
                )

                refundDialogOrder = null
            }
        )
    }
}

@Composable
private fun ShopHeaderCard(
    name: String,
    shopId: String,
    isOpen: Boolean,
    isApproved: Boolean,
    isBlocked: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = name.ifBlank {
                    "Unnamed Shop"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = shopId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    text = if (isApproved) "Approved" else "Not Approved",
                    backgroundColor = if (isApproved) {
                        SuccessGreen.copy(alpha = 0.10f)
                    } else {
                        DangerRed.copy(alpha = 0.10f)
                    },
                    contentColor = if (isApproved) SuccessGreen else DangerRed
                )

                StatusBadge(
                    text = if (isOpen) "Open" else "Closed",
                    backgroundColor = if (isOpen) {
                        SuccessGreen.copy(alpha = 0.10f)
                    } else {
                        MutedGrey.copy(alpha = 0.10f)
                    },
                    contentColor = if (isOpen) SuccessGreen else MutedGrey
                )

                if (isBlocked) {
                    StatusBadge(
                        text = "Blocked",
                        backgroundColor = DangerRed.copy(alpha = 0.10f),
                        contentColor = DangerRed
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsSummaryCard(
    summary: AdminShopReportSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sales Analytics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMetric(
                    label = "Today",
                    value = "₹${"%.0f".format(summary.todaySales)}",
                    sub = "${summary.todayOrders} orders"
                )

                AnalyticsMetric(
                    label = "Month",
                    value = "₹${"%.0f".format(summary.monthSales)}",
                    sub = "${summary.monthOrders} orders"
                )

                AnalyticsMetric(
                    label = "Lifetime",
                    value = "₹${"%.0f".format(summary.lifetimeSales)}",
                    sub = "${summary.lifetimeOrders} orders"
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            InfoRow(
                label = "Pending Payment",
                value = summary.pendingPaymentOrders.toString()
            )

            InfoRow(
                label = "Cancelled Orders",
                value = summary.cancelledOrders.toString()
            )
        }
    }
}

@Composable
private fun AnalyticsMetric(
    label: String,
    value: String,
    sub: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(
    title: String
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun AdminOrderCard(
    order: Order,
    compact: Boolean = false,
    showActions: Boolean,
    onCancel: () -> Unit,
    onSettle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "#${order.orderId.takeLast(6).uppercase()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = order.studentName.ifBlank {
                            order.studentEmail.ifBlank {
                                "Unknown user"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "₹${"%.0f".format(order.totalPrice)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Orange
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            InfoRow(
                label = "Status",
                value = order.status
            )

            InfoRow(
                label = "Payment",
                value = order.paymentStatus
            )

            InfoRow(
                label = "Refund",
                value = order.refundStatus.ifBlank {
                    "none"
                }
            )

            if (!compact) {
                InfoRow(
                    label = "Pickup",
                    value = "${order.pickupDate} ${order.pickupSlot}"
                )

                if (order.cancelReason.isNotBlank()) {
                    InfoRow(
                        label = "Cancel Reason",
                        value = order.cancelReason
                    )
                }
            }

            if (showActions) {
                val canCancel = order.status.lowercase() !in listOf(
                    OrderStatusValue.CANCELLED,
                    OrderStatusValue.PICKED_UP
                )

                val canSettleRefund = order.refundStatus.lowercase() == RefundStatusValue.REFUND_PENDING ||
                        order.paymentStatus.lowercase() == "refund_pending"

                if (canCancel || canSettleRefund) {
                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canCancel) {
                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = DangerRed
                                )
                            ) {
                                Text("Cancel")
                            }
                        }

                        if (canSettleRefund) {
                            Button(
                                onClick = onSettle,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Mark Refund Settled")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CancelOrderDialog(
    order: Order,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    var selectedPaymentType by remember {
        mutableStateOf(PaymentReceivedType.NONE)
    }

    var reason by remember {
        mutableStateOf("")
    }

    val paymentOptions = listOf(
        PaymentReceivedType.NONE to "Payment not received",
        PaymentReceivedType.PARTIAL to "Partial payment received",
        PaymentReceivedType.FULL to "Full payment received"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cancel Order")
        },
        text = {
            Column {
                Text(
                    text = "Order #${order.orderId.takeLast(6).uppercase()}",
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "Payment received type",
                    style = MaterialTheme.typography.labelLarge
                )

                Box {
                    OutlinedButton(
                        onClick = {
                            expanded = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = paymentOptions.first {
                                it.first == selectedPaymentType
                            }.second
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        paymentOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option.second)
                                },
                                onClick = {
                                    selectedPaymentType = option.first
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Cancel reason")
                    },
                    placeholder = {
                        Text("Example: Item unavailable")
                    },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        selectedPaymentType,
                        reason
                    )
                }
            ) {
                Text(
                    text = "Cancel Order",
                    color = DangerRed
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun RefundSettleDialog(
    order: Order,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var referenceId by remember {
        mutableStateOf("")
    }

    var note by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Mark Refund Settled")
        },
        text = {
            Column {
                Text(
                    text = "Order #${order.orderId.takeLast(6).uppercase()}",
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                OutlinedTextField(
                    value = referenceId,
                    onValueChange = {
                        referenceId = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Refund reference ID")
                    },
                    placeholder = {
                        Text("UPI/refund transaction ID")
                    },
                    singleLine = true
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Note")
                    },
                    placeholder = {
                        Text("Optional note")
                    },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        referenceId,
                        note
                    )
                }
            ) {
                Text("Mark Settled")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ErrorState(
    modifier: Modifier,
    message: String,
    shopId: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = DangerRed
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = message,
                color = DangerRed,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "shopId used: $shopId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = onRetry
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ErrorRow(
    message: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = DangerRed
        )

        Spacer(
            modifier = Modifier.width(6.dp)
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = DangerRed
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(
        modifier = Modifier.height(4.dp)
    )
}

@Composable
private fun StatusBadge(
    text: String,
    backgroundColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp
            ),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptySmallText(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}