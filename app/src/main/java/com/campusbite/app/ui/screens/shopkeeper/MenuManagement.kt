package com.campusbite.app.ui.screens.shopkeeper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.viewmodel.ShopkeeperViewModel
import com.google.firebase.auth.FirebaseAuth

private val Orange_10 = Orange.copy(alpha = 0.12f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShopkeeperViewModel = hiltViewModel()
) {
    val menuItems by viewModel.menuItems.collectAsState()
    val shopId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }

    // Load menu items when screen opens
    LaunchedEffect(shopId) {
        if (shopId.isNotEmpty()) {
            viewModel.loadMenuItems(shopId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Menu Management", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add item", tint = Orange)
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
            if (menuItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍽️", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No menu items yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add your first dish to get started",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Orange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add First Item")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(menuItems, key = { it.itemId }) { item ->
                        MenuItemCard(
                            item = item,
                            onEdit = { editingItem = it },
                            onDelete = { viewModel.deleteMenuItem(item.itemId) },
                            onToggleAvailability = { viewModel.toggleMenuItemAvailability(item.itemId, item) }
                        )
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingItem != null) {
        MenuItemDialog(
            item = editingItem,
            shopId = shopId,
            onDismiss = {
                showAddDialog = false
                editingItem = null
            },
            onSave = { item ->
                if (editingItem != null) {
                    viewModel.updateMenuItem(item)
                } else {
                    viewModel.addMenuItem(item)
                }
                showAddDialog = false
                editingItem = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Menu Item Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MenuItemCard(
    item: MenuItem,
    onEdit: (MenuItem) -> Unit,
    onDelete: () -> Unit,
    onToggleAvailability: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (item.isAvailable) Color(0xFF4CAF50).copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ) {
                        Text(
                            if (item.isAvailable) "Available" else "Unavailable",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isAvailable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "₹${item.price.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Orange
                    )
                    Text(
                        "⏱ ${item.prepTimeMinutes}min",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.category.isNotBlank()) {
                        Text(
                            item.category,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Orange_10)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onToggleAvailability,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (item.isAvailable) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle availability",
                        tint = Orange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onEdit(item) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Orange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add/Edit Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MenuItemDialog(
    item: MenuItem?,
    shopId: String,
    onDismiss: () -> Unit,
    onSave: (MenuItem) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var price by remember { mutableStateOf(item?.price?.toString() ?: "") }
    var prepTime by remember { mutableStateOf(item?.prepTimeMinutes?.toString() ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (item == null) "Add New Item" else "Edit Item",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Dish Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = prepTime,
                    onValueChange = { prepTime = it },
                    label = { Text("Prep Time (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g., Breakfast, Main)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && price.isNotBlank() && prepTime.isNotBlank()) {
                        val menuItem = MenuItem(
                            itemId = item?.itemId ?: "",
                            shopId = shopId,
                            name = name.trim(),
                            price = price.toDoubleOrNull() ?: 0.0,
                            prepTimeMinutes = prepTime.toIntOrNull() ?: 0,
                            category = category.trim(),
                            isAvailable = item?.isAvailable ?: true,
                            imageUrl = item?.imageUrl ?: ""
                        )
                        onSave(menuItem)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (item == null) "Add" else "Update", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Orange)
            }
        }
    )
}