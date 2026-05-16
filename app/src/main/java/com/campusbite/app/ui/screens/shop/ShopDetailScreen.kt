package com.campusbite.app.ui.screens.shop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.ui.screens.home.shimmerEffect
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeDark
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.theme.VegGreen
import com.campusbite.app.ui.theme.VegGreenLight
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailScreen(
    shopId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCart: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel()
) {
    val shops by viewModel.shops.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val cartItems by cartViewModel.cartItems.collectAsState()
    val showDialog by cartViewModel.showShopConflict.collectAsState()

    val normalizedId = shopId.trim().lowercase()

    // ✅ Find shop once and memoize it
    val shop = remember(shops, shopId) {
        shops.find { it.shopId.trim().lowercase() == normalizedId }
            ?: shops.find { it.name.trim().lowercase() == normalizedId }
    }

    val shopMenuItems = remember(menuItems, shopId) {
        menuItems.filter { it.shopId.trim().lowercase() == normalizedId }
    }

    var showExitDialog by remember { mutableStateOf(false) }

    // ✅ Log only once, not on every recomposition
    LaunchedEffect(shop, shops.size) {
        android.util.Log.d("ShopDetail", "Shop found: ${shop?.name ?: "NULL"} | Total shops: ${shops.size}")
    }

    // ✅ Show loading state
    if (isLoading || shops.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading shop details...", fontSize = 14.sp)
            }
        }
        return
    }

    // ✅ Show error state
    if (shop == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Shop detail not found", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Looking for: '$normalizedId'", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    // ✅ Everything below here is safe - shop is NOT null
    BackHandler(enabled = cartItems.isNotEmpty()) { showExitDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        shop.name,  // ✅ Safe to use now
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (cartItems.isNotEmpty()) showExitDialog = true
                            else onNavigateBack()
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        // ... rest of your code (everything that was after Scaffold) stays the same

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                ShopDetailShimmer()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = if (cartViewModel.itemCount > 0) 88.dp else 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ShopHeaderCard(
                            name = shop?.name ?: shopId,
                            description = shop?.description ?: "",
                            isOpen = shop?.isOpen == true
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Orange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Menu",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (shopMenuItems.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(OrangeLight)
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${shopMenuItems.size} items",
                                        fontSize = 12.sp,
                                        color = Orange,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (shopMenuItems.isEmpty()) {
                        item { ShopEmptyState() }
                    } else {
                        items(shopMenuItems) { item ->
                            ShopMenuItemCard(
                                menuItem = item,
                                quantity = cartItems.find { it.itemId == item.itemId }?.quantity ?: 0,
                                isShopOpen = shop?.isOpen == true,
                                onAddClick = { cartViewModel.addItem(item) },
                                onRemoveClick = { cartViewModel.removeItem(item.itemId) }
                            )
                        }
                    }
                }
            }

            val itemCount = cartViewModel.itemCount
            AnimatedVisibility(
                visible = itemCount > 0,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
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
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                onNavigateBack()
                            }
                        ) { Text("Cancel Order", color = Orange) }
                    }
                )
            }

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
        }
    }
}

// ---------------------------------------------------------------------------
// Shop header card
// ---------------------------------------------------------------------------
@Composable
private fun ShopHeaderCard(name: String, description: String, isOpen: Boolean) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeLight),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Orange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Store, contentDescription = null, tint = OrangeDark, modifier = Modifier.size(30.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(description, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                }
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isOpen) VegGreenLight else MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isOpen) "● Accepting Orders" else "● Currently Closed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOpen) VegGreen else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Menu item card
// ---------------------------------------------------------------------------
@Composable
fun ShopMenuItemCard(
    menuItem: MenuItem,
    quantity: Int,
    isShopOpen: Boolean,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Text(menuItem.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("⏱ ${menuItem.prepTimeMinutes} min", fontSize = 11.sp, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹${menuItem.price.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Orange)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!isShopOpen) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Closed", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            IconButton(onClick = onRemoveClick, modifier = Modifier.size(36.dp)) {
                                Text("−", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text(
                                text = qty.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.widthIn(min = 20.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = onAddClick, modifier = Modifier.size(36.dp)) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------
@Composable
private fun ShopEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🍱", fontSize = 56.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("No items yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This shop hasn't added any menu items yet. Check back soon!",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Shimmer skeleton
// ---------------------------------------------------------------------------
@Composable
private fun ShopDetailShimmer() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(20.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(4.dp))
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .shimmerEffect()
            )
        }
    }
}