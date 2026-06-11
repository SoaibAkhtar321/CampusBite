package com.campusbite.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.campusbite.app.ui.screens.admin.AdminDashboardScreen
import com.campusbite.app.ui.screens.admin.AdminShopReportScreen
import com.campusbite.app.ui.screens.auth.CompleteProfileScreen
import com.campusbite.app.ui.screens.auth.LoginScreen
import com.campusbite.app.ui.screens.auth.ShopkeeperPendingScreen
import com.campusbite.app.ui.screens.home.HomeScreen
import com.campusbite.app.ui.screens.order.CartScreen
import com.campusbite.app.ui.screens.order.OrderHistoryScreen
import com.campusbite.app.ui.screens.order.OrderStatusScreen
import com.campusbite.app.ui.screens.profile.AdminProfileScreen
import com.campusbite.app.ui.screens.profile.ShopkeeperProfileScreen
import com.campusbite.app.ui.screens.profile.StudentProfileScreen
import com.campusbite.app.ui.screens.shop.ShopDetailScreen
import com.campusbite.app.ui.screens.shopkeeper.MenuManagementScreen
import com.campusbite.app.ui.screens.shopkeeper.ShopkeeperAnalyticsScreen
import com.campusbite.app.ui.screens.shopkeeper.ShopkeeperDashboardScreen
import com.campusbite.app.ui.screens.shopkeeper.ShopkeeperOrderHistoryScreen
import com.campusbite.app.ui.screens.splash.SplashScreen
import com.campusbite.app.ui.viewmodel.AuthViewModel
import com.campusbite.app.ui.viewmodel.CartViewModel
import com.campusbite.app.ui.viewmodel.HomeViewModel
import com.campusbite.app.ui.viewmodel.OrderViewModel
import kotlinx.coroutines.delay

