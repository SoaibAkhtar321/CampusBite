object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val SHOP_DETAIL = "shop_detail/{shopId}"
    const val CART = "cart"
    const val ORDER_STATUS = "order_status/{orderId}"
    const val ORDER_HISTORY = "order_history"

    fun shopDetail(shopId: String) = "shop_detail/$shopId"
    fun orderStatus(orderId: String) = "order_status/$orderId"

    const val STUDENT_HOME = "student_home"
    const val SHOPKEEPER_DASHBOARD = "shopkeeper_dashboard"
    const val SHOPKEEPER_PENDING = "shopkeeper_pending"   // ✅ NEW
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val COMPLETE_PROFILE = "complete_profile"
}