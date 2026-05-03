package com.campusbite.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.campusbite.app.ui.screens.admin.AdminDashboardScreen
import com.campusbite.app.ui.screens.auth.LoginScreen
import com.campusbite.app.ui.screens.auth.RegisterScreen
import com.campusbite.app.ui.screens.home.HomeScreen
import com.campusbite.app.ui.screens.order.CartScreen
import com.campusbite.app.ui.screens.order.OrderStatusScreen
import com.campusbite.app.ui.screens.splash.SplashScreen
import com.campusbite.app.ui.viewmodel.AuthViewModel
import com.campusbite.app.ui.viewmodel.CartViewModel

@Composable
fun NavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val cartViewModel: CartViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToStaff = {
                    navController.navigate(Routes.ADMIN_DASHBOARD) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onNavigateToStaff = {
                    navController.navigate(Routes.ADMIN_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToCart = {
                    navController.navigate(Routes.CART)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                cartViewModel = cartViewModel
            )
        }
        composable(Routes.SEARCH) {
            // SearchScreen will go here
        }
        composable(Routes.SHOP_DETAIL) {
            // ShopDetailScreen will go here
        }
        composable(Routes.CART) {
            CartScreen(
                onNavigateBack = { navController.popBackStack() },
                onOrderPlaced = { orderId ->
                    navController.navigate(Routes.orderStatus(orderId)) {
                        popUpTo(Routes.CART) { inclusive = true }
                    }
                },
                cartViewModel = cartViewModel
            )
        }
        composable(Routes.ORDER_STATUS) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
            OrderStatusScreen(
                orderId = orderId,
                onNavigateHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}