package com.campusbite.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue // IMPORTANT: Fixes the 'getValue' delegate error
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.campusbite.app.ui.screens.admin.AdminDashboardScreen
import com.campusbite.app.ui.screens.auth.LoginScreen
import com.campusbite.app.ui.screens.auth.RegisterScreen
import com.campusbite.app.ui.screens.home.HomeScreen
import com.campusbite.app.ui.screens.order.CartScreen
import com.campusbite.app.ui.screens.order.OrderHistoryScreen
import com.campusbite.app.ui.screens.order.OrderStatusScreen
import com.campusbite.app.ui.screens.splash.SplashScreen
import com.campusbite.app.ui.viewmodel.AuthViewModel
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.screens.shop.ShopDetailScreen
import com.campusbite.app.ui.screens.profile.ProfileScreen
import com.campusbite.app.ui.screens.profile.ShopkeeperProfileScreen // IMPORTANT: Fixes Unresolved reference

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

        composable("profile") {
            // "by" delegation now works because of the "import androidx.compose.runtime.getValue"
            val userRole by authViewModel.userRole.collectAsState()

            if (userRole == "shopkeeper") {
                ShopkeeperProfileScreen(
                    onNavigateToEditShop = {
                        navController.navigate("edit_shop")
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0)
                        }
                    }
                )
            } else {
                ProfileScreen(
                    onNavigateToOrderHistory = {
                        navController.navigate(Routes.ORDER_HISTORY)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
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
                onNavigateToShopDetail = { shopId ->
                    navController.navigate(Routes.shopDetail(shopId))
                },
                onNavigateToCart = {
                    navController.navigate(Routes.CART)
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                cartViewModel = cartViewModel
            )
        }

        composable(Routes.SHOP_DETAIL) { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            ShopDetailScreen(
                shopId = shopId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCart = { navController.navigate(Routes.CART) },
                cartViewModel = cartViewModel
            )
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

        composable(Routes.ORDER_HISTORY) {
            OrderHistoryScreen()
        }
        composable("edit_shop") {
            // For now, just a placeholder so it doesn't crash
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Edit Shop Info Screen Coming Soon")
            }
        }

        // Inside NavGraph.kt
        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                onNavigateToProfile = {
                    navController.navigate("profile")
                }
                // onLogout is no longer needed here
            )
        }
    }
}