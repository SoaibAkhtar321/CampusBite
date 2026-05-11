package com.campusbite.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ChevronRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.OrangeLight
import com.campusbite.app.ui.viewmodel.ShopkeeperProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopkeeperProfileScreen(
    onNavigateToEditShop: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ShopkeeperProfileViewModel = hiltViewModel()
) {
    val shop by viewModel.shopData.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Shop Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Shop Image Placeholder
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(OrangeLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(50.dp), tint = Orange)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = shop?.name ?: "Loading Shop...", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text = "Owner Account", color = Color.Gray, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // QUICK ACTION: OPEN/CLOSE SHOP
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (shop?.isOpen == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (shop?.isOpen == true) "Shop is OPEN" else "Shop is CLOSED",
                            fontWeight = FontWeight.Bold,
                            color = if (shop?.isOpen == true) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text("Instant toggle for customers", fontSize = 12.sp)
                    }
                    Switch(
                        checked = shop?.isOpen ?: false,
                        onCheckedChange = { viewModel.toggleShopStatus(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2E7D32))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // OPTIONS
            ProfileOptionItem("Edit Shop Info", Icons.Default.Edit) { onNavigateToEditShop() }
            ProfileOptionItem("Order History", Icons.Default.History) { /* Navigate to Shop Orders */ }
            ProfileOptionItem("Business Insights", Icons.Default.Assessment) { /* Navigate to Sales */ }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Logout Manager", fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
private fun ProfileOptionItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Orange)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            // Using a standard Arrow icon to avoid ChevronRight indexing errors
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}