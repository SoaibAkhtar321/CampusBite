package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.Shop
import com.campusbite.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ShopkeeperProfileUiState(
    val user: User? = null,
    val shop: Shop? = null,

    // Timings
    val draftOpeningTime: String = "",
    val draftClosingTime: String = "",
    val isTimingsDirty: Boolean = false,
    val timingsSaveSuccess: Boolean = false,

    // Capacity
    val draftMaxOrdersPerSlot: Int = 5,
    val isCapacityDirty: Boolean = false,
    val capacitySaveSuccess: Boolean = false,

    // Closed slots - stored in Firestore, not in Shop model
    val closedSlots: List<String> = emptyList(),

    // Shop info
    val draftDescription: String = "",
    val isShopInfoDirty: Boolean = false,
    val shopInfoSaveSuccess: Boolean = false,

    // Stats
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

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {
                val uid = auth.currentUser?.uid
                    ?: return@launch

                val userDoc = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                val user = try {
                    userDoc.toObject(User::class.java)
                } catch (e: Exception) {
                    null
                }

                _uiState.update {
                    it.copy(user = user)
                }

                val userShopId = user?.shopId.orEmpty()

                if (userShopId.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = "Shop ID not found in profile.")
                    }
                    return@launch
                }

                val shopDoc = getShopDocumentByIdOrField(userShopId)

                if (shopDoc == null || !shopDoc.exists()) {
                    _uiState.update {
                        it.copy(errorMessage = "Shop profile not found.")
                    }
                    return@launch
                }

                val shop = shopDoc.toShopOrNull()

                if (shop == null) {
                    _uiState.update {
                        it.copy(errorMessage = "Failed to load shop profile.")
                    }
                    return@launch
                }

                val closedSlots = getClosedSlotsFromDoc(shopDoc)

                _uiState.update {
                    it.copy(
                        shop = shop,
                        draftOpeningTime = shop.openingTime,
                        draftClosingTime = shop.closingTime,
                        draftMaxOrdersPerSlot = getMaxOrdersPerSlotFromDoc(shopDoc),
                        closedSlots = closedSlots,
                        draftDescription = shop.description,
                        isTimingsDirty = false,
                        timingsSaveSuccess = false,
                        isCapacityDirty = false,
                        capacitySaveSuccess = false,
                        isShopInfoDirty = false,
                        shopInfoSaveSuccess = false
                    )
                }

                loadOrderStats(shop.shopId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to load profile.")
                }
            } finally {
                _uiState.update {
                    it.copy(isLoading = false)
                }
            }
        }
    }

    private fun loadOrderStats(shopId: String) {
        if (shopId.isBlank()) return

        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("orders")
                    .whereEqualTo("shopId", shopId)
                    .get()
                    .await()

                val todayPrefix = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(Date())

                var pending = 0
                var completedToday = 0

                for (doc in snapshot.documents) {
                    val status = doc.getString("status")?.lowercase().orEmpty()
                    val createdAtMillis = doc.getLong("createdAt") ?: 0L

                    val createdDate = SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).format(Date(createdAtMillis))

                    if (status in listOf("pending", "accepted", "preparing")) {
                        pending++
                    }

                    if (
                        createdDate == todayPrefix &&
                        status in listOf("completed", "picked_up")
                    ) {
                        completedToday++
                    }
                }

                _uiState.update {
                    it.copy(
                        pendingOrdersCount = pending,
                        completedTodayCount = completedToday
                    )
                }
            } catch (_: Exception) {
                // Keep profile usable even if stats fail.
            }
        }
    }

    fun toggleShopOpen() {
        val shop = _uiState.value.shop ?: return
        val newValue = !shop.isOpen

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shop.shopId)
                    .update("isOpen", newValue)
                    .await()

                _uiState.update {
                    it.copy(
                        shop = it.shop?.copy(isOpen = newValue)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Toggle failed: ${e.message}")
                }
            }
        }
    }

    fun updateOpeningTime(value: String) {
        _uiState.update {
            it.copy(
                draftOpeningTime = value,
                isTimingsDirty = true,
                timingsSaveSuccess = false
            )
        }
    }

    fun updateClosingTime(value: String) {
        _uiState.update {
            it.copy(
                draftClosingTime = value,
                isTimingsDirty = true,
                timingsSaveSuccess = false
            )
        }
    }

    fun saveTimings() {
        val shopId = _uiState.value.shop?.shopId ?: return

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shopId)
                    .update(
                        mapOf(
                            "openingTime" to _uiState.value.draftOpeningTime,
                            "closingTime" to _uiState.value.draftClosingTime
                        )
                    )
                    .await()

                _uiState.update {
                    it.copy(
                        shop = it.shop?.copy(
                            openingTime = it.draftOpeningTime,
                            closingTime = it.draftClosingTime
                        ),
                        isTimingsDirty = false,
                        timingsSaveSuccess = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Save failed: ${e.message}")
                }
            }
        }
    }

    fun incrementMaxOrders() {
        _uiState.update {
            it.copy(
                draftMaxOrdersPerSlot = (it.draftMaxOrdersPerSlot + 1).coerceAtMost(50),
                isCapacityDirty = true,
                capacitySaveSuccess = false
            )
        }
    }

    fun decrementMaxOrders() {
        _uiState.update {
            it.copy(
                draftMaxOrdersPerSlot = (it.draftMaxOrdersPerSlot - 1).coerceAtLeast(1),
                isCapacityDirty = true,
                capacitySaveSuccess = false
            )
        }
    }

    fun saveCapacity() {
        val shopId = _uiState.value.shop?.shopId ?: return

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shopId)
                    .update(
                        "maxOrdersPerSlot",
                        _uiState.value.draftMaxOrdersPerSlot
                    )
                    .await()

                _uiState.update {
                    it.copy(
                        isCapacityDirty = false,
                        capacitySaveSuccess = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Save failed: ${e.message}")
                }
            }
        }
    }

    fun unblockSlot(slot: String) {
        val shopId = _uiState.value.shop?.shopId ?: return

        val updatedSlots = _uiState.value.closedSlots
            .filterNot { it == slot }

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shopId)
                    .update("closedSlots", updatedSlots)
                    .await()

                _uiState.update {
                    it.copy(closedSlots = updatedSlots)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to unblock slot.")
                }
            }
        }
    }

    fun blockNextSlot() {
        val shop = _uiState.value.shop ?: return
        val closedSlots = _uiState.value.closedSlots

        val allSlots = generateSlots(
            openingTime = shop.openingTime,
            closingTime = shop.closingTime
        )

        val nextSlot = allSlots.firstOrNull { it !in closedSlots }
            ?: return

        val updatedSlots = closedSlots + nextSlot

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shop.shopId)
                    .update("closedSlots", updatedSlots)
                    .await()

                _uiState.update {
                    it.copy(closedSlots = updatedSlots)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to block slot.")
                }
            }
        }
    }

    fun updateDescription(value: String) {
        _uiState.update {
            it.copy(
                draftDescription = value,
                isShopInfoDirty = true,
                shopInfoSaveSuccess = false
            )
        }
    }

    fun saveShopInfo() {
        val shopId = _uiState.value.shop?.shopId ?: return

        viewModelScope.launch {
            try {
                firestore.collection("shops")
                    .document(shopId)
                    .update("description", _uiState.value.draftDescription)
                    .await()

                _uiState.update {
                    it.copy(
                        shop = it.shop?.copy(
                            description = it.draftDescription
                        ),
                        isShopInfoDirty = false,
                        shopInfoSaveSuccess = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Save failed: ${e.message}")
                }
            }
        }
    }

    fun changePassword(newPassword: String) {
        viewModelScope.launch {
            try {
                auth.currentUser
                    ?.updatePassword(newPassword)
                    ?.await()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Password update failed: ${e.message}")
                }
            }
        }
    }

    fun logout() {
        auth.signOut()
    }

    private suspend fun getShopDocumentByIdOrField(shopId: String): DocumentSnapshot? {
        val cleanShopId = shopId.trim()

        if (cleanShopId.isBlank()) {
            return null
        }

        val directDoc = firestore.collection("shops")
            .document(cleanShopId)
            .get()
            .await()

        if (directDoc.exists()) {
            return directDoc
        }

        return firestore.collection("shops")
            .whereEqualTo("shopId", cleanShopId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
    }

    private fun DocumentSnapshot.toShopOrNull(): Shop? {
        val shop = try {
            toObject(Shop::class.java)
        } catch (e: Exception) {
            null
        } ?: return null

        return shop.copy(
            shopId = id,
            name = shop.name.ifBlank {
                getString("name") ?: "Shop"
            },
            description = shop.description.ifBlank {
                getString("description") ?: ""
            },
            openingTime = shop.openingTime.ifBlank {
                getString("openingTime") ?: "08:00"
            },
            closingTime = shop.closingTime.ifBlank {
                getString("closingTime") ?: "21:00"
            },
            isOpen = getBoolean("isOpen") ?: shop.isOpen,
            isApproved = getBoolean("isApproved") ?: shop.isApproved,
            isBlocked = getBoolean("isBlocked") ?: shop.isBlocked,
            isDeleted = getBoolean("isDeleted") ?: shop.isDeleted
        )
    }

    private fun getMaxOrdersPerSlotFromDoc(
        doc: DocumentSnapshot
    ): Int {
        return doc.getLong("maxOrdersPerSlot")
            ?.toInt()
            ?.coerceAtLeast(1)
            ?: 5
    }

    private fun getClosedSlotsFromDoc(
        doc: DocumentSnapshot
    ): List<String> {
        return (doc.get("closedSlots") as? List<*>)
            ?.filterIsInstance<String>()
            ?: emptyList()
    }

    private fun generateSlots(
        openingTime: String,
        closingTime: String
    ): List<String> {
        val displayFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        val start = parseTimeOrDefault(
            value = openingTime,
            fallback = LocalTime.of(8, 0)
        )

        val end = parseTimeOrDefault(
            value = closingTime,
            fallback = LocalTime.of(21, 0)
        )

        val slots = mutableListOf<String>()
        var current = start

        while (current.isBefore(end)) {
            slots.add(current.format(displayFormatter))
            current = current.plusMinutes(30)
        }

        return slots
    }

    private fun parseTimeOrDefault(
        value: String,
        fallback: LocalTime
    ): LocalTime {
        return try {
            LocalTime.parse(
                value.trim().replace("\"", ""),
                DateTimeFormatter.ofPattern("HH:mm")
            )
        } catch (e: Exception) {
            fallback
        }
    }
}