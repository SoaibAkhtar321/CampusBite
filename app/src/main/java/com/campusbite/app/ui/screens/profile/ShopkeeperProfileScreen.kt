package com.campusbite.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.viewmodel.ProfileViewModel

private val BrandOrange = Color(0xFFFF6B00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopkeeperProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val currentUpiId by viewModel.upiId.collectAsState()
    val openingTime by viewModel.openingTime.collectAsState()
    val closingTime by viewModel.closingTime.collectAsState()
    val maxOrdersPerSlot by viewModel.maxOrdersPerSlot.collectAsState()
    val message by viewModel.message.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var isEditingShopSettings by remember { mutableStateOf(false) }

    var upiInput by remember { mutableStateOf("") }
    var openingInput by remember { mutableStateOf("") }
    var closingInput by remember { mutableStateOf("") }
    var maxOrdersInput by remember { mutableStateOf("") }

    LaunchedEffect(currentUpiId, openingTime, closingTime, maxOrdersPerSlot) {
        upiInput = currentUpiId
        openingInput = openingTime
        closingInput = closingTime
        maxOrdersInput = maxOrdersPerSlot.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Shopkeeper Profile", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BrandOrange)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile?.name?.firstOrNull()?.uppercase() ?: "S",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = userProfile?.name ?: "Shopkeeper",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = userProfile?.email ?: "CampusBite Partner",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Store,
                            contentDescription = null,
                            tint = BrandOrange
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Shop Info",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    InfoText(
                        label = "Shop ID",
                        value = userProfile?.shopId?.ifBlank { "Not assigned" } ?: "Loading..."
                    )
                    InfoText(label = "Role", value = userProfile?.role ?: "shopkeeper")
                    InfoText(
                        label = "Phone",
                        value = userProfile?.phone?.ifBlank { "Not available" } ?: "Loading..."
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = null,
                            tint = BrandOrange
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Payment & Timings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {
                                isEditingShopSettings = !isEditingShopSettings
                                viewModel.clearMessage()
                            }
                        ) {
                            Text(if (isEditingShopSettings) "Cancel" else "Edit")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isEditingShopSettings) {
                        InfoText(label = "UPI ID", value = currentUpiId.ifBlank { "Not added" })
                        InfoText(label = "Opening Time", value = openingTime)
                        InfoText(label = "Closing Time", value = closingTime)
                        InfoText(label = "Max Orders / Slot", value = maxOrdersPerSlot.toString())
                    } else {
                        OutlinedTextField(
                            value = upiInput,
                            onValueChange = {
                                upiInput = it
                                viewModel.clearMessage()
                            },
                            label = { Text("UPI ID") },
                            placeholder = { Text("example@paytm") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = openingInput,
                                onValueChange = {
                                    openingInput = it
                                    viewModel.clearMessage()
                                },
                                label = { Text("Opening") },
                                placeholder = { Text("08:00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = closingInput,
                                onValueChange = {
                                    closingInput = it
                                    viewModel.clearMessage()
                                },
                                label = { Text("Closing") },
                                placeholder = { Text("21:00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = maxOrdersInput,
                            onValueChange = {
                                maxOrdersInput = it
                                viewModel.clearMessage()
                            },
                            label = { Text("Max Orders Per Slot") },
                            placeholder = { Text("5") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.updateShopSettings(
                                    newUpiId = upiInput,
                                    newOpeningTime = openingInput,
                                    newClosingTime = closingInput,
                                    newMaxOrdersPerSlot = maxOrdersInput
                                )
                                isEditingShopSettings = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                        ) {
                            Text("Save Changes")
                        }
                    }

                    if (message != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = message ?: "",
                            color = if (message?.contains("success", true) == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = BrandOrange
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Logout,
                            contentDescription = null
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text("Logout")
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InfoText(
    label: String,
    value: String
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.height(6.dp))
}