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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateToProfile: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val shops by viewModel.shops.collectAsState()
    val users by viewModel.users.collectAsState()
    val pendingShopkeepers by viewModel.pendingShopkeepers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var tabIndex by remember { mutableStateOf(0) }

    val tabs = listOf(
        "Pending (${pendingShopkeepers.size})",
        "Shops",
        "Users"
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
            TabRow(
                selectedTabIndex = tabIndex
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
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
                    viewModel = viewModel
                )

                1 -> ShopsTab(
                    shops = shops,
                    viewModel = viewModel
                )

                2 -> UsersTab(
                    users = users,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun PendingShopkeepersTab(
    pendingShopkeepers: List<AdminUser>,
    viewModel: AdminViewModel
) {
    if (pendingShopkeepers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No pending shopkeeper requests",
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = pendingShopkeepers,
            key = { user -> user.docId }
        ) { user ->
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

                    Text(
                        text = "Email: ${user.email.ifBlank { "Not available" }}"
                    )

                    Text(
                        text = "Phone: ${user.phone.ifBlank { "Not available" }}"
                    )

                    Text(
                        text = "Role: ${user.role}"
                    )

                    Text(
                        text = "Status: Pending approval",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.setShopkeeperApproved(
                                    userDocId = user.docId,
                                    approved = true
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Approve")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.setUserBlocked(
                                    userDocId = user.docId,
                                    blocked = true
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Block")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopsTab(
    shops: List<AdminShop>,
    viewModel: AdminViewModel
) {
    if (shops.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No shops created yet",
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = shops,
            key = { shop -> shop.docId }
        ) { shop ->
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

                    Text(text = "ShopId: ${shop.shopId.ifBlank { "Not available" }}")
                    Text(text = "OwnerUid: ${shop.ownerUid.ifBlank { "Not assigned" }}")

                    Text(
                        text = if (shop.isApproved) {
                            "Status: Approved"
                        } else {
                            "Status: Not approved"
                        },
                        color = if (shop.isApproved) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.SemiBold
                    )

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
                                onCheckedChange = {
                                    viewModel.setShopApproved(
                                        shopDocId = shop.docId,
                                        approved = it,
                                        shopId = shop.shopId
                                    )
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Open")

                            Switch(
                                checked = shop.isOpen,
                                onCheckedChange = {
                                    viewModel.setShopOpen(
                                        shopDocId = shop.docId,
                                        open = it
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<AdminUser>,
    viewModel: AdminViewModel
) {
    if (users.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No users found",
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = users,
            key = { user -> user.docId }
        ) { user ->
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

                    Text(
                        text = "Email: ${user.email.ifBlank { "Not available" }}"
                    )

                    Text(
                        text = "Phone: ${user.phone.ifBlank { "Not available" }}"
                    )

                    Text(
                        text = "Role: ${user.role}"
                    )

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
                            Text(
                                text = "ShopId: ${user.shopId}"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoleDropdown(
                            currentRole = user.role,
                            onRoleChange = { role ->
                                viewModel.setUserRole(
                                    userDocId = user.docId,
                                    role = role
                                )
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Blocked")

                            Switch(
                                checked = user.isBlocked,
                                onCheckedChange = { blocked ->
                                    viewModel.setUserBlocked(
                                        userDocId = user.docId,
                                        blocked = blocked
                                    )
                                }
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
                                onCheckedChange = { approved ->
                                    viewModel.setShopkeeperApproved(
                                        userDocId = user.docId,
                                        approved = approved
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleDropdown(
    currentRole: String,
    onRoleChange: (String) -> Unit
) {
    val roles = listOf("student", "shopkeeper", "admin")

    var expanded by remember { mutableStateOf(false) }
    var selected by remember(currentRole) {
        mutableStateOf(currentRole)
    }

    Box {
        OutlinedButton(
            onClick = {
                expanded = true
            }
        ) {
            Text(selected)
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
                        selected = role
                        onRoleChange(role)
                        expanded = false
                    }
                )
            }
        }
    }
}