package com.campusbite.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.campusbite.app.ui.screens.admin.AdminDashboardScreen
import com.campusbite.app.ui.screens.auth.LoginScreen
import com.campusbite.app.ui.screens.auth.RegisterScreen
import com.campusbite.app.ui.screens.auth.ShopkeeperPendingScreen
import com.campusbite.app.ui.screens.home.HomeScreen
import com.campusbite.app.ui.screens.order.CartScreen
import com.campusbite.app.ui.screens.order.OrderHistoryScreen
import com.campusbite.app.ui.screens.order.OrderStatusScreen
import com.campusbite.app.ui.screens.profile.ShopkeeperProfileScreen
import com.campusbite.app.ui.screens.profile.StudentProfileScreen
import com.campusbite.app.ui.screens.shop.ShopDetailScreen
import com.campusbite.app.ui.screens.shopkeeper.MenuManagementScreen
import com.campusbite.app.ui.screens.shopkeeper.ShopkeeperDashboardScreen
import com.campusbite.app.ui.screens.splash.SplashScreen
import com.campusbite.app.ui.viewmodel.AuthViewModel
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel
import com.campusbite.app.ui.screens.auth.CompleteProfileScreen

@Composable
fun NavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val cartViewModel: CartViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        // ───────────────────────── Splash ─────────────────────────
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToStudent = {
                    navController.navigate(Routes.STUDENT_HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToShopkeeper = {
                    navController.navigate(Routes.SHOPKEEPER_DASHBOARD) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToAdmin = {
                    navController.navigate(Routes.ADMIN_DASHBOARD) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToPending = {   // ✅ NEW
                    navController.navigate(Routes.SHOPKEEPER_PENDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
//                onNavigateToCompleteProfile = {
//                    navController.navigate(Routes.COMPLETE_PROFILE) {
//                        popUpTo(Routes.LOGIN) { inclusive = true }
//                    }
//                }
            )
        }

        // ───────────────────────── Auth ─────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToStudent = {
                    navController.navigate(Routes.STUDENT_HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToShopkeeper = {
                    navController.navigate(Routes.SHOPKEEPER_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToAdmin = {
                    navController.navigate(Routes.ADMIN_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToPending = {
                    navController.navigate(Routes.SHOPKEEPER_PENDING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToCompleteProfile = {
                    navController.navigate(Routes.COMPLETE_PROFILE) {
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
                    navController.navigate(Routes.STUDENT_HOME) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onNavigateToPending = {   // ✅ NEW
                    navController.navigate(Routes.SHOPKEEPER_PENDING) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // ───────────────────────── Shopkeeper Pending ─────────────────────────
        composable(Routes.SHOPKEEPER_PENDING) {
            ShopkeeperPendingScreen(
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ───────────────────────── Student ─────────────────────────
        composable(Routes.STUDENT_HOME) {
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
                onNavigateToOrderStatus = { orderId ->
                    navController.navigate(Routes.orderStatus(orderId))
                },
                cartViewModel = cartViewModel
            )
        }

// ✅ CORRECT VERSION
        composable(Routes.SHOP_DETAIL) { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.STUDENT_HOME)  // ✅ Use correct route constant
            }
            val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)

            ShopDetailScreen(
                shopId = shopId,
                viewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCart = { navController.navigate(Routes.CART) },
                cartViewModel = cartViewModel
            )
        }

        composable(Routes.CART) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.STUDENT_HOME)
            }
            val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)

            CartScreen(
                onNavigateBack = { navController.popBackStack() },
                onOrderPlaced = { orderId ->
                    navController.navigate(Routes.orderStatus(orderId)) {
                        popUpTo(Routes.CART) { inclusive = true }
                    }
                },
                cartViewModel = cartViewModel,
                homeViewModel = homeViewModel  // ✅ Add this
            )
        }

        composable(Routes.ORDER_STATUS) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
            OrderStatusScreen(
                orderId = orderId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ORDER_HISTORY) {
            OrderHistoryScreen()
        }
        composable("menu_management") {
            MenuManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ───────────────────────── Shopkeeper ─────────────────────────
        composable(Routes.SHOPKEEPER_DASHBOARD) {
            ShopkeeperDashboardScreen(
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToMenu = {  // ← ADD THIS PARAMETER
                    navController.navigate("menu_management")
                }
            )
        }

        // ───────────────────────── Admin ─────────────────────────
        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                onNavigateToProfile = {
                    navController.navigate("profile")
                }
            )
        }

        // ───────────────────────── Profile ─────────────────────────
        composable("profile") {
            val userRole by authViewModel.userRole.collectAsState()

            if (userRole == "shopkeeper") {
                ShopkeeperProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            } else {
                StudentProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToOrderStatus = { orderId ->
                        navController.navigate(Routes.orderStatus(orderId))
                    },
                    onNavigateToOrderHistory = {
                        navController.navigate(Routes.ORDER_HISTORY)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("edit_shop") {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Edit Shop Info Screen Coming Soon")
            }
        }
        composable(Routes.COMPLETE_PROFILE) {
            CompleteProfileScreen(
                onNavigateToStudent = {
                    navController.navigate(Routes.STUDENT_HOME) {
                        popUpTo(Routes.COMPLETE_PROFILE) { inclusive = true }
                    }
                },
                onNavigateToPending = {
                    navController.navigate(Routes.SHOPKEEPER_PENDING) {
                        popUpTo(Routes.COMPLETE_PROFILE) { inclusive = true }
                    }
                }
            )
        }
    }
}