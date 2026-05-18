package com.campusbite.app.ui.screens.shopkeeper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.campusbite.app.ui.viewmodel.MenuManagementViewModel

private val Orange_10 = Orange.copy(alpha = 0.12f)
private val AvailableGreen = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: MenuManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }

        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Menu Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add item",
                            tint = Orange
                        )
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
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading menu...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.menuItems.isEmpty() -> {
                    EmptyMenuState(
                        onAddClick = { showAddDialog = true }
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.menuItems,
                            key = { it.itemId }
                        ) { item ->
                            MenuItemCard(
                                item = item,
                                onEdit = {
                                    editingItem = it
                                },
                                onDelete = {
                                    viewModel.deleteMenuItem(item.itemId)
                                },
                                onToggleAvailability = {
                                    viewModel.updateItemAvailability(
                                        itemId = item.itemId,
                                        isAvailable = !item.isAvailable
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingItem != null) {
        MenuItemDialog(
            item = editingItem,
            onDismiss = {
                showAddDialog = false
                editingItem = null
            },
            onSave = { menuItem ->
                if (editingItem != null) {
                    viewModel.updateMenuItem(menuItem)
                } else {
                    viewModel.addMenuItem(menuItem)
                }

                showAddDialog = false
                editingItem = null
            }
        )
    }
}

@Composable
private fun EmptyMenuState(
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🍽️",
                fontSize = 56.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No menu items yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add your first dish to get started",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(text = "Add First Item")
            }
        }
    }
}

@Composable
private fun MenuItemCard(
    item: MenuItem,
    onEdit: (MenuItem) -> Unit,
    onDelete: () -> Unit,
    onToggleAvailability: () -> Unit
) {
    val statusText = if (item.isAvailable) {
        "Available right now"
    } else {
        "Not available right now"
    }

    val statusColor = if (item.isAvailable) {
        AvailableGreen
    } else {
        MaterialTheme.colorScheme.error
    }

    val statusBg = if (item.isAvailable) {
        AvailableGreen.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 3.dp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "₹${item.price.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Orange
                    )

                    Text(
                        text = "⏱ ${item.prepTimeMinutes} min",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.category.isNotBlank()) {
                        Text(
                            text = item.category,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Orange_10)
                                .padding(
                                    horizontal = 6.dp,
                                    vertical = 2.dp
                                )
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
                        imageVector = if (item.isAvailable) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (item.isAvailable) {
                            "Mark unavailable"
                        } else {
                            "Mark available"
                        },
                        tint = if (item.isAvailable) Orange else statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { onEdit(item) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit item",
                        tint = Orange,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete item",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuItemDialog(
    item: MenuItem?,
    onDismiss: () -> Unit,
    onSave: (MenuItem) -> Unit
) {
    var name by remember(item) {
        mutableStateOf(item?.name ?: "")
    }

    var price by remember(item) {
        mutableStateOf(
            if (item == null || item.price == 0.0) "" else item.price.toString()
        )
    }

    var prepTime by remember(item) {
        mutableStateOf(
            if (item == null || item.prepTimeMinutes == 0) "" else item.prepTimeMinutes.toString()
        )
    }

    var category by remember(item) {
        mutableStateOf(item?.category ?: "")
    }

    var isAvailable by remember(item) {
        mutableStateOf(item?.isAvailable ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (item == null) "Add New Item" else "Edit Item",
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
                    label = {
                        Text(text = "Dish Name")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = {
                        Text(text = "Price ₹")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = prepTime,
                    onValueChange = { prepTime = it },
                    label = {
                        Text(text = "Prep Time minutes")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = {
                        Text(text = "Category")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                AvailabilitySelector(
                    isAvailable = isAvailable,
                    onAvailabilityChange = {
                        isAvailable = it
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPrice = price.toDoubleOrNull()
                    val finalPrepTime = prepTime.toIntOrNull()

                    if (
                        name.isNotBlank() &&
                        finalPrice != null &&
                        finalPrepTime != null
                    ) {
                        val menuItem = MenuItem(
                            itemId = item?.itemId ?: "",
                            shopId = item?.shopId ?: "",
                            name = name.trim(),
                            price = finalPrice,
                            prepTimeMinutes = finalPrepTime,
                            category = category.trim(),
                            isAvailable = isAvailable,
                            imageUrl = item?.imageUrl ?: ""
                        )

                        onSave(menuItem)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (item == null) "Add" else "Update",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Cancel",
                    color = Orange
                )
            }
        }
    )
}

@Composable
private fun AvailabilitySelector(
    isAvailable: Boolean,
    onAvailabilityChange: (Boolean) -> Unit
) {
    val statusColor = if (isAvailable) {
        AvailableGreen
    } else {
        MaterialTheme.colorScheme.error
    }

    val statusBg = if (isAvailable) {
        AvailableGreen.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = statusBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isAvailable) {
                        "Available right now"
                    } else {
                        "Not available right now"
                    },
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )

                Text(
                    text = if (isAvailable) {
                        "Students can order this item."
                    } else {
                        "Students cannot order this item."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isAvailable,
                onCheckedChange = onAvailabilityChange
            )
        }
    }
}