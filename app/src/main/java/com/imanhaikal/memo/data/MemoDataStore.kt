package com.imanhaikal.memo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Every Preferences DataStore in the app is declared here and nowhere else.
 *
 * Declaring two `preferencesDataStore` delegates with the same file name anywhere in the
 * process throws `IllegalStateException: There are multiple DataStores active for the same
 * file` — at runtime, not compile time. Keeping the delegates in one file makes a duplicate
 * name obvious instead of a crash on a user's device.
 */
internal val Context.memoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "budget_preferences"
)
