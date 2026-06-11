package com.campusbite.app.ui.viewmodel

data class ShopkeeperAnalyticsState(
    val isLoading: Boolean = false,
    val error: String? = null,


    val todaySales: Double = 0.0,
    val todayOrders: Int = 0,
    val todayVerified: Int = 0,
    val todayCancelled: Int = 0,
    val todayPendingVerification: Int = 0,

    val monthSales: Double = 0.0,
    val monthOrders: Int = 0,
    val monthVerified: Int = 0,
    val monthCancelled: Int = 0,
    val monthPendingVerification: Int = 0,

    val lifetimeSales: Double = 0.0,
    val lifetimeOrders: Int = 0,
    val lifetimeVerified: Int = 0,
    val lifetimeCancelled: Int = 0,
    val lifetimePendingVerification: Int = 0


)
