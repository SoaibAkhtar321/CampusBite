package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.campusbite.app.data.repository.ShopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val shopRepository: ShopRepository
) : ViewModel() {

    // ====== Data Streams ======
    private val _shops = MutableStateFlow<List<Shop>>(emptyList())
    val shops: StateFlow<List<Shop>> = _shops.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ====== Filter States ======
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _priceRange = MutableStateFlow(0f..500f)
    val priceRange: StateFlow<ClosedFloatingPointRange<Float>> = _priceRange.asStateFlow()

    // ====== Constants ======
    val categories = listOf("All", "Snacks", "Meals", "Drinks")
    val priceSteps = listOf(0f, 20f, 50f, 100f, 200f, 500f)
    val defaultPriceRange = 0f..500f

    // ====== UI State Helper ======
    val isDataReady: StateFlow<Boolean> = combine(_shops, _menuItems) { shops, items ->
        shops.isNotEmpty() && items.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // ====== Filtered Items Flow ======
    val filteredItems: StateFlow<List<MenuItem>> = combine(
        _menuItems,
        _selectedCategory,
        _searchQuery,
        _priceRange
    ) { menuItems, category, query, range ->
        getFilteredItemsInternal(menuItems, category, query, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    init {
        loadData()
    }

    // ====== Data Loading ======
    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                coroutineScope {
                    val shopsDeferred = async { shopRepository.getAllShops() }
                    val itemsDeferred = async { shopRepository.getAllMenuItems() }

                    val shopsResult = shopsDeferred.await()
                    val itemsResult = itemsDeferred.await()

                    if (shopsResult.isSuccess) {
                        _shops.value = shopsResult.getOrNull() ?: emptyList()
                    }
                    if (itemsResult.isSuccess) {
                        _menuItems.value = itemsResult.getOrNull() ?: emptyList()
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ====== Filter Update Functions ======
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

    // ====== Filter Logic ======
    /**
     * Get filtered items based on current filter states
     * This is a public function that uses current state values
     */
    fun getFilteredItems(): List<MenuItem> {
        return getFilteredItemsInternal(
            _menuItems.value,
            _selectedCategory.value,
            _searchQuery.value,
            _priceRange.value
        )
    }

    /**
     * Internal filter logic that can be used by both public function and Flow
     */
    private fun getFilteredItemsInternal(
        menuItems: List<MenuItem>,
        category: String,
        query: String,
        range: ClosedFloatingPointRange<Float>
    ): List<MenuItem> {
        val processedQuery = query.lowercase().trim()

        return menuItems.filter { item ->
            val matchesCategory = category == "All" || item.category == category

            val matchesPrice = item.price >= range.start && item.price <= range.endInclusive

            val shopName = getShopName(item.shopId).lowercase()
            val matchesSearch = processedQuery.isEmpty() ||
                    item.name.lowercase().contains(processedQuery) ||
                    shopName.contains(processedQuery)

            matchesCategory && matchesPrice && matchesSearch
        }
    }

    // ====== Helper Functions ======
    /**
     * Check if any filter is active (to show/hide the Clear button)
     */
    fun isFilterActive(): Boolean {
        val hasActiveCategory = _selectedCategory.value != "All"
        val hasActivePrice = _priceRange.value != defaultPriceRange
        val hasActiveSearch = _searchQuery.value.isNotBlank()

        return hasActiveCategory || hasActivePrice || hasActiveSearch
    }

    /**
     * Get shop name by shop ID
     */
    fun getShopName(shopId: String): String {
        return _shops.value.find { it.shopId == shopId }?.name ?: "Unknown Shop"
    }

    /**
     * Get shop by ID
     */
    fun getShopById(shopId: String): Shop? {
        return _shops.value.find { it.shopId == shopId }
    }

    /**
     * Get all menu items for a specific shop
     */
    fun getMenuItemsByShopId(shopId: String): List<MenuItem> {
        return _menuItems.value.filter { it.shopId == shopId }
    }

    /**
     * Get filtered items for a specific shop
     */
    fun getFilteredItemsByShopId(shopId: String): List<MenuItem> {
        return getFilteredItems().filter { it.shopId == shopId }
    }
}