@Composable
fun NavGraph(
    navController: NavHostController,
    notificationOrderId: String? = null,
    notificationType: String? = null,
    onNotificationHandled: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val cartViewModel: CartViewModel = hiltViewModel()
    val orderViewModel: OrderViewModel = hiltViewModel()

    LaunchedEffect(notificationOrderId, notificationType) {
        val orderId = notificationOrderId.orEmpty()
        val type = notificationType.orEmpty()

        if (orderId.isBlank() && type.isBlank()) {
            return@LaunchedEffect
        }

        delay(700)

        when {
            type == "new_order" || type == "shopkeeper_order" -> {
                navController.safeNavigate(Routes.SHOPKEEPER_DASHBOARD) {
                    popUpTo(Routes.SPLASH) {
                        inclusive = false
                    }
                }

                onNotificationHandled()
            }

            orderId.isNotBlank() -> {
                navController.safeNavigate(Routes.orderStatus(orderId))
                onNotificationHandled()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onNavigateToStudent = {
                        navController.replaceSplashWith(Routes.STUDENT_HOME)
                    },
                    onNavigateToShopkeeper = {
                        navController.replaceSplashWith(Routes.SHOPKEEPER_DASHBOARD)
                    },
                    onNavigateToAdmin = {
                        navController.replaceSplashWith(Routes.ADMIN_DASHBOARD)
                    },
                    onNavigateToLogin = {
                        navController.replaceSplashWith(Routes.LOGIN)
                    },
                    onNavigateToPending = {
                        navController.replaceSplashWith(Routes.SHOPKEEPER_PENDING)
                    },
                    onNavigateToCompleteProfile = {
                        navController.replaceSplashWith(Routes.COMPLETE_PROFILE)
                    }
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onNavigateToStudent = {
                        navController.replaceLoginWith(Routes.STUDENT_HOME)
                    },
                    onNavigateToShopkeeper = {
                        navController.replaceLoginWith(Routes.SHOPKEEPER_DASHBOARD)
                    },
                    onNavigateToAdmin = {
                        navController.replaceLoginWith(Routes.ADMIN_DASHBOARD)
                    },
                    onNavigateToPending = {
                        navController.replaceLoginWith(Routes.SHOPKEEPER_PENDING)
                    },
                    onNavigateToCompleteProfile = {
                        navController.replaceLoginWith(Routes.COMPLETE_PROFILE)
                    }
                )
            }

            composable(Routes.COMPLETE_PROFILE) {
                CompleteProfileScreen(
                    onNavigateToStudent = {
                        navController.clearStackAndNavigate(Routes.STUDENT_HOME)
                    },
                    onNavigateToPending = {
                        navController.clearStackAndNavigate(Routes.SHOPKEEPER_PENDING)
                    },
                    onNavigateToLogin = {
                        navController.clearStackAndNavigate(Routes.LOGIN)
                    }
                )
            }

            composable(Routes.SHOPKEEPER_PENDING) {
                ShopkeeperPendingScreen(
                    onLogout = {
                        authViewModel.logout()
                        navController.clearStackAndNavigate(Routes.LOGIN)
                    }
                )
            }

            composable(Routes.STUDENT_HOME) {
                LaunchedEffect(Unit) {
                    orderViewModel.listenToActiveOrder()
                }

                HomeScreen(
                    onNavigateToShopDetail = { shopId ->
                        navController.safeNavigate(Routes.shopDetail(shopId))
                    },
                    onNavigateToCart = {
                        navController.safeNavigate(Routes.CART)
                    },
                    onNavigateToProfile = {
                        navController.safeNavigate(Routes.STUDENT_PROFILE)
                    },
                    onNavigateToOrderStatus = { orderId ->
                        navController.safeNavigate(Routes.orderStatus(orderId))
                    },
                    cartViewModel = cartViewModel,
                    orderViewModel = orderViewModel
                )
            }

            composable(
                route = Routes.SHOP_DETAIL,
                arguments = listOf(
                    navArgument("shopId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val shopId = backStackEntry.arguments?.getString("shopId").orEmpty()

                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.STUDENT_HOME)
                }

                val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)

                ShopDetailScreen(
                    shopId = shopId,
                    viewModel = homeViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToCart = {
                        navController.safeNavigate(Routes.CART)
                    },
                    cartViewModel = cartViewModel
                )
            }

            composable(Routes.CART) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.STUDENT_HOME)
                }

                val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)

                CartScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOrderPlaced = { orderId ->
                        navController.safeNavigate(Routes.orderStatus(orderId)) {
                            popUpTo(Routes.CART) {
                                inclusive = true
                            }
                        }
                    },
                    cartViewModel = cartViewModel,
                    homeViewModel = homeViewModel
                )
            }

            composable(
                route = Routes.ORDER_STATUS,
                arguments = listOf(
                    navArgument("orderId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()

                OrderStatusScreen(
                    orderId = orderId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.ORDER_HISTORY) {
                OrderHistoryScreen(
                    onNavigateToOrderStatus = { orderId ->
                        navController.safeNavigate(Routes.orderStatus(orderId))
                    }
                )
            }

            composable(Routes.MENU_MANAGEMENT) {
                MenuManagementScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.SHOPKEEPER_DASHBOARD) {
                ShopkeeperDashboardScreen(
                    onNavigateToProfile = {
                        navController.safeNavigate(Routes.SHOPKEEPER_PROFILE)
                    },
                    onNavigateToMenu = {
                        navController.safeNavigate(Routes.MENU_MANAGEMENT)
                    },
                    onNavigateToAnalytics = {
                        navController.safeNavigate(Routes.SHOPKEEPER_ANALYTICS)
                    }
                )
            }

            composable(
                route = Routes.SHOPKEEPER_ORDER_HISTORY,
                arguments = listOf(
                    navArgument("shopId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val shopId = backStackEntry.arguments?.getString("shopId").orEmpty()

                ShopkeeperOrderHistoryScreen(
                    shopId = shopId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToOrderStatus = { orderId ->
                        navController.safeNavigate(Routes.orderStatus(orderId))
                    }
                )
            }

            composable(Routes.SHOPKEEPER_ANALYTICS) {
                ShopkeeperAnalyticsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.ADMIN_DASHBOARD) {
                AdminDashboardScreen(
                    onNavigateToProfile = {
                        navController.safeNavigate(Routes.ADMIN_PROFILE)
                    },
                    onNavigateToShopReport = { shopId ->
                        navController.safeNavigate(Routes.adminShopReport(shopId))
                    }
                )
            }

            composable(
                route = Routes.ADMIN_SHOP_REPORT,
                arguments = listOf(
                    navArgument("shopId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val shopId = backStackEntry.arguments?.getString("shopId").orEmpty()

                AdminShopReportScreen(
                    shopId = shopId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.STUDENT_PROFILE) {
                StudentProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToOrderStatus = { orderId ->
                        navController.safeNavigate(Routes.orderStatus(orderId))
                    },
                    onNavigateToOrderHistory = {
                        navController.safeNavigate(Routes.ORDER_HISTORY)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.clearStackAndNavigate(Routes.LOGIN)
                    }
                )
            }

            composable(Routes.SHOPKEEPER_PROFILE) {
                ShopkeeperProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToOrderHistory = { shopId ->
                        navController.safeNavigate(Routes.shopkeeperOrderHistory(shopId))
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.clearStackAndNavigate(Routes.LOGIN)
                    }
                )
            }

            composable(Routes.ADMIN_PROFILE) {
                AdminProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.clearStackAndNavigate(Routes.LOGIN)
                    }
                )
            }

            composable(Routes.EDIT_SHOP) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Edit Shop Info Screen Coming Soon")
                }
            }
        }
    }
}

private fun NavHostController.safeNavigate(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}

private fun NavHostController.replaceSplashWith(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(Routes.SPLASH) {
            inclusive = true
        }
    }
}

private fun NavHostController.replaceLoginWith(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(Routes.LOGIN) {
            inclusive = true
        }
    }
}

private fun NavHostController.clearStackAndNavigate(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(0) {
            inclusive = true
        }
    }
}
