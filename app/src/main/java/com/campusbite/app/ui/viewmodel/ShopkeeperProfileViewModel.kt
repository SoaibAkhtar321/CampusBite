package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.campusbite.app.data.model.Shop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ShopkeeperProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _shopData = MutableStateFlow<Shop?>(null)
    val shopData = _shopData.asStateFlow()

    init {
        fetchShopDetails()
    }

    private fun fetchShopDetails() {
        val currentUser = auth.currentUser ?: return

        // Assuming your 'shops' collection has an 'ownerId' field
        firestore.collection("shops")
            .whereEqualTo("ownerId", currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val shop = snapshot.documents[0].toObject(Shop::class.java)
                    _shopData.value = shop
                }
            }
    }

    fun toggleShopStatus(isOpen: Boolean) {
        val shopId = _shopData.value?.shopId ?: return
        firestore.collection("shops").document(shopId)
            .update("isOpen", isOpen)
    }

    fun logout() {
        auth.signOut()
    }
}