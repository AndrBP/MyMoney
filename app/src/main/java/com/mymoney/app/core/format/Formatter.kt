package com.mymoney.app.core.format

import android.content.Context
import com.mymoney.app.data.settings.SettingsRepository
import com.mymoney.app.domain.rules.MoneyMath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

enum class DateFormatPattern(val apiId: String, val pattern: String) {
    DD_MM_YYYY("dd/MM/yyyy", "dd/MM/yyyy"),
    MM_DD_YYYY("MM/dd/yyyy", "MM/dd/yyyy"),
    YYYY_MM_DD("yyyy-MM-dd", "yyyy-MM-dd");

    companion object {
        fun fromId(id: String?): DateFormatPattern = entries.firstOrNull { it.apiId == id } ?: DD_MM_YYYY
    }
}

/**
 * Central formatter for money and dates, honoring user preferences.
 * Currency formatting uses java.text.NumberFormat with the configured
 * currency code (BRL by default) — no external services involved.
 */
@Singleton
class Formatter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun money(cents: Long): String {
        val s = settingsRepository.current()
        return moneySync(cents, s.currencyCode, s.showCents)
    }

    fun moneySync(cents: Long, currencyCode: String, showCents: Boolean): String {
        val nf = currencyFormat(currencyCode)
        if (!showCents) {
            nf.maximumFractionDigits = 0
            nf.minimumFractionDigits = 0
            return nf.format(Math.round(cents / 100.0))
        }
        return nf.format(cents / 100.0)
    }

    private fun currencyFormat(code: String): NumberFormat {
        val nf = NumberFormat.getCurrencyInstance(locale())
        nf.currency = java.util.Currency.getInstance(if (code.isBlank()) "BRL" else code)
        return nf
    }

    private fun locale(): Locale = context.resources.configuration.locales[0]

    suspend fun date(epochDay: Long): String {
        val s = settingsRepository.current()
        return dateSync(epochDay, s.dateFormat)
    }

    fun dateSync(epochDay: Long, fmt: DateFormatPattern): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(fmt.pattern, locale()))

    companion object {
        /** Locale-independent parse used by CSV import & quick forms. */
        fun parseAmount(raw: String): Long? = MoneyMath.parseToCents(raw)
    }
}
