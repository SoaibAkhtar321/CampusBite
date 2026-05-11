package com.campusbite.app.ui.screens.order

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeDark
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.viewmodel.OrderViewModel

// ─── Status helpers ───────────────────────────────────────────────────────────

private enum class OrderStep(
    val label: String,
    val subtitle: String,
    val icon: ImageVector
) {
    PENDING(
        label = "Order Placed",
        subtitle = "Waiting for the shop to accept",
        icon = Icons.Default.Receipt
    ),
    ACCEPTED(
        label = "Accepted",
        subtitle = "Shop confirmed your order",
        icon = Icons.Default.ThumbUp
    ),
    PREPARING(
        label = "Preparing",
        subtitle = "Your food is being cooked",
        icon = Icons.Default.OutdoorGrill
    ),
    READY(
        label = "Ready for Pickup",
        subtitle = "Head to the counter now!",
        icon = Icons.Default.DoneAll
    )
}

/** Maps a Firestore status string to the index of the CURRENT (active) step */
private fun statusToStepIndex(status: String?): Int = when (status) {
    "pending"   -> 0
    "accepted"  -> 1
    "preparing" -> 2   // "Accept & Prepare" in staff panel sets "preparing"
    "ready"     -> 3
    "picked_up" -> 3
    else        -> 0
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderStatusScreen(
    orderId: String,
    onNavigateHome: () -> Unit,
    viewModel: OrderViewModel = hiltViewModel()
) {
    val order by viewModel.currentOrder.collectAsState()

    LaunchedEffect(orderId) {
        viewModel.listenToOrderById(orderId)
    }

    val currentStep = statusToStepIndex(order?.status)
    val isReady     = order?.status == "ready" || order?.status == "picked_up"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Order Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Ready Banner ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = isReady,
                enter   = fadeIn() + scaleIn(initialScale = 0.9f)
            ) {
                ReadyBanner()
            }

            if (isReady) Spacer(Modifier.height(20.dp))

            // ── Order ID chip ─────────────────────────────────────────────────
            if (order != null) {
                Surface(
                    shape  = RoundedCornerShape(50),
                    color  = OrangeLight,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(
                        text  = "Order #${order!!.orderId.takeLast(6).uppercase()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OrangeDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Timeline stepper ──────────────────────────────────────────────
            StatusTimeline(
                steps       = OrderStep.values().toList(),
                currentStep = currentStep
            )

            Spacer(Modifier.height(28.dp))

            // ── Order summary card ────────────────────────────────────────────
            order?.let { o ->
                OrderSummaryCard(o)
            }

            Spacer(Modifier.height(28.dp))

            // ── Pickup slot pill ──────────────────────────────────────────────
            order?.let { o ->
                if (o.pickupSlot.isNotBlank()) {
                    PickupSlotRow(slot = o.pickupSlot)
                    Spacer(Modifier.height(24.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Back button ───────────────────────────────────────────────────
            Button(
                onClick  = onNavigateHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("Back to Home", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Ready Banner ─────────────────────────────────────────────────────────────

@Composable
private fun ReadyBanner() {
    // Gentle pulse on the banner
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.03f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bannerScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape  = RoundedCornerShape(16.dp),
        color  = Color(0xFF2E7D32).copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎉", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Ready for Pickup!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 16.sp,
                    color      = Color(0xFF2E7D32)
                )
                Text(
                    "Head to the counter and show this screen.",
                    fontSize = 12.sp,
                    color    = Color(0xFF2E7D32).copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ─── Timeline stepper ─────────────────────────────────────────────────────────

@Composable
private fun StatusTimeline(
    steps: List<OrderStep>,
    currentStep: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val state = when {
                index < currentStep  -> StepState.DONE
                index == currentStep -> StepState.ACTIVE
                else                 -> StepState.UPCOMING
            }

            TimelineRow(step = step, state = state, isLast = index == steps.lastIndex)
        }
    }
}

private enum class StepState { DONE, ACTIVE, UPCOMING }

@Composable
private fun TimelineRow(
    step: OrderStep,
    state: StepState,
    isLast: Boolean
) {
    // Animate dot color
    val dotColor by animateColorAsState(
        targetValue = when (state) {
            StepState.DONE     -> Orange
            StepState.ACTIVE   -> Orange
            StepState.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(400),
        label = "dotColor"
    )

    val labelColor = when (state) {
        StepState.DONE     -> MaterialTheme.colorScheme.onBackground
        StepState.ACTIVE   -> Orange
        StepState.UPCOMING -> TextSecondary
    }

    // Pulse animation for the active dot
    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (state == StepState.ACTIVE) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier           = Modifier.fillMaxWidth(),
        verticalAlignment  = Alignment.Top
    ) {
        // Dot + connector column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Outer ring for active state
            Box(contentAlignment = Alignment.Center) {
                if (state == StepState.ACTIVE) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Orange.copy(alpha = 0.18f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        StepState.DONE -> Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(14.dp)
                        )
                        StepState.ACTIVE -> Icon(
                            imageVector        = step.icon,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(14.dp)
                        )
                        StepState.UPCOMING -> Icon(
                            imageVector        = step.icon,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Connector line
            if (!isLast) {
                val lineColor by animateColorAsState(
                    targetValue = if (state == StepState.DONE) Orange else MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = tween(400),
                    label = "lineColor"
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .background(lineColor)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Text content
        Column(modifier = Modifier.padding(top = 4.dp, bottom = if (isLast) 0.dp else 16.dp)) {
            Text(
                text       = step.label,
                fontWeight = if (state == StepState.ACTIVE) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize   = 15.sp,
                color      = labelColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = step.subtitle,
                fontSize = 12.sp,
                color    = TextSecondary.copy(alpha = if (state == StepState.UPCOMING) 0.5f else 1f)
            )
        }
    }
}

// ─── Order Summary Card ───────────────────────────────────────────────────────

@Composable
private fun OrderSummaryCard(order: com.campusbite.app.data.model.Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border   = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Your Order",
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quantity badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = OrangeLight
                        ) {
                            Text(
                                text  = "×${item.quantity}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangeDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text     = item.name,
                            fontSize = 13.sp,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }
                    Text(
                        text       = "₹${(item.price * item.quantity).toInt()}",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Orange
                    )
                }
                // Cooking note (if any)
                if (item.cookingNote.isNotBlank()) {
                    Text(
                        text     = "📝 ${item.cookingNote}",
                        fontSize = 11.sp,
                        color    = TextSecondary,
                        modifier = Modifier.padding(start = 40.dp, bottom = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier  = Modifier.padding(vertical = 10.dp),
                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Total",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 14.sp,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "₹${order.totalPrice.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 15.sp,
                    color      = Orange
                )
            }
        }
    }
}

// ─── Pickup Slot Row ──────────────────────────────────────────────────────────

@Composable
private fun PickupSlotRow(slot: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = MaterialTheme.colorScheme.surface,
        border   = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Default.Schedule,
                contentDescription = null,
                tint               = Orange,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Pickup Slot",
                fontSize   = 13.sp,
                color      = TextSecondary,
                modifier   = Modifier.weight(1f)
            )
            Text(
                slot,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                color      = Orange
            )
        }
    }
}
