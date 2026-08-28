package com.campusbite.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.fcmTokenSyncDataStore by preferencesDataStore(name = "fcm_token_sync")

/**
 * Local-only optimization guard for FCM token sync to Firestore.
 *
 * Tracks the (uid, token) pair that was LAST SUCCESSFULLY written to
 * users/{uid}.fcmToken, so callers can skip a redundant Firestore write
 * when the current token is already the one on the server.
 *
 * This is only a performance optimization: it is never read as the
 * authoritative token value, and a cache miss / stale cache only ever
 * results in an extra (harmless) Firestore write, never a skipped one
 * for a token that hasn't actually been confirmed synced.
 */
class FcmTokenSyncPrefs(private val context: Context) {

    private val lastSyncedUidKey = stringPreferencesKey("last_synced_uid")
    private val lastSyncedTokenKey = stringPreferencesKey("last_synced_token")

    /**
     * True only if [token] for [uid] is exactly the (uid, token) pair
     * that was last confirmed written to Firestore. Scoping by uid
     * (not just token) ensures a different account signing in on the
     * same device/app install is never mistaken for an already-synced
     * token, even though FCM tokens are per-install rather than per-account.
     */
    suspend fun isAlreadySynced(uid: String, token: String): Boolean {
        val prefs = context.fcmTokenSyncDataStore.data.first()

        return prefs[lastSyncedUidKey] == uid &&
                prefs[lastSyncedTokenKey] == token
    }

    /**
     * Call ONLY after a successful Firestore write for (uid, token).
     * Never call this speculatively before the write is confirmed.
     */
    suspend fun markSynced(uid: String, token: String) {
        context.fcmTokenSyncDataStore.edit { prefs ->
            prefs[lastSyncedUidKey] = uid
            prefs[lastSyncedTokenKey] = token
        }
    }
}