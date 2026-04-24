package com.campusbite.app.ui.navigation


object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SEARCH = "search"
    const val SHOP_DETAIL = "shop_detail/{shopId}"
    const val CART = "cart"
    const val ORDER_CONFIRMATION = "order_confirmation"
    const val ORDER_STATUS = "order_status"
    const val ADMIN_DASHBOARD = "admin_dashboard"

    fun shopDetail(shopId: String) = "shop_detail/$shopId"
}