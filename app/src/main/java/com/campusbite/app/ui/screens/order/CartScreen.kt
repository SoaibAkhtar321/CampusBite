package com.campusbite.app.ui.screens.order

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext

private val Orange_10 = Orange.copy(alpha = 0.12f)

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)
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
    var paymentDone by remember { mutableStateOf(false) }
    var upiPayerName by remember { mutableStateOf("") }

    val expandedNoteItemId = remember { mutableStateOf<String?>(null) }
    val noteDrafts = remember { mutableStateMapOf<String, String>() }

    val shopId = currentShopId ?: ""
    val cartPrepTime = cartItems.maxOfOrNull { it.prepTimeMinutes } ?: 0
    val focusManager = LocalFocusManager.current

    val shopLoaded = selectedShop != null
    val finalShopId = selectedShop?.shopId ?: currentShopId.orEmpty()

    val isShopAcceptingOrders = selectedShop?.let { shop ->
        shop.isApproved &&
                shop.isOpen &&
                !shop.isBlocked &&
                !shop.isDeleted
    } ?: false



    val shopUpiId = selectedShop?.upiId.orEmpty()
    val shopName = selectedShop?.name ?: "CampusBite"

    val totalAmount = String.format(
        Locale.US,
        "%.2f",
        cartViewModel.totalPrice
    )

    val canShowPayment =
        shopLoaded &&
                isShopAcceptingOrders &&
                !isLoadingSlots &&
                availableSlots.isNotEmpty() &&
                selectedSlot.isNotBlank() &&
                cartItems.isNotEmpty() &&
                finalShopId.isNotBlank() &&
                shopUpiId.isNotBlank()

    val canOrder =
        canShowPayment &&
                paymentDone &&
                upiPayerName.trim().isNotBlank() &&
                orderState !is OrderState.Loading &&
                orderState !is OrderState.Error

    val upiQrContent = remember(
        shopUpiId,
        shopName,
        totalAmount
    ) {
        if (
            shopUpiId.isBlank() ||
            !shopUpiId.contains("@")
        ) {
            ""
        } else {
            val encodedName = URLEncoder.encode(
                shopName,
                "UTF-8"
            )

            val encodedNote = URLEncoder.encode(
                "CampusBite Order",
                "UTF-8"
            )

            "upi://pay" +
                    "?pa=$shopUpiId" +
                    "&pn=$encodedName" +
                    "&am=$totalAmount" +
                    "&cu=INR" +
                    "&tn=$encodedNote"
        }
    }

    val qrBitmap = remember(upiQrContent) {
        if (upiQrContent.isBlank()) {
            null
        } else {
            generateQrBitmap(upiQrContent)
        }
    }

    LaunchedEffect(
        finalShopId,
        cartPrepTime,
        cartItems.size,
        isShopAcceptingOrders
    ) {
        selectedSlot = ""
        paymentDone = false

        if (
            finalShopId.isNotBlank() &&
            cartItems.isNotEmpty()
        ) {
            orderViewModel.listenToShopAvailability(finalShopId)

            if (isShopAcceptingOrders) {
                orderViewModel.loadAvailableSlots(
                    shopId = finalShopId,
                    cartPrepTimeMinutes = cartPrepTime
                )
            }
        }
    }

    LaunchedEffect(
        isShopAcceptingOrders,
        availableSlots
    ) {
        if (!isShopAcceptingOrders) {
            selectedSlot = ""
            paymentDone = false
        }

        if (
            selectedSlot.isNotBlank() &&
            selectedSlot !in availableSlots
        ) {
            selectedSlot = ""
            paymentDone = false
        }
    }

    LaunchedEffect(selectedSlot) {
        paymentDone = false
    }

    LaunchedEffect(canShowPayment) {
        if (!canShowPayment) {
            paymentDone = false
        }
    }

    LaunchedEffect(orderState) {
        when (val state = orderState) {
            is OrderState.Success -> {
                cartViewModel.clearCart()
                selectedSlot = ""
                paymentDone = false
                upiPayerName = ""
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
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        MaterialTheme.colorScheme.background
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = cartItems,
                    key = { it.itemId }
                ) { item ->
                    CartItemCard(
                        item = item,
                        isNoteExpanded =
                            expandedNoteItemId.value == item.itemId,
                        noteDraft =
                            noteDrafts[item.itemId] ?: item.cookingNote,
                        onToggleNote = {
                            focusManager.clearFocus()

                            expandedNoteItemId.value =
                                if (
                                    expandedNoteItemId.value == item.itemId
                                ) {
                                    null
                                } else {
                                    item.itemId
                                }

                            if (noteDrafts[item.itemId] == null) {
                                noteDrafts[item.itemId] =
                                    item.cookingNote
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

                        !shopLoaded -> {
                            Text(
                                text = "Checking shop availability...",
                                fontSize = 13.sp,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        !isShopAcceptingOrders -> {
                            Text(
                                text =
                                    slotMessage.ifBlank {
                                        "This shop is currently not accepting orders."
                                    },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        availableSlots.isNotEmpty() -> {
                            FlowRow(
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                availableSlots.forEach { slot ->
                                    SlotChip(
                                        slot = slot,
                                        isSelected =
                                            selectedSlot == slot,
                                        onClick = {
                                            selectedSlot = slot
                                        }
                                    )
                                }
                            }

                            if (slotMessage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = slotMessage,
                                    fontSize = 12.sp,
                                    color =
                                        MaterialTheme.colorScheme.error,
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
                                text = "No pickup slots available right now.",
                                fontSize = 13.sp,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))

                    SectionLabel(text = "Payment")

                    Spacer(modifier = Modifier.height(10.dp))

                    when {
                        !shopLoaded || isLoadingSlots -> {
                            DisabledPaymentCard(
                                text = "Checking shop availability..."
                            )
                        }

                        !isShopAcceptingOrders -> {
                            DisabledPaymentCard(
                                text = "Shop is currently closed. Payment is disabled."
                            )
                        }

                        availableSlots.isEmpty() -> {
                            DisabledPaymentCard(
                                text = "No pickup slots available. Payment is disabled."
                            )
                        }

                        selectedSlot.isBlank() -> {
                            DisabledPaymentCard(
                                text = "Select a pickup slot to continue payment."
                            )
                        }

                        shopUpiId.isBlank() -> {
                            DisabledPaymentCard(
                                text = "Shop payment QR is not available."
                            )
                        }

                        else -> {
                            PaymentQrCard(
                                shopName = shopName,
                                shopUpiId = shopUpiId,
                                qrBitmap = qrBitmap,
                                totalPrice = cartViewModel.totalPrice,
                                upiPayerName = upiPayerName,
                                paymentDone = paymentDone,
                                onUpiPayerNameChange = {
                                    upiPayerName = it
                                    paymentDone = false
                                },
                                onPaymentDone = {
                                    paymentDone = true
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    if (orderState is OrderState.Error) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text =
                                (orderState as OrderState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))

                    HorizontalDivider(
                        color =
                            MaterialTheme
                                .colorScheme
                                .outline
                                .copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Amount",
                                fontSize = 13.sp,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )

                            Text(
                                text =
                                    "₹${cartViewModel.totalPrice.toInt()}",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Orange_10)
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 6.dp
                                )
                        ) {
                            Text(
                                text =
                                    "${cartViewModel.itemCount} item${
                                        if (cartViewModel.itemCount > 1) {
                                            "s"
                                        } else {
                                            ""
                                        }
                                    }",
                                fontSize = 13.sp,
                                color = Orange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Button(
                onClick = {
                    orderViewModel.resetState()

                    if (!isShopAcceptingOrders) {
                        orderViewModel.setError(
                            "This shop is currently not accepting orders."
                        )
                        return@Button
                    }

                    if (!canShowPayment) {
                        orderViewModel.setError(
                            "Payment is not allowed right now. Please check shop status and pickup slot."
                        )
                        return@Button
                    }

                    if (shopUpiId.isBlank()) {
                        orderViewModel.setError(
                            "UPI ID is missing for this shop."
                        )
                        return@Button
                    }
                    val totalPrice = cartItems.sumOf { item ->
                        item.price * item.quantity
                    }

                    val normalizedItems = cartItems.map { item ->
                        item.copy(shopId = finalShopId)
                    }

                    val order = Order(
                        shopId = finalShopId,
                        shopName = selectedShop?.name.orEmpty(),
                        items = normalizedItems,
                        totalPrice = totalPrice,
                        status = "pending",
                        pickupSlot = selectedSlot,
                        pickupDate = LocalDate.now().toString(),
                        paymentMethod = "UPI_QR",
                        paymentStatus = "pending_verification",
                        upiPayerName = upiPayerName.trim(),
                        shopkeeperPhone = selectedShop?.phone.orEmpty()
                    )

                    orderViewModel.placeOrder(order)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                    .height(54.dp),
                enabled = canOrder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange,
                    disabledContainerColor =
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp
                )
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
                            finalShopId.isBlank() ->
                                "Shop not found"

                            cartItems.isEmpty() ->
                                "Cart is Empty"

                            !shopLoaded || isLoadingSlots ->
                                "Checking Shop..."

                            !isShopAcceptingOrders ->
                                "🔒 Shop is Closed"

                            availableSlots.isEmpty() ->
                                "No Pickup Slots Available"

                            selectedSlot.isBlank() ->
                                "Select Pickup Slot"

                            shopUpiId.isBlank() ->
                                "Shop UPI ID Missing"

                            upiPayerName.trim().isBlank() ->
                                "Enter UPI payer name"

                            !paymentDone ->
                                "Scan QR & Confirm Payment"

                            else ->
                                "Place Order  •  ₹${cartViewModel.totalPrice.toInt()}"
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

@Composable
private fun DisabledPaymentCard(
    text: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PaymentQrCard(
    shopName: String,
    shopUpiId: String,
    qrBitmap: Bitmap?,
    totalPrice: Double,
    upiPayerName: String,
    paymentDone: Boolean,
    onUpiPayerNameChange: (String) -> Unit,
    onPaymentDone: () -> Unit
) {
    val context = LocalContext.current

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
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = shopName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Scan this QR using any UPI app",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            qrBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "UPI QR Code",
                    modifier = Modifier
                        .size(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    filterQuality = FilterQuality.None
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Amount: ₹${totalPrice.toInt()}",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Orange
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
                            text = "Pay using UPI ID",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        SelectionContainer {
                            Text(
                                text = shopUpiId,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            copyTextToClipboard(
                                context = context,
                                label = "CampusBite UPI ID",
                                text = shopUpiId
                            )
                        }
                    ) {
                        Text(
                            text = "Copy",
                            color = Orange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can either scan the QR or copy the UPI ID and pay manually.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = upiPayerName,
                onValueChange = onUpiPayerNameChange,
                label = {
                    Text("Enter your UPI name")
                },
                placeholder = {
                    Text("Name visible to shopkeeper after payment")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    cursorColor = Orange
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPaymentDone,
                enabled = upiPayerName.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (paymentDone) {
                        "Payment Marked Done ✓"
                    } else {
                        "I Have Paid"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (paymentDone) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Place order only after payment. The shopkeeper will verify it. If payment is not found, order will be cancelled and your account may be blocked.",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                )
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
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
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
                        text =
                            "₹${item.price.toInt()} × ${item.quantity}",
                        fontSize = 13.sp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    if (item.prepTimeMinutes > 0) {
                        Text(
                            text = "⏱ ${item.prepTimeMinutes} min prep",
                            fontSize = 12.sp,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    if (
                        item.cookingNote.isNotBlank() &&
                        !isNoteExpanded
                    ) {
                        Spacer(modifier = Modifier.height(5.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Orange_10)
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = 3.dp
                                )
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
                        text =
                            "₹${(item.price * item.quantity).toInt()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Orange
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isNoteExpanded) {
                                    Orange
                                } else {
                                    Orange_10
                                }
                            )
                            .clickable {
                                onToggleNote()
                            }
                            .padding(
                                horizontal = 8.dp,
                                vertical = 5.dp
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Add note",
                                tint =
                                    if (isNoteExpanded) {
                                        Color.White
                                    } else {
                                        Orange
                                    },
                                modifier = Modifier.size(13.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text =
                                    if (item.cookingNote.isBlank()) {
                                        "Add note"
                                    } else {
                                        "Edit note"
                                    },
                                fontSize = 11.sp,
                                color =
                                    if (isNoteExpanded) {
                                        Color.White
                                    } else {
                                        Orange
                                    },
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
                        color =
                            MaterialTheme
                                .colorScheme
                                .outline
                                .copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Cooking Preferences",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
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
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp),
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
                                    .background(
                                        if (isSelected) {
                                            Orange
                                        } else {
                                            Orange_10
                                        }
                                    )
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

                                        onNoteDraftChange(
                                            current.joinToString(", ")
                                        )
                                    }
                                    .padding(
                                        horizontal = 10.dp,
                                        vertical = 5.dp
                                    )
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    color =
                                        if (isSelected) {
                                            Color.White
                                        } else {
                                            Orange
                                        },
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
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Orange,
                            unfocusedBorderColor =
                                MaterialTheme
                                    .colorScheme
                                    .outline
                                    .copy(alpha = 0.4f),
                            cursorColor = Orange
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onNoteSaved()
                            }
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
        targetValue =
            if (isSelected) {
                Orange
            } else {
                MaterialTheme.colorScheme.surface
            },
        animationSpec = tween(180),
        label = "slot_bg"
    )

    val textColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        animationSpec = tween(180),
        label = "slot_text"
    )

    val borderColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                Orange
            } else {
                MaterialTheme
                    .colorScheme
                    .outline
                    .copy(alpha = 0.4f)
            },
        animationSpec = tween(180),
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
                onClick()
            }
            .padding(
                horizontal = 14.dp,
                vertical = 9.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = slot,
            fontSize = 13.sp,
            fontWeight =
                if (isSelected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
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

private fun generateQrBitmap(
    content: String,
    size: Int = 700
): Bitmap {
    val bitMatrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size
    )

    val bitmap = Bitmap.createBitmap(
        size,
        size,
        Bitmap.Config.RGB_565
    )

    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x,
                y,
                if (bitMatrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            )
        }
    }

    return bitmap
}
private fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String
) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager

    val clip = ClipData.newPlainText(
        label,
        text
    )

    clipboard.setPrimaryClip(clip)

    Toast.makeText(
        context,
        "UPI ID copied",
        Toast.LENGTH_SHORT
    ).show()
}
