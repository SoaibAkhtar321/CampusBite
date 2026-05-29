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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.viewmodel.ProfileViewModel

private val BrandOrange = Color(0xFFFF6B00)

private const val SUPPORT_EMAIL = "support.campusbite@gmail.com"

// Format: country code + number, without + sign.
private const val SUPPORT_WHATSAPP_NUMBER = "918957833269"

private const val WEBSITE_BASE_URL =
    "https://thecampusbite.vercel.app"

private const val PRIVACY_POLICY_URL =
    "$WEBSITE_BASE_URL/privacy-policy"

private const val TERMS_URL =
    "$WEBSITE_BASE_URL/terms-and-conditions"

private const val REFUND_POLICY_URL =
    "$WEBSITE_BASE_URL/refund-cancellation-policy"

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

    val context = LocalContext.current

    var showLogoutDialog by remember { mutableStateOf(false) }
    var isEditingShopSettings by remember { mutableStateOf(false) }

    var upiInput by remember { mutableStateOf("") }
    var openingInput by remember { mutableStateOf("") }
    var closingInput by remember { mutableStateOf("") }
    var maxOrdersInput by remember { mutableStateOf("") }

    val displayName = userProfile?.name?.trim().orEmpty()
    val displayEmail = userProfile?.email?.trim().orEmpty()

    val profileInitial =
        displayName.firstOrNull()?.uppercaseChar()?.toString()
            ?: displayEmail.firstOrNull()?.uppercaseChar()?.toString()
            ?: "U"

    LaunchedEffect(
        currentUpiId,
        openingTime,
        closingTime,
        maxOrdersPerSlot
    ) {
        upiInput = currentUpiId
        openingInput = openingTime
        closingInput = closingTime
        maxOrdersInput = maxOrdersPerSlot.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Shopkeeper Profile",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
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
                            text = displayName.ifBlank { "Shopkeeper" },
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = displayEmail.ifBlank { "CampusBite Partner" },
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            SectionCard(
                title = "Shop Info",
                icon = Icons.Outlined.Store
            ) {
                InfoText(
                    label = "Shop ID",
                    value = userProfile?.shopId
                        ?.ifBlank { "Not assigned" }
                        ?: "Loading..."
                )

                InfoText(
                    label = "Role",
                    value = userProfile?.role ?: "shopkeeper"
                )

                InfoText(
                    label = "Phone",
                    value = userProfile?.phone
                        ?.ifBlank { "Not available" }
                        ?: "Loading..."
                )
            }

            SectionCard(
                title = "Payment & Timings",
                icon = Icons.Outlined.Payments,
                trailingContent = {
                    TextButton(
                        onClick = {
                            isEditingShopSettings = !isEditingShopSettings
                            viewModel.clearMessage()
                        }
                    ) {
                        Text(
                            text = if (isEditingShopSettings) {
                                "Cancel"
                            } else {
                                "Edit"
                            },
                            color = BrandOrange
                        )
                    }
                }
            ) {

                if (!isEditingShopSettings) {
                    InfoText(
                        label = "UPI ID",
                        value = currentUpiId.ifBlank { "Not added" }
                    )

                    InfoText(
                        label = "Opening Time",
                        value = openingTime
                    )

                    InfoText(
                        label = "Closing Time",
                        value = closingTime
                    )

                    InfoText(
                        label = "Max Orders / Slot",
                        value = maxOrdersPerSlot.toString()
                    )
                } else {
                    OutlinedTextField(
                        value = upiInput,
                        onValueChange = {
                            upiInput = it
                            viewModel.clearMessage()
                        },
                        label = {
                            Text("UPI ID")
                        },
                        placeholder = {
                            Text("example@paytm")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = openingInput,
                            onValueChange = {
                                openingInput = it
                                viewModel.clearMessage()
                            },
                            label = {
                                Text("Opening")
                            },
                            placeholder = {
                                Text("08:00")
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = closingInput,
                            onValueChange = {
                                closingInput = it
                                viewModel.clearMessage()
                            },
                            label = {
                                Text("Closing")
                            },
                            placeholder = {
                                Text("21:00")
                            },
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
                        label = {
                            Text("Max Orders Per Slot")
                        },
                        placeholder = {
                            Text("5")
                        },
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandOrange
                        )
                    ) {
                        Text("Save Changes")
                    }
                }

                if (message != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = message.orEmpty(),
                        color = if (
                            message?.contains(
                                other = "success",
                                ignoreCase = true
                            ) == true
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SectionCard(
                title = "Help & Support",
                icon = Icons.Outlined.SupportAgent
            ) {
                Text(
                    text = "Need help with orders, payments, shop setup, or app issues?",
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
                            shopId = userProfile?.shopId.orEmpty(),
                            shopkeeperEmail = displayEmail
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
                            shopId = userProfile?.shopId.orEmpty(),
                            shopkeeperEmail = displayEmail
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

            SectionCard(
                title = "Settings",
                icon = Icons.Outlined.Settings
            ) {
                Button(
                    onClick = {
                        showLogoutDialog = true
                    },
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

                        viewModel.closeShopBeforeLogout {
                            onLogout()
                        }
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
    shopId: String,
    shopkeeperEmail: String
) {
    val message = """
        Hi CampusBite Support,
        I need help with my shop/account.
        
        Shop ID: ${shopId.ifBlank { "Not available" }}
        Shopkeeper Email: ${shopkeeperEmail.ifBlank { "Not available" }}
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
    shopId: String,
    shopkeeperEmail: String
) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")

        putExtra(
            Intent.EXTRA_SUBJECT,
            "CampusBite Shopkeeper Support"
        )

        putExtra(
            Intent.EXTRA_TEXT,
            """
            Hi CampusBite Support,
            
            I need help with:
            
            Shop ID: ${shopId.ifBlank { "Not available" }}
            Shopkeeper Email: ${shopkeeperEmail.ifBlank { "Not available" }}
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
private fun SectionCard(
    title: String,
    icon: ImageVector,
    trailingContent: @Composable (() -> Unit)? = null,
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
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                trailingContent?.invoke()
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