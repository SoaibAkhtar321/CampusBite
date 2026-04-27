package com.campusbite.app.ui.navigation


import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.campusbite.app.ui.screens.auth.LoginScreen
import com.campusbite.app.ui.screens.auth.RegisterScreen
import com.campusbite.app.ui.screens.home.HomeScreen
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
            // HomeScreen will go here
        }
        composable(Routes.SEARCH) {
            // SearchScreen will go here
        }
        composable(Routes.SHOP_DETAIL) {
            // ShopDetailScreen will go here
        }
        composable(Routes.CART) {
            // CartScreen will go here
        }
        composable(Routes.ORDER_STATUS) {
            // OrderStatusScreen will go here
        }
        composable(Routes.ADMIN_DASHBOARD) {
            // AdminDashboardScreen will go here
        }
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}