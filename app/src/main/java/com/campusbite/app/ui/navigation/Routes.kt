package com.campusbite.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val COMPLETE_PROFILE = "complete_profile"

    const val STUDENT_HOME = "student_home"
    const val SHOP_DETAIL = "shop_detail/{shopId}"
    const val CART = "cart"
    const val ORDER_STATUS = "order_status/{orderId}"
    const val ORDER_HISTORY = "order_history"

    const val SHOPKEEPER_DASHBOARD = "shopkeeper_dashboard"
    const val SHOPKEEPER_PENDING = "shopkeeper_pending"
    const val SHOPKEEPER_PROFILE = "shopkeeper_profile"
    const val SHOPKEEPER_ORDER_HISTORY = "shopkeeper_order_history/{shopId}"
    const val MENU_MANAGEMENT = "menu_management"

    const val SHOPKEEPER_ANALYTICS = "shopkeeper_analytics"

    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_PROFILE = "admin_profile"
    const val ADMIN_SHOP_REPORT = "admin_shop_report/{shopId}"

    const val STUDENT_PROFILE = "student_profile"

    // Intentional future scaffolding for an upcoming Edit Shop feature.
    // Not yet wired to any navigation action (Finding 50).
    const val EDIT_SHOP = "edit_shop"

    fun shopDetail(shopId: String): String {
        return "shop_detail/$shopId"
    }

    fun orderStatus(orderId: String): String {
        return "order_status/$orderId"
    }

    fun adminShopReport(shopId: String): String {
        return "admin_shop_report/$shopId"
    }

    fun shopkeeperOrderHistory(shopId: String): String {
        return "shopkeeper_order_history/$shopId"
    }
}