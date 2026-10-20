package com.mymoney.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mymoney.app.core.format.AppThemeMode
import com.mymoney.app.core.format.DateFormatPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val currencyCode: String = "BRL",
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val dateFormat: DateFormatPattern = DateFormatPattern.DD_MM_YYYY,
    val showCents: Boolean = true,
    val pinEnabled: Boolean = false,
    val biometryEnabled: Boolean = false,
    val autoGeneratePlanned: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mymoney_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val CURRENCY = stringPreferencesKey("currency")
        val FIRST_DAY = intPreferencesKey("first_day_of_week")
        val DATE_FMT = stringPreferencesKey("date_format")
        val SHOW_CENTS = booleanPreferencesKey("show_cents")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val BIOMETRY = booleanPreferencesKey("biometry_enabled")
        val AUTO_PLANNED = booleanPreferencesKey("auto_generate_planned")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        // Security material (never the PIN itself): PBKDF2 salt + hash, base64.
        val PIN_SALT_B64 = stringPreferencesKey("pin_salt_b64")
        val PIN_HASH_B64 = stringPreferencesKey("pin_hash_b64")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { AppThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(AppThemeMode.SYSTEM),
            currencyCode = p[Keys.CURRENCY] ?: "BRL",
            firstDayOfWeek = DayOfWeek.of(p[Keys.FIRST_DAY] ?: 1),
            dateFormat = DateFormatPattern.fromId(p[Keys.DATE_FMT]),
            showCents = p[Keys.SHOW_CENTS] ?: true,
            pinEnabled = p[Keys.PIN_ENABLED] ?: false,
            biometryEnabled = p[Keys.BIOMETRY] ?: false,
            autoGeneratePlanned = p[Keys.AUTO_PLANNED] ?: true,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
        )
    }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun setTheme(mode: AppThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setCurrency(code: String) = edit { it[Keys.CURRENCY] = code }
    suspend fun setFirstDayOfWeek(day: DayOfWeek) = edit { it[Keys.FIRST_DAY] = day.value }
    suspend fun setDateFormat(fmt: DateFormatPattern) = edit { it[Keys.DATE_FMT] = fmt.apiId }
    suspend fun setShowCents(v: Boolean) = edit { it[Keys.SHOW_CENTS] = v }
    suspend fun setBiometry(v: Boolean) = edit { it[Keys.BIOMETRY] = v }
    suspend fun setAutoGeneratePlanned(v: Boolean) = edit { it[Keys.AUTO_PLANNED] = v }
    suspend fun setNotifications(v: Boolean) = edit { it[Keys.NOTIFICATIONS] = v }
    suspend fun setOnboardingDone(v: Boolean) = edit { it[Keys.ONBOARDING_DONE] = v }

    suspend fun setPin(pinHashB64: String, saltB64: String) = edit {
        it[Keys.PIN_HASH_B64] = pinHashB64
        it[Keys.PIN_SALT_B64] = saltB64
        it[Keys.PIN_ENABLED] = true
    }

    suspend fun clearPin() = edit {
        it.remove(Keys.PIN_HASH_B64)
        it.remove(Keys.PIN_SALT_B64)
        it[Keys.PIN_ENABLED] = false
        it[Keys.BIOMETRY] = false
    }

    /** Returns stored (hash, salt) base64 pair or null when no PIN exists. */
    suspend fun pinMaterial(): Pair<String, String>? {
        val p = context.dataStore.data.first()
        val h = p[Keys.PIN_HASH_B64] ?: return null
        val s = p[Keys.PIN_SALT_B64] ?: return null
        return h to s
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
