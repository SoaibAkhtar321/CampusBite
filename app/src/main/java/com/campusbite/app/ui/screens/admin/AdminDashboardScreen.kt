package com.campusbite.app.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.viewmodel.AdminUser
import com.campusbite.app.ui.viewmodel.AdminViewModel
import com.campusbite.app.ui.viewmodel.AdminShop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateToProfile: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val shops by viewModel.shops.collectAsState()
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Shops", "Users")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel") },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            when (tabIndex) {
                0 -> ShopsTab(shops = shops, viewModel = viewModel)
                1 -> UsersTab(users = users, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ShopsTab(shops: List<AdminShop>, viewModel: AdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(shops, key = { it.docId }) { shop ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(shop.name, style = MaterialTheme.typography.titleMedium)
                    Text("ShopId: ${shop.shopId}")
                    Text("OwnerUid: ${shop.ownerUid}")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Approved")
                            Switch(
                                checked = shop.isApproved,
                                onCheckedChange = { viewModel.setShopApproved(shop.docId, it) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Open")
                            Switch(
                                checked = shop.isOpen,
                                onCheckedChange = { viewModel.setShopOpen(shop.docId, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTab(users: List<AdminUser>, viewModel: AdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(users, key = { it.docId }) { user ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(user.name, style = MaterialTheme.typography.titleMedium)
                    Text(user.email)
                    Text("Role: ${user.role}")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RoleDropdown(
                            currentRole = user.role,
                            onRoleChange = { viewModel.setUserRole(user.docId, it) }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Blocked")
                            Switch(
                                checked = user.isBlocked,
                                onCheckedChange = { viewModel.setUserBlocked(user.docId, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleDropdown(currentRole: String, onRoleChange: (String) -> Unit) {
    val roles = listOf("student", "shopkeeper", "admin")
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(currentRole) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role) },
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