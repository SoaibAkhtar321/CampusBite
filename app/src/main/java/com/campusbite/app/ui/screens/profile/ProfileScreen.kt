package com.campusbite.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeDark
import com.campusbite.app.ui.viewmodel.OrderViewModel
import com.campusbite.app.ui.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth

// Dark-mode-safe tint replacing hardcoded OrangeLight
private val Orange_10 = Orange.copy(alpha = 0.12f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToOrderHistory: () -> Unit,
    onNavigateToOrderStatus: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    orderViewModel: OrderViewModel = hiltViewModel()
) {
    val user by viewModel.userProfile.collectAsState()
    val activeOrder by orderViewModel.activeOrder.collectAsState()

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        orderViewModel.listenToActiveOrder(uid)
        // No need to loadUserOrders here — that's OrderHistoryScreen's job
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Avatar ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Orange_10),
                contentAlignment = Alignment.Center
            ) {
                val initials = user?.name
                    ?.split(" ")
                    ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    ?.take(2)
                    ?.joinToString("")
                    ?: "?"
                Text(
                    text = initials,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrangeDark
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = user?.name ?: "Loading...",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = user?.email ?: "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!user?.phoneNumber.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = user?.phoneNumber ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Active order card (only shown when an order is in-progress) ───
            if (activeOrder != null) {
                SectionLabel("Active Order")
                Spacer(Modifier.height(8.dp))
                ActiveOrderCard(
                    order = activeOrder!!,
                    onClick = { onNavigateToOrderStatus(activeOrder!!.orderId) }
                )
                Spacer(Modifier.height(24.dp))
            }

            // ── Orders section — always shows Order History nav row ───────────
            SectionLabel("Orders")
            Spacer(Modifier.height(8.dp))
            ProfileOptionItem(
                title = "Order History",
                subtitle = "View all your past orders",
                icon = Icons.Default.ReceiptLong,
                onClick = onNavigateToOrderHistory
            )

            Spacer(Modifier.height(24.dp))

            // ── Support ───────────────────────────────────────────────────────
            SectionLabel("Support")
            Spacer(Modifier.height(8.dp))
            ProfileOptionItem(
                title = "Refunds & Support",
                subtitle = "Get help with your orders",
                icon = Icons.Default.SupportAgent,
                onClick = { /* TODO */ }
            )

            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.weight(1f))

            // ── Logout ────────────────────────────────────────────────────────
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Active Order Card ────────────────────────────────────────────────────────

@Composable
private fun ActiveOrderCard(order: Order, onClick: () -> Unit) {
    val statusLabel = when (order.status) {
        "pending"   -> "⏳ Waiting for acceptance"
        "accepted"  -> "👍 Accepted by shop"
        "preparing" -> "👨‍🍳 Being prepared now"
        "ready"     -> "✅ Ready for pickup!"
        else        -> "Tracking..."
    }
    val isReady = order.status == "ready"
    val bgColor = if (isReady) Color(0xFF2E7D32).copy(alpha = 0.10f) else Orange.copy(alpha = 0.08f)
    val borderColor = if (isReady) Color(0xFF2E7D32).copy(alpha = 0.4f) else Orange.copy(alpha = 0.35f)
    val accentColor = if (isReady) Color(0xFF2E7D32) else OrangeDark

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (isReady) "✅" else "🍽️", fontSize = 20.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = accentColor
                )
                Spacer(Modifier.height(2.dp))
                val itemSummary = order.items
                    .take(2)
                    .joinToString(", ") { it.name }
                    .let { if (order.items.size > 2) "$it +${order.items.size - 2} more" else it }
                Text(
                    text = itemSummary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (order.pickupSlot.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pickup: ${order.pickupSlot}  •  ₹${order.totalPrice.toInt()}",
                        fontSize = 11.sp,
                        color = accentColor.copy(alpha = 0.75f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Section label ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

// ─── Profile option item ──────────────────────────────────────────────────────

@Composable
private fun ProfileOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Orange_10),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
