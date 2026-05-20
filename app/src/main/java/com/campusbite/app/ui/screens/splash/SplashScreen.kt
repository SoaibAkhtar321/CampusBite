package com.campusbite.app.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope

@Composable
fun SplashScreen(
    onNavigateToStudent: () -> Unit,
    onNavigateToShopkeeper: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToPending: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        delay(700)

        if (!viewModel.isLoggedIn) {
            onNavigateToLogin()
            return@LaunchedEffect
        }

        supervisorScope {
            val roleDeferred = async {
                viewModel.getUserRole()
            }

            val approvedDeferred = async {
                viewModel.isShopkeeperApproved()
            }

            val role = roleDeferred.await()

            when (role) {
                "admin" -> {
                    onNavigateToAdmin()
                }

                "shopkeeper" -> {
                    val isApproved = approvedDeferred.await()

                    if (isApproved) {
                        onNavigateToShopkeeper()
                    } else {
                        onNavigateToPending()
                    }
                }

                else -> {
                    onNavigateToStudent()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CampusBite",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "- Bhukh Mitao, Time Bachao -",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                letterSpacing = 1.5.sp
            )
        }
    }
}