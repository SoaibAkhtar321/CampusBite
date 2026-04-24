package com.campusbite.app.ui.navigation


import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            // SplashScreen will go here
        }
        composable(Routes.LOGIN) {
            // LoginScreen will go here
        }
        composable(Routes.REGISTER) {
            // RegisterScreen will go here
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
    }
}