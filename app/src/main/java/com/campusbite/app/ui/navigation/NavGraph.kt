package com.campusbite.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.campusbite.app.ui.screens.auth.LoginScreen
import com.campusbite.app.ui.screens.auth.RegisterScreen
import com.campusbite.app.ui.screens.home.HomeScreen
import com.campusbite.app.ui.screens.order.CartScreen
import com.campusbite.app.ui.screens.order.OrderStatusScreen
import com.campusbite.app.ui.screens.splash.SplashScreen

@Composable
fun NavGraph(navController: NavHostController) {
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
                }
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
                }
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
            // AdminDashboardScreen will go here
        }
    }
}