package com.campusbite.app.ui.screens.shopkeeper

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.ShopkeeperSalesSummary
import com.campusbite.app.ui.viewmodel.ShopkeeperViewModel
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private val StatusPending = Color(0xFFE65100)
private val StatusPreparing = Color(0xFF1565C0)
private val StatusReady = Color(0xFF2E7D32)
private val StatusCancelled = Color(0xFFD32F2F)

private val Orange_10 = Orange.copy(alpha = 0.12f)

private fun statusColor(status: String): Color {
    return when (status.lowercase()) {
        OrderStatusValue.PENDING -> StatusPending
        OrderStatusValue.PREPARING -> StatusPreparing
        OrderStatusValue.READY -> StatusReady
        OrderStatusValue.CANCELLED -> StatusCancelled
        else -> Color.Gray
    }
}

private fun statusLabel(status: String): String {
    return when (status.lowercase()) {
        OrderStatusValue.PENDING -> "⏳ Pending"
        OrderStatusValue.PREPARING -> "👨‍🍳 Preparing"
        OrderStatusValue.READY -> "✅ Ready"
        OrderStatusValue.PICKED_UP -> "📦 Picked Up"
        OrderStatusValue.CANCELLED -> "❌ Cancelled"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

private fun paymentStatusLabel(paymentStatus: String): String {
    return when (paymentStatus.lowercase()) {
        "verified", PaymentStatusValue.PAID -> "PAID"
        PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED -> "PARTIAL PAID"
        PaymentStatusValue.REFUNDED -> "REFUNDED"
        PaymentStatusValue.PAYMENT_NOT_RECEIVED -> "NOT RECEIVED"
        else -> "VERIFY PAYMENT"
    }
}

private fun paymentStatusColor(paymentStatus: String): Color {
    return when (paymentStatus.lowercase()) {
        "verified", PaymentStatusValue.PAID -> Color(0xFF2E7D32)
        PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED -> Color(0xFF1565C0)
        PaymentStatusValue.REFUNDED -> Color(0xFF6A1B9A)
        PaymentStatusValue.PAYMENT_NOT_RECEIVED -> Color(0xFFD32F2F)
        else -> Color(0xFFE65100)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopkeeperDashboardScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToMenu: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit,
    viewModel: ShopkeeperViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val salesSummary by viewModel.salesSummary.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val shopOpen by viewModel.shopOpen.collectAsStateWithLifecycle()
    val closedSlots by viewModel.closedSlots.collectAsStateWithLifecycle()

    var showRefundPending by remember {
        mutableStateOf(false)
    }

    val activeOrders = remember(orders) {
        orders.filter { order ->
            order.status.lowercase() in listOf(
                OrderStatusValue.PENDING,
                OrderStatusValue.PREPARING,
                OrderStatusValue.READY
            )
        }
    }

    val refundPendingOrders = remember(orders) {
        orders.filter { order ->
            order.status.lowercase() == OrderStatusValue.CANCELLED &&
                    order.refundStatus.lowercase() == RefundStatusValue.REFUND_PENDING
        }
    }

    val pendingCount = remember(activeOrders) {
        activeOrders.count {
            it.status.lowercase() == OrderStatusValue.PENDING
        }
    }

    val preparingCount = remember(activeOrders) {
        activeOrders.count {
            it.status.lowercase() == OrderStatusValue.PREPARING
        }
    }

    val readyCount = remember(activeOrders) {
        activeOrders.count {
            it.status.lowercase() == OrderStatusValue.READY
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Shopkeeper Panel",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )

                        Text(
                            text = if (shopOpen) {
                                "Accepting orders"
                            } else {
                                "Shop closed"
                            },
                            fontSize = 11.sp,
                            color = if (shopOpen) {
                                StatusReady
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                },
                actions = {
                    Switch(
                        checked = shopOpen,
                        onCheckedChange = { isOpen ->
                            viewModel.toggleShopOpen(isOpen)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = StatusReady,
                            uncheckedTrackColor = MaterialTheme.colorScheme.error.copy(
                                alpha = 0.5f
                            )
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(
                        onClick = onNavigateToMenu
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = "Menu",
                            tint = Orange
                        )
                    }

                    IconButton(
                        onClick = onNavigateToProfile
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Orange
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Orange)
            }

            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryChip(
                        label = "Pending",
                        count = pendingCount,
                        color = StatusPending,
                        modifier = Modifier.weight(1f)
                    )

                    SummaryChip(
                        label = "Preparing",
                        count = preparingCount,
                        color = StatusPreparing,
                        modifier = Modifier.weight(1f)
                    )

                    SummaryChip(
                        label = "Ready",
                        count = readyCount,
                        color = StatusReady,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                AnalyticsShortcutCard(
                    refundPendingCount = refundPendingOrders.size,
                    onClick = onNavigateToAnalytics
                )
            }

            item {
                SlotControlCard(
                    closedSlots = closedSlots,
                    onToggleSlot = { slot ->
                        viewModel.toggleSlot(slot)
                    }
                )
            }

            item {
                SectionTitleWithCount(
                    title = "Active Orders",
                    count = activeOrders.size,
                    color = Orange
                )
            }

            if (activeOrders.isEmpty()) {
                item {
                    EmptyOrdersCard()
                }
            }

            items(
                items = activeOrders,
                key = { order -> order.orderId }
            ) { order ->
                OrderCard(
                    order = order,
                    onUpdateStatus = { newStatus ->
                        viewModel.updateOrderStatus(
                            orderId = order.orderId,
                            newStatus = newStatus
                        )
                    },
                    onCancelOrder = { paymentReceivedType, cancelReason, paymentReceivedAmount ->
                        viewModel.cancelOrderByShopkeeper(
                            orderId = order.orderId,
                            paymentReceivedType = paymentReceivedType,
                            cancelReason = cancelReason,
                            paymentReceivedAmount = paymentReceivedAmount
                        )
                    }
                )
            }

            if (refundPendingOrders.isNotEmpty()) {
                item {
                    RefundPendingCollapsedHeader(
                        count = refundPendingOrders.size,
                        isExpanded = showRefundPending,
                        onClick = {
                            showRefundPending = !showRefundPending
                        }
                    )
                }

                if (showRefundPending) {
                    items(
                        items = refundPendingOrders,
                        key = { order -> "refund_${order.orderId}" }
                    ) { order ->
                        CompactRefundPendingOrderCard(
                            order = order,
                            onMarkRefundSettled = { refundReferenceId, refundNote ->
                                viewModel.markRefundSettled(
                                    orderId = order.orderId,
                                    refundReferenceId = refundReferenceId,
                                    refundNote = refundNote
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsShortcutCard(
    refundPendingCount: Int,
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Sales & Analytics",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "View today, monthly and lifetime reports",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (refundPendingCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = "Refund pending: $refundPendingCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 7.dp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Open analytics",
                tint = Orange,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
@Composable
private fun CompactSalesSummaryCard(
    summary: ShopkeeperSalesSummary,
    refundPendingCount: Int
) {
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
            Text(
                text = "Sales Summary",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactSalesBox(
                    title = "Today",
                    value = "₹${summary.todaySales.toInt()}",
                    subValue = "${summary.todayOrders} orders",
                    modifier = Modifier.weight(1f)
                )

                CompactSalesBox(
                    title = "Month",
                    value = "₹${summary.monthSales.toInt()}",
                    subValue = "${summary.monthOrders} orders",
                    modifier = Modifier.weight(1f)
                )

                CompactSalesBox(
                    title = "Pending",
                    value = "${summary.pendingPaymentOrders}",
                    subValue = "verify",
                    modifier = Modifier.weight(1f)
                )
            }

            if (refundPendingCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "Refund pending: $refundPendingCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSalesBox(
    title: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Orange_10,
        border = BorderStroke(
            width = 1.dp,
            color = Orange.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = value,
                fontSize = 16.sp,
                color = Orange,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = subValue,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RefundPendingCollapsedHeader(
    count: Int,
    isExpanded: Boolean,
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
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
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
                    text = "Refund Pending Orders",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$count order${if (count == 1) "" else "s"} need manual refund",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (isExpanded) {
                        "Hide"
                    } else {
                        "View"
                    },
                    color = MaterialTheme.colorScheme.error,
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
private fun CompactRefundPendingOrderCard(
    order: Order,
    onMarkRefundSettled: (refundReferenceId: String, refundNote: String) -> Unit
) {
    val context = LocalContext.current

    var showRefundDialog by remember {
        mutableStateOf(false)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = StatusCancelled.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
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
                        fontSize = 14.sp
                    )

                    Text(
                        text = order.cancelReason.ifBlank {
                            "Refund pending"
                        },
                        fontSize = 12.sp,
                        color = StatusCancelled,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val refundAmountToShow = if (order.refundAmount > 0.0) {
                    order.refundAmount
                } else {
                    order.totalPrice
                }

                Text(
                    text = "Refund ₹${refundAmountToShow.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    color = StatusCancelled,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${order.studentName.ifBlank { "Student" }} • ${order.studentPhone.ifBlank { "No phone" }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
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
                        text = "Call",
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
                        containerColor = StatusReady
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
private fun SalesSummaryCard(
    summary: ShopkeeperSalesSummary,
    refundPendingCount: Int
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
                text = "Only verified payments are counted in sales.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SalesMetricCard(
                    title = "Today",
                    mainValue = "₹${summary.todaySales.toInt()}",
                    subValue = "${summary.todayOrders} orders",
                    modifier = Modifier.weight(1f)
                )

                SalesMetricCard(
                    title = "This Month",
                    mainValue = "₹${summary.monthSales.toInt()}",
                    subValue = "${summary.monthOrders} orders",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SalesMetricCard(
                    title = "Lifetime",
                    mainValue = "₹${summary.lifetimeSales.toInt()}",
                    subValue = "${summary.lifetimeOrders} orders",
                    modifier = Modifier.weight(1f)
                )

                SalesMetricCard(
                    title = "Pending Pay",
                    mainValue = "${summary.pendingPaymentOrders}",
                    subValue = "need verify",
                    modifier = Modifier.weight(1f)
                )
            }

            if (summary.cancelledOrders > 0 || refundPendingCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                    ) {
                        if (summary.cancelledOrders > 0) {
                            Text(
                                text = "Cancelled/payment not received: ${summary.cancelledOrders}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (refundPendingCount > 0) {
                            Text(
                                text = "Refund pending: $refundPendingCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesMetricCard(
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
private fun SectionTitleWithCount(
    title: String,
    count: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$count",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 2.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyOrdersCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎉",
                fontSize = 40.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "All caught up!",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "No active orders right now",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    onUpdateStatus: (String) -> Unit,
    onCancelOrder: (
        paymentReceivedType: String,
        cancelReason: String,
        paymentReceivedAmount: Double
    ) -> Unit
){
    val statusCol = statusColor(order.status)
    val context = LocalContext.current

    var showCancelDialog by remember {
        mutableStateOf(false)
    }

    val timeStr = remember(order.createdAt) {
        if (order.createdAt > 0) {
            SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            ).format(Date(order.createdAt))
        } else {
            ""
        }
    }

    val canCancelOrder = order.status.lowercase() in listOf(
        OrderStatusValue.PENDING,
        OrderStatusValue.PREPARING
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(statusCol)
            )

            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Order #${order.orderId.takeLast(5).uppercase()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )

                        if (timeStr.isNotBlank()) {
                            Text(
                                text = "Placed at $timeStr",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    StatusBadge(
                        status = order.status
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                order.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Orange_10)
                                    .padding(
                                        horizontal = 6.dp,
                                        vertical = 2.dp
                                    )
                            ) {
                                Text(
                                    text = "×${item.quantity}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Orange
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Text(
                                    text = item.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (item.cookingNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📝",
                                            fontSize = 10.sp
                                        )

                                        Spacer(modifier = Modifier.width(3.dp))

                                        Text(
                                            text = item.cookingNote,
                                            fontSize = 11.sp,
                                            color = Orange,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "₹${(item.price * item.quantity).toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                PaymentVerificationCard(
                    order = order,
                    onCallStudent = {
                        if (order.studentPhone.isNotBlank()) {
                            val intent = Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:${order.studentPhone}")
                            )

                            context.startActivity(intent)
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Orange,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Pickup: ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = order.pickupSlot,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Orange
                        )
                    }

                    Text(
                        text = "₹${order.totalPrice.toInt()}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (order.status.lowercase()) {
                    OrderStatusValue.PENDING -> {
                        ActionButton(
                            label = "Payment Received & Start Preparing",
                            color = StatusPending,
                            onClick = {
                                onUpdateStatus(OrderStatusValue.PREPARING)
                            }
                        )
                    }

                    OrderStatusValue.PREPARING -> {
                        ActionButton(
                            label = "Mark as Ready for Pickup",
                            color = StatusPreparing,
                            onClick = {
                                onUpdateStatus(OrderStatusValue.READY)
                            }
                        )
                    }

                    OrderStatusValue.READY -> {
                        ActionButton(
                            label = "Mark as Picked Up ✓",
                            color = StatusReady,
                            onClick = {
                                onUpdateStatus(OrderStatusValue.PICKED_UP)
                            }
                        )
                    }
                }

                if (canCancelOrder) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showCancelDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Cancel Order",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        CancelOrderDialog(
            orderTotalPrice = order.totalPrice,
            onDismiss = {
                showCancelDialog = false
            },
            onConfirm = { paymentReceivedType, cancelReason, paymentReceivedAmount ->
                showCancelDialog = false
                onCancelOrder(
                    paymentReceivedType,
                    cancelReason,
                    paymentReceivedAmount
                )
            }
        )
    }
}

@Composable
private fun PaymentVerificationCard(
    order: Order,
    onCallStudent: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Orange_10
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Payment Verification",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Orange
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                label = "UPI Payer",
                value = order.upiPayerName.ifBlank {
                    "Not provided"
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            InfoRow(
                label = "Student",
                value = order.studentName
            )

            Spacer(modifier = Modifier.height(6.dp))

            InfoRow(
                label = "Phone",
                value = order.studentPhone
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCallStudent,
                    modifier = Modifier.weight(1f),
                    enabled = order.studentPhone.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text("Call")
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = paymentStatusColor(order.paymentStatus)
                ) {
                    Text(
                        text = paymentStatusLabel(order.paymentStatus),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CancelOrderDialog(
    orderTotalPrice: Double,
    onDismiss: () -> Unit,
    onConfirm: (
        paymentReceivedType: String,
        cancelReason: String,
        paymentReceivedAmount: Double
    ) -> Unit
) {
    var selectedPaymentType by remember {
        mutableStateOf("")
    }

    var selectedReason by remember {
        mutableStateOf("")
    }

    var partialAmountText by remember {
        mutableStateOf("")
    }

    var localError by remember {
        mutableStateOf<String?>(null)
    }

    val paymentOptions = listOf(
        PaymentReceivedType.NONE to "No payment received",
        PaymentReceivedType.FULL to "Full payment received",
        PaymentReceivedType.PARTIAL to "Partial payment received"
    )

    val fullPaymentCancellationReasons = listOf(
        "Item/menu unavailable",
        "Shop emergency",
        "Gas/electricity issue",
        "Shop closing unexpectedly",
        "Other operational issue"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cancel Order")
        },
        text = {
            Column {
                Text(
                    text = "How much payment did you receive?",
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
                                partialAmountText = ""
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
                                partialAmountText = ""
                                localError = null
                            }
                        )

                        Text(label)
                    }
                }

                when (selectedPaymentType) {
                    PaymentReceivedType.NONE -> {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Order will be cancelled because payment was not received. No refund required.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PaymentReceivedType.FULL -> {
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Why are you cancelling this order?",
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        fullPaymentCancellationReasons.forEach { reason ->
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
                            text = "Refund amount: ₹${orderTotalPrice.toInt()}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    PaymentReceivedType.PARTIAL -> {
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = partialAmountText,
                            onValueChange = { value ->
                                partialAmountText = value
                                    .filter { it.isDigit() }
                                    .take(5)
                                localError = null
                            },
                            label = {
                                Text("Amount received")
                            },
                            prefix = {
                                Text("₹")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Reason will be saved as: Payment incomplete / wrong amount paid.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Only the received amount will be marked as Refund Pending.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
                    val partialAmount = partialAmountText.toDoubleOrNull() ?: 0.0

                    when {
                        selectedPaymentType.isBlank() -> {
                            localError = "Please select payment status"
                            return@TextButton
                        }

                        selectedPaymentType == PaymentReceivedType.FULL &&
                                selectedReason.isBlank() -> {
                            localError = "Please select cancellation reason"
                            return@TextButton
                        }

                        selectedPaymentType == PaymentReceivedType.PARTIAL &&
                                partialAmount <= 0.0 -> {
                            localError = "Enter the amount received"
                            return@TextButton
                        }

                        selectedPaymentType == PaymentReceivedType.PARTIAL &&
                                partialAmount >= orderTotalPrice -> {
                            localError = "For full amount, select Full payment received"
                            return@TextButton
                        }
                    }

                    val finalReceivedAmount = when (selectedPaymentType) {
                        PaymentReceivedType.FULL -> orderTotalPrice
                        PaymentReceivedType.PARTIAL -> partialAmount
                        else -> 0.0
                    }

                    val finalCancelReason = when (selectedPaymentType) {
                        PaymentReceivedType.NONE -> "Payment not received"
                        PaymentReceivedType.PARTIAL -> "Payment incomplete / wrong amount paid"
                        PaymentReceivedType.FULL -> selectedReason
                        else -> "Order cancelled"
                    }

                    onConfirm(
                        selectedPaymentType,
                        finalCancelReason,
                        finalReceivedAmount
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
                    text = "Enter the refund transaction/reference ID after sending money back to the user.",
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

@Composable
private fun SlotControlCard(
    closedSlots: List<String>,
    onToggleSlot: (String) -> Unit
) {
    val dynamicSlots = remember {
        val now = LocalTime.now()
        val minutesToAdd = 15 - (now.minute % 15)
        val startTime = now
            .plusMinutes(minutesToAdd.toLong())
            .withSecond(0)
            .withNano(0)

        val formatter = DateTimeFormatter.ofPattern("hh:mm a")

        List(6) { i ->
            startTime
                .plusMinutes(i * 15L)
                .format(formatter)
                .uppercase()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
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
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Orange,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Slot Controls",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Close slots you can't fulfil",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dynamicSlots, key = { it }) { slot ->
                    val isClosed = closedSlots.contains(slot)

                    val bgColor by animateColorAsState(
                        targetValue = if (isClosed) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        } else {
                            Orange_10
                        },
                        animationSpec = tween(200),
                        label = "slot_bg"
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isClosed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Orange
                        },
                        animationSpec = tween(200),
                        label = "slot_text"
                    )

                    val borderColor by animateColorAsState(
                        targetValue = if (isClosed) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        } else {
                            Orange.copy(alpha = 0.4f)
                        },
                        animationSpec = tween(200),
                        label = "slot_border"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(
                                width = 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                onToggleSlot(slot)
                            }
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = slot,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )

                            Text(
                                text = if (isClosed) {
                                    "CLOSED"
                                } else {
                                    "OPEN"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textColor.copy(alpha = 0.7f),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )

            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StatusBadge(
    status: String
) {
    val color = statusColor(status)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = statusLabel(status),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 4.dp
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}