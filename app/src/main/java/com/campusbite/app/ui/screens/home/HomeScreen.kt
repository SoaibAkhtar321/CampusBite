package com.campusbite.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeDark
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.theme.VegGreen
import com.campusbite.app.ui.theme.VegGreenLight
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel
import com.campusbite.app.ui.viewmodel.OrderViewModel
import com.google.firebase.auth.FirebaseAuth

// ---------------------------------------------------------------------------
// Shimmer helper (imported by other screens as
// com.campusbite.app.ui.screens.home.shimmerEffect)
// ---------------------------------------------------------------------------
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.15f),
        Color.LightGray.copy(alpha = 0.35f),
        Color.LightGray.copy(alpha = 0.15f)
    )
    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 200f, 0f),
            end = Offset(translateAnim, 0f)
        )
    )
}

// ---------------------------------------------------------------------------
// HomeScreen
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToShopDetail: (String) -> Unit = {},
    onNavigateToCart: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToOrderStatus: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel(),
    orderViewModel: OrderViewModel = hiltViewModel()
) {
    val shops by viewModel.shops.collectAsState()
    val isDataReady by viewModel.isDataReady.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val priceRange by viewModel.priceRange.collectAsState()

    val cartItems by cartViewModel.cartItems.collectAsState()
    val showDialog by cartViewModel.showShopConflict.collectAsState()

    val activeOrder by orderViewModel.activeOrder.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Start listening to active order for the current user
    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            orderViewModel.listenToActiveOrder(uid)
        }
    }

    BackHandler(enabled = cartItems.isNotEmpty()) { showExitDialog = true }

    val filteredItems by remember(searchQuery, selectedCategory, priceRange) {
        derivedStateOf { viewModel.getFilteredItems() }
    }

    // Bottom padding: grows when banner and/or cart FAB are visible
    val hasActiveOrder = activeOrder != null
    val hasCartItems = cartItems.isNotEmpty()
    val bottomContentPadding = when {
        hasActiveOrder && hasCartItems -> 180.dp
        hasActiveOrder || hasCartItems -> 96.dp
        else -> 16.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── TOP BAR ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "CampusBite",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Bhukh Mitao, Time Bachao 🍱",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OrangeLight)
                        .clickable { onNavigateToProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Orange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── SEARCH & FILTER ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search dish or shop...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Orange)
                    },
                    trailingIcon = {
                        AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        cursorColor = Orange
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (viewModel.isFilterActive()) Orange else OrangeLight)
                        .clickable { showFilterSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = if (viewModel.isFilterActive()) Color.White else Orange
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── BODY ──────────────────────────────────────────────────────────
            if (isLoading || !isDataReady) {
                ShimmerHomeContent()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomContentPadding)
                ) {
                    // Shops section
                    item {
                        SectionHeader(
                            title = "Shops",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(shops) { shop ->
                                ShopCard(
                                    shop = shop,
                                    onClick = { onNavigateToShopDetail(shop.shopId) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Categories + filter clear
                    item {
                        SectionHeader(
                            title = "Menu",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(viewModel.categories) { category ->
                                CategoryChip(
                                    category = category,
                                    isSelected = category == selectedCategory,
                                    onClick = { viewModel.selectCategory(category) }
                                )
                            }
                        }
                        if (viewModel.isFilterActive()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { viewModel.resetFilters() },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = Orange
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Clear All Filters",
                                    color = Orange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Menu items or empty state
                    if (filteredItems.isEmpty()) {
                        item {
                            EmptyMenuState(hasFilters = viewModel.isFilterActive())
                        }
                    } else {
                        val groupedItems = filteredItems.groupBy { it.shopId }
                        groupedItems.forEach { (shopId, items) ->
                            val shopName = viewModel.getShopName(shopId)
                            item {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Orange)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        shopName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Orange
                                    )
                                }
                            }
                            items(items) { menuItem ->
                                MenuItemCard(
                                    menuItem = menuItem,
                                    quantity = cartItems
                                        .find { it.itemId == menuItem.itemId }?.quantity ?: 0,
                                    onAddClick = { cartViewModel.addItem(menuItem) },
                                    onRemoveClick = { cartViewModel.removeItem(menuItem.itemId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── BOTTOM FLOATING STACK (active order banner + cart FAB) ───────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active order banner
            AnimatedVisibility(
                visible = activeOrder != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                activeOrder?.let { order ->
                    ActiveOrderBanner(
                        orderId = order.orderId,
                        status = order.status,
                        shopId = order.shopId,
                        pickupSlot = order.pickupSlot,
                        onClick = { onNavigateToOrderStatus(order.orderId) }
                    )
                }
            }

            // Cart FAB
            val itemCount = cartViewModel.itemCount
            AnimatedVisibility(
                visible = itemCount > 0,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Button(
                    onClick = onNavigateToCart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$itemCount item${if (itemCount > 1) "s" else ""} in cart",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "₹${cartViewModel.totalPrice.toInt()}  →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── PRICE FILTER BOTTOM SHEET ────────────────────────────────────────
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 40.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Budget Filters",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        TextButton(onClick = { viewModel.resetFilters() }) {
                            Text("Reset", color = Orange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Quick Select",
                        style = MaterialTheme.typography.labelMedium,
                        color = Orange
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val quickFilters = listOf(
                        "Under ₹50" to 0f..50f,
                        "₹50–₹150" to 50f..150f,
                        "Above ₹150" to 150f..500f
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickFilters) { (label, range) ->
                            FilterChip(
                                selected = priceRange == range,
                                onClick = { viewModel.updatePriceRange(range) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Orange,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Custom Range: ₹${priceRange.start.toInt()} – ₹${priceRange.endInclusive.toInt()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    RangeSlider(
                        value = priceRange,
                        onValueChange = { viewModel.updatePriceRange(it) },
                        valueRange = 0f..500f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Orange,
                            thumbColor = Orange,
                            inactiveTrackColor = OrangeLight
                        )
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { showFilterSheet = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Show Results", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        // ── SHOP CONFLICT DIALOG ─────────────────────────────────────────────
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { cartViewModel.dismissShopConflict() },
                title = { Text("Order from one shop at a time") },
                text = { Text("Your cart already has items from another shop. Clear cart and add this item?") },
                confirmButton = {
                    Button(
                        onClick = { cartViewModel.confirmClearCartAndAdd() },
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) { Text("Clear & Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { cartViewModel.dismissShopConflict() }) {
                        Text("Cancel", color = Orange)
                    }
                }
            )
        }

        // ── EXIT DIALOG ──────────────────────────────────────────────────────
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Items in cart") },
                text = { Text("You have selected items. Do you want to continue to cart or cancel this order?") },
                confirmButton = {
                    Button(
                        onClick = { showExitDialog = false; onNavigateToCart() },
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) { Text("Go to Cart") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            cartViewModel.clearCart()
                            showExitDialog = false
                        }
                    ) { Text("Cancel Order", color = Orange) }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Active Order Banner
// ---------------------------------------------------------------------------
@Composable
private fun ActiveOrderBanner(
    orderId: String,
    status: String,
    shopId: String,
    pickupSlot: String,
    onClick: () -> Unit
) {
    val statusLabel = when (status) {
        "pending"   -> "⏳ Waiting for shop to accept"
        "accepted"  -> "👍 Order accepted!"
        "preparing" -> "👨‍🍳 Your food is being prepared"
        "ready"     -> "✅ Ready for pickup!"
        else        -> "Tracking order..."
    }

    val bannerColor = if (status == "ready")
        Color(0xFF2E7D32).copy(alpha = 0.12f)
    else
        OrangeLight

    val borderColor = if (status == "ready")
        Color(0xFF2E7D32).copy(alpha = 0.5f)
    else
        Orange.copy(alpha = 0.4f)

    val textColor = if (status == "ready") Color(0xFF2E7D32) else OrangeDark

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = bannerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = textColor
                )
                if (pickupSlot.isNotBlank()) {
                    Text(
                        text = "Pickup at $pickupSlot  •  #${orderId.takeLast(6).uppercase()}",
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.75f)
                    )
                }
            }
            Text(
                text = "Track →",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shimmer skeleton
// ---------------------------------------------------------------------------
@Composable
private fun ShimmerHomeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text("Shops", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect()
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Menu", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .shimmerEffect()
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------
@Composable
private fun EmptyMenuState(hasFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🍽️", fontSize = 56.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasFilters) "No items match your filters" else "Nothing found",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasFilters)
                "Try adjusting the price range or category"
            else
                "Try searching with a different name",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// Shop card
// ---------------------------------------------------------------------------
@Composable
fun ShopCard(shop: Shop, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Orange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shop.name.first().uppercaseChar().toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrangeDark
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = shop.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (shop.isOpen) VegGreenLight
                        else MaterialTheme.colorScheme.errorContainer
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (shop.isOpen) "● Open" else "● Closed",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (shop.isOpen) VegGreen else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category chip
// ---------------------------------------------------------------------------
@Composable
fun CategoryChip(category: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Orange else OrangeLight,
        animationSpec = tween(200),
        label = "chip_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextPrimary,
        animationSpec = tween(200),
        label = "chip_text"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = category,
            fontSize = 13.sp,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// Menu item card
// ---------------------------------------------------------------------------
@Composable
fun MenuItemCard(
    menuItem: MenuItem,
    quantity: Int = 0,
    onAddClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(VegGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = menuItem.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⏱ ${menuItem.prepTimeMinutes} min",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹${menuItem.price.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Orange
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            AnimatedContent(
                targetState = quantity,
                transitionSpec = {
                    (slideInVertically { -it } + fadeIn()) togetherWith
                            (slideOutVertically { it } + fadeOut())
                },
                label = "qty_transition"
            ) { qty ->
                if (qty == 0) {
                    OutlinedButton(
                        onClick = onAddClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, Orange),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
                    ) {
                        Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Orange)
                            .height(36.dp)
                    ) {
                        IconButton(
                            onClick = onRemoveClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(
                                "−", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 18.sp
                            )
                        }
                        Text(
                            text = qty.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.widthIn(min = 20.dp),
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = onAddClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(
                                "+", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
