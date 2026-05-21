package com.campusbite.app.ui.screens.order

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.Order
import com.campusbite.app.data.model.OrderItem
import com.campusbite.app.ui.screens.home.shimmerEffect
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel
import com.campusbite.app.ui.viewmodel.OrderState
import com.campusbite.app.ui.viewmodel.OrderViewModel
import java.time.LocalDate
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

private val Orange_10 = Orange.copy(alpha = 0.12f)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onNavigateBack: () -> Unit,
    onOrderPlaced: (String) -> Unit,
    cartViewModel: CartViewModel = hiltViewModel(),
    orderViewModel: OrderViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel? = null
) {
    val cartItems by cartViewModel.cartItems.collectAsState()
    val currentShopId by cartViewModel.currentShopId.collectAsState()
    val orderState by orderViewModel.orderState.collectAsState()
    val selectedShop by orderViewModel.selectedShop.collectAsState()
    val slotUiState by orderViewModel.slotUiState.collectAsState()

    val availableSlots = slotUiState.slots
    val isLoadingSlots = slotUiState.isLoading
    val slotMessage = slotUiState.message

    var selectedSlot by remember { mutableStateOf("") }

    val expandedNoteItemId = remember { mutableStateOf<String?>(null) }
    val noteDrafts = remember { mutableStateMapOf<String, String>() }

    val shopId = currentShopId ?: ""
    val cartPrepTime = cartItems.maxOfOrNull { it.prepTimeMinutes } ?: 0
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(shopId) {
        if (shopId.isNotBlank()) {
            orderViewModel.loadShop(shopId)
        }

        if (shopId.isNotBlank() && homeViewModel != null) {
            val shop = homeViewModel.shops.value.find { it.shopId == shopId }
            if (shop != null) {
                orderViewModel.setSelectedShop(shop)
            }
        }
    }

    LaunchedEffect(shopId, cartPrepTime, cartItems.size) {
        if (shopId.isNotBlank() && cartItems.isNotEmpty()) {
            selectedSlot = ""
            orderViewModel.loadAvailableSlots(
                shopId = shopId,
                cartPrepTimeMinutes = cartPrepTime
            )
        }
    }

    LaunchedEffect(orderState) {
        when (val state = orderState) {
            is OrderState.Success -> {
                cartViewModel.clearCart()
                selectedSlot = ""
                onOrderPlaced(state.orderId)
                orderViewModel.resetState()
            }

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Your Cart",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = cartItems,
                    key = { it.itemId }
                ) { item ->
                    CartItemCard(
                        item = item,
                        isNoteExpanded = expandedNoteItemId.value == item.itemId,
                        noteDraft = noteDrafts[item.itemId] ?: item.cookingNote,
                        onToggleNote = {
                            focusManager.clearFocus()

                            expandedNoteItemId.value =
                                if (expandedNoteItemId.value == item.itemId) {
                                    null
                                } else {
                                    item.itemId
                                }

                            if (noteDrafts[item.itemId] == null) {
                                noteDrafts[item.itemId] = item.cookingNote
                            }
                        },
                        onNoteDraftChange = { draft ->
                            noteDrafts[item.itemId] = draft
                        },
                        onNoteSaved = {
                            cartViewModel.updateCookingNote(
                                item.itemId,
                                noteDrafts[item.itemId] ?: ""
                            )
                            expandedNoteItemId.value = null
                            focusManager.clearFocus()
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))

                    SectionLabel(text = "Select Pickup Slot")

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        isLoadingSlots -> {
                            SlotShimmer()
                        }

                        availableSlots.isNotEmpty() -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableSlots.forEach { slot ->
                                    SlotChip(
                                        slot = slot,
                                        isSelected = selectedSlot == slot,
                                        onClick = { selectedSlot = slot }
                                    )
                                }
                            }

                            if (slotMessage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = slotMessage,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        slotMessage.isNotBlank() -> {
                            Text(
                                text = slotMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        else -> {
                            Text(
                                text = "Pickup slots will appear here.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))

                    SectionLabel(text = "Payment Method")

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Orange_10)
                            .border(1.5.dp, Orange, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Orange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "UPI Payment",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Pay using GPay / PhonePe / Paytm",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    if (orderState is OrderState.Error) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = (orderState as OrderState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Amount",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "₹${cartViewModel.totalPrice.toInt()}",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Orange_10)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${cartViewModel.itemCount} item${if (cartViewModel.itemCount > 1) "s" else ""}",
                                fontSize = 13.sp,
                                color = Orange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            val canOrder = selectedShop?.isOpen == true &&
                    selectedSlot.isNotBlank() &&
                    availableSlots.isNotEmpty() &&
                    cartItems.isNotEmpty() &&
                    shopId.isNotBlank() &&
                    orderState !is OrderState.Loading

            Button(
                onClick = {
                    val upiId = selectedShop?.upiId ?: ""

                    if (upiId.isBlank()) {
                        orderViewModel.setError("UPI ID is missing for this shop.")
                        return@Button
                    }

                    val uri = Uri.parse(
                        "upi://pay?pa=$upiId" +
                                "&pn=${selectedShop?.name ?: "CampusBite"}" +
                                "&am=${cartViewModel.totalPrice}" +
                                "&cu=INR"
                    )

                    val upiIntent = Intent(Intent.ACTION_VIEW, uri)

                    context.startActivity(
                        Intent.createChooser(
                            upiIntent,
                            "Pay with UPI"
                        )
                    )

                    val order = Order(
                        shopId = shopId,
                        items = cartItems,
                        totalPrice = cartViewModel.totalPrice,
                        status = "pending",
                        pickupSlot = selectedSlot,
                        pickupDate = LocalDate.now().toString(),
                        paymentMethod = "UPI",
                        paymentStatus = "pending_verification"
                    )

                    orderViewModel.placeOrder(order)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(54.dp),
                enabled = canOrder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                if (orderState is OrderState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = when {
                            shopId.isBlank() -> "Shop not found"
                            cartItems.isEmpty() -> "Cart is Empty"
                            selectedShop?.isOpen == false -> "🔒 Shop is Closed"
                            selectedShop?.upiId.isNullOrBlank() -> "Shop UPI ID Missing"
                            isLoadingSlots -> "Loading slots..."
                            availableSlots.isEmpty() -> "No Slots Available"
                            selectedSlot.isBlank() -> "Select a pickup slot first"
                            else -> "Pay & Place Order  •  ₹${cartViewModel.totalPrice.toInt()}"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartItemCard(
    item: OrderItem,
    isNoteExpanded: Boolean,
    noteDraft: String,
    onToggleNote: () -> Unit,
    onNoteDraftChange: (String) -> Unit,
    onNoteSaved: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "₹${item.price.toInt()} × ${item.quantity}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.prepTimeMinutes > 0) {
                        Text(
                            text = "⏱ ${item.prepTimeMinutes} min prep",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.cookingNote.isNotBlank() && !isNoteExpanded) {
                        Spacer(modifier = Modifier.height(5.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Orange_10)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "📝 ${item.cookingNote}",
                                fontSize = 11.sp,
                                color = Orange,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "₹${(item.price * item.quantity).toInt()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Orange
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isNoteExpanded) Orange else Orange_10)
                            .clickable { onToggleNote() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Add note",
                                tint = if (isNoteExpanded) Color.White else Orange,
                                modifier = Modifier.size(13.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = if (item.cookingNote.isBlank()) "Add note" else "Edit note",
                                fontSize = 11.sp,
                                color = if (isNoteExpanded) Color.White else Orange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isNoteExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Cooking Preferences",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val suggestions = listOf(
                        "Extra spicy",
                        "Less spicy",
                        "No onion",
                        "Less oil",
                        "Less sugar",
                        "Extra crispy"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            val isSelected = noteDraft
                                .split(",")
                                .map { it.trim() }
                                .contains(suggestion)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) Orange else Orange_10)
                                    .clickable {
                                        val current = noteDraft
                                            .split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                            .toMutableList()

                                        if (isSelected) {
                                            current.remove(suggestion)
                                        } else {
                                            current.add(suggestion)
                                        }

                                        onNoteDraftChange(current.joinToString(", "))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Orange,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = onNoteDraftChange,
                        placeholder = {
                            Text(
                                text = "e.g. extra spicy, no onion...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Orange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            cursorColor = Orange
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onNoteSaved() }
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onNoteSaved,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Save Preference",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotChip(
    slot: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Orange else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "slot_bg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "slot_text"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Orange else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        animationSpec = tween(180),
        label = "slot_border"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = slot,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotShimmer() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(8) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .shimmerEffect()
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String
) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}