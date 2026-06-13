package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.ranges.ClosedFloatingPointRange

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _shops = MutableStateFlow<List<Shop>>(emptyList())
    val shops: StateFlow<List<Shop>> = _shops.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _priceRange = MutableStateFlow(0f..500f)
    val priceRange: StateFlow<ClosedFloatingPointRange<Float>> =
        _priceRange.asStateFlow()

    val categories = listOf("All", "Snacks", "Meals", "Drinks")
    val priceSteps = listOf(0f, 20f, 50f, 100f, 200f, 500f)
    val defaultPriceRange = 0f..500f

    private var lastVisibleShopDocument: DocumentSnapshot? = null
    private var isPageRequestRunning = false
    private var reachedEndOfShopCollection = false

    private val pendingShopBuffer = mutableListOf<Shop>()

    val isDataReady: StateFlow<Boolean> = _isLoading
        .map { loading -> !loading }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    val filteredItems: StateFlow<List<MenuItem>> = combine(
        _menuItems,
        _selectedCategory,
        _searchQuery,
        _priceRange
    ) { menuItems, category, query, range ->
        getFilteredItemsInternal(
            menuItems = menuItems,
            category = category,
            query = query,
            range = range
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = emptyList()
    )

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            loadFirstPage()
        }
    }

    private suspend fun loadFirstPage() {
        if (isPageRequestRunning) return

        isPageRequestRunning = true
        _isLoading.value = true
        _isLoadingMore.value = false

        try {
            lastVisibleShopDocument = null
            reachedEndOfShopCollection = false
            pendingShopBuffer.clear()

            _canLoadMore.value = true
            _shops.value = emptyList()
            _menuItems.value = emptyList()

            loadShopPage(reset = true)
        } catch (e: Exception) {
            e.printStackTrace()
            _shops.value = emptyList()
            _menuItems.value = emptyList()
            _canLoadMore.value = false
        } finally {
            _isLoading.value = false
            isPageRequestRunning = false
        }
    }

    fun loadMoreShops() {
        viewModelScope.launch {
            if (isPageRequestRunning) return@launch
            if (!_canLoadMore.value) return@launch
            if (_isLoading.value) return@launch

            isPageRequestRunning = true
            _isLoadingMore.value = true

            try {
                loadShopPage(reset = false)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMore.value = false
                isPageRequestRunning = false
            }
        }
    }

    private suspend fun loadShopPage(reset: Boolean) {
        val pageShops = mutableListOf<Shop>()

        while (pageShops.size < SHOP_PAGE_SIZE && pendingShopBuffer.isNotEmpty()) {
            pageShops.add(pendingShopBuffer.removeAt(0))
        }

        val alreadyKnownShopIds = mutableSetOf<String>()
        alreadyKnownShopIds.addAll(_shops.value.map { it.shopId })
        alreadyKnownShopIds.addAll(pageShops.map { it.shopId })
        alreadyKnownShopIds.addAll(pendingShopBuffer.map { it.shopId })

        while (pageShops.size < SHOP_PAGE_SIZE && !reachedEndOfShopCollection) {
            var query: Query = firestore.collection("shops")
                .orderBy("displayOrder", Query.Direction.ASCENDING)
                .limit(SHOP_FETCH_LIMIT)

            if (lastVisibleShopDocument != null) {
                query = query.startAfter(lastVisibleShopDocument!!)
            }

            val snapshot = query.get().await()
            val docs = snapshot.documents

            if (docs.isEmpty()) {
                reachedEndOfShopCollection = true
                break
            }

            lastVisibleShopDocument = docs.lastOrNull()

            if (docs.size < SHOP_FETCH_LIMIT.toInt()) {
                reachedEndOfShopCollection = true
            }

            val visibleShops = docs
                .filter { doc -> doc.shouldShowOnHome() }
                .mapNotNull { doc -> doc.toShopOrNull() }

            visibleShops.forEach { shop ->
                if (shop.shopId.isNotBlank() && alreadyKnownShopIds.add(shop.shopId)) {
                    if (pageShops.size < SHOP_PAGE_SIZE) {
                        pageShops.add(shop)
                    } else {
                        pendingShopBuffer.add(shop)
                    }
                }
            }
        }

        val newShopIds = pageShops
            .map { it.shopId }
            .filter { it.isNotBlank() }

        val newMenuItems = loadMenuItemsForShopIds(newShopIds)

        _shops.value = if (reset) {
            pageShops
        } else {
            (_shops.value + pageShops)
                .distinctBy { it.shopId }
                .sortedWith(
                    compareBy<Shop> { it.displayOrder }
                        .thenBy { it.name.lowercase() }
                )
        }

        _menuItems.value = if (reset) {
            newMenuItems
        } else {
            (_menuItems.value + newMenuItems)
                .distinctBy { item ->
                    item.itemId.ifBlank {
                        "${item.shopId}_${item.name}_${item.price}"
                    }
                }
        }

        _canLoadMore.value =
            pendingShopBuffer.isNotEmpty() || !reachedEndOfShopCollection
    }

    private fun DocumentSnapshot.shouldShowOnHome(): Boolean {
        val isApproved = getBoolean("isApproved") ?: true
        val isBlocked = getBoolean("isBlocked") ?: false
        val isDeleted = getBoolean("isDeleted") ?: false
        val isVisible = getBoolean("isVisible") ?: true

        return isApproved && !isBlocked && !isDeleted && isVisible
    }

    private suspend fun loadMenuItemsForShopIds(
        shopIds: List<String>
    ): List<MenuItem> {
        if (shopIds.isEmpty()) return emptyList()

        val allItems = mutableListOf<MenuItem>()

        shopIds.chunked(10).forEach { shopIdChunk ->
            val snapshot = firestore.collection("menuItems")
                .whereIn("shopId", shopIdChunk)
                .get()
                .await()

            val items = snapshot.documents.mapNotNull { doc ->
                doc.toMenuItemOrNull()
            }

            allItems.addAll(items)
        }

        return allItems.sortedWith(
            compareBy<MenuItem> { it.shopId }
                .thenBy { it.category.lowercase() }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun DocumentSnapshot.toShopOrNull(): Shop? {
        return try {
            val shop = toObject(Shop::class.java) ?: return null

            shop.copy(
                shopId = id,
                name = shop.name.ifBlank {
                    getString("name") ?: "Shop"
                },
                isOpen = getBoolean("isOpen") ?: false,
                isApproved = getBoolean("isApproved") ?: true,
                isBlocked = getBoolean("isBlocked") ?: false,
                isDeleted = getBoolean("isDeleted") ?: false,
                displayOrder = getLong("displayOrder") ?: 1000L
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun DocumentSnapshot.toMenuItemOrNull(): MenuItem? {
        return try {
            val item = toObject(MenuItem::class.java) ?: return null

            item.copy(
                itemId = item.itemId.ifBlank {
                    getString("itemId") ?: id
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updatePriceRange(range: ClosedFloatingPointRange<Float>) {
        _priceRange.value = range
    }

    fun resetFilters() {
        _priceRange.value = defaultPriceRange
        _selectedCategory.value = "All"
        _searchQuery.value = ""
    }

    fun getFilteredItems(): List<MenuItem> {
        return getFilteredItemsInternal(
            menuItems = _menuItems.value,
            category = _selectedCategory.value,
            query = _searchQuery.value,
            range = _priceRange.value
        )
    }

    private fun getFilteredItemsInternal(
        menuItems: List<MenuItem>,
        category: String,
        query: String,
        range: ClosedFloatingPointRange<Float>
    ): List<MenuItem> {
        val processedQuery = query.lowercase().trim()

        return menuItems.filter { item ->
            val itemCategory = item.category.lowercase().trim()
            val selectedCategory = category.lowercase().trim()

            val matchesCategory =
                selectedCategory == "all" ||
                        itemCategory == selectedCategory ||
                        itemCategory == selectedCategory.removeSuffix("s") ||
                        itemCategory + "s" == selectedCategory

            val matchesPrice =
                item.price >= range.start &&
                        item.price <= range.endInclusive

            val shopName = getShopName(item.shopId).lowercase()

            val matchesSearch =
                processedQuery.isEmpty() ||
                        item.name.lowercase().contains(processedQuery) ||
                        shopName.contains(processedQuery)

            matchesCategory && matchesPrice && matchesSearch
        }
    }

    fun isFilterActive(): Boolean {
        val hasActiveCategory = _selectedCategory.value != "All"
        val hasActivePrice = _priceRange.value != defaultPriceRange
        val hasActiveSearch = _searchQuery.value.isNotBlank()

        return hasActiveCategory || hasActivePrice || hasActiveSearch
    }

    fun getShopName(shopId: String): String {
        return _shops.value.find { it.shopId == shopId }?.name ?: "Unknown Shop"
    }

    fun getShopById(shopId: String): Shop? {
        return _shops.value.find { it.shopId == shopId }
    }

    fun getMenuItemsByShopId(shopId: String): List<MenuItem> {
        return _menuItems.value.filter { it.shopId == shopId }
    }

    fun getFilteredItemsByShopId(shopId: String): List<MenuItem> {
        return getFilteredItems().filter { it.shopId == shopId }
    }

    companion object {
        private const val SHOP_PAGE_SIZE = 4
        private const val SHOP_FETCH_LIMIT = 8L
    }
}