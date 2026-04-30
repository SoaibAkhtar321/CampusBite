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
import com.campusbite.app.ui.theme.TextSecondary
import com.campusbite.app.ui.viewmodel.HomeViewModel
import androidx.compose.foundation.layout.statusBarsPadding
@Composable
fun HomeScreen(
    onNavigateToShopDetail: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val shops by viewModel.shops.collectAsState()
    val isDataReady by viewModel.isDataReady.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredItems by remember(searchQuery, selectedCategory) {
        derivedStateOf { viewModel.getFilteredItems() }
    }

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
            Column {
                Text("CampusBite", fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Search dish or shop...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Orange) },
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

        if (isLoading || !isDataReady)  {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
        } else {
            LazyColumn {
                // Shop cards
                item {
                    Text("Shops", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shops) { shop ->
                            ShopCard(shop = shop, onClick = { onNavigateToShopDetail(shop.shopId) })
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Category chips
                item {
                    Text("Menu", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // Menu items grouped by shop
                val groupedItems = filteredItems.groupBy { it.shopId }
                groupedItems.forEach { (shopId, items) ->
                    val shopName = viewModel.getShopName(shopId)
                    item {
                        Text(shopName, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = Orange, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(items) { menuItem ->
                        MenuItemCard(menuItem = menuItem)
                    }
                }
            }
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
                text = shop.name,  // ← fix this
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary  // ← fix this
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
        Text(text = category, fontSize = 13.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun MenuItemCard(menuItem: MenuItem) {
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
                Text(text = menuItem.name, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Ready in ${menuItem.prepTimeMinutes} min",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "₹${menuItem.price.toInt()}",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Orange)
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { },
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Add", fontSize = 12.sp)
                }
            }
        }
    }
}
