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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.CartItem
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Order
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
import com.campusbite.app.data.model.OrderItem

// ═══════════════════════════════════════════════════════════════════════════
// SHIMMER EFFECT - Reusable loading animation
// ═══════════════════════════════════════════════════════════════════════════
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

// ═══════════════════════════════════════════════════════════════════════════
// MAIN HOMESCREEN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════
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
    // ─────────────────────────────────────────────────────────────────────────
    // STATE COLLECTION
    // ─────────────────────────────────────────────────────────────────────────
    val shops by viewModel.shops.collectAsState()
    val isDataReady by viewModel.isDataReady.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val priceRange by viewModel.priceRange.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()

    val cartItems by cartViewModel.cartItems.collectAsState()
    val showDialog by cartViewModel.showShopConflict.collectAsState()

    val activeOrder by orderViewModel.activeOrder.collectAsState()

    // ─────────────────────────────────────────────────────────────────────────
    // LOCAL STATE
    // ─────────────────────────────────────────────────────────────────────────
    var showExitDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var dismissedOrderIds by remember { mutableStateOf(setOf<String>()) }
    val sheetState = rememberModalBottomSheetState()

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE EFFECTS
    // ─────────────────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            orderViewModel.listenToActiveOrder(uid)
        }
    }

    BackHandler(enabled = cartItems.isNotEmpty()) {
        showExitDialog = true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DERIVED STATE
    // ─────────────────────────────────────────────────────────────────────────
    val shouldShowBanner = activeOrder != null &&
            !dismissedOrderIds.contains(activeOrder?.orderId) &&
            activeOrder?.status?.uppercase() !in listOf("COMPLETED", "CANCELLED")

    val hasCartItems = cartItems.isNotEmpty()
    val bottomContentPadding: Dp = when {
        shouldShowBanner && hasCartItems -> 180.dp
        shouldShowBanner || hasCartItems -> 96.dp
        else -> 16.dp
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN UI LAYOUT
    // ─────────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TopAppBarSection(onProfileClick = onNavigateToProfile)

            SearchAndFilterBar(
                searchQuery = searchQuery,
                isFilterActive = viewModel.isFilterActive(),
                onSearchChange = { viewModel.updateSearchQuery(it) },
                onFilterClick = { showFilterSheet = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading || !isDataReady) {
                ShimmerHomeContent()
            } else {
                HomeContentList(
                    shops = shops,
                    filteredItems = filteredItems,
                    selectedCategory = selectedCategory,
                    cartItems = cartItems,
                    isFilterActive = viewModel.isFilterActive(),
                    bottomContentPadding = bottomContentPadding,
                    viewModel = viewModel,
                    cartViewModel = cartViewModel,
                    onNavigateToShopDetail = onNavigateToShopDetail
                )
            }
        }

        // ── FLOATING BOTTOM STACK ─────────────────────────────────────────────
        BottomFloatingStack(
            shouldShowBanner = shouldShowBanner,
            activeOrder = activeOrder,
            cartItemCount = cartItems.size,
            cartTotalPrice = cartViewModel.totalPrice,
            onTrackClick = { activeOrder?.let { onNavigateToOrderStatus(it.orderId) } },
            onCartClick = onNavigateToCart,
            onDismissOrder = { orderId ->
                dismissedOrderIds = dismissedOrderIds + orderId
            }
        )

        // ── DIALOGS & SHEETS ──────────────────────────────────────────────────
        PriceFilterBottomSheet(
            isVisible = showFilterSheet,
            sheetState = sheetState,
            selectedRange = priceRange,
            viewModel = viewModel,
            onDismiss = { showFilterSheet = false }
        )

        ShopConflictDialog(
            isVisible = showDialog,
            cartViewModel = cartViewModel
        )

        ExitConfirmationDialog(
            isVisible = showExitDialog,
            cartViewModel = cartViewModel,
            onDismiss = { showExitDialog = false },
            onConfirm = { showExitDialog = false; onNavigateToCart() }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLE SECTIONS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TopAppBarSection(onProfileClick: () -> Unit) {
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
                .clickable { onProfileClick() },
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
}

@Composable
private fun SearchAndFilterBar(
    searchQuery: String,
    isFilterActive: Boolean,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search dish or shop...", fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Orange)
            },
            trailingIcon = {
                AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary
                        )
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
                .background(if (isFilterActive) Orange else OrangeLight)
                .clickable { onFilterClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = "Filter",
                tint = if (isFilterActive) Color.White else Orange
            )
        }
    }
}

@Composable
private fun HomeContentList(
    shops: List<Shop>,
    filteredItems: List<MenuItem>,
    selectedCategory: String,
    cartItems: List<OrderItem>,
    isFilterActive: Boolean,
    bottomContentPadding: Dp,
    viewModel: HomeViewModel,
    cartViewModel: CartViewModel,
    onNavigateToShopDetail: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        // ── SHOPS SECTION ─────────────────────────────────────────────────────
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
                items(
                    items = shops,
                    key = { shop -> shop.shopId }
                ) { shop ->
                    ShopCard(
                        shop = shop,
                        onClick = { onNavigateToShopDetail(shop.shopId) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── CATEGORIES SECTION ────────────────────────────────────────────────
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
                items(
                    items = viewModel.categories,
                    key = { category -> category }
                ) { category ->
                    CategoryChip(
                        category = category,
                        isSelected = category == selectedCategory,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }
            if (isFilterActive) {
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

        // ── MENU ITEMS OR EMPTY STATE ─────────────────────────────────────────
        if (filteredItems.isEmpty()) {
            item {
                EmptyMenuState(hasFilters = isFilterActive)
            }
        } else {
            val groupedItems = filteredItems.groupBy { menuItem -> menuItem.shopId }
            groupedItems.forEach { (shopId, itemsList) ->
                val shopName = viewModel.getShopName(shopId)
                item {
                    ShopNameHeader(shopName = shopName)
                }
                items(
                    items = itemsList,
                    key = { menuItem -> menuItem.itemId }
                ) { menuItem ->
                    // Find the OrderItem in cartItems that matches this MenuItem
                    val matchingCartItem = cartItems.firstOrNull { orderItem ->
                        orderItem.itemId == menuItem.itemId
                    }

                    MenuItemCard(
                        menuItem = menuItem,
                        quantity = matchingCartItem?.quantity ?: 0,
                        onAddClick = { cartViewModel.addItem(menuItem) },
                        onRemoveClick = { cartViewModel.removeItem(menuItem.itemId) }
                    )
                }
            }
        }
    }
}
@Composable
private fun ShopNameHeader(shopName: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
private fun BottomFloatingStack(
    shouldShowBanner: Boolean,
    activeOrder: Order?,
    cartItemCount: Int,
    cartTotalPrice: Double,
    onTrackClick: () -> Unit,
    onCartClick: () -> Unit,
    onDismissOrder: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active order banner
            AnimatedVisibility(
                visible = shouldShowBanner && activeOrder != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                activeOrder?.let { order ->
                    ActiveOrderBanner(
                        orderId = order.orderId,
                        status = order.status,
                        shopId = order.shopId,
                        pickupSlot = order.pickupSlot,
                        onTrackClick = onTrackClick,
                        onDismissClick = { onDismissOrder(order.orderId) }
                    )
                }
            }

            // Cart FAB
            AnimatedVisibility(
                visible = cartItemCount > 0,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Button(
                    onClick = onCartClick,
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
                        text = "$cartItemCount item${if (cartItemCount > 1) "s" else ""} in cart",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "₹${cartTotalPrice.toInt()}  →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceFilterBottomSheet(
    isVisible: Boolean,
    sheetState: SheetState,
    selectedRange: ClosedFloatingPointRange<Float>,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
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
                    items(quickFilters.size) { index ->
                        val (label, range) = quickFilters[index]
                        FilterChip(
                            selected = selectedRange == range,
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
                    text = "Custom Range: ₹${selectedRange.start.toInt()} – ₹${selectedRange.endInclusive.toInt()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                RangeSlider(
                    value = selectedRange,
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
                    onClick = onDismiss,
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
}

@Composable
private fun ShopConflictDialog(
    isVisible: Boolean,
    cartViewModel: CartViewModel
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = { cartViewModel.dismissShopConflict() },
            title = { Text("Order from one shop at a time") },
            text = { Text("Your cart already has items from another shop. Clear cart and add this item?") },
            confirmButton = {
                Button(
                    onClick = { cartViewModel.confirmClearCartAndAdd() },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Clear & Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { cartViewModel.dismissShopConflict() }) {
                    Text("Cancel", color = Orange)
                }
            }
        )
    }
}

@Composable
private fun ExitConfirmationDialog(
    isVisible: Boolean,
    cartViewModel: CartViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Items in cart") },
            text = { Text("You have selected items. Do you want to continue to cart or cancel this order?") },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Go to Cart")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        cartViewModel.clearCart()
                        onDismiss()
                    }
                ) {
                    Text("Cancel Order", color = Orange)
                }
            }
        )
    }
}

@Composable
private fun ActiveOrderBanner(
    orderId: String,
    status: String,
    shopId: String,
    pickupSlot: String,
    onTrackClick: () -> Unit = {},
    onDismissClick: () -> Unit = {}
) {
    val statusLabel = when (status.uppercase()) {
        "PENDING" -> "⏳ Waiting for shop to accept"
        "ACCEPTED" -> "👍 Order accepted!"
        "PREPARING" -> "👨‍🍳 Your food is being prepared"
        "READY" -> "✅ Ready for pickup!"
        else -> "Tracking order..."
    }

    val isReady = status.uppercase() == "READY"

    val bannerColor = if (isReady)
        Color(0xFF2E7D32).copy(alpha = 0.12f)
    else
        OrangeLight

    val borderColor = if (isReady)
        Color(0xFF2E7D32).copy(alpha = 0.5f)
    else
        Orange.copy(alpha = 0.4f)

    val textColor = if (isReady) Color(0xFF2E7D32) else OrangeDark

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackClick() },
        shape = RoundedCornerShape(16.dp),
        color = bannerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
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
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Track →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.clickable { onTrackClick() }
                )

                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss order banner",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

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
                                "−",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
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
                                "+",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}