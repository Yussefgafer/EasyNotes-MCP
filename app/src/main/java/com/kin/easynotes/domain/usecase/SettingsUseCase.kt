package com.kin.easynotes.domain.usecase

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kin.easynotes.core.constant.ConnectionConst
import com.kin.easynotes.domain.repository.SettingsRepository
import com.kin.easynotes.domain.model.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SettingsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val TAG = "SettingsUseCase"

    suspend fun loadSettingsFromRepository(): Settings {
        val settings = Settings()
        Settings::class.java.declaredFields.forEach { field ->
            try {
                field.isAccessible = true
                val settingName = field.name
                if (settingName.contains("$")) return@forEach
                
                val defaultValue = field.get(settings)
                val settingValue = getSettingValue(field.type, settingName, defaultValue)
                field.set(settings, settingValue)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading field ${field.name}", e)
            }
        }
        return settings
    }

    private suspend fun getSettingValue(fieldType: Class<*>, settingName: String, defaultValue: Any?): Any? {
        return try {
            when (fieldType) {
                Boolean::class.java -> settingsRepository.getBoolean(settingName) ?: defaultValue
                String::class.java -> settingsRepository.getString(settingName) ?: defaultValue
                Int::class.java -> settingsRepository.getInt(settingName) ?: defaultValue
                else -> defaultValue
            }
        } catch (e: ClassCastException) {
            Log.e(TAG, "Corrupted preference: $settingName", e)
            defaultValue
        }
    }

    suspend fun saveSettingsToRepository(settings: Settings) {
        Log.d(TAG, "Saving settings to repository... current mcpEnabled=${settings.mcpEnabled}")
        Settings::class.java.declaredFields.forEach { field ->
            try {
                field.isAccessible = true
                val settingName = field.name
                if (settingName.contains("$")) return@forEach
                
                val settingValue = field.get(settings)
                saveSettingValue(settingName, settingValue)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving field ${field.name}", e)
            }
        }
    }

    private suspend fun saveSettingValue(settingName: String, settingValue: Any?) {
        when (settingValue) {
            is Boolean -> settingsRepository.putBoolean(settingName, settingValue)
            is String -> settingsRepository.putString(settingName, settingValue)
            is Int -> settingsRepository.putInt(settingName, settingValue)
            null -> { /* Don't remove keys if null during mass save */ }
        }
    }
}
