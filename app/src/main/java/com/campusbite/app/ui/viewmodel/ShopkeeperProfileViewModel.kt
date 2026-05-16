package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.Shop
import com.campusbite.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ShopkeeperProfileUiState(
    val user: User? = null,
    val shop: Shop? = null,

    // ── Timing drafts ────────────────────────────────────────────────────────
    val draftOpeningTime: String = "",
    val draftClosingTime: String = "",
    val isTimingsDirty: Boolean = false,
    val timingsSaveSuccess: Boolean = false,

    // ── Capacity drafts ──────────────────────────────────────────────────────
    val draftMaxOrdersPerSlot: Int = 5,
    val isCapacityDirty: Boolean = false,
    val capacitySaveSuccess: Boolean = false,

    // ── Shop info drafts ─────────────────────────────────────────────────────
    val draftDescription: String = "",
    val isShopInfoDirty: Boolean = false,
    val shopInfoSaveSuccess: Boolean = false,

    // ── Live operational stats ───────────────────────────────────────────────
    val pendingOrdersCount: Int = 0,
    val completedTodayCount: Int = 0,

    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ShopkeeperProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopkeeperProfileUiState())
    val uiState: StateFlow<ShopkeeperProfileUiState> = _uiState.asStateFlow()

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val uid = auth.currentUser?.uid ?: return@launch

                val userDoc = firestore.collection("users").document(uid).get().await()
                val user = try { userDoc.toObject(User::class.java) } catch (e: Exception) { null }
                _uiState.update { it.copy(user = user) }

                val shopId = user?.shopId ?: return@launch
                val shopDoc = firestore.collection("shops").document(shopId).get().await()
                val shop = try { shopDoc.toObject(Shop::class.java) } catch (e: Exception) { null }

                _uiState.update {
                    it.copy(
                        shop = shop,
                        draftOpeningTime = shop?.openingTime ?: "",
                        draftClosingTime = shop?.closingTime ?: "",
                        draftMaxOrdersPerSlot = shop?.maxOrdersPerSlot ?: 5,
                        draftDescription = shop?.description ?: ""
                    )
                }

                loadOrderStats(shopId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadOrderStats(shopId: String) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("orders")
                    .whereEqualTo("shopId", shopId)
                    .get().await()

                val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date())

                var pending = 0
                var completedToday = 0

                for (doc in snapshot.documents) {
                    try {
                        val status = doc.getString("status")?.lowercase() ?: continue
                        val createdAtMillis = doc.getLong("createdAt") ?: 0L
                        val createdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(createdAtMillis))

                        if (status in listOf("pending", "accepted", "preparing")) pending++
                        if (createdDate == todayPrefix && status in listOf("completed", "picked_up")) {
                            completedToday++
                        }
                    } catch (_: Exception) { }
                }

                _uiState.update {
                    it.copy(
                        pendingOrdersCount = pending,
                        completedTodayCount = completedToday
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun toggleShopOpen() {
        val shop = _uiState.value.shop ?: return
        val newValue = !shop.isOpen
        viewModelScope.launch {
            try {
                firestore.collection("shops").document(shop.shopId)
                    .update("isOpen", newValue).await()
                _uiState.update { it.copy(shop = it.shop?.copy(isOpen = newValue)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Toggle failed: ${e.message}") }
            }
        }
    }

    fun updateOpeningTime(v: String) =
        _uiState.update { it.copy(draftOpeningTime = v, isTimingsDirty = true, timingsSaveSuccess = false) }

    fun updateClosingTime(v: String) =
        _uiState.update { it.copy(draftClosingTime = v, isTimingsDirty = true, timingsSaveSuccess = false) }

    fun saveTimings() {
        val shopId = _uiState.value.shop?.shopId ?: return
        viewModelScope.launch {
            try {
                firestore.collection("shops").document(shopId).update(
                    mapOf(
                        "openingTime" to _uiState.value.draftOpeningTime,
                        "closingTime" to _uiState.value.draftClosingTime
                    )
                ).await()
                _uiState.update { it.copy(isTimingsDirty = false, timingsSaveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun incrementMaxOrders() =
        _uiState.update { it.copy(draftMaxOrdersPerSlot = (it.draftMaxOrdersPerSlot + 1).coerceAtMost(50), isCapacityDirty = true, capacitySaveSuccess = false) }

    fun decrementMaxOrders() =
        _uiState.update { it.copy(draftMaxOrdersPerSlot = (it.draftMaxOrdersPerSlot - 1).coerceAtLeast(1), isCapacityDirty = true, capacitySaveSuccess = false) }

    fun saveCapacity() {
        val shopId = _uiState.value.shop?.shopId ?: return
        viewModelScope.launch {
            try {
                firestore.collection("shops").document(shopId).update(
                    "maxOrdersPerSlot", _uiState.value.draftMaxOrdersPerSlot
                ).await()
                _uiState.update { it.copy(isCapacityDirty = false, capacitySaveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun unblockSlot(slot: String) {
        val shop = _uiState.value.shop ?: return
        val updated = shop.closedSlots.filter { it != slot }
        viewModelScope.launch {
            try {
                firestore.collection("shops").document(shop.shopId)
                    .update("closedSlots", updated).await()
                _uiState.update { it.copy(shop = it.shop?.copy(closedSlots = updated)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to unblock slot") }
            }
        }
    }

    fun blockNextSlot() {
        val shop = _uiState.value.shop ?: return
        val endHour = shop.closingTime.split(":").firstOrNull()?.toIntOrNull() ?: 21
        val startHour = shop.openingTime.split(":").firstOrNull()?.toIntOrNull() ?: 8
        val allSlots = generateSlots(startHour, endHour)
        val next = allSlots.firstOrNull { it !in shop.closedSlots } ?: return
        val updated = shop.closedSlots + next
        viewModelScope.launch {
            try {
                firestore.collection("shops").document(shop.shopId)
                    .update("closedSlots", updated).await()
                _uiState.update { it.copy(shop = it.shop?.copy(closedSlots = updated)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to block slot") }
            }
        }
    }

    private fun generateSlots(startHour: Int, endHour: Int): List<String> {
        val slots = mutableListOf<String>()
        var h = startHour; var m = 0
        while (h < endHour) {
            slots.add("%02d:%02d".format(h, m))
            m += 30; if (m >= 60) { m = 0; h++ }
        }
        return slots
    }

    fun updateDescription(v: String) =
        _uiState.update { it.copy(draftDescription = v, isShopInfoDirty = true, shopInfoSaveSuccess = false) }

    fun saveShopInfo() {
        val shopId = _uiState.value.shop?.shopId ?: return
        viewModelScope.launch {
            try {
                firestore.collection("shops").document(shopId).update(
                    "description", _uiState.value.draftDescription
                ).await()
                _uiState.update { it.copy(isShopInfoDirty = false, shopInfoSaveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun changePassword(newPassword: String) {
        viewModelScope.launch {
            try {
                auth.currentUser?.updatePassword(newPassword)?.await()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Password update failed: ${e.message}") }
            }
        }
    }

    fun logout() = auth.signOut()
}