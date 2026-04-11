package com.finalis.mobile.app

import java.math.BigDecimal
import java.math.RoundingMode

private const val FINALIS_DECIMALS = 8

private fun canonicalizeFinalisAmount(raw: String): String = raw.trim().replace(',', '.')

fun formatFinalisUnits(units: Long): String =
    BigDecimal.valueOf(units, FINALIS_DECIMALS).setScale(FINALIS_DECIMALS, RoundingMode.DOWN).toPlainString()

fun formatFinalisAmountLabel(units: Long): String = "${formatFinalisUnits(units)} FLS"

fun parseFinalisUnits(raw: String): Long {
    val normalized = canonicalizeFinalisAmount(raw)
    require(normalized.isNotEmpty()) { "Amount is required" }
    val decimal = normalized.toBigDecimalOrNull()
        ?: throw IllegalArgumentException("Amount must be a valid number")
    require(decimal > BigDecimal.ZERO) { "Amount must be positive" }
    require(decimal.scale() <= FINALIS_DECIMALS) { "Amount supports up to 8 decimal places" }
    val shifted = decimal.movePointRight(FINALIS_DECIMALS)
    require(shifted.stripTrailingZeros().scale() <= 0) { "Amount supports up to 8 decimal places" }
    return try {
        shifted.longValueExact()
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("Amount is too large")
    }
}
