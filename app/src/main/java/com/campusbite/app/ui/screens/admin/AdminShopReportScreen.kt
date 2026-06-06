package com.campusbite.app.ui.screens.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.AdminShop
import com.campusbite.app.ui.viewmodel.AdminShopReportSummary
import com.campusbite.app.ui.viewmodel.AdminViewModel
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Orange_10 = Orange.copy(alpha = 0.12f)
private val SuccessGreen = Color(0xFF2E7D32)
private val DangerRed = Color(0xFFD32F2F)
private val InfoBlue = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShopReportScreen(
    shopId: String,
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val reportState by viewModel.shopReportState.collectAsState()

    var showRefundPendingOrders by remember {
        mutableStateOf(false)
    }

    var showCancelledOrders by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(shopId) {
        viewModel.loadShopReport(shopId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Shop Report",
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
    ) { padding ->

        when {
            reportState.isLoading -> {
                LoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            reportState.error.isNotBlank() -> {
                ErrorState(
                    message = reportState.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                )
            }

            else -> {
                val refundPendingOrders = reportState.recentOrders.filter { order ->
                    order.status.lowercase() == OrderStatusValue.CANCELLED &&
                            order.refundStatus.lowercase() == RefundStatusValue.REFUND_PENDING
                }

                val cancelledOrders = reportState.recentOrders.filter { order ->
                    order.status.lowercase() == OrderStatusValue.CANCELLED &&
                            order.refundStatus.lowercase() != RefundStatusValue.REFUND_PENDING
                }

                val historyOrders = reportState.recentOrders.filter { order ->
                    order.status.lowercase() != OrderStatusValue.CANCELLED
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(
                        key = "shop_header",
                        contentType = "shop_header"
                    ) {
                        ShopReportHeaderCard(
                            shop = reportState.shop
                        )
                    }

                    item(
                        key = "summary",
                        contentType = "summary"
                    ) {
                        AdminReportSummaryCard(
                            summary = reportState.summary
                        )
                    }

                    if (refundPendingOrders.isNotEmpty()) {
                        item(
                            key = "refund_pending_header",
                            contentType = "refund_pending_header"
                        ) {
                            AdminCollapsedSectionHeader(
                                title = "Refund Pending Orders",
                                subtitle = "${refundPendingOrders.size} order${if (refundPendingOrders.size == 1) "" else "s"} need manual refund",
                                count = refundPendingOrders.size,
                                isExpanded = showRefundPendingOrders,
                                color = DangerRed,
                                onClick = {
                                    showRefundPendingOrders = !showRefundPendingOrders
                                }
                            )
                        }

                        if (showRefundPendingOrders) {
                            items(
                                items = refundPendingOrders,
                                key = { order -> "refund_${order.orderId}" },
                                contentType = { "refund_pending_order" }
                            ) { order ->
                                AdminRefundPendingOrderCard(
                                    order = order,
                                    onMarkRefundSettled = { refundReferenceId, refundNote ->
                                        viewModel.markRefundSettledByAdmin(
                                            orderId = order.orderId,
                                            refundReferenceId = refundReferenceId,
                                            refundNote = refundNote
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (cancelledOrders.isNotEmpty()) {
                        item(
                            key = "cancelled_header",
                            contentType = "cancelled_header"
                        ) {
                            AdminCollapsedSectionHeader(
                                title = "Cancelled Orders",
                                subtitle = "${cancelledOrders.size} cancelled order${if (cancelledOrders.size == 1) "" else "s"} with reason and payment details",
                                count = cancelledOrders.size,
                                isExpanded = showCancelledOrders,
                                color = DangerRed,
                                onClick = {
                                    showCancelledOrders = !showCancelledOrders
                                }
                            )
                        }

                        if (showCancelledOrders) {
                            items(
                                items = cancelledOrders,
                                key = { order -> "cancelled_${order.orderId}" },
                                contentType = { "cancelled_order" }
                            ) { order ->
                                AdminCancelledOrderDetailsCard(
                                    order = order
                                )
                            }
                        }
                    }

                    item(
                        key = "history_title",
                        contentType = "history_title"
                    ) {
                        RecentOrdersHeader(
                            count = historyOrders.size
                        )
                    }

                    if (historyOrders.isEmpty()) {
                        item(
                            key = "empty_orders",
                            contentType = "empty"
                        ) {
                            EmptyReportState(
                                text = "No active or completed orders found for this shop."
                            )
                        }
                    } else {
                        items(
                            items = historyOrders,
                            key = { order ->
                                order.orderId.ifBlank {
                                    "${order.createdAt}_${order.studentId}"
                                }
                            },
                            contentType = {
                                "history_order"
                            }
                        ) { order ->
                            AdminRecentOrderCard(
                                order = order,
                                onAdminCancelOrder = { paymentReceivedType, cancelReason ->
                                    viewModel.cancelOrderByAdmin(
                                        orderId = order.orderId,
                                        paymentReceivedType = paymentReceivedType,
                                        cancelReason = cancelReason
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Orange
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ShopReportHeaderCard(
    shop: AdminShop?
) {
    val statusText = remember(shop) {
        when {
            shop == null -> "Status: Not available"
            shop.isBlocked -> "Status: Blocked"
            shop.isDeleted -> "Status: Deleted"
            shop.isApproved && shop.isOpen -> "Status: Open"
            shop.isApproved && !shop.isOpen -> "Status: Closed"
            else -> "Status: Not approved"
        }
    }

    val statusColor = when {
        shop?.isBlocked == true || shop?.isDeleted == true ->
            MaterialTheme.colorScheme.error

        shop?.isApproved == true && shop.isOpen ->
            SuccessGreen

        else ->
            Orange
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Orange_10
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = Orange,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = shop?.name?.ifBlank { "Unnamed Shop" } ?: "Shop",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = "ShopId: ${shop?.shopId?.ifBlank { "Not available" } ?: "Not available"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AdminReportSummaryCard(
    summary: AdminShopReportSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sales Summary",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Only verified payments are counted as sales.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            SummaryMetricRow(
                leftTitle = "Today",
                leftMainValue = formatCurrency(summary.todaySales),
                leftSubValue = "${summary.todayOrders} orders",
                rightTitle = "This Month",
                rightMainValue = formatCurrency(summary.monthSales),
                rightSubValue = "${summary.monthOrders} orders"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SummaryMetricRow(
                leftTitle = "Lifetime",
                leftMainValue = formatCurrency(summary.lifetimeSales),
                leftSubValue = "${summary.lifetimeOrders} orders",
                rightTitle = "Pending Pay",
                rightMainValue = "${summary.pendingPaymentOrders}",
                rightSubValue = "need verify"
            )

            if (summary.cancelledOrders > 0) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "Cancelled orders: ${summary.cancelledOrders}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricRow(
    leftTitle: String,
    leftMainValue: String,
    leftSubValue: String,
    rightTitle: String,
    rightMainValue: String,
    rightSubValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ReportMetricCard(
            title = leftTitle,
            mainValue = leftMainValue,
            subValue = leftSubValue,
            modifier = Modifier.weight(1f)
        )

        ReportMetricCard(
            title = rightTitle,
            mainValue = rightMainValue,
            subValue = rightSubValue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReportMetricCard(
    title: String,
    mainValue: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Orange_10,
        border = BorderStroke(
            width = 1.dp,
            color = Orange.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = mainValue,
                fontSize = 18.sp,
                color = Orange,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = subValue,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminCollapsedSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    isExpanded: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = color
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (isExpanded) {
                        "Hide"
                    } else {
                        "View $count"
                    },
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 6.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun RecentOrdersHeader(
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Recent Order History",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Cancelled and refund-pending orders are shown separately",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = CircleShape,
            color = Orange_10
        ) {
            Text(
                text = "$count",
                color = Orange,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 4.dp
                )
            )
        }
    }
}

@Composable
private fun AdminRecentOrderCard(
    order: Order,
    onAdminCancelOrder: (paymentReceivedType: String, cancelReason: String) -> Unit
) {
    val timeText = remember(order.createdAt) {
        formatOrderTime(order.createdAt)
    }

    var showCancelDialog by remember {
        mutableStateOf(false)
    }

    val canAdminCancel = order.status.lowercase() in listOf(
        OrderStatusValue.PENDING,
        OrderStatusValue.PREPARING,
        OrderStatusValue.READY
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            RecentOrderHeader(
                order = order,
                timeText = timeText
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                label = "User",
                value = order.studentName.ifBlank { "Not available" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Phone",
                value = order.studentPhone.ifBlank { "Not available" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Pickup",
                value = order.pickupSlot.ifBlank { "Not selected" }
            )

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OrderItemsPreview(order = order)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = order.status.ifBlank { "unknown" },
                    color = statusColor(order.status),
                    modifier = Modifier.weight(1f)
                )

                StatusPill(
                    text = order.paymentStatus.ifBlank { "unknown" },
                    color = paymentStatusColor(order.paymentStatus),
                    modifier = Modifier.weight(1f)
                )
            }

            if (order.upiPayerName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "UPI payer: ${order.upiPayerName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (canAdminCancel) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        showCancelDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = DangerRed
                    )
                ) {
                    Text(
                        text = "Admin Cancel Order",
                        color = DangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showCancelDialog) {
        AdminCancelOrderDialog(
            onDismiss = {
                showCancelDialog = false
            },
            onConfirm = { paymentReceivedType, cancelReason ->
                showCancelDialog = false

                onAdminCancelOrder(
                    paymentReceivedType,
                    cancelReason
                )
            }
        )
    }
}

@Composable
private fun AdminRefundPendingOrderCard(
    order: Order,
    onMarkRefundSettled: (refundReferenceId: String, refundNote: String) -> Unit
) {
    val context = LocalContext.current

    var showRefundDialog by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DangerRed.copy(alpha = 0.04f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = DangerRed.copy(alpha = 0.25f)
        ),
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
                        text = "Order #${order.orderId.takeLast(5).uppercase()}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Refund pending",
                        fontSize = 12.sp,
                        color = DangerRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = formatCurrency(order.totalPrice),
                    fontWeight = FontWeight.ExtraBold,
                    color = DangerRed
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                label = "User",
                value = order.studentName.ifBlank { "Not available" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Phone",
                value = order.studentPhone.ifBlank { "Not available" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Reason",
                value = order.cancelReason.ifBlank { "Not provided" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Cancelled by",
                value = order.cancelledBy.ifBlank { "Not available" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Payment",
                value = readableStatus(order.paymentStatus)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (order.studentPhone.isNotBlank()) {
                            val intent = Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:${order.studentPhone}")
                            )

                            context.startActivity(intent)
                        }
                    },
                    enabled = order.studentPhone.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "Call User",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        showRefundDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen
                    )
                ) {
                    Text(
                        text = "Mark Refunded",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showRefundDialog) {
        MarkRefundSettledDialog(
            onDismiss = {
                showRefundDialog = false
            },
            onConfirm = { refundReferenceId, refundNote ->
                showRefundDialog = false

                onMarkRefundSettled(
                    refundReferenceId,
                    refundNote
                )
            }
        )
    }
}

@Composable
private fun AdminCancelledOrderDetailsCard(
    order: Order
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.04f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        ),
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
                        text = "Order #${order.orderId.takeLast(5).uppercase()}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Cancelled by: ${order.cancelledBy.ifBlank { "Not available" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = formatCurrency(order.totalPrice),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                label = "User",
                value = order.studentName.ifBlank { "Not available" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Phone",
                value = order.studentPhone.ifBlank { "Not available" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Pickup",
                value = order.pickupSlot.ifBlank { "Not selected" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Reason",
                value = order.cancelReason.ifBlank { "Not provided" }
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Payment",
                value = readableStatus(order.paymentStatus)
            )

            Spacer(modifier = Modifier.height(5.dp))

            InfoRow(
                label = "Refund",
                value = readableStatus(order.refundStatus.ifBlank { RefundStatusValue.NONE })
            )

            if (order.refundReferenceId.isNotBlank()) {
                Spacer(modifier = Modifier.height(5.dp))

                InfoRow(
                    label = "Refund Ref",
                    value = order.refundReferenceId
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OrderItemsPreview(order = order)
        }
    }
}

@Composable
private fun RecentOrderHeader(
    order: Order,
    timeText: String
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
                text = "Order #${order.orderId.takeLast(5).uppercase()}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )

            if (timeText.isNotBlank()) {
                Text(
                    text = "Placed at $timeText",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = formatCurrency(order.totalPrice),
            fontWeight = FontWeight.ExtraBold,
            color = Orange
        )
    }
}

@Composable
private fun OrderItemsPreview(
    order: Order
) {
    val visibleItems = order.items.take(4)
    val remainingCount = order.items.size - visibleItems.size

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        visibleItems.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.name} x${item.quantity}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatCurrency(item.price * item.quantity),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (remainingCount > 0) {
            Text(
                text = "+$remainingCount more item${if (remainingCount == 1) "" else "s"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.22f)
        )
    ) {
        Text(
            text = readableStatus(text).uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 5.dp
            )
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
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmptyReportState(
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AdminCancelOrderDialog(
    onDismiss: () -> Unit,
    onConfirm: (paymentReceivedType: String, cancelReason: String) -> Unit
) {
    var selectedPaymentType by remember {
        mutableStateOf("")
    }

    var selectedReason by remember {
        mutableStateOf("")
    }

    var localError by remember {
        mutableStateOf<String?>(null)
    }

    val paymentOptions = listOf(
        PaymentReceivedType.NONE to "Payment not received",
        PaymentReceivedType.FULL to "Full payment received",
        PaymentReceivedType.PARTIAL to "Partial payment received"
    )

    val cancellationReasons = listOf(
        "Item/menu unavailable",
        "Shop emergency",
        "Gas/electricity issue",
        "Shop closing unexpectedly",
        "User/shop dispute",
        "Admin support override",
        "Other operational issue"
    )

    val paymentReceived = selectedPaymentType in listOf(
        PaymentReceivedType.PARTIAL,
        PaymentReceivedType.FULL
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Admin Cancel Order")
        },
        text = {
            Column {
                Text(
                    text = "Select payment status before cancelling",
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                paymentOptions.forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPaymentType = type
                                selectedReason = ""
                                localError = null
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPaymentType == type,
                            onClick = {
                                selectedPaymentType = type
                                selectedReason = ""
                                localError = null
                            }
                        )

                        Text(label)
                    }
                }

                if (selectedPaymentType == PaymentReceivedType.NONE) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This will cancel the order as payment not received. No refund will be marked.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (paymentReceived) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Select cancellation reason",
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    cancellationReasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = {
                                selectedReason = reason
                                localError = null
                            },
                            label = {
                                Text(reason)
                            },
                            modifier = Modifier.padding(
                                end = 6.dp,
                                bottom = 6.dp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Because payment was received, this order will be marked as Refund Pending.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                localError?.let {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        selectedPaymentType.isBlank() -> {
                            localError = "Please select payment status"
                            return@TextButton
                        }

                        paymentReceived && selectedReason.isBlank() -> {
                            localError = "Please select cancellation reason"
                            return@TextButton
                        }
                    }

                    onConfirm(
                        selectedPaymentType,
                        if (paymentReceived) {
                            selectedReason
                        } else {
                            "Payment not received"
                        }
                    )
                }
            ) {
                Text(
                    text = "Cancel Order",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Go Back")
            }
        }
    )
}

@Composable
private fun MarkRefundSettledDialog(
    onDismiss: () -> Unit,
    onConfirm: (refundReferenceId: String, refundNote: String) -> Unit
) {
    var refundReferenceId by remember {
        mutableStateOf("")
    }

    var refundNote by remember {
        mutableStateOf("")
    }

    var localError by remember {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Mark Refund Settled")
        },
        text = {
            Column {
                Text(
                    text = "Enter the refund transaction/reference ID after confirming money was returned to the user.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = refundReferenceId,
                    onValueChange = {
                        refundReferenceId = it
                        localError = null
                    },
                    label = {
                        Text("Refund Reference ID")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = refundNote,
                    onValueChange = {
                        refundNote = it
                    },
                    label = {
                        Text("Refund Note Optional")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                localError?.let {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (refundReferenceId.trim().isBlank()) {
                        localError = "Refund reference ID is required"
                        return@TextButton
                    }

                    onConfirm(
                        refundReferenceId.trim(),
                        refundNote.trim()
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
                Text("Cancel")
            }
        }
    )
}

private fun statusColor(
    status: String
): Color {
    return when (status.lowercase()) {
        OrderStatusValue.PENDING -> Color(0xFFE65100)
        OrderStatusValue.PREPARING -> InfoBlue
        OrderStatusValue.READY -> SuccessGreen
        OrderStatusValue.PICKED_UP -> SuccessGreen
        OrderStatusValue.CANCELLED -> DangerRed
        else -> Color.Gray
    }
}

private fun paymentStatusColor(
    paymentStatus: String
): Color {
    return when (paymentStatus.lowercase()) {
        "verified", PaymentStatusValue.PAID -> SuccessGreen
        PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED -> InfoBlue
        PaymentStatusValue.REFUNDED -> Color(0xFF6A1B9A)
        PaymentStatusValue.PAYMENT_NOT_RECEIVED -> DangerRed
        else -> Color(0xFFE65100)
    }
}

private fun readableStatus(
    status: String
): String {
    return status
        .ifBlank { "unknown" }
        .replace("_", " ")
}

private fun formatCurrency(
    value: Double
): String {
    return "₹${value.toInt()}"
}

private fun formatOrderTime(
    createdAt: Long
): String {
    return if (createdAt > 0) {
        SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(Date(createdAt))
    } else {
        ""
    }
}
