package com.campusbite.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.viewmodel.AdminShop
import com.campusbite.app.ui.viewmodel.AdminUser
import com.campusbite.app.ui.viewmodel.AdminViewModel

private data class ConfirmAction(
    val title: String,
    val message: String,
    val confirmText: String,
    val isDanger: Boolean = false,
    val onConfirm: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToShopReport: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val shops by viewModel.shops.collectAsState()
    val users by viewModel.users.collectAsState()
    val pendingShopkeepers by viewModel.pendingShopkeepers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()

    var tabIndex by remember { mutableStateOf(0) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    val activeShops = shops.filter { !it.isDeleted }

    val tabs = listOf(
        "Pending (${pendingShopkeepers.size})",
        "Shops (${activeShops.size})",
        "Users (${users.size})"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Admin Panel",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = {
                            tabIndex = index
                        },
                        text = {
                            Text(
                                text = title,
                                color = if (tabIndex == index) {
                                    Orange
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    )
                }
            }

            if (!message.isNullOrBlank()) {
                Text(
                    text = message ?: "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (
                        message?.contains("failed", ignoreCase = true) == true ||
                        message?.contains("missing", ignoreCase = true) == true ||
                        message?.contains("error", ignoreCase = true) == true
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            when (tabIndex) {
                0 -> PendingShopkeepersTab(
                    pendingShopkeepers = pendingShopkeepers,
                    onApprove = { user ->
                        confirmAction = ConfirmAction(
                            title = "Approve Shopkeeper?",
                            message = "This will approve ${user.name.ifBlank { user.email }} and create or activate their shop.",
                            confirmText = "Approve"
                        ) {
                            viewModel.setShopkeeperApproved(
                                userDocId = user.docId,
                                approved = true
                            )
                        }
                    },
                    onBlock = { user ->
                        confirmAction = ConfirmAction(
                            title = "Block Request?",
                            message = "This will block ${user.name.ifBlank { user.email }} from approval.",
                            confirmText = "Block",
                            isDanger = true
                        ) {
                            viewModel.setUserBlocked(
                                userDocId = user.docId,
                                blocked = true
                            )
                        }
                    },
                    onRemove = { user ->
                        confirmAction = ConfirmAction(
                            title = "Remove Pending Request?",
                            message = "This will remove the shopkeeper request and convert this account back to student.",
                            confirmText = "Remove",
                            isDanger = true
                        ) {
                            viewModel.removePendingShopkeeper(
                                userDocId = user.docId
                            )
                        }
                    }
                )

                1 -> ShopsTab(
                    shops = activeShops,
                    onViewReport = { shop ->
                        onNavigateToShopReport(shop.shopId.ifBlank { shop.docId })
                    },
                    onApprovedChange = { shop, approved ->
                        confirmAction = ConfirmAction(
                            title = if (approved) {
                                "Approve Shop?"
                            } else {
                                "Remove Shop Approval?"
                            },
                            message = if (approved) {
                                "This shop will become visible after approval if it is not blocked."
                            } else {
                                "This shop will no longer be treated as approved."
                            },
                            confirmText = if (approved) {
                                "Approve"
                            } else {
                                "Remove Approval"
                            },
                            isDanger = !approved
                        ) {
                            viewModel.setShopApproved(
                                shopDocId = shop.docId,
                                approved = approved,
                                shopId = shop.shopId
                            )
                        }
                    },
                    onOpenChange = { shop, open ->
                        viewModel.setShopOpen(
                            shopDocId = shop.docId,
                            open = open
                        )
                    },
                    onBlockToggle = { shop ->
                        val shouldBlock = !shop.isBlocked

                        confirmAction = ConfirmAction(
                            title = if (shouldBlock) {
                                "Block Shop?"
                            } else {
                                "Unblock Shop?"
                            },
                            message = if (shouldBlock) {
                                "This shop will be hidden from students and marked closed."
                            } else {
                                "This shop can become visible again if approved."
                            },
                            confirmText = if (shouldBlock) {
                                "Block"
                            } else {
                                "Unblock"
                            },
                            isDanger = shouldBlock
                        ) {
                            viewModel.setShopBlocked(
                                shopDocId = shop.docId,
                                shopId = shop.shopId,
                                blocked = shouldBlock
                            )
                        }
                    },
                    onDelete = { shop ->
                        confirmAction = ConfirmAction(
                            title = "Delete Shop Completely?",
                            message = "This will delete the shop and its menu items, and remove the shop link from its shopkeeper. This action cannot be easily undone.",
                            confirmText = "Delete",
                            isDanger = true
                        ) {
                            viewModel.deleteShopCompletely(
                                shopDocId = shop.docId,
                                shopId = shop.shopId
                            )
                        }
                    }
                )

                2 -> UsersTab(
                    users = users,
                    onRoleChange = { user, role ->
                        confirmAction = ConfirmAction(
                            title = "Change User Role?",
                            message = "Change ${user.name.ifBlank { user.email }} role to $role?",
                            confirmText = "Change"
                        ) {
                            viewModel.setUserRole(
                                userDocId = user.docId,
                                role = role
                            )
                        }
                    },
                    onBlockedChange = { user, blocked ->
                        confirmAction = ConfirmAction(
                            title = if (blocked) {
                                "Block User?"
                            } else {
                                "Unblock User?"
                            },
                            message = if (blocked) {
                                "This user may lose access to app actions."
                            } else {
                                "This user will be allowed again."
                            },
                            confirmText = if (blocked) {
                                "Block"
                            } else {
                                "Unblock"
                            },
                            isDanger = blocked
                        ) {
                            viewModel.setUserBlocked(
                                userDocId = user.docId,
                                blocked = blocked
                            )
                        }
                    },
                    onShopkeeperApprovedChange = { user, approved ->
                        confirmAction = ConfirmAction(
                            title = if (approved) {
                                "Approve Shopkeeper?"
                            } else {
                                "Move Shopkeeper To Pending?"
                            },
                            message = if (approved) {
                                "This will approve the shopkeeper and create or activate their shop."
                            } else {
                                "This will remove shopkeeper approval."
                            },
                            confirmText = if (approved) {
                                "Approve"
                            } else {
                                "Move Pending"
                            },
                            isDanger = !approved
                        ) {
                            viewModel.setShopkeeperApproved(
                                userDocId = user.docId,
                                approved = approved
                            )
                        }
                    }
                )
            }
        }
    }

    confirmAction?.let { action ->
        ConfirmDialog(
            action = action,
            onDismiss = {
                confirmAction = null
            },
            onConfirm = {
                action.onConfirm()
                confirmAction = null
            }
        )
    }
}

@Composable
private fun PendingShopkeepersTab(
    pendingShopkeepers: List<AdminUser>,
    onApprove: (AdminUser) -> Unit,
    onBlock: (AdminUser) -> Unit,
    onRemove: (AdminUser) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filteredUsers = pendingShopkeepers.filter { user ->
        matchesUserSearch(user, query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        AdminSearchBar(
            query = query,
            onQueryChange = {
                query = it
            },
            placeholder = "Search pending shopkeepers..."
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredUsers.isEmpty()) {
            EmptyState(
                text = if (query.isBlank()) {
                    "No pending shopkeeper requests"
                } else {
                    "No pending request found"
                }
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = filteredUsers,
                key = { user -> user.docId }
            ) { user ->
                PendingShopkeeperCard(
                    user = user,
                    onApprove = {
                        onApprove(user)
                    },
                    onBlock = {
                        onBlock(user)
                    },
                    onRemove = {
                        onRemove(user)
                    }
                )
            }
        }
    }
}

@Composable
private fun PendingShopkeeperCard(
    user: AdminUser,
    onApprove: () -> Unit,
    onBlock: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = user.name.ifBlank { "Unnamed Shopkeeper" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text("Email: ${user.email.ifBlank { "Not available" }}")
            Text("Phone: ${user.phone.ifBlank { "Not available" }}")
            Text("Role: ${user.role}")

            if (user.shopId.isNotBlank()) {
                Text("ShopId: ${user.shopId}")
            }

            Text(
                text = "Status: Pending approval",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Approve")
                }

                OutlinedButton(
                    onClick = onBlock,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Block")
                }

                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun ShopsTab(
    shops: List<AdminShop>,
    onViewReport: (AdminShop) -> Unit,
    onApprovedChange: (AdminShop, Boolean) -> Unit,
    onOpenChange: (AdminShop, Boolean) -> Unit,
    onBlockToggle: (AdminShop) -> Unit,
    onDelete: (AdminShop) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filteredShops = shops.filter { shop ->
        val q = query.trim().lowercase()

        q.isBlank() ||
                shop.name.lowercase().contains(q) ||
                shop.shopId.lowercase().contains(q) ||
                shop.ownerUid.lowercase().contains(q)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        AdminSearchBar(
            query = query,
            onQueryChange = {
                query = it
            },
            placeholder = "Search shops..."
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredShops.isEmpty()) {
            EmptyState(
                text = if (query.isBlank()) {
                    "No shops created yet"
                } else {
                    "No shop found"
                }
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = filteredShops,
                key = { shop -> shop.docId }
            ) { shop ->
                ShopAdminCard(
                    shop = shop,
                    onViewReport = {
                        onViewReport(shop)
                    },
                    onApprovedChange = {
                        onApprovedChange(shop, it)
                    },
                    onOpenChange = {
                        onOpenChange(shop, it)
                    },
                    onBlockToggle = {
                        onBlockToggle(shop)
                    },
                    onDelete = {
                        onDelete(shop)
                    }
                )
            }
        }
    }
}

@Composable
private fun ShopAdminCard(
    shop: AdminShop,
    onViewReport: () -> Unit,
    onApprovedChange: (Boolean) -> Unit,
    onOpenChange: (Boolean) -> Unit,
    onBlockToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = shop.name.ifBlank { "Unnamed Shop" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text("ShopId: ${shop.shopId.ifBlank { "Not available" }}")
            Text("OwnerUid: ${shop.ownerUid.ifBlank { "Not assigned" }}")

            Text(
                text = when {
                    shop.isBlocked -> "Status: Blocked"
                    shop.isApproved -> "Status: Approved"
                    else -> "Status: Not approved"
                },
                color = when {
                    shop.isBlocked -> MaterialTheme.colorScheme.error
                    shop.isApproved -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onViewReport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "View Report",
                    fontWeight = FontWeight.Bold,
                    color = Orange
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Approved")

                    Switch(
                        checked = shop.isApproved,
                        enabled = !shop.isBlocked,
                        onCheckedChange = onApprovedChange
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Open")

                    Switch(
                        checked = shop.isOpen,
                        enabled = !shop.isBlocked && shop.isApproved,
                        onCheckedChange = onOpenChange
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBlockToggle,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (shop.isBlocked) {
                            "Unblock"
                        } else {
                            "Block"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<AdminUser>,
    onRoleChange: (AdminUser, String) -> Unit,
    onBlockedChange: (AdminUser, Boolean) -> Unit,
    onShopkeeperApprovedChange: (AdminUser, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filteredUsers = users.filter { user ->
        matchesUserSearch(user, query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        AdminSearchBar(
            query = query,
            onQueryChange = {
                query = it
            },
            placeholder = "Search users..."
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredUsers.isEmpty()) {
            EmptyState(
                text = if (query.isBlank()) {
                    "No users found"
                } else {
                    "No user found"
                }
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = filteredUsers,
                key = { user -> user.docId }
            ) { user ->
                UserAdminCard(
                    user = user,
                    onRoleChange = { role ->
                        onRoleChange(user, role)
                    },
                    onBlockedChange = { blocked ->
                        onBlockedChange(user, blocked)
                    },
                    onShopkeeperApprovedChange = { approved ->
                        onShopkeeperApprovedChange(user, approved)
                    }
                )
            }
        }
    }
}

@Composable
private fun UserAdminCard(
    user: AdminUser,
    onRoleChange: (String) -> Unit,
    onBlockedChange: (Boolean) -> Unit,
    onShopkeeperApprovedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = user.name.ifBlank { "Unnamed User" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text("Email: ${user.email.ifBlank { "Not available" }}")
            Text("Phone: ${user.phone.ifBlank { "Not available" }}")
            Text("Role: ${user.role}")

            if (user.role == "shopkeeper") {
                Text(
                    text = if (user.isApproved) {
                        "Approval: Approved"
                    } else {
                        "Approval: Pending"
                    },
                    color = if (user.isApproved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold
                )

                if (user.shopId.isNotBlank()) {
                    Text("ShopId: ${user.shopId}")
                }
            }

            if (user.isBlocked) {
                Text(
                    text = "Blocked",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleDropdown(
                    currentRole = user.role,
                    onRoleChange = onRoleChange
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Blocked")

                    Switch(
                        checked = user.isBlocked,
                        onCheckedChange = onBlockedChange
                    )
                }
            }

            if (user.role == "shopkeeper") {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Approved")

                    Switch(
                        checked = user.isApproved,
                        enabled = !user.isBlocked,
                        onCheckedChange = onShopkeeperApprovedChange
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        placeholder = {
            Text(placeholder)
        }
    )
}

@Composable
private fun EmptyState(
    text: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ConfirmDialog(
    action: ConfirmAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(action.title)
        },
        text = {
            Text(action.message)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(
                    text = action.confirmText,
                    color = if (action.isDanger) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Orange
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RoleDropdown(
    currentRole: String,
    onRoleChange: (String) -> Unit
) {
    val roles = listOf(
        "student",
        "shopkeeper",
        "admin"
    )

    var expanded by remember {
        mutableStateOf(false)
    }

    Box {
        OutlinedButton(
            onClick = {
                expanded = true
            }
        ) {
            Text(currentRole)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = {
                        Text(role)
                    },
                    onClick = {
                        onRoleChange(role)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun matchesUserSearch(
    user: AdminUser,
    query: String
): Boolean {
    val q = query.trim().lowercase()

    return q.isBlank() ||
            user.name.lowercase().contains(q) ||
            user.email.lowercase().contains(q) ||
            user.phone.lowercase().contains(q) ||
            user.role.lowercase().contains(q) ||
            user.shopId.lowercase().contains(q)
}
