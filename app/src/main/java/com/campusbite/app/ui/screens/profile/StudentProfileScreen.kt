package com.campusbite.app.ui.screens.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.viewmodel.OrderViewModel
import com.campusbite.app.ui.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private val BrandOrange = Color(0xFFFF6B00)

private const val SUPPORT_EMAIL = "support.campusbite@gmail.com"
private const val SUPPORT_WHATSAPP_NUMBER = "918957833269"

private const val WEBSITE_BASE_URL = "https://thecampusbite.vercel.app"
private const val PRIVACY_POLICY_URL = "$WEBSITE_BASE_URL/privacy-policy"
private const val TERMS_URL = "$WEBSITE_BASE_URL/terms-and-conditions"
private const val REFUND_POLICY_URL = "$WEBSITE_BASE_URL/refund-cancellation-policy"

@Composable
fun StudentProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOrderStatus: (String) -> Unit,
    onNavigateToOrderHistory: () -> Unit,
    onLogout: () -> Unit,
    orderViewModel: OrderViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser

    var showLogoutDialog by remember {
        mutableStateOf(false)
    }

    var showEditProfileDialog by remember {
        mutableStateOf(false)
    }

    var notificationsEnabled by remember {
        mutableStateOf(true)
    }

    var firestoreName by remember {
        mutableStateOf("")
    }

    var firestoreEmail by remember {
        mutableStateOf("")
    }

    var firestorePhone by remember {
        mutableStateOf("")
    }

    var editName by remember {
        mutableStateOf("")
    }

    var editPhone by remember {
        mutableStateOf("")
    }

    val activeOrder by orderViewModel.activeOrder.collectAsState()
    val userOrders by orderViewModel.userOrders.collectAsState()
    val profileMessage by profileViewModel.message.collectAsState()

    val latestOrder = userOrders.firstOrNull()

    val latestCancelledOrder = latestOrder?.takeIf {
        it.status.lowercase() == "cancelled"
    }

    val trackableOrder = activeOrder ?: latestCancelledOrder

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid

        if (uid.isNullOrBlank()) {
            onDispose { }
        } else {
            val registration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        firestoreName = snapshot.getString("name").orEmpty()
                        firestoreEmail = snapshot.getString("email").orEmpty()
                        firestorePhone = snapshot.getString("phone")
                            ?: snapshot.getString("phoneNumber")
                                    ?: snapshot.getString("mobile")
                                    ?: ""
                    }
                }

            onDispose {
                registration.remove()
            }
        }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid

        if (!uid.isNullOrBlank()) {
            orderViewModel.listenToActiveOrder(uid)
            orderViewModel.loadUserOrders(uid)
        }
    }

    LaunchedEffect(profileMessage) {
        val message = profileMessage.orEmpty()

        if (message.contains("success", ignoreCase = true)) {
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()

            showEditProfileDialog = false
            profileViewModel.clearMessage()
        }
    }

    val displayEmail = firestoreEmail.trim()
        .ifBlank {
            currentUser?.email?.trim().orEmpty()
        }
        .ifBlank {
            "No Email"
        }

    val displayName = firestoreName.trim()
        .ifBlank {
            currentUser?.displayName?.trim().orEmpty()
        }
        .ifBlank {
            currentUser?.email
                ?.substringBefore("@")
                ?.trim()
                ?.replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase()
                    } else {
                        char.toString()
                    }
                }
                .orEmpty()
        }
        .ifBlank {
            "Student"
        }

    val displayPhone = firestorePhone.trim()
        .ifBlank {
            currentUser?.phoneNumber?.trim().orEmpty()
        }
        .ifBlank {
            "Phone not added"
        }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StudentHeaderCard(
                name = displayName,
                email = displayEmail,
                phone = displayPhone
            )

            Button(
                onClick = {
                    editName = displayName
                    editPhone = firestorePhone.trim()
                    profileViewModel.clearMessage()
                    showEditProfileDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandOrange
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text("Edit Profile")
            }

            if (trackableOrder != null) {
                val isCancelled = trackableOrder.status.lowercase() == "cancelled"

                SectionCard(
                    title = if (isCancelled) {
                        "Order Cancelled"
                    } else {
                        "Track Your Order"
                    },
                    icon = Icons.Outlined.History
                ) {
                    Text(
                        text = if (isCancelled) {
                            "Your latest order was cancelled because payment was not received."
                        } else {
                            "Current Status: ${trackableOrder.status}"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isCancelled) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "If you already paid, open details and call the shopkeeper.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            onNavigateToOrderStatus(trackableOrder.orderId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandOrange
                        )
                    ) {
                        Text(
                            text = if (isCancelled) {
                                "View Details"
                            } else {
                                "Track Order"
                            }
                        )
                    }
                }
            }

            SectionCard(
                title = "Recent Orders",
                icon = Icons.Outlined.History
            ) {
                if (userOrders.isNotEmpty()) {
                    val latest = userOrders.first()

                    Text(
                        text = "Latest Order",
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Status: ${latest.status}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Amount: ₹${latest.totalPrice.toInt()}",
                        color = BrandOrange,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = onNavigateToOrderHistory,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "View Order History",
                            color = BrandOrange
                        )
                    }
                } else {
                    Text(
                        text = "No recent orders yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard(
                title = "Settings",
                icon = Icons.Outlined.Settings
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text("Order Notifications")

                            Text(
                                text = "Get updates about your orders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            notificationsEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = BrandOrange
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ActionRow(
                    icon = Icons.Outlined.Logout,
                    label = "Logout",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        showLogoutDialog = true
                    }
                )
            }

            SectionCard(
                title = "Help & Support",
                icon = Icons.Outlined.SupportAgent
            ) {
                Text(
                    text = "Need help with an order, payment, or app issue?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                ActionRow(
                    icon = Icons.Outlined.SupportAgent,
                    label = "WhatsApp Support",
                    trailingText = "Fast help",
                    onClick = {
                        openWhatsAppSupport(
                            context = context,
                            userEmail = displayEmail
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ActionRow(
                    icon = Icons.Outlined.SupportAgent,
                    label = "Email Support",
                    trailingText = SUPPORT_EMAIL,
                    onClick = {
                        openEmailSupport(
                            context = context,
                            userEmail = displayEmail
                        )
                    }
                )
            }

            SectionCard(
                title = "Legal & Policies",
                icon = Icons.Outlined.Settings
            ) {
                ActionRow(
                    icon = Icons.Outlined.Settings,
                    label = "Privacy Policy",
                    trailingText = "Data usage",
                    onClick = {
                        openWebPage(context, PRIVACY_POLICY_URL)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ActionRow(
                    icon = Icons.Outlined.Settings,
                    label = "Terms & Conditions",
                    trailingText = "App rules",
                    onClick = {
                        openWebPage(context, TERMS_URL)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                ActionRow(
                    icon = Icons.Outlined.Settings,
                    label = "Refund & Cancellation Policy",
                    trailingText = "Refund support",
                    onClick = {
                        openWebPage(context, REFUND_POLICY_URL)
                    }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = {
                showEditProfileDialog = false
                profileViewModel.clearMessage()
            },
            title = {
                Text("Edit Profile")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = {
                            editName = it
                            profileViewModel.clearMessage()
                        },
                        label = {
                            Text("Full Name")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { value ->
                            editPhone = value
                                .filter { it.isDigit() }
                                .take(10)

                            profileViewModel.clearMessage()
                        },
                        label = {
                            Text("Phone Number")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    profileMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileViewModel.updateStudentProfile(
                            newName = editName,
                            newPhone = editPhone
                        )
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditProfileDialog = false
                        profileViewModel.clearMessage()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = {
                showLogoutDialog = false
            },
            title = {
                Text("Logout")
            },
            text = {
                Text("Are you sure you want to logout?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(
                        text = "Logout",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun openWhatsAppSupport(
    context: android.content.Context,
    userEmail: String
) {
    val message = """
        Hi CampusBite Support,
        I need help with my order/payment issue.

        User Email: ${userEmail.ifBlank { "Not available" }}
    """.trimIndent()

    val encodedMessage = Uri.encode(message)

    val uri = Uri.parse(
        "https://wa.me/$SUPPORT_WHATSAPP_NUMBER?text=$encodedMessage"
    )

    val intent = Intent(
        Intent.ACTION_VIEW,
        uri
    )

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "WhatsApp is not available on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun openEmailSupport(
    context: android.content.Context,
    userEmail: String
) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")

        putExtra(
            Intent.EXTRA_SUBJECT,
            "CampusBite Support"
        )

        putExtra(
            Intent.EXTRA_TEXT,
            """
                Hi CampusBite Support,
                I need help with:

                User Email: ${userEmail.ifBlank { "Not available" }}
            """.trimIndent()
        )
    }

    try {
        context.startActivity(
            Intent.createChooser(
                intent,
                "Contact CampusBite Support"
            )
        )
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "No email app found on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun openWebPage(
    context: android.content.Context,
    url: String
) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(url)
    )

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "No browser found on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun StudentHeaderCard(
    name: String,
    email: String,
    phone: String
) {
    val profileInitial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
        ?: email.trim().firstOrNull()?.uppercaseChar()?.toString()
        ?: "U"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = BrandOrange
        )
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
                    text = profileInitial,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = name.ifBlank {
                        "Student"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = email.ifBlank {
                        "No Email"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = phone.ifBlank {
                        "Phone not added"
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandOrange
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                color = tint
            )
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null
            )
        }
    }
}