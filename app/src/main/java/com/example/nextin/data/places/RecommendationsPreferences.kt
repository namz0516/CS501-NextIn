package com.example.nextin.data.places

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recommendationsStore by preferencesDataStore(name = "recommendations")

class RecommendationsPreferences(private val context: Context) {
    private val key = booleanPreferencesKey("show_nearby_restaurants")
    val showNearby: Flow<Boolean> = context.recommendationsStore.data.map { it[key] ?: true }
    suspend fun setShowNearby(enabled: Boolean) {
        context.recommendationsStore.edit { it[key] = enabled }
    }
}
