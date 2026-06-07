package com.campusbite.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.Order
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class AdminShop(
    val docId: String = "",
    val shopId: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val isOpen: Boolean = false,
    val isApproved: Boolean = false,
    val isBlocked: Boolean = false,
    val isDeleted: Boolean = false,
    val displayOrder: Int = 1000
)

data class AdminUser(
    val docId: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "student",
    val shopId: String = "",
    val isApproved: Boolean = true,
    val isBlocked: Boolean = false,
    val createdAt: Long = 0L
)

data class AdminShopReportSummary(
    val todayOrders: Int = 0,
    val todaySales: Double = 0.0,
    val monthOrders: Int = 0,
    val monthSales: Double = 0.0,
    val lifetimeOrders: Int = 0,
    val lifetimeSales: Double = 0.0,
    val pendingPaymentOrders: Int = 0,
    val cancelledOrders: Int = 0
)

data class AdminShopReportState(
    val isLoading: Boolean = false,
    val shop: AdminShop? = null,
    val summary: AdminShopReportSummary = AdminShopReportSummary(),
    val recentOrders: List<Order> = emptyList(),
    val error: String = ""
)

private data class AdminAnalyticsSnapshot(
    val verifiedOrders: Int = 0,
    val verifiedSales: Double = 0.0,
    val cancelledOrders: Int = 0
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _shops = MutableStateFlow<List<AdminShop>>(emptyList())
    val shops: StateFlow<List<AdminShop>> = _shops.asStateFlow()

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users.asStateFlow()

    private val _pendingShopkeepers = MutableStateFlow<List<AdminUser>>(emptyList())
    val pendingShopkeepers: StateFlow<List<AdminUser>> =
        _pendingShopkeepers.asStateFlow()

    private val _shopReportState = MutableStateFlow(AdminShopReportState())
    val shopReportState: StateFlow<AdminShopReportState> =
        _shopReportState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var shopsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null

    private var shopReportRecentOrdersListener: ListenerRegistration? = null
    private var shopReportActiveOrdersListener: ListenerRegistration? = null
    private var shopReportTodayAnalyticsListener: ListenerRegistration? = null
    private var shopReportMonthAnalyticsListener: ListenerRegistration? = null
    private var shopReportLifetimeAnalyticsListener: ListenerRegistration? = null

    private var currentReportShop: AdminShop? = null
    private var reportRecentOrders: List<Order> = emptyList()
    private var reportActiveOrders: List<Order> = emptyList()
    private var reportTodayAnalytics = AdminAnalyticsSnapshot()
    private var reportMonthAnalytics = AdminAnalyticsSnapshot()
    private var reportLifetimeAnalytics = AdminAnalyticsSnapshot()

    init {
        listenToShops()
        listenToUsers()
    }

    private fun listenToShops() {
        shopsListener?.remove()

        shopsListener = firestore.collection("shops")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AdminViewModel", "Failed to listen to shops", error)
                    _message.value = error.message ?: "Failed to load shops"
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val shopList = snapshot?.documents
                    ?.map { doc ->
                        AdminShop(
                            docId = doc.id,
                            shopId = doc.getString("shopId") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            ownerUid = doc.getString("ownerUid")
                                ?: doc.getString("ownerId")
                                ?: "",
                            isOpen = doc.getBoolean("isOpen") ?: false,
                            isApproved = doc.getBoolean("isApproved") ?: false,
                            isBlocked = doc.getBoolean("isBlocked") ?: false,
                            isDeleted = doc.getBoolean("isDeleted") ?: false,
                            displayOrder = doc.getLong("displayOrder")?.toInt() ?: 1000
                        )
                    }
                    ?.sortedWith(
                        compareBy<AdminShop> { it.displayOrder }
                            .thenBy { it.name.lowercase() }
                    )
                    ?: emptyList()

                _shops.value = shopList
                _isLoading.value = false
            }
    }

    private fun listenToUsers() {
        usersListener?.remove()

        usersListener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AdminViewModel", "Failed to listen to users", error)
                    _message.value = error.message ?: "Failed to load users"
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val userList = snapshot?.documents
                    ?.map { doc ->
                        val role = doc.getString("role") ?: "student"

                        val isApproved = doc.getBoolean("isApproved")
                            ?: (role != "shopkeeper")

                        AdminUser(
                            docId = doc.id,
                            uid = doc.getString("uid") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            email = doc.getString("email") ?: "",
                            phone = doc.getString("phone")
                                ?: doc.getString("phoneNumber")
                                ?: "",
                            role = role,
                            shopId = doc.getString("shopId") ?: "",
                            isApproved = isApproved,
                            isBlocked = doc.getBoolean("isBlocked") ?: false,
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    }
                    ?: emptyList()

                _users.value = userList.sortedWith(
                    compareBy<AdminUser> { it.role }
                        .thenBy { it.name.lowercase() }
                )

                _pendingShopkeepers.value = userList
                    .filter { user ->
                        user.role == "shopkeeper" &&
                                !user.isApproved &&
                                !user.isBlocked
                    }
                    .sortedByDescending { it.createdAt }

                _isLoading.value = false
            }
    }

    fun loadShopReport(shopId: String) {
        clearShopReportListeners()

        if (shopId.isBlank()) {
            _shopReportState.value = AdminShopReportState(
                error = "Shop ID is missing."
            )
            return
        }

        _shopReportState.value = AdminShopReportState(isLoading = true)

        currentReportShop = null
        reportRecentOrders = emptyList()
        reportActiveOrders = emptyList()
        reportTodayAnalytics = AdminAnalyticsSnapshot()
        reportMonthAnalytics = AdminAnalyticsSnapshot()
        reportLifetimeAnalytics = AdminAnalyticsSnapshot()

        viewModelScope.launch {
            try {
                val directDoc = firestore.collection("shops")
                    .document(shopId)
                    .get()
                    .await()

                val shopDoc = if (directDoc.exists()) {
                    directDoc
                } else {
                    firestore.collection("shops")
                        .whereEqualTo("shopId", shopId)
                        .limit(1)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()
                }

                if (shopDoc == null || !shopDoc.exists()) {
                    _shopReportState.value = AdminShopReportState(
                        error = "Shop not found."
                    )
                    return@launch
                }

                currentReportShop = AdminShop(
                    docId = shopDoc.id,
                    shopId = shopDoc.getString("shopId") ?: shopDoc.id,
                    name = shopDoc.getString("name") ?: "",
                    ownerUid = shopDoc.getString("ownerUid")
                        ?: shopDoc.getString("ownerId")
                        ?: "",
                    isOpen = shopDoc.getBoolean("isOpen") ?: false,
                    isApproved = shopDoc.getBoolean("isApproved") ?: false,
                    isBlocked = shopDoc.getBoolean("isBlocked") ?: false,
                    isDeleted = shopDoc.getBoolean("isDeleted") ?: false,
                    displayOrder = shopDoc.getLong("displayOrder")?.toInt() ?: 1000
                )

                startShopReportListeners(
                    shop = currentReportShop!!
                )

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to start shop report", e)

                _shopReportState.value = AdminShopReportState(
                    error = e.message ?: "Failed to load shop report."
                )
            }
        }
    }

    private fun startShopReportListeners(
        shop: AdminShop
    ) {
        val today = LocalDate.now().toString()
        val currentMonth = YearMonth.now().toString()

        shopReportTodayAnalyticsListener = dailyAnalyticsRef(
            shopId = shop.shopId,
            date = today
        ).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AdminViewModel", "Failed to listen today analytics", error)
                return@addSnapshotListener
            }

            reportTodayAnalytics = snapshot.toAdminAnalyticsSnapshot()
            updateShopReportState()
        }

        shopReportMonthAnalyticsListener = monthlyAnalyticsRef(
            shopId = shop.shopId,
            month = currentMonth
        ).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AdminViewModel", "Failed to listen month analytics", error)
                return@addSnapshotListener
            }

            reportMonthAnalytics = snapshot.toAdminAnalyticsSnapshot()
            updateShopReportState()
        }

        shopReportLifetimeAnalyticsListener = lifetimeAnalyticsRef(
            shopId = shop.shopId
        ).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AdminViewModel", "Failed to listen lifetime analytics", error)
                return@addSnapshotListener
            }

            reportLifetimeAnalytics = snapshot.toAdminAnalyticsSnapshot()
            updateShopReportState()
        }

        shopReportActiveOrdersListener = firestore.collection("orders")
            .whereEqualTo("shopId", shop.shopId)
            .whereIn("status", listOf("pending", "preparing", "ready"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AdminViewModel", "Failed to listen active orders", error)

                    _shopReportState.value = _shopReportState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load active orders."
                    )
                    return@addSnapshotListener
                }

                reportActiveOrders = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e("AdminViewModel", "Failed to parse active order", e)
                            null
                        }
                    }
                    ?: emptyList()

                updateShopReportState()
            }

        shopReportRecentOrdersListener = firestore.collection("orders")
            .whereEqualTo("shopId", shop.shopId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AdminViewModel", "Failed to listen recent orders", error)

                    _shopReportState.value = _shopReportState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load recent orders."
                    )
                    return@addSnapshotListener
                }

                reportRecentOrders = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e("AdminViewModel", "Failed to parse recent order", e)
                            null
                        }
                    }
                    ?: emptyList()

                updateShopReportState()
            }
    }

    private fun updateShopReportState() {
        val shop = currentReportShop ?: return
        val today = LocalDate.now().toString()
        val currentMonth = YearMonth.now().toString()

        val activePendingPaymentOrders = reportActiveOrders.filter { order ->
            order.paymentStatus.lowercase() == "pending_verification"
        }

        val todayPendingOrders = activePendingPaymentOrders.count { order ->
            order.pickupDate == today
        }

        val monthPendingOrders = activePendingPaymentOrders.count { order ->
            order.pickupDate.startsWith(currentMonth)
        }

        val summary = AdminShopReportSummary(
            todayOrders = reportTodayAnalytics.verifiedOrders + todayPendingOrders,
            todaySales = reportTodayAnalytics.verifiedSales,

            monthOrders = reportMonthAnalytics.verifiedOrders + monthPendingOrders,
            monthSales = reportMonthAnalytics.verifiedSales,

            lifetimeOrders = reportLifetimeAnalytics.verifiedOrders,
            lifetimeSales = reportLifetimeAnalytics.verifiedSales,

            pendingPaymentOrders = activePendingPaymentOrders.size,
            cancelledOrders = reportLifetimeAnalytics.cancelledOrders
        )

        _shopReportState.value = AdminShopReportState(
            isLoading = false,
            shop = shop,
            summary = summary,
            recentOrders = reportRecentOrders
        )
    }

    private fun dailyAnalyticsRef(
        shopId: String,
        date: String
    ): DocumentReference {
        return firestore.collection("shopAnalytics")
            .document(shopId)
            .collection("daily")
            .document(date)
    }

    private fun monthlyAnalyticsRef(
        shopId: String,
        month: String
    ): DocumentReference {
        return firestore.collection("shopAnalytics")
            .document(shopId)
            .collection("monthly")
            .document(month)
    }

    private fun lifetimeAnalyticsRef(
        shopId: String
    ): DocumentReference {
        return firestore.collection("shopAnalytics")
            .document(shopId)
            .collection("lifetime")
            .document("summary")
    }

    private fun DocumentSnapshot?.toAdminAnalyticsSnapshot(): AdminAnalyticsSnapshot {
        if (this == null || !exists()) {
            return AdminAnalyticsSnapshot()
        }

        return AdminAnalyticsSnapshot(
            verifiedOrders = getLong("verifiedOrders")?.toInt() ?: 0,
            verifiedSales = getDouble("verifiedSales")
                ?: getLong("verifiedSales")?.toDouble()
                ?: 0.0,
            cancelledOrders = getLong("cancelledOrders")?.toInt() ?: 0
        )
    }

    private fun clearShopReportListeners() {
        shopReportRecentOrdersListener?.remove()
        shopReportRecentOrdersListener = null

        shopReportActiveOrdersListener?.remove()
        shopReportActiveOrdersListener = null

        shopReportTodayAnalyticsListener?.remove()
        shopReportTodayAnalyticsListener = null

        shopReportMonthAnalyticsListener?.remove()
        shopReportMonthAnalyticsListener = null

        shopReportLifetimeAnalyticsListener?.remove()
        shopReportLifetimeAnalyticsListener = null
    }

    fun setShopkeeperApproved(
        userDocId: String,
        approved: Boolean
    ) {
        viewModelScope.launch {
            try {
                if (userDocId.isBlank()) {
                    _message.value = "User ID missing"
                    return@launch
                }

                val userRef = firestore.collection("users")
                    .document(userDocId)

                val userSnapshot = userRef.get().await()

                if (!userSnapshot.exists()) {
                    _message.value = "User not found"
                    return@launch
                }

                val uid = userSnapshot.getString("uid") ?: userDocId
                val name = userSnapshot.getString("name") ?: "Unnamed Shop"
                val email = userSnapshot.getString("email") ?: ""

                val phone = userSnapshot.getString("phone")
                    ?: userSnapshot.getString("phoneNumber")
                    ?: ""

                val role = userSnapshot.getString("role") ?: "student"
                val existingShopId = userSnapshot.getString("shopId") ?: ""

                if (role != "shopkeeper") {
                    _message.value = "Only shopkeepers can be approved as shops"
                    return@launch
                }

                if (!approved) {
                    userRef.update(
                        mapOf(
                            "isApproved" to false
                        )
                    ).await()

                    _message.value = "Shopkeeper moved to pending"
                    return@launch
                }

                val finalShopId = if (existingShopId.isNotBlank()) {
                    existingShopId
                } else {
                    generateShopId(name, uid)
                }

                val shopRef = firestore.collection("shops")
                    .document(finalShopId)

                val shopSnapshot = shopRef.get().await()

                if (!shopSnapshot.exists()) {
                    val shopData = mapOf(
                        "shopId" to finalShopId,
                        "name" to name,
                        "description" to "",

                        "ownerUid" to uid,
                        "ownerEmail" to email,
                        "ownerPhone" to phone,

                        "phone" to phone,

                        "isOpen" to false,
                        "isApproved" to true,
                        "isBlocked" to false,
                        "isDeleted" to false,

                        "upiId" to "",

                        "openingTime" to "08:00",
                        "closingTime" to "20:00",

                        "maxOrdersPerSlot" to 5,
                        "closedSlots" to emptyList<String>(),

                        "displayOrder" to getNextShopDisplayOrder(),

                        "createdAt" to System.currentTimeMillis()
                    )

                    shopRef.set(shopData).await()
                } else {
                    val existingDisplayOrder = shopSnapshot.getLong("displayOrder")?.toInt()
                        ?: getNextShopDisplayOrder()

                    shopRef.update(
                        mapOf(
                            "isApproved" to true,
                            "isBlocked" to false,
                            "isDeleted" to false,
                            "ownerUid" to uid,
                            "ownerEmail" to email,
                            "ownerPhone" to phone,
                            "phone" to phone,
                            "displayOrder" to existingDisplayOrder
                        )
                    ).await()
                }

                userRef.update(
                    mapOf(
                        "isApproved" to true,
                        "isBlocked" to false,
                        "shopId" to finalShopId
                    )
                ).await()

                _message.value = "Shopkeeper approved and shop created"

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to approve shopkeeper", e)
                _message.value = e.message ?: "Failed to approve shopkeeper"
            }
        }
    }

    fun setShopApproved(
        shopDocId: String,
        approved: Boolean,
        shopId: String
    ) {
        viewModelScope.launch {
            try {
                if (shopDocId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                firestore.collection("shops")
                    .document(shopDocId)
                    .update("isApproved", approved)
                    .await()

                if (shopId.isNotBlank()) {
                    val usersSnapshot = firestore.collection("users")
                        .whereEqualTo("shopId", shopId)
                        .get()
                        .await()

                    usersSnapshot.documents.forEach { userDoc ->
                        userDoc.reference
                            .update("isApproved", approved)
                            .await()
                    }
                }

                _message.value = if (approved) {
                    "Shop approved successfully"
                } else {
                    "Shop approval removed"
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update shop approval", e)
                _message.value = e.message ?: "Failed to update shop approval"
            }
        }
    }

    fun setShopOpen(
        shopDocId: String,
        open: Boolean
    ) {
        viewModelScope.launch {
            try {
                if (shopDocId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                firestore.collection("shops")
                    .document(shopDocId)
                    .update("isOpen", open)
                    .await()

                _message.value = if (open) {
                    "Shop marked as open"
                } else {
                    "Shop marked as closed"
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update shop open status", e)
                _message.value = e.message ?: "Failed to update shop status"
            }
        }
    }

    fun setShopBlocked(
        shopDocId: String,
        shopId: String,
        blocked: Boolean
    ) {
        viewModelScope.launch {
            try {
                val finalShopDocId = shopDocId.ifBlank { shopId }

                if (finalShopDocId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                firestore.collection("shops")
                    .document(finalShopDocId)
                    .update(
                        mapOf(
                            "isBlocked" to blocked,
                            "isOpen" to false,
                            "isApproved" to !blocked
                        )
                    )
                    .await()

                val finalShopId = shopId.ifBlank { finalShopDocId }

                val usersSnapshot = firestore.collection("users")
                    .whereEqualTo("shopId", finalShopId)
                    .get()
                    .await()

                usersSnapshot.documents.forEach { userDoc ->
                    userDoc.reference.update(
                        mapOf(
                            "isApproved" to !blocked,
                            "isBlocked" to blocked
                        )
                    ).await()
                }

                _message.value = if (blocked) {
                    "Shop blocked successfully"
                } else {
                    "Shop unblocked successfully"
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to block/unblock shop", e)
                _message.value = e.message ?: "Failed to update shop"
            }
        }
    }

    fun deleteShopCompletely(
        shopDocId: String,
        shopId: String
    ) {
        viewModelScope.launch {
            try {
                val finalShopId = shopId.ifBlank { shopDocId }

                if (finalShopId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                val menuSnapshot = firestore.collection("menuItems")
                    .whereEqualTo("shopId", finalShopId)
                    .get()
                    .await()

                menuSnapshot.documents.forEach { menuDoc ->
                    menuDoc.reference.delete().await()
                }

                val usersSnapshot = firestore.collection("users")
                    .whereEqualTo("shopId", finalShopId)
                    .get()
                    .await()

                usersSnapshot.documents.forEach { userDoc ->
                    userDoc.reference.update(
                        mapOf(
                            "shopId" to "",
                            "role" to "student",
                            "isApproved" to true,
                            "isBlocked" to false
                        )
                    ).await()
                }

                firestore.collection("shops")
                    .document(shopDocId)
                    .delete()
                    .await()

                _message.value = "Shop deleted completely"

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to delete shop", e)
                _message.value = e.message ?: "Failed to delete shop"
            }
        }
    }

    fun removePendingShopkeeper(
        userDocId: String
    ) {
        viewModelScope.launch {
            try {
                if (userDocId.isBlank()) {
                    _message.value = "User ID missing"
                    return@launch
                }

                firestore.collection("users")
                    .document(userDocId)
                    .update(
                        mapOf(
                            "role" to "student",
                            "isApproved" to true,
                            "isBlocked" to false,
                            "shopId" to ""
                        )
                    )
                    .await()

                _message.value = "Pending request removed"

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to remove pending request", e)
                _message.value = e.message ?: "Failed to remove request"
            }
        }
    }

    fun setUserBlocked(
        userDocId: String,
        blocked: Boolean
    ) {
        viewModelScope.launch {
            try {
                if (userDocId.isBlank()) {
                    _message.value = "User ID missing"
                    return@launch
                }

                firestore.collection("users")
                    .document(userDocId)
                    .update("isBlocked", blocked)
                    .await()

                _message.value = if (blocked) {
                    "User blocked successfully"
                } else {
                    "User unblocked successfully"
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update user block status", e)
                _message.value = e.message ?: "Failed to update user"
            }
        }
    }

    fun setUserRole(
        userDocId: String,
        role: String
    ) {
        viewModelScope.launch {
            try {
                if (userDocId.isBlank()) {
                    _message.value = "User ID missing"
                    return@launch
                }

                val cleanRole = role.trim().lowercase()

                val updates = mutableMapOf<String, Any>(
                    "role" to cleanRole
                )

                when (cleanRole) {
                    "shopkeeper" -> {
                        updates["isApproved"] = false
                        updates["isBlocked"] = false
                        updates["shopId"] = ""
                    }

                    "student" -> {
                        updates["isApproved"] = true
                        updates["isBlocked"] = false
                        updates["shopId"] = ""
                    }

                    "admin" -> {
                        updates["isApproved"] = true
                        updates["isBlocked"] = false
                        updates["shopId"] = ""
                    }
                }

                firestore.collection("users")
                    .document(userDocId)
                    .update(updates)
                    .await()

                _message.value = "User role updated to $cleanRole"

            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to update user role", e)
                _message.value = e.message ?: "Failed to update role"
            }
        }
    }

    fun moveShopToTop(shop: AdminShop) {
        viewModelScope.launch {
            try {
                val orderedShops = getOrderedActiveShops().toMutableList()

                val currentIndex = orderedShops.indexOfFirst {
                    it.docId == shop.docId
                }

                if (currentIndex <= 0) {
                    _message.value = "Shop is already at top"
                    return@launch
                }

                val selectedShop = orderedShops.removeAt(currentIndex)
                orderedShops.add(0, selectedShop)

                updateShopDisplayOrders(orderedShops)

                _message.value = "Shop moved to top"
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to move shop to top", e)
                _message.value = e.message ?: "Failed to update shop position"
            }
        }
    }

    fun moveShopUp(shop: AdminShop) {
        viewModelScope.launch {
            try {
                val orderedShops = getOrderedActiveShops().toMutableList()

                val currentIndex = orderedShops.indexOfFirst {
                    it.docId == shop.docId
                }

                if (currentIndex <= 0) {
                    _message.value = "Shop is already at top"
                    return@launch
                }

                val temp = orderedShops[currentIndex - 1]
                orderedShops[currentIndex - 1] = orderedShops[currentIndex]
                orderedShops[currentIndex] = temp

                updateShopDisplayOrders(orderedShops)

                _message.value = "Shop moved up"
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to move shop up", e)
                _message.value = e.message ?: "Failed to update shop position"
            }
        }
    }

    fun moveShopDown(shop: AdminShop) {
        viewModelScope.launch {
            try {
                val orderedShops = getOrderedActiveShops().toMutableList()

                val currentIndex = orderedShops.indexOfFirst {
                    it.docId == shop.docId
                }

                if (currentIndex == -1 || currentIndex >= orderedShops.lastIndex) {
                    _message.value = "Shop is already at bottom"
                    return@launch
                }

                val temp = orderedShops[currentIndex + 1]
                orderedShops[currentIndex + 1] = orderedShops[currentIndex]
                orderedShops[currentIndex] = temp

                updateShopDisplayOrders(orderedShops)

                _message.value = "Shop moved down"
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to move shop down", e)
                _message.value = e.message ?: "Failed to update shop position"
            }
        }
    }

    private fun getOrderedActiveShops(): List<AdminShop> {
        return _shops.value
            .filter { !it.isDeleted }
            .sortedWith(
                compareBy<AdminShop> { it.displayOrder }
                    .thenBy { it.name.lowercase() }
            )
    }

    private suspend fun updateShopDisplayOrders(
        orderedShops: List<AdminShop>
    ) {
        val batch = firestore.batch()

        orderedShops.forEachIndexed { index, shop ->
            val newDisplayOrder = (index + 1) * 10

            if (shop.docId.isNotBlank()) {
                val shopRef = firestore.collection("shops")
                    .document(shop.docId)

                batch.update(
                    shopRef,
                    "displayOrder",
                    newDisplayOrder
                )
            }
        }

        batch.commit().await()
    }

    private suspend fun getNextShopDisplayOrder(): Int {
        val snapshot = firestore.collection("shops")
            .get()
            .await()

        val maxOrder = snapshot.documents
            .mapNotNull { doc ->
                doc.getLong("displayOrder")?.toInt()
            }
            .maxOrNull()
            ?: 0

        return maxOrder + 10
    }

    fun cancelOrderByAdmin(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String
    ) {
        if (orderId.isBlank()) {
            _message.value = "Order ID missing"
            return
        }

        viewModelScope.launch {
            try {
                val cleanPaymentType = paymentReceivedType
                    .trim()
                    .lowercase()

                val cleanReason = cancelReason.trim()

                val validPaymentTypes = listOf(
                    PaymentReceivedType.NONE,
                    PaymentReceivedType.PARTIAL,
                    PaymentReceivedType.FULL
                )

                if (cleanPaymentType !in validPaymentTypes) {
                    throw IllegalStateException("Please select payment status.")
                }

                val paymentReceivedByShopkeeper = cleanPaymentType in listOf(
                    PaymentReceivedType.PARTIAL,
                    PaymentReceivedType.FULL
                )

                if (paymentReceivedByShopkeeper && cleanReason.isBlank()) {
                    throw IllegalStateException("Please select a cancellation reason.")
                }

                val orderRef = firestore.collection("orders")
                    .document(orderId)

                firestore.runTransaction { transaction ->
                    val orderDoc = transaction.get(orderRef)

                    if (!orderDoc.exists()) {
                        throw IllegalStateException("Order not found.")
                    }

                    val currentStatus = orderDoc.getString("status")
                        ?.lowercase()
                        .orEmpty()

                    if (currentStatus == OrderStatusValue.CANCELLED) {
                        throw IllegalStateException("This order is already cancelled.")
                    }

                    if (currentStatus == OrderStatusValue.PICKED_UP) {
                        throw IllegalStateException("Picked up order cannot be cancelled.")
                    }

                    val paymentStatus = when (cleanPaymentType) {
                        PaymentReceivedType.FULL -> PaymentStatusValue.PAID
                        PaymentReceivedType.PARTIAL -> PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED
                        else -> PaymentStatusValue.PAYMENT_NOT_RECEIVED
                    }

                    val refundStatus = if (paymentReceivedByShopkeeper) {
                        RefundStatusValue.REFUND_PENDING
                    } else {
                        RefundStatusValue.NONE
                    }

                    val finalCancelReason = if (paymentReceivedByShopkeeper) {
                        cleanReason
                    } else {
                        "Payment not received"
                    }

                    val totalPrice = orderDoc.getDouble("totalPrice")
                        ?: orderDoc.getLong("totalPrice")?.toDouble()
                        ?: 0.0

                    val refundAmount = when (cleanPaymentType) {
                        PaymentReceivedType.FULL -> totalPrice
                        PaymentReceivedType.PARTIAL -> 0.0
                        else -> 0.0
                    }

                    val now = System.currentTimeMillis()

                    transaction.update(
                        orderRef,
                        mapOf(
                            "status" to OrderStatusValue.CANCELLED,

                            "paymentStatus" to paymentStatus,
                            "paymentReceivedByShopkeeper" to paymentReceivedByShopkeeper,
                            "paymentReceivedType" to cleanPaymentType,

                            "cancelReason" to finalCancelReason,
                            "cancelledBy" to "admin",
                            "cancelledAt" to now,

                            "refundStatus" to refundStatus,
                            "refundAmount" to refundAmount,
                            "refundReferenceId" to "",
                            "refundSettledAt" to 0L,
                            "refundNote" to "",

                            "updatedAt" to now
                        )
                    )

                    val orderShopId = orderDoc.getString("shopId").orEmpty()

                    val pickupDate = orderDoc.getString("pickupDate")
                        ?.takeIf { it.isNotBlank() }
                        ?: LocalDate.now().toString()

                    val month = pickupDate.take(7)

                    if (orderShopId.isNotBlank()) {
                        incrementCancelledAnalytics(
                            transaction = transaction,
                            shopId = orderShopId,
                            pickupDate = pickupDate,
                            month = month
                        )
                    }

                    null
                }.await()

                _message.value = if (paymentReceivedByShopkeeper) {
                    "Order cancelled by admin. Refund is now pending."
                } else {
                    "Order cancelled by admin because payment was not received."
                }
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to cancel order by admin", e)
                _message.value = e.message ?: "Failed to cancel order."
            }
        }
    }

    fun markRefundSettledByAdmin(
        orderId: String,
        refundReferenceId: String,
        refundNote: String
    ) {
        if (orderId.isBlank()) {
            _message.value = "Order ID missing"
            return
        }

        viewModelScope.launch {
            try {
                val cleanRefundReferenceId = refundReferenceId.trim()
                val cleanRefundNote = refundNote.trim()

                if (cleanRefundReferenceId.isBlank()) {
                    throw IllegalStateException("Refund reference ID is required.")
                }

                val orderRef = firestore.collection("orders")
                    .document(orderId)

                firestore.runTransaction { transaction ->
                    val orderDoc = transaction.get(orderRef)

                    if (!orderDoc.exists()) {
                        throw IllegalStateException("Order not found.")
                    }

                    val refundStatus = orderDoc.getString("refundStatus")
                        ?.lowercase()
                        .orEmpty()

                    if (refundStatus != RefundStatusValue.REFUND_PENDING) {
                        throw IllegalStateException("This order is not pending refund.")
                    }

                    val now = System.currentTimeMillis()

                    transaction.update(
                        orderRef,
                        mapOf(
                            "paymentStatus" to PaymentStatusValue.REFUNDED,
                            "refundStatus" to RefundStatusValue.REFUNDED,
                            "refundReferenceId" to cleanRefundReferenceId,
                            "refundNote" to cleanRefundNote,
                            "refundSettledAt" to now,
                            "refundSettledBy" to "admin",
                            "updatedAt" to now
                        )
                    )

                    null
                }.await()

                _message.value = "Refund marked as settled by admin."
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Failed to mark refund settled by admin", e)
                _message.value = e.message ?: "Failed to mark refund settled."
            }
        }
    }

    private fun incrementCancelledAnalytics(
        transaction: com.google.firebase.firestore.Transaction,
        shopId: String,
        pickupDate: String,
        month: String
    ) {
        val dailyRef = dailyAnalyticsRef(
            shopId = shopId,
            date = pickupDate
        )

        val monthlyRef = monthlyAnalyticsRef(
            shopId = shopId,
            month = month
        )

        val lifetimeRef = lifetimeAnalyticsRef(
            shopId = shopId
        )

        val update = mapOf(
            "cancelledOrders" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        transaction.set(dailyRef, update, SetOptions.merge())
        transaction.set(monthlyRef, update, SetOptions.merge())
        transaction.set(lifetimeRef, update, SetOptions.merge())
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun generateShopId(
        name: String,
        uid: String
    ): String {
        val base = name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "shop" }

        return "${base}_${uid.takeLast(5)}"
    }

    override fun onCleared() {
        super.onCleared()
        shopsListener?.remove()
        usersListener?.remove()
        clearShopReportListeners()
    }
}
