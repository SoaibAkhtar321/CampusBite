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
import com.campusbite.app.ui.viewmodel.StartupDestination
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToStudent: () -> Unit,
    onNavigateToShopkeeper: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToCompleteProfile: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        delay(700)

        try {
            if (!viewModel.isLoggedIn) {
                onNavigateToLogin()
                return@LaunchedEffect
            }

            when (viewModel.resolveStartupDestination()) {
                StartupDestination.CompleteProfile -> {
                    onNavigateToCompleteProfile()
                }

                StartupDestination.Admin -> {
                    onNavigateToAdmin()
                }

                StartupDestination.ShopkeeperApproved -> {
                    onNavigateToShopkeeper()
                }

                StartupDestination.ShopkeeperPending -> {
                    onNavigateToPending()
                }

                StartupDestination.Student -> {
                    onNavigateToStudent()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onNavigateToLogin()
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