package com.campusbite.app.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShopkeeperPendingScreen(
    onLogout: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⏳ Approval Pending", fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Your shopkeeper account is waiting for admin approval.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogout) { Text("Logout") }
        }
    }
}