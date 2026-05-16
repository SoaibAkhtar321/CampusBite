package com.campusbite.app.ui.screens.shopkeeper

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.ShopkeeperViewModel
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

// ── Semantic status colours ───────────────────────────────────────────────────
private val StatusPending = Color(0xFFE65100)
private val StatusPreparing = Color(0xFF1565C0)
private val StatusReady = Color(0xFF2E7D32)

private fun statusColor(status: String) = when (status) {
    "pending" -> StatusPending
    "preparing" -> StatusPreparing
    "ready" -> StatusReady
    else -> Color.Gray
}

private fun statusLabel(status: String) = when (status) {
    "pending" -> "⏳ Pending"
    "preparing" -> "👨‍🍳 Preparing"
    "ready" -> "✅ Ready"
    else -> status.replaceFirstChar { it.uppercase() }
}

private val Orange_10 = Orange.copy(alpha = 0.12f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopkeeperDashboardScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToMenu: () -> Unit = {},  // ← ADD THIS
    viewModel: ShopkeeperViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val shopOpen by viewModel.shopOpen.collectAsState()
    val closedSlots by viewModel.closedSlots.collectAsState()

    val pendingCount = orders.count { it.status == "pending" }
    val preparingCount = orders.count { it.status == "preparing" }
    val readyCount = orders.count { it.status == "ready" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shopkeeper Panel", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(
                            text = if (shopOpen) "Accepting orders" else "Shop closed",
                            fontSize = 11.sp,
                            color = if (shopOpen) StatusReady else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    Switch(
                        checked = shopOpen,
                        onCheckedChange = { viewModel.toggleShopOpen(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = StatusReady,
                            uncheckedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    // ✅ ADD THIS BUTTON
                    IconButton(onClick = onNavigateToMenu) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = "Menu", tint = Orange)
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = Orange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    SummaryChip("Pending", pendingCount, StatusPending, Modifier.weight(1f))
                    SummaryChip("Preparing", preparingCount, StatusPreparing, Modifier.weight(1f))
                    SummaryChip("Ready", readyCount, StatusReady, Modifier.weight(1f))
                }
            }

            item {
                SlotControlCard(
                    closedSlots = closedSlots,
                    onToggleSlot = { viewModel.toggleSlot(it) }
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Active Orders", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    if (orders.isNotEmpty()) {
                        Surface(shape = CircleShape, color = Orange_10) {
                            Text(
                                text = "${orders.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (orders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "All caught up!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No active orders right now",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(orders, key = { it.orderId }) { order ->
                OrderCard(
                    order = order,
                    onUpdateStatus = { newStatus ->
                        viewModel.updateOrderStatus(order.orderId, newStatus)
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OrderCard(order: Order, onUpdateStatus: (String) -> Unit) {
    val statusCol = statusColor(order.status)
    val timeStr = remember(order.createdAt) {
        if (order.createdAt > 0)
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(order.createdAt))
        else ""
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(statusCol)
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Order #${order.orderId.takeLast(5).uppercase()}",
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
                    StatusBadge(status = order.status)
                }

                Spacer(Modifier.height(12.dp))

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
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "×${item.quantity}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Orange
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    item.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (item.cookingNote.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📝", fontSize = 10.sp)
                                        Spacer(Modifier.width(3.dp))
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
                            "₹${(item.price * item.quantity).toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Orange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Pickup: ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            order.pickupSlot,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Orange
                        )
                    }
                    Text(
                        "₹${order.totalPrice.toInt()}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(12.dp))

                when (order.status) {
                    "pending" -> ActionButton(
                        label = "Accept & Start Preparing",
                        color = StatusPending,
                        onClick = { onUpdateStatus("preparing") }
                    )
                    "preparing" -> ActionButton(
                        label = "Mark as Ready for Pickup",
                        color = StatusPreparing,
                        onClick = { onUpdateStatus("ready") }
                    )
                    "ready" -> ActionButton(
                        label = "Mark as Picked Up ✓",
                        color = StatusReady,
                        onClick = { onUpdateStatus("picked_up") }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slot Control Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SlotControlCard(
    closedSlots: List<String>,
    onToggleSlot: (String) -> Unit
) {
    val dynamicSlots = remember {
        val now = LocalTime.now()
        val minutesToAdd = 15 - (now.minute % 15)
        val startTime = now.plusMinutes(minutesToAdd.toLong()).withSecond(0).withNano(0)
        val formatter = DateTimeFormatter.ofPattern("hh:mm a")
        List(6) { i -> startTime.plusMinutes(i * 15L).format(formatter).uppercase() }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Orange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Slot Controls", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Close slots you can't fulfil",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dynamicSlots) { slot ->
                    val isClosed = closedSlots.contains(slot)
                    val bgColor by animateColorAsState(
                        targetValue = if (isClosed) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else Orange_10,
                        animationSpec = tween(200), label = "slot_bg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isClosed) MaterialTheme.colorScheme.error else Orange,
                        animationSpec = tween(200), label = "slot_text"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isClosed) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else Orange.copy(alpha = 0.4f),
                        animationSpec = tween(200), label = "slot_border"
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable { onToggleSlot(slot) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                slot,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                if (isClosed) "CLOSED" else "OPEN",
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

// ─────────────────────────────────────────────────────────────────────────────
// Reusable composables
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SummaryChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = statusColor(status)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = statusLabel(status),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}