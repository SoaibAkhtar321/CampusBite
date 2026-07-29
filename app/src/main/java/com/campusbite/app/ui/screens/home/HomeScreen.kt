package com.campusbite.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SheetState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.local.OrderBannerPrefs
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Order
import com.campusbite.app.data.model.OrderItem
import com.campusbite.app.data.model.Shop
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeDark
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.VegGreen
import com.campusbite.app.ui.theme.VegGreenLight
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel
import com.campusbite.app.ui.viewmodel.OrderViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// ─────────────────────────────────────────────
// Shimmer
// ─────────────────────────────────────────────

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
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    )
    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 200f, 0f),
            end = Offset(translateAnim, 0f)
        )
    )
}

// ─────────────────────────────────────────────
// HomeScreen
// ─────────────────────────────────────────────

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
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val priceRange by viewModel.priceRange.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()

    val cartItems by cartViewModel.cartItems.collectAsState()
    val showDialog by cartViewModel.showShopConflict.collectAsState()

    val activeOrder by orderViewModel.activeOrder.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val context = LocalContext.current
    val bannerPrefs = remember { OrderBannerPrefs(context) }
    val dismissedIds by bannerPrefs.dismissedIds.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val order = activeOrder
    val shouldShowBanner = order != null &&
            !dismissedIds.contains(order.orderId) &&
            order.status.uppercase() !in listOf("COMPLETED", "CANCELLED")

    LaunchedEffect(order?.status, order?.orderId) {
        if (order?.status?.uppercase() in listOf("COMPLETED", "CANCELLED") && order?.orderId != null) {
            bannerPrefs.clear(order.orderId)
        }
    }

    BackHandler(enabled = cartItems.isNotEmpty()) { showExitDialog = true }

    val hasCartItems = cartItems.isNotEmpty()
    val bottomContentPadding: Dp = when {
        shouldShowBanner && hasCartItems -> 220.dp
        shouldShowBanner || hasCartItems -> 140.dp
        else -> 32.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TopAppBarSection(onProfileClick = onNavigateToProfile)

            SearchAndFilterBar(
                searchQuery = searchQuery,
                isFilterActive = viewModel.isFilterActive(),
                onSearchChange = { viewModel.updateSearchQuery(it) },
                onFilterClick = { showFilterSheet = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    isLoadingMore = isLoadingMore,
                    canLoadMore = canLoadMore,
                    onLoadMore = { viewModel.loadMoreShops() },
                    viewModel = viewModel,
                    cartViewModel = cartViewModel,
                    onNavigateToShopDetail = onNavigateToShopDetail
                )
            }
        }

        BottomFloatingStack(
            shouldShowBanner = shouldShowBanner,
            activeOrder = order,
            cartItemCount = cartItems.size,
            cartTotalPrice = cartViewModel.totalPrice,
            onTrackClick = { order?.let { onNavigateToOrderStatus(it.orderId) } },
            onCartClick = onNavigateToCart,
            onDismissOrder = { orderId -> scope.launch { bannerPrefs.dismiss(orderId) } }
        )

        PriceFilterBottomSheet(
            isVisible = showFilterSheet,
            sheetState = sheetState,
            selectedRange = priceRange,
            viewModel = viewModel,
            onDismiss = { showFilterSheet = false }
        )

        ShopConflictDialog(isVisible = showDialog, cartViewModel = cartViewModel)

        ExitConfirmationDialog(
            isVisible = showExitDialog,
            cartViewModel = cartViewModel,
            onDismiss = { showExitDialog = false },
            onConfirm = { showExitDialog = false; onNavigateToCart() }
        )
    }
}

// ─────────────────────────────────────────────
// Top App Bar  — compact, less tall orange band
// Changes: vertical padding 18→10dp, title 24→22sp/SemiBold,
//          subtitle 12→11sp, profile box 40→36dp
// ─────────────────────────────────────────────

@Composable
private fun TopAppBarSection(onProfileClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(colors = listOf(Orange, OrangeDark))
            )
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CampusBite",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,   // was ExtraBold
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Bhukh Mitao, Time Bachao 🍱",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,     // was Medium
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Search & Filter Bar
// ─────────────────────────────────────────────

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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    text = "Search dish or shop...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Orange)
            },
            trailingIcon = {
                AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                cursorColor = Orange,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isFilterActive) Orange else OrangeLight)
                .clickable { onFilterClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter",
                tint = if (isFilterActive) Color.White else Orange,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
// Home Content List
// ─────────────────────────────────────────────

@Composable
private fun HomeContentList(
    shops: List<Shop>,
    filteredItems: List<MenuItem>,
    selectedCategory: String,
    cartItems: List<OrderItem>,
    isFilterActive: Boolean,
    bottomContentPadding: Dp,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    viewModel: HomeViewModel,
    cartViewModel: CartViewModel,
    onNavigateToShopDetail: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
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
                    key = { it.shopId }
                ) { shop ->
                    ShopCard(
                        shop = shop,
                        onClick = {
                            onNavigateToShopDetail(shop.shopId)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

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
                    key = { it }
                ) { category ->
                    CategoryChip(
                        category = category,
                        isSelected = category == selectedCategory,
                        onClick = {
                            viewModel.selectCategory(category)
                        }
                    )
                }
            }

            if (isFilterActive) {
                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = {
                        viewModel.resetFilters()
                    },
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Orange
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Clear All Filters",
                        color = Orange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredItems.isEmpty()) {
            item {
                EmptyMenuState(hasFilters = isFilterActive)
            }
        } else {
            val orderedShops = shops.sortedWith(
                compareBy<Shop> { it.displayOrder }
                    .thenBy { it.name.lowercase() }
            )

            val itemsByShopId = filteredItems.groupBy { it.shopId }

            orderedShops.forEach { shop ->
                val shopItems = itemsByShopId[shop.shopId].orEmpty()

                if (shopItems.isNotEmpty()) {
                    item(
                        key = "shop_header_${shop.shopId}"
                    ) {
                        ShopNameHeader(shopName = shop.name)
                    }

                    items(
                        items = shopItems,
                        key = { it.itemId }
                    ) { menuItem ->
                        val matchingCartItem = cartItems.firstOrNull {
                            it.itemId == menuItem.itemId
                        }

                        MenuItemCard(
                            menuItem = menuItem,
                            quantity = matchingCartItem?.quantity ?: 0,
                            onAddClick = {
                                cartViewModel.addItem(menuItem)
                            },
                            onRemoveClick = {
                                cartViewModel.removeItem(menuItem.itemId)
                            }
                        )
                    }
                }
            }
        }

        item(
            key = "home_pagination_footer"
        ) {
            PaginationFooter(
                isLoadingMore = isLoadingMore,
                canLoadMore = canLoadMore,
                onLoadMore = onLoadMore
            )
        }
    }
}
@Composable
private fun PaginationFooter(
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit
) {
    when {
        isLoadingMore -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Orange
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Loading more shops...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        canLoadMore -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = onLoadMore,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, Orange),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Orange
                    )
                ) {
                    Text(
                        text = "Load more shops",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        else -> {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
// ─────────────────────────────────────────────
// Shop Name Header
// ─────────────────────────────────────────────

@Composable
private fun ShopNameHeader(shopName: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Orange)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = shopName,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,   // was ExtraBold
            color = Orange
        )
    }
}

// ─────────────────────────────────────────────
// Bottom Floating Stack
// ─────────────────────────────────────────────

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
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$cartItemCount item${if (cartItemCount > 1) "s" else ""} in cart",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "₹${cartTotalPrice.toInt()}  →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Price Filter Bottom Sheet
// ─────────────────────────────────────────────

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
                        text = "Budget Filters",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { viewModel.resetFilters() }) {
                        Text(text = "Reset", color = Orange, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Quick Select",
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
                    items(quickFilters.size, key = { index -> quickFilters[index].first }) { index ->
                        val (label, range) = quickFilters[index]
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = { viewModel.updatePriceRange(range) },
                            label = { Text(label, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Orange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Custom Range: ₹${selectedRange.start.toInt()} – ₹${selectedRange.endInclusive.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
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

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "Show Results", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────

@Composable
private fun ShopConflictDialog(isVisible: Boolean, cartViewModel: CartViewModel) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = { cartViewModel.dismissShopConflict() },
            title = { Text("Order from one shop at a time", fontWeight = FontWeight.SemiBold) },
            text = { Text("Your cart already has items from another shop. Clear cart and add this item?", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { cartViewModel.confirmClearCartAndAdd() },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Clear & Continue", fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { cartViewModel.dismissShopConflict() }) {
                    Text("Cancel", color = Orange, fontSize = 13.sp)
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
            title = { Text("Items in cart", fontWeight = FontWeight.SemiBold) },
            text = { Text("You have selected items. Do you want to continue to cart or cancel this order?", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Go to Cart", fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { cartViewModel.clearCart(); onDismiss() }) {
                    Text("Cancel Order", color = Orange, fontSize = 13.sp)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// Active Order Banner
// ─────────────────────────────────────────────

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
        "PENDING"   -> "⏳ Waiting for shop to accept"
        "ACCEPTED"  -> "👍 Order accepted!"
        "PREPARING" -> "👨‍🍳 Your food is being prepared"
        "READY"     -> "✅ Ready for pickup!"
        else        -> "Tracking order..."
    }
    val isReady = status.uppercase() == "READY"
    val bannerColor = if (isReady) Color(0xFF2E7D32).copy(alpha = 0.12f) else OrangeLight
    val borderColor = if (isReady) Color(0xFF2E7D32).copy(alpha = 0.5f) else Orange.copy(alpha = 0.4f)
    val textColor   = if (isReady) Color(0xFF2E7D32) else OrangeDark

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackClick() },
        shape = RoundedCornerShape(16.dp),
        color = bannerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = statusLabel,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
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
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Track →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.clickable { onTrackClick() }
                )
                IconButton(onClick = onDismissClick, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Shimmer Skeleton
// ─────────────────────────────────────────────

@Composable
private fun ShimmerHomeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Shops",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .shimmerEffect()
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Menu",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .shimmerEffect()
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ─────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────

@Composable
private fun EmptyMenuState(hasFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🍽️", fontSize = 52.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasFilters) "No items match your filters" else "Nothing found",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasFilters) "Try adjusting the price range or category"
            else "Try searching with a different name",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────
// Section Header
// ─────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,   // was ExtraBold
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────
// Shop Card  — FIX: wrapContentHeight so "Closed" badge never clips
// Width fixed at 150dp; height wraps content naturally
// ─────────────────────────────────────────────

@Composable
fun ShopCard(
    shop: Shop,
    onClick: () -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()
    val isOpen = shop.isOpen

    val cardColor = if (isDarkMode) {
        Color(0xFF1F1F1F)
    } else {
        OrangeLight
    }

    val titleColor = if (isDarkMode) {
        Color.White
    } else {
        Color(0xFF1F1F1F)
    }

    val avatarBgColor = if (isOpen) {
        Orange.copy(alpha = 0.16f)
    } else {
        Color(0xFFFFE1E1)
    }

    val avatarTextColor = if (isOpen) {
        OrangeDark
    } else {
        Color(0xFFD32F2F)
    }

    val badgeBgColor = if (isOpen) {
        if (isDarkMode) {
            Color(0xFF1E3A25)
        } else {
            VegGreenLight
        }
    } else {
        if (isDarkMode) {
            Color(0xFF3A1F1F)
        } else {
            Color(0xFFFFE1E1)
        }
    }

    val badgeTextColor = if (isOpen) {
        VegGreen
    } else {
        Color(0xFFD32F2F)
    }

    Card(
        modifier = Modifier
            .width(150.dp)
            .height(132.dp)
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(avatarBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shop.name
                            .trim()
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "S",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = avatarTextColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = shop.name.ifBlank { "Shop" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(badgeBgColor)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isOpen) {
                        "● Open"
                    } else {
                        "● Closed"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = badgeTextColor
                )
            }
        }
    }
}
@Composable
fun CategoryChip(
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> Orange
            isDarkMode -> Color(0xFF1F1F1F)
            else -> OrangeLight
        },
        animationSpec = tween(200),
        label = "chip_bg"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isDarkMode -> Color.White
            else -> Color(0xFF1F1F1F)
        },
        animationSpec = tween(200),
        label = "chip_text"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .clickable {
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = category,
            fontSize = 13.sp,
            color = textColor,
            fontWeight = if (isSelected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Medium
            }
        )
    }
}
// ─────────────────────────────────────────────
// Menu Item Card
// ─────────────────────────────────────────────

@Composable
fun MenuItemCard(
    menuItem: MenuItem,
    quantity: Int = 0,
    shopIsOpen: Boolean = true,
    onAddClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {}
){
    val canOrder = menuItem.isAvailable && shopIsOpen

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canOrder) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (canOrder) VegGreen else MaterialTheme.colorScheme.error
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = menuItem.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,     // was ExtraBold
                        color = if (canOrder) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⏱ ${menuItem.prepTimeMinutes} min",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "₹${menuItem.price.toInt()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,   // was ExtraBold
                        color = if (canOrder) Orange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!canOrder) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (!shopIsOpen) {
                                    "Shop is closed right now"
                                } else {
                                    "Not available right now"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!canOrder) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (!shopIsOpen) "Closed" else "Unavailable",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            } else {
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
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, Orange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
                        ) {
                            Text(text = "Add", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Orange)
                                .height(40.dp)
                        ) {
                            IconButton(onClick = onRemoveClick, modifier = Modifier.size(40.dp)) {
                                Text(text = "−", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                            }
                            Text(
                                text = qty.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.widthIn(min = 20.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = onAddClick, modifier = Modifier.size(40.dp)) {
                                Text(text = "+", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}