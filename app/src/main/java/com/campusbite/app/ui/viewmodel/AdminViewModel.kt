package com.campusbite.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.Order
import com.campusbite.app.data.repository.OrderActionRepository
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
    val isVisible: Boolean = true,
    val visibilityStatus: String = "visible",
    val hiddenReason: String = "",
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
    val isDeleted: Boolean = false,
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
    private val firestore: FirebaseFirestore,
    private val orderActionRepository: OrderActionRepository
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

    private val _messageSource = MutableStateFlow<String?>(null)

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

    private fun setAdminMessage(
        source: String,
        text: String
    ) {
        _messageSource.value = source
        _message.value = "$source: $text"
    }

    private fun clearMessageIfSource(
        source: String
    ) {
        val currentSource = _messageSource.value
        val currentMessage = _message.value.orEmpty()

        if (currentSource == source || currentMessage.startsWith("$source:")) {
            _messageSource.value = null
            _message.value = null
        }
    }

    private fun Throwable.readableAdminError(): String {
        return if (this is FirebaseFirestoreException) {
            when (code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "PERMISSION_DENIED"

                FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                    "FAILED_PRECONDITION - likely missing Firestore index"

                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "UNAVAILABLE - network/server issue"

                FirebaseFirestoreException.Code.NOT_FOUND ->
                    "NOT_FOUND"

                else ->
                    "${code.name}: ${message.orEmpty()}"
            }
        } else {
            message ?: "Unknown error"
        }
    }

    private fun DocumentSnapshot.toAdminShop(): AdminShop {
        return AdminShop(
            docId = id,
            shopId = getString("shopId") ?: id,
            name = getString("name") ?: "",
            ownerUid = getString("ownerUid")
                ?: getString("ownerId")
                ?: "",
            isOpen = getBoolean("isOpen") ?: false,
            isApproved = getBoolean("isApproved") ?: false,
            isBlocked = getBoolean("isBlocked") ?: false,
            isDeleted = getBoolean("isDeleted") ?: false,
            isVisible = getBoolean("isVisible") ?: true,
            visibilityStatus = getString("visibilityStatus") ?: "visible",
            hiddenReason = getString("hiddenReason") ?: "",
            displayOrder = getLong("displayOrder")?.toInt() ?: 1000
        )
    }

    private fun listenToShops() {
        shopsListener?.remove()

        shopsListener = firestore.collection("shops")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val readableError = error.readableAdminError()

                    Log.e(
                        ADMIN_TAG,
                        "FAILED QUERY: shops listener | $readableError",
                        error
                    )

                    setAdminMessage(
                        source = "shops listener",
                        text = readableError
                    )

                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val shopList = snapshot?.documents
                    ?.map { doc ->
                        doc.toAdminShop()
                    }
                    ?.sortedWith(
                        compareBy<AdminShop> { it.displayOrder }
                            .thenBy { it.name.lowercase() }
                    )
                    ?: emptyList()

                _shops.value = shopList
                clearMessageIfSource("shops listener")
                _isLoading.value = false
            }
    }

    private fun listenToUsers() {
        usersListener?.remove()

        usersListener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val readableError = error.readableAdminError()

                    Log.e(
                        ADMIN_TAG,
                        "FAILED QUERY: users listener | $readableError",
                        error
                    )

                    setAdminMessage(
                        source = "users listener",
                        text = readableError
                    )

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
                            isDeleted = doc.getBoolean("isDeleted") ?: false,
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
                                !user.isBlocked &&
                                !user.isDeleted
                    }
                    .sortedByDescending { it.createdAt }

                clearMessageIfSource("users listener")
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
                        error = "Shop not found. shopId used: $shopId"
                    )
                    return@launch
                }

                currentReportShop = shopDoc.toAdminShop()

                startShopReportListeners(
                    shop = currentReportShop!!
                )
            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED QUERY: start shop report | shopId=$shopId | $readableError",
                    e
                )

                _shopReportState.value = AdminShopReportState(
                    error = "Failed to load shop report. Source: start shop report. Error: $readableError"
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
                val readableError = error.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED QUERY: today analytics | shopId=${shop.shopId} | $readableError",
                    error
                )

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
                val readableError = error.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED QUERY: month analytics | shopId=${shop.shopId} | $readableError",
                    error
                )

                return@addSnapshotListener
            }

            reportMonthAnalytics = snapshot.toAdminAnalyticsSnapshot()
            updateShopReportState()
        }

        shopReportLifetimeAnalyticsListener = lifetimeAnalyticsRef(
            shopId = shop.shopId
        ).addSnapshotListener { snapshot, error ->
            if (error != null) {
                val readableError = error.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED QUERY: lifetime analytics | shopId=${shop.shopId} | $readableError",
                    error
                )

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
                    val readableError = error.readableAdminError()

                    Log.e(
                        ADMIN_TAG,
                        "FAILED QUERY: active orders | shopId=${shop.shopId} | $readableError",
                        error
                    )

                    _shopReportState.value = _shopReportState.value.copy(
                        isLoading = false,
                        error = "Source: active orders. Error: $readableError"
                    )
                    return@addSnapshotListener
                }

                reportActiveOrders = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e(
                                ADMIN_TAG,
                                "FAILED PARSE: active order | docId=${doc.id}",
                                e
                            )
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
                    val readableError = error.readableAdminError()

                    Log.e(
                        ADMIN_TAG,
                        "FAILED QUERY: recent orders | shopId=${shop.shopId} | $readableError",
                        error
                    )

                    _shopReportState.value = _shopReportState.value.copy(
                        isLoading = false,
                        error = "Source: recent orders. Error: $readableError"
                    )
                    return@addSnapshotListener
                }

                reportRecentOrders = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e(
                                ADMIN_TAG,
                                "FAILED PARSE: recent order | docId=${doc.id}",
                                e
                            )
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
                            "isApproved" to false,
                            "updatedAt" to System.currentTimeMillis()
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
                val now = System.currentTimeMillis()

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
                        "isVisible" to true,
                        "visibilityStatus" to "visible",
                        "hiddenReason" to "",
                        "hiddenAt" to 0L,

                        "upiId" to "",

                        "openingTime" to "08:00",
                        "closingTime" to "20:00",

                        "maxOrdersPerSlot" to 5,
                        "closedSlots" to emptyList<String>(),

                        "displayOrder" to getNextShopDisplayOrder(),

                        "createdAt" to now,
                        "updatedAt" to now
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
                            "isVisible" to true,
                            "visibilityStatus" to "visible",
                            "hiddenReason" to "",
                            "hiddenAt" to 0L,
                            "ownerUid" to uid,
                            "ownerEmail" to email,
                            "ownerPhone" to phone,
                            "phone" to phone,
                            "displayOrder" to existingDisplayOrder,
                            "updatedAt" to now
                        )
                    ).await()
                }

                userRef.update(
                    mapOf(
                        "isApproved" to true,
                        "isBlocked" to false,
                        "isDeleted" to false,
                        "shopId" to finalShopId,
                        "updatedAt" to now
                    )
                ).await()

                _message.value = "Shopkeeper approved and shop created"

            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: setShopkeeperApproved | userDocId=$userDocId | $readableError",
                    e
                )

                _message.value = "Failed to approve shopkeeper: $readableError"
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
                val finalShopDocId = shopDocId.ifBlank { shopId }
                val finalShopId = shopId.ifBlank { finalShopDocId }

                if (finalShopDocId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                val now = System.currentTimeMillis()
                val batch = firestore.batch()

                val shopRef = firestore.collection("shops")
                    .document(finalShopDocId)

                batch.update(
                    shopRef,
                    mapOf(
                        "isApproved" to approved,
                        "isVisible" to approved,
                        "isOpen" to false,
                        "visibilityStatus" to if (approved) {
                            "visible"
                        } else {
                            "approval_removed"
                        },
                        "hiddenReason" to if (approved) {
                            ""
                        } else {
                            "Shop approval removed by admin"
                        },
                        "updatedAt" to now
                    )
                )

                if (finalShopId.isNotBlank()) {
                    val usersSnapshot = firestore.collection("users")
                        .whereEqualTo("shopId", finalShopId)
                        .get()
                        .await()

                    usersSnapshot.documents.forEach { userDoc ->
                        batch.update(
                            userDoc.reference,
                            mapOf(
                                "isApproved" to approved,
                                "updatedAt" to now
                            )
                        )
                    }
                }

                batch.commit().await()

                _message.value = if (approved) {
                    "Shop approved successfully"
                } else {
                    "Shop approval removed and hidden from users"
                }

            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: setShopApproved | shopDocId=$shopDocId | $readableError",
                    e
                )

                _message.value = "Failed to update shop approval: $readableError"
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

                val shopRef = firestore.collection("shops")
                    .document(shopDocId)

                val shopDoc = shopRef.get().await()

                if (!shopDoc.exists()) {
                    _message.value = "Shop not found"
                    return@launch
                }

                val isApproved = shopDoc.getBoolean("isApproved") ?: false
                val isBlocked = shopDoc.getBoolean("isBlocked") ?: false
                val isDeleted = shopDoc.getBoolean("isDeleted") ?: false
                val isVisible = shopDoc.getBoolean("isVisible") ?: true

                if (open && (!isApproved || isBlocked || isDeleted || !isVisible)) {
                    _message.value = "Shop cannot be opened because it is hidden, blocked, deleted, or not approved"
                    return@launch
                }

                shopRef.update(
                    mapOf(
                        "isOpen" to open,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()

                _message.value = if (open) {
                    "Shop marked as open"
                } else {
                    "Shop marked as closed"
                }

            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: setShopOpen | shopDocId=$shopDocId | $readableError",
                    e
                )

                _message.value = "Failed to update shop status: $readableError"
            }
        }
    }

    fun setShopVisibility(
        shopDocId: String,
        shopId: String,
        visible: Boolean,
        reason: String = ""
    ) {
        viewModelScope.launch {
            try {
                val finalShopDocId = shopDocId.ifBlank { shopId }

                if (finalShopDocId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                val shopRef = firestore.collection("shops")
                    .document(finalShopDocId)

                val shopDoc = shopRef.get().await()

                if (!shopDoc.exists()) {
                    _message.value = "Shop not found"
                    return@launch
                }

                val isBlocked = shopDoc.getBoolean("isBlocked") ?: false
                val isDeleted = shopDoc.getBoolean("isDeleted") ?: false

                if (visible && (isBlocked || isDeleted)) {
                    _message.value = "Blocked or deleted shop cannot be made visible"
                    return@launch
                }

                val now = System.currentTimeMillis()

                val updateData = if (visible) {
                    mapOf(
                        "isVisible" to true,
                        "visibilityStatus" to "visible",
                        "hiddenReason" to "",
                        "hiddenAt" to 0L,
                        "updatedAt" to now
                    )
                } else {
                    mapOf(
                        "isVisible" to false,
                        "isOpen" to false,
                        "visibilityStatus" to "temporarily_hidden",
                        "hiddenReason" to reason.ifBlank {
                            "Temporarily hidden by admin"
                        },
                        "hiddenAt" to now,
                        "updatedAt" to now
                    )
                }

                shopRef.update(updateData).await()

                _message.value = if (visible) {
                    "Shop is now visible to users"
                } else {
                    "Shop hidden from users"
                }
            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: setShopVisibility | shopDocId=$shopDocId | shopId=$shopId | $readableError",
                    e
                )

                _message.value = "Failed to update shop visibility: $readableError"
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
                val finalShopId = shopId.ifBlank { finalShopDocId }

                if (finalShopDocId.isBlank() || finalShopId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                val now = System.currentTimeMillis()
                val batch = firestore.batch()

                val shopRef = firestore.collection("shops")
                    .document(finalShopDocId)

                if (blocked) {
                    batch.update(
                        shopRef,
                        mapOf(
                            "isBlocked" to true,
                            "isApproved" to false,
                            "isVisible" to false,
                            "isOpen" to false,
                            "visibilityStatus" to "blocked",
                            "hiddenReason" to "Shop blocked by admin",
                            "blockedAt" to now,
                            "updatedAt" to now
                        )
                    )
                } else {
                    batch.update(
                        shopRef,
                        mapOf(
                            "isBlocked" to false,
                            "isApproved" to true,
                            "isVisible" to true,
                            "isOpen" to false,
                            "visibilityStatus" to "visible",
                            "hiddenReason" to "",
                            "hiddenAt" to 0L,
                            "updatedAt" to now
                        )
                    )
                }

                val usersSnapshot = firestore.collection("users")
                    .whereEqualTo("shopId", finalShopId)
                    .get()
                    .await()

                usersSnapshot.documents.forEach { userDoc ->
                    batch.update(
                        userDoc.reference,
                        mapOf(
                            "isApproved" to !blocked,
                            "isBlocked" to blocked,
                            "updatedAt" to now
                        )
                    )
                }

                batch.commit().await()

                _message.value = if (blocked) {
                    "Shop blocked and hidden from users"
                } else {
                    "Shop unblocked and visible to users"
                }
            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: setShopBlocked | shopDocId=$shopDocId | shopId=$shopId | $readableError",
                    e
                )

                _message.value = "Failed to update shop: $readableError"
            }
        }
    }

    fun deleteShopCompletely(
        shopDocId: String,
        shopId: String
    ) {
        viewModelScope.launch {
            try {
                val finalShopDocId = shopDocId.ifBlank { shopId }
                val finalShopId = shopId.ifBlank { finalShopDocId }

                if (finalShopDocId.isBlank() || finalShopId.isBlank()) {
                    _message.value = "Shop ID missing"
                    return@launch
                }

                val now = System.currentTimeMillis()
                val batch = firestore.batch()

                val shopRef = firestore.collection("shops")
                    .document(finalShopDocId)

                batch.update(
                    shopRef,
                    mapOf(
                        "isOpen" to false,
                        "isApproved" to false,
                        "isBlocked" to true,
                        "isDeleted" to true,
                        "isVisible" to false,
                        "visibilityStatus" to "deleted",
                        "hiddenReason" to "Shop deleted by admin",
                        "deletedAt" to now,
                        "updatedAt" to now
                    )
                )

                val usersSnapshot = firestore.collection("users")
                    .whereEqualTo("shopId", finalShopId)
                    .get()
                    .await()

                usersSnapshot.documents.forEach { userDoc ->
                    batch.update(
                        userDoc.reference,
                        mapOf(
                            "role" to "shopkeeper",
                            "isApproved" to false,
                            "isBlocked" to true,
                            "isDeleted" to true,
                            "deletedAt" to now,
                            "updatedAt" to now
                        )
                    )
                }

                val menuSnapshot = firestore.collection("menuItems")
                    .whereEqualTo("shopId", finalShopId)
                    .get()
                    .await()

                menuSnapshot.documents.forEach { menuDoc ->
                    batch.update(
                        menuDoc.reference,
                        mapOf(
                            "isAvailable" to false,
                            "isDeleted" to true,
                            "updatedAt" to now
                        )
                    )
                }

                batch.commit().await()

                _message.value = "Shop deleted and hidden from users"
            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: deleteShopCompletely | shopDocId=$shopDocId | shopId=$shopId | $readableError",
                    e
                )

                _message.value = "Failed to delete shop: $readableError"
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
                            "isDeleted" to false,
                            "shopId" to "",
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
                    .await()

                _message.value = "Pending request removed"

            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: removePendingShopkeeper | userDocId=$userDocId | $readableError",
                    e
                )

                _message.value = "Failed to remove request: $readableError"
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

                val now = System.currentTimeMillis()
                val batch = firestore.batch()

                val userRef = firestore.collection("users")
                    .document(userDocId)

                val userSnapshot = userRef.get().await()

                if (!userSnapshot.exists()) {
                    _message.value = "User not found"
                    return@launch
                }

                val role = userSnapshot.getString("role") ?: "student"
                val shopId = userSnapshot.getString("shopId") ?: ""

                batch.update(
                    userRef,
                    mapOf(
                        "isBlocked" to blocked,
                        "isApproved" to if (role == "shopkeeper") !blocked else true,
                        "updatedAt" to now
                    )
                )

                if (role == "shopkeeper" && shopId.isNotBlank()) {
                    val shopRef = firestore.collection("shops")
                        .document(shopId)

                    if (blocked) {
                        batch.update(
                            shopRef,
                            mapOf(
                                "isBlocked" to true,
                                "isApproved" to false,
                                "isVisible" to false,
                                "isOpen" to false,
                                "visibilityStatus" to "blocked",
                                "hiddenReason" to "Shopkeeper blocked by admin",
                                "blockedAt" to now,
                                "updatedAt" to now
                            )
                        )
                    } else {
                        batch.update(
                            shopRef,
                            mapOf(
                                "isBlocked" to false,
                                "isApproved" to true,
                                "isVisible" to true,
                                "isOpen" to false,
                                "visibilityStatus" to "visible",
                                "hiddenReason" to "",
                                "hiddenAt" to 0L,
                                "updatedAt" to now
                            )
                        )
                    }
                }

                batch.commit().await()

                _message.value = if (blocked) {
                    "User blocked successfully"
                } else {
                    "User unblocked successfully"
                }

            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: setUserBlocked | userDocId=$userDocId | $readableError",
                    e
                )

                _message.value = "Failed to update user: $readableError"
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
                    "role" to cleanRole,
                    "updatedAt" to System.currentTimeMillis()
                )

                when (cleanRole) {
                    "shopkeeper" -> {
                        updates["isApproved"] = false
                        updates["isBlocked"] = false
                        updates["isDeleted"] = false
                        updates["shopId"] = ""
                    }

                    "student" -> {
                        updates["isApproved"] = true
                        updates["isBlocked"] = false
                        updates["isDeleted"] = false
                        updates["shopId"] = ""
                    }

                    "admin" -> {
                        updates["isApproved"] = true
                        updates["isBlocked"] = false
                        updates["isDeleted"] = false
                        updates["shopId"] = ""
                    }
                }

                firestore.collection("users")
                    .document(userDocId)
                    .update(updates)
                    .await()

                _message.value = "User role updated to $cleanRole"

            } catch (e: Exception) {
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: setUserRole | userDocId=$userDocId | role=$role | $readableError",
                    e
                )

                _message.value = "Failed to update role: $readableError"
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
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: moveShopToTop | shop=${shop.docId} | $readableError",
                    e
                )

                _message.value = "Failed to update shop position: $readableError"
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
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: moveShopUp | shop=${shop.docId} | $readableError",
                    e
                )

                _message.value = "Failed to update shop position: $readableError"
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
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: moveShopDown | shop=${shop.docId} | $readableError",
                    e
                )

                _message.value = "Failed to update shop position: $readableError"
            }
        }
    }

    private fun getOrderedActiveShops(): List<AdminShop> {
        return _shops.value
            .filter { shop ->
                !shop.isDeleted &&
                        !shop.isBlocked &&
                        shop.isApproved &&
                        shop.isVisible
            }
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
                val readableError = e.readableAdminError()

                Log.e(
                    ADMIN_TAG,
                    "FAILED WRITE: cancelOrderByAdmin | orderId=$orderId | $readableError",
                    e
                )

                _message.value = "Failed to cancel order: $readableError"
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
            val cleanRefundReferenceId = refundReferenceId.trim()
            val cleanRefundNote = refundNote.trim()

            if (cleanRefundReferenceId.isBlank()) {
                _message.value = "Refund reference ID is required."
                return@launch
            }

            // markRefundSettled Cloud Function accepts admin or shopkeeper.
            val result = orderActionRepository.markRefundSettled(
                orderId = orderId,
                refundReferenceId = cleanRefundReferenceId,
                refundNote = cleanRefundNote
            )

            result.fold(
                onSuccess = {
                    _message.value = "Refund marked as settled by admin."
                },
                onFailure = { error ->
                    val readableError = error.readableAdminError()
                    Log.e(
                        ADMIN_TAG,
                        "FAILED WRITE: markRefundSettledByAdmin | orderId=$orderId | $readableError",
                        error
                    )
                    _message.value = "Failed to mark refund settled: $readableError"
                }
            )
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
        _messageSource.value = null
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

    companion object {
        private const val ADMIN_TAG = "AdminViewModel"
    }
}