package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.campusbite.app.data.repository.ShopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.ranges.ClosedFloatingPointRange
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val shopRepository: ShopRepository
) : ViewModel() {

    private val _shops = MutableStateFlow<List<Shop>>(emptyList())
    val shops: StateFlow<List<Shop>> = _shops.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _priceRange = MutableStateFlow(0f..500f)
    val priceRange: StateFlow<ClosedFloatingPointRange<Float>> = _priceRange.asStateFlow()

    val categories = listOf("All", "Snacks", "Meals", "Drinks")
    val priceSteps = listOf(0f, 20f, 50f, 100f, 200f, 500f)
    val defaultPriceRange = 0f..500f

    val isDataReady: StateFlow<Boolean> = combine(
        _shops,
        _menuItems
    ) { shops, items ->
        shops.isNotEmpty() || items.isNotEmpty()
    }.stateIn(
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
        loadData()
    }

    fun refreshData() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                coroutineScope {
                    val shopsDeferred = async {
                        shopRepository.getAllShops()
                    }

                    val itemsDeferred = async {
                        shopRepository.getAllMenuItems()
                    }

                    val shopsResult = shopsDeferred.await()
                    val itemsResult = itemsDeferred.await()

                    if (shopsResult.isSuccess) {
                        _shops.value = shopsResult.getOrNull().orEmpty()
                    }

                    if (itemsResult.isSuccess) {
                        _menuItems.value = itemsResult.getOrNull().orEmpty()
                    }
                }
            } finally {
                _isLoading.value = false
            }
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
}