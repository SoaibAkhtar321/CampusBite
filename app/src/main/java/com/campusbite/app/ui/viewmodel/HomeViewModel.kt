package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.campusbite.app.data.repository.ShopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val shopRepository: ShopRepository
) : ViewModel() {

    private val _shops = MutableStateFlow<List<Shop>>(emptyList())
    val shops: StateFlow<List<Shop>> = _shops

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val categories = listOf("All", "Snacks", "Meals", "Drinks")
    val isDataReady: StateFlow<Boolean> = combine(_shops, _menuItems) { shops, items ->
        shops.isNotEmpty() && items.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            coroutineScope {
                val shopsDeferred = async { shopRepository.getAllShops() }
                val itemsDeferred = async { shopRepository.getAllMenuItems() }

                val shopsResult = shopsDeferred.await()
                val itemsResult = itemsDeferred.await()

                if (shopsResult.isSuccess) _shops.value = shopsResult.getOrNull() ?: emptyList()
                if (itemsResult.isSuccess) _menuItems.value = itemsResult.getOrNull() ?: emptyList()
            }
            _isLoading.value = false
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredItems(): List<MenuItem> {
        val query = _searchQuery.value.lowercase()
        val category = _selectedCategory.value
        return _menuItems.value.filter { item ->
            val matchesCategory = category == "All" || item.category == category
            val matchesSearch = query.isEmpty() ||
                    item.name.lowercase().contains(query) ||
                    _shops.value.find { it.shopId == item.shopId }?.name?.lowercase()?.contains(query) == true
            matchesCategory && matchesSearch
        }
    }
    fun getShopName(shopId: String): String {
        return _shops.value.find { it.shopId == shopId }?.name ?: shopId
    }

}