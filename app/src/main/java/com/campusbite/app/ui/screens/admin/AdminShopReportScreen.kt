package com.campusbite.app.ui.screens.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.AdminShop
import com.campusbite.app.ui.viewmodel.AdminShopReportSummary
import com.campusbite.app.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Orange_10 = Orange.copy(alpha = 0.12f)
private val SuccessGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShopReportScreen(
    shopId: String,
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val reportState by viewModel.shopReportState.collectAsState()

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

                    item(
                        key = "recent_title",
                        contentType = "title"
                    ) {
                        RecentOrdersHeader(
                            count = reportState.recentOrders.size
                        )
                    }

                    if (reportState.recentOrders.isEmpty()) {
                        item(
                            key = "empty_orders",
                            contentType = "empty"
                        ) {
                            EmptyReportState()
                        }
                    } else {
                        items(
                            items = reportState.recentOrders,
                            key = { order ->
                                order.orderId.ifBlank {
                                    "${order.createdAt}_${order.studentId}"
                                }
                            },
                            contentType = {
                                "recent_order"
                            }
                        ) { order ->
                            AdminRecentOrderCard(
                                order = order
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
                text = "Recent Orders",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Showing latest 50 orders only",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
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

                Spacer(modifier = Modifier.padding(6.dp))

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
                        text = "Cancelled/payment not received: ${summary.cancelledOrders}",
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
private fun AdminRecentOrderCard(
    order: Order
) {
    val timeText = remember(order.createdAt) {
        formatOrderTime(order.createdAt)
    }

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
                label = "Student",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = order.status.ifBlank { "unknown" },
                    isError = order.status.lowercase() == "cancelled"
                )

                StatusPill(
                    text = order.paymentStatus.ifBlank { "unknown" },
                    isError = order.paymentStatus.lowercase() == "payment_not_received"
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
private fun StatusPill(
    text: String,
    isError: Boolean
) {
    val bgColor = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    } else {
        Orange_10
    }

    val textColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        Orange
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Text(
            text = text.replace("_", " ").uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
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
private fun EmptyReportState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No orders found for this shop.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
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
