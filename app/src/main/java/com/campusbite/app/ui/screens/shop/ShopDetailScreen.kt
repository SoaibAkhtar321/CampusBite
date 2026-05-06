package com.campusbite.app.ui.screens.shop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel
import androidx.activity.compose.BackHandler

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
    val cartItems by cartViewModel.cartItems.collectAsState()
    val showDialog by cartViewModel.showShopConflict.collectAsState()

    val shop = shops.find { it.shopId == shopId }
    val shopMenuItems = menuItems.filter { it.shopId == shopId }
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = cartItems.isNotEmpty()) {
        showExitDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        shop?.name ?: "Shop Detail",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (cartItems.isNotEmpty()) {
                                showExitDialog = true
                            } else {
                                onNavigateBack()
                            }
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OrangeLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = shop?.name ?: shopId,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (shop?.isOpen == true)
                                    "Accepting Orders"
                                else
                                    "Currently Closed",
                                color = if (shop?.isOpen == true)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Menu",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (shopMenuItems.isEmpty()) {
                    item {
                        Text(
                            text = "No menu items available.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(shopMenuItems) { item ->

                        ShopMenuItemCard(
                            menuItem = item,
                            quantity = cartItems.find {
                                it.itemId == item.itemId
                            }?.quantity ?: 0,
                            isShopOpen = shop?.isOpen == true,
                            onAddClick = {
                                cartViewModel.addItem(item)
                            },
                            onRemoveClick = {
                                cartViewModel.removeItem(item.itemId)
                            }
                        )
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
                                onNavigateBack()
                            }
                        ) {
                            Text("Cancel Order")
                        }
                    }
                )
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
}

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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                    text = menuItem.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Ready in ${menuItem.prepTimeMinutes} min",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "₹${menuItem.price.toInt()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Orange
                )
            }

            if (!isShopOpen) {

                Text(
                    text = "Closed",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )

            } else {

                if (quantity == 0) {

                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Add",
                            fontWeight = FontWeight.Bold
                        )
                    }

                } else {

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Orange
                        )
                    ) {

                        Row(
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            ),
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