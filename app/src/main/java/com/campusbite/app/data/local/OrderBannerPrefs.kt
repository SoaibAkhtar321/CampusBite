package com.campusbite.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "order_banner")

class OrderBannerPrefs(private val context: Context) {

    private val dismissedKey = stringSetPreferencesKey("dismissed_order_ids")

    val dismissedIds: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[dismissedKey] ?: emptySet() }

    suspend fun dismiss(orderId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[dismissedKey] ?: emptySet()
            prefs[dismissedKey] = current + orderId
        }
    }

    suspend fun clear(orderId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[dismissedKey] ?: emptySet()
            prefs[dismissedKey] = current - orderId
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.remove(dismissedKey) }
    }
}