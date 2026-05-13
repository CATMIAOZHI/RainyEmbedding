package com.rainyembedding.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用偏好设置（DataStore 持久化）
 */
class AppPreferences(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("rainyembedding_settings")

        val KEY_PORT = intPreferencesKey("server_port")
        val KEY_ACCELERATOR = stringPreferencesKey("accelerator")
        val KEY_OUTPUT_DIMENSION = intPreferencesKey("output_dimension")
        val KEY_IDLE_TIMEOUT_MIN = intPreferencesKey("idle_timeout_min")
        val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        val KEY_KEEP_ALIVE = booleanPreferencesKey("keep_alive")
        val KEY_HF_TOKEN = stringPreferencesKey("hf_token")
    }

    val serverPort: Flow<Int> = context.dataStore.data.map { it[KEY_PORT] ?: 8081 }
    val accelerator: Flow<String> = context.dataStore.data.map { it[KEY_ACCELERATOR] ?: "cpu" }
    val outputDimension: Flow<Int> = context.dataStore.data.map { it[KEY_OUTPUT_DIMENSION] ?: 128 }
    val idleTimeoutMin: Flow<Int> = context.dataStore.data.map { it[KEY_IDLE_TIMEOUT_MIN] ?: 5 }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_SELECTED_MODEL] ?: "embeddinggemma-300m" }
    val keepAlive: Flow<Boolean> = context.dataStore.data.map { it[KEY_KEEP_ALIVE] ?: true }
    val hfToken: Flow<String> = context.dataStore.data.map { it[KEY_HF_TOKEN] ?: "" }

    suspend fun setServerPort(port: Int) { context.dataStore.edit { it[KEY_PORT] = port } }
    suspend fun setAccelerator(acc: String) { context.dataStore.edit { it[KEY_ACCELERATOR] = acc } }
    suspend fun setOutputDimension(dim: Int) { context.dataStore.edit { it[KEY_OUTPUT_DIMENSION] = dim } }
    suspend fun setIdleTimeoutMin(min: Int) { context.dataStore.edit { it[KEY_IDLE_TIMEOUT_MIN] = min } }
    suspend fun setSelectedModel(model: String) { context.dataStore.edit { it[KEY_SELECTED_MODEL] = model } }
    suspend fun setKeepAlive(on: Boolean) { context.dataStore.edit { it[KEY_KEEP_ALIVE] = on } }
    suspend fun setHfToken(token: String) { context.dataStore.edit { it[KEY_HF_TOKEN] = token } }
}