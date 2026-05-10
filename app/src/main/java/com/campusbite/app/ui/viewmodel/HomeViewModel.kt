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

    // Data Streams
    private val _shops = MutableStateFlow<List<Shop>>(emptyList())
    val shops: StateFlow<List<Shop>> = _shops.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Filter States
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _priceRange = MutableStateFlow(0f..500f)
    val priceRange: StateFlow<ClosedFloatingPointRange<Float>> = _priceRange.asStateFlow()

    val categories = listOf("All", "Snacks", "Meals", "Drinks")

    // UI state helper to know when data is loaded
    val isDataReady: StateFlow<Boolean> = combine(_shops, _menuItems) { shops, items ->
        shops.isNotEmpty() && items.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    init {
        loadData()
    }

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

    // Update Functions
    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updatePriceRange(range: ClosedFloatingPointRange<Float>) {
        _priceRange.value = range
    }


    // Inside HomeViewModel class

    // Define logical price steps
    val priceSteps = listOf(0f, 20f, 50f, 100f, 200f, 500f)

    fun getFilteredItems(): List<MenuItem> {
        val query = _searchQuery.value.lowercase().trim()
        val category = _selectedCategory.value
        val range = _priceRange.value

        return _menuItems.value.filter { item ->
            val matchesCategory = category == "All" || item.category == category

            // Price check using the selected range
            val matchesPrice = item.price >= range.start && item.price <= range.endInclusive

            val shopName = getShopName(item.shopId).lowercase()
            val matchesSearch = query.isEmpty() ||
                    item.name.lowercase().contains(query) ||
                    shopName.contains(query)

            matchesCategory && matchesPrice && matchesSearch
        }
    }
    // Inside HomeViewModel.kt

    // Default range to check against
    val defaultPriceRange = 0f..500f

    fun resetFilters() {
        _priceRange.value = defaultPriceRange
        _selectedCategory.value = "All"
    }

    // Helper to check if any filter is active (to show/hide the Clear button)
    fun isFilterActive(): Boolean {
        return _selectedCategory.value != "All" || _priceRange.value != defaultPriceRange
    }

    fun getShopName(shopId: String): String {
        return _shops.value.find { it.shopId == shopId }?.name ?: "Unknown Shop"
    }
}