package com.campusbite.app.ui.screens.order

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeDark
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.viewmodel.OrderViewModel
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue

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

private enum class StepState {
    DONE,
    ACTIVE,
    UPCOMING
}

private fun statusToStepIndex(status: String?): Int {
    return when (status?.lowercase()) {
        OrderStatusValue.PENDING -> 0
        OrderStatusValue.ACCEPTED -> 1
        OrderStatusValue.PREPARING -> 2
        OrderStatusValue.READY -> 3
        OrderStatusValue.PICKED_UP -> 3
        OrderStatusValue.CANCELLED -> 0
        else -> 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderStatusScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    viewModel: OrderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val order by viewModel.currentOrder.collectAsState()

    LaunchedEffect(orderId) {
        viewModel.listenToOrderById(orderId)
    }

    DisposableEffect(Unit) {
        onDispose {
            // Do not clear currentOrder here.
            // Student profile and notifications depend on the active listener/userOrders state.
        }
    }

    val currentStep = statusToStepIndex(order?.status)

    val isReady = order?.status?.lowercase() == OrderStatusValue.READY ||
            order?.status?.lowercase() == OrderStatusValue.PICKED_UP

    val isCancelled = order?.status?.lowercase() == OrderStatusValue.CANCELLED

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
                    IconButton(
                        onClick = onNavigateBack
                    ) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = isReady,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.9f
                )
            ) {
                ReadyBanner()
            }

            AnimatedVisibility(
                visible = isCancelled && order != null,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.9f
                )
            ) {
                order?.let { currentOrder ->
                    CancelledOrderBanner(
                        order = currentOrder
                    )
                }
            }

            if (isReady || isCancelled) {
                Spacer(modifier = Modifier.height(20.dp))
            }

            order?.let { currentOrder ->
                OrderIdBadge(
                    orderId = currentOrder.orderId
                )
            }

            if (!isCancelled) {
                StatusTimeline(
                    steps = OrderStep.values().toList(),
                    currentStep = currentStep
                )

                Spacer(modifier = Modifier.height(28.dp))
            }

            order?.let { currentOrder ->
                OrderSummaryCard(
                    order = currentOrder
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (currentOrder.pickupSlot.isNotBlank()) {
                    PickupSlotRow(
                        slot = currentOrder.pickupSlot
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isCancelled) {
                    CancelledPaymentInfoCard(
                        order = currentOrder
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                CallShopkeeperButton(
                    phone = currentOrder.shopkeeperPhone,
                    onCallClick = {
                        val phone = currentOrder.shopkeeperPhone.trim()

                        if (phone.isNotBlank()) {
                            val intent = Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:$phone")
                            )

                            context.startActivity(intent)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange
                )
            ) {
                Text(
                    text = "Back",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OrderIdBadge(
    orderId: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = OrangeLight,
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        Text(
            text = "Order #${orderId.takeLast(6).uppercase()}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = OrangeDark,
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 6.dp
            )
        )
    }
}

@Composable
private fun ReadyBanner() {
    val infiniteTransition = rememberInfiniteTransition(
        label = "ready_banner_pulse"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ready_banner_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2E7D32).copy(alpha = 0.12f),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFF2E7D32).copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎉",
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Ready for Pickup!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF2E7D32)
                )

                Text(
                    text = "Head to the counter and show this screen.",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32).copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun CancelledOrderBanner(
    order: Order
) {
    val refundStatus = order.refundStatus.lowercase()
    val paymentStatus = order.paymentStatus.lowercase()

    val isRefundPending = refundStatus == RefundStatusValue.REFUND_PENDING
    val isRefunded = refundStatus == RefundStatusValue.REFUNDED

    val title = when {
        isRefunded -> "Order Cancelled - Refund Settled"
        isRefundPending -> "Order Cancelled - Refund Pending"
        else -> "Order Cancelled"
    }

    val mainMessage = when {
        isRefunded -> "The shopkeeper has marked your refund as settled."
        isRefundPending -> "The shopkeeper has received your payment and will settle the refund manually."
        paymentStatus == PaymentStatusValue.PAYMENT_NOT_RECEIVED ->
            "The shopkeeper cancelled this order because payment was not received."
        else -> "This order has been cancelled by the shopkeeper."
    }

    val helperMessage = when {
        isRefunded && order.refundReferenceId.isNotBlank() ->
            "Refund Reference: ${order.refundReferenceId}"

        isRefunded ->
            "Please contact the shopkeeper if the refund is not visible in your account."

        isRefundPending ->
            "You may call the shopkeeper if needed. Keep your payment proof ready."

        else ->
            "If you already paid, call the shopkeeper and share your payment proof."
    }

    val bannerColor = if (isRefunded) {
        Color(0xFF2E7D32)
    } else {
        Color(0xFFD32F2F)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bannerColor.copy(alpha = 0.10f),
        border = BorderStroke(
            width = 1.dp,
            color = bannerColor.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = if (isRefunded) {
                    "✅"
                } else {
                    "⚠️"
                },
                fontSize = 26.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = bannerColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (order.cancelReason.isNotBlank()) {
                    Text(
                        text = "Reason: ${order.cancelReason}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = bannerColor.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = mainMessage,
                    fontSize = 13.sp,
                    color = bannerColor.copy(alpha = 0.88f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = helperMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CancelledPaymentInfoCard(
    order: Order
) {
    val refundStatus = order.refundStatus.lowercase()
    val paymentStatus = order.paymentStatus.lowercase()

    val title = when {
        refundStatus == RefundStatusValue.REFUND_PENDING -> "Refund pending"
        refundStatus == RefundStatusValue.REFUNDED -> "Refund settled"
        paymentStatus == PaymentStatusValue.PAYMENT_NOT_RECEIVED -> "Payment not received"
        else -> "Need help?"
    }

    val message = when {
        refundStatus == RefundStatusValue.REFUND_PENDING ->
            "The shopkeeper has marked payment as received. They will manually settle the refund. You can call them for updates."

        refundStatus == RefundStatusValue.REFUNDED && order.refundReferenceId.isNotBlank() ->
            "Refund marked settled by shopkeeper. Reference ID: ${order.refundReferenceId}"

        refundStatus == RefundStatusValue.REFUNDED ->
            "Refund marked settled by shopkeeper. Contact the shopkeeper if you have not received it."

        paymentStatus == PaymentStatusValue.PAYMENT_NOT_RECEIVED ->
            "If you already paid, contact the shopkeeper directly and share your UPI screenshot or transaction proof."

        else ->
            "Please contact the shopkeeper directly. Keep your UPI screenshot or payment proof ready."
    }

    val borderColor = when {
        refundStatus == RefundStatusValue.REFUNDED -> Color(0xFF2E7D32)
        else -> Color(0xFFD32F2F)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StatusTimeline(
    steps: List<OrderStep>,
    currentStep: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        steps.forEachIndexed { index, step ->
            val state = when {
                index < currentStep -> StepState.DONE
                index == currentStep -> StepState.ACTIVE
                else -> StepState.UPCOMING
            }

            TimelineRow(
                step = step,
                state = state,
                isLast = index == steps.lastIndex
            )
        }
    }
}

@Composable
private fun TimelineRow(
    step: OrderStep,
    state: StepState,
    isLast: Boolean
) {
    val dotColor by animateColorAsState(
        targetValue = when (state) {
            StepState.DONE -> Orange
            StepState.ACTIVE -> Orange
            StepState.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(400),
        label = "dot_color"
    )

    val labelColor = when (state) {
        StepState.DONE -> MaterialTheme.colorScheme.onBackground
        StepState.ACTIVE -> Orange
        StepState.UPCOMING -> TextSecondary
    }

    val infiniteTransition = rememberInfiniteTransition(
        label = "dot_pulse"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == StepState.ACTIVE) {
            1.25f
        } else {
            1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 700,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
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
                        StepState.DONE -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        StepState.ACTIVE -> {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        StepState.UPCOMING -> {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (!isLast) {
                val lineColor by animateColorAsState(
                    targetValue = if (state == StepState.DONE) {
                        Orange
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    animationSpec = tween(400),
                    label = "line_color"
                )

                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .background(lineColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.padding(
                top = 4.dp,
                bottom = if (isLast) {
                    0.dp
                } else {
                    16.dp
                }
            )
        ) {
            Text(
                text = step.label,
                fontWeight = if (state == StepState.ACTIVE) {
                    FontWeight.ExtraBold
                } else {
                    FontWeight.SemiBold
                },
                fontSize = 15.sp,
                color = labelColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = step.subtitle,
                fontSize = 12.sp,
                color = TextSecondary.copy(
                    alpha = if (state == StepState.UPCOMING) {
                        0.5f
                    } else {
                        1f
                    }
                )
            )
        }
    }
}

@Composable
private fun OrderSummaryCard(
    order: Order
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Your Order",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = OrangeLight
                        ) {
                            Text(
                                text = "×${item.quantity}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangeDark,
                                modifier = Modifier.padding(
                                    horizontal = 6.dp,
                                    vertical = 2.dp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = item.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }

                    Text(
                        text = "₹${(item.price * item.quantity).toInt()}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Orange
                    )
                }

                if (item.cookingNote.isNotBlank()) {
                    Text(
                        text = "📝 ${item.cookingNote}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(
                            start = 40.dp,
                            bottom = 4.dp
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "₹${order.totalPrice.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Orange
                )
            }
        }
    }
}

@Composable
private fun PickupSlotRow(
    slot: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = Orange,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Pickup Slot",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = slot,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Orange
            )
        }
    }
}

@Composable
private fun CallShopkeeperButton(
    phone: String,
    onCallClick: () -> Unit
) {
    Button(
        onClick = onCallClick,
        enabled = phone.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.5f
            )
        )
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = null,
            tint = Orange
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (phone.isBlank()) {
                "Shopkeeper Phone Missing"
            } else {
                "Call Shopkeeper"
            },
            color = Orange,
            fontWeight = FontWeight.Bold
        )
    }
}
