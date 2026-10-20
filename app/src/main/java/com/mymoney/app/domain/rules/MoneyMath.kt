package com.mymoney.app.domain.rules

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Money parsing/formatting helpers. Parsing uses BigDecimal (never
 * float/double arithmetic on money). All amounts are stored as Long cents.
 */
object MoneyMath {

    /**
     * Parses user/CSV input into cents. Accepts:
     *  "1234.56", "1234,56", "1.234,56" (pt grouping), "1,234.56" (en
     *  grouping), "R$ 1.234,56", "(50,00)" or "-50" for negatives.
     * Returns null when the value is not a valid amount.
     */
    fun parseToCents(raw: String): Long? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"))
        s = s.removePrefix("-").removePrefix("(").removeSuffix(")")
        // strip currency symbols / spaces
        s = s.replace("R$", "").replace("$", "").replace(" ", "").replace("\u00A0", "")
        if (s.isEmpty()) return null
        for (c in s) if (!(c.isDigit() || c == '.' || c == ',')) return null

        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalSep: Char? = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot >= 0 || lastComma >= 0 -> {
                // A single separator type: treat as decimal only when it appears once
                // and the trailing group length != 3 (otherwise thousands grouping).
                val sep = if (lastDot >= 0) '.' else ','
                val count = s.count { it == sep }
                val tail = s.substring((if (sep == '.') lastDot else lastComma) + 1)
                if (count == 1 && tail.length != 3) sep else null
            }
            else -> null
        }
        val cleaned = StringBuilder()
        for (c in s) {
            if (c.isDigit()) cleaned.append(c)
            else if (decimalSep != null && c == decimalSep) cleaned.append('.')
            // other separators are thousands grouping → drop
        }
        val text = cleaned.toString()
        if (text.isEmpty() || text == ".") return null
        val bd = try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            return null
        }
        val cents = try {
            bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            return null
        }
        return if (negative) -cents else cents
    }

    /** Formats cents as plain decimal major units (deterministic; used by CSV export & tests). */
    fun formatPlain(cents: Long, showCents: Boolean, decimalComma: Boolean): String {
        val abs = if (cents < 0) -cents else cents
        val sign = if (cents < 0) "-" else ""
        val whole = abs / 100
        val frac = abs % 100
        val groupedWhole = groupThousands(whole, decimalComma)
        return if (showCents) {
            val sep = if (decimalComma) "," else "."
            "$sign$groupedWhole$sep${frac.toString().padStart(2, '0')}"
        } else {
            sign + groupedWhole
        }
    }

    private fun groupThousands(v: Long, decimalComma: Boolean): String {
        val s = v.toString()
        val sb = StringBuilder()
        val g = if (decimalComma) '.' else ','
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(g)
            sb.append(c)
        }
        return sb.toString()
    }

    fun formatDate(epochDay: Long, pattern: String, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(pattern, locale))
}
