package com.campusbite.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler

@Composable
fun HomeScreen(
    onNavigateToShopDetail: (String) -> Unit = {},
    onNavigateToCart: () -> Unit = {},
    onNavigateToOrderHistory: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel()
) {
    val shops by viewModel.shops.collectAsState()
    val isDataReady by viewModel.isDataReady.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val cartItems by cartViewModel.cartItems.collectAsState()
    val showDialog by cartViewModel.showShopConflict.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = cartItems.isNotEmpty()) {
        showExitDialog = true
    }

    val filteredItems by remember(searchQuery, selectedCategory) {
        derivedStateOf { viewModel.getFilteredItems() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CampusBite",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(onClick = onLogout) {
                    Text("Logout", color = Orange, fontSize = 13.sp)
                }
            }

            // My Orders button
            Button(
                onClick = onNavigateToOrderHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("My Orders")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search dish or shop...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Orange)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = Orange
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showExitDialog = false
                    },
                    title = {
                        Text("Items in cart")
                    },
                    text = {
                        Text("You have selected items. Do you want to continue to cart or cancel this order?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExitDialog = false
                                onNavigateToCart()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)
                        ) {
                            Text("Go to Cart")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                cartViewModel.clearCart()
                                showExitDialog = false
                            }
                        ) {
                            Text("Cancel Order")
                        }
                    }
                )
            }

            if (isLoading || !isDataReady) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Orange)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = if (cartItems.isNotEmpty()) 80.dp else 0.dp
                    )
                ) {
                    item {
                        Text(
                            "Shops",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Text(
                            "Menu",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val groupedItems = filteredItems.groupBy { it.shopId }

                    groupedItems.forEach { (shopId, items) ->
                        val shopName = viewModel.getShopName(shopId)

                        item {
                            Text(
                                shopName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(items) { menuItem ->
                            MenuItemCard(
                                menuItem = menuItem,
                                quantity = cartItems.find { it.itemId == menuItem.itemId }?.quantity ?: 0,
                                onAddClick = { cartViewModel.addItem(menuItem) },
                                onRemoveClick = { cartViewModel.removeItem(menuItem.itemId) }
                            )
                        }
                    }
                }
            }
        }

        val itemCount = cartViewModel.itemCount

        if (itemCount > 0) {
            Button(
                onClick = onNavigateToCart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text(
                    text = "$itemCount item${if (itemCount > 1) "s" else ""} | View Cart • ₹${cartViewModel.totalPrice.toInt()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    cartViewModel.dismissShopConflict()
                },
                title = {
                    Text("Order from one shop at a time")
                },
                text = {
                    Text("Your cart already has items from another shop. Clear cart and add this item?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            cartViewModel.confirmClearCartAndAdd()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) {
                        Text("Clear & Continue")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            cartViewModel.dismissShopConflict()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ShopCard(shop: Shop, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeLight)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = shop.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (shop.isOpen) "Open" else "Closed",
                fontSize = 11.sp,
                color = if (shop.isOpen) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun CategoryChip(category: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Orange else OrangeLight)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = category, fontSize = 13.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = menuItem.name, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ready in ${menuItem.prepTimeMinutes} min",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${menuItem.price.toInt()}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Orange
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (quantity == 0) {
                    Button(
                        onClick = onAddClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) {
                        Text(
                            "Add",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Orange)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onRemoveClick,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "-",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Text(
                                text = quantity.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            TextButton(
                                onClick = onAddClick,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "+",
                                    color = MaterialTheme.colorScheme.onPrimary,
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
}
