package com.provisionstore.app.util

import kotlin.math.abs

// Quantities are stored in kg or litres. These helpers show and enter them in grams or
// millilitres, which is how shopkeepers and customers think about them.
// Typing "250" for a kg item means 250 g.

private fun smallUnit(unit: String): String? = when (unit) {
    "kg" -> "g"
    "litre" -> "ml"
    else -> null
}

/** The unit to type in: g for kg, ml for litre, otherwise the unit itself. */
fun inputUnit(unit: String): String = smallUnit(unit) ?: unit

/** Units offered when creating an item. */
fun unitChoices(): List<String> = listOf("kg", "litre", "pc", "packet", "dozen")

/** A stored quantity as the number to show in an input box. 0.25 kg becomes "250". */
fun Double.toEntry(unit: String): String =
    if (smallUnit(unit) == null) qty() else (this * 1000).qty()

/** A typed number as the stored quantity. "250" for kg becomes 0.25. */
fun parseEntry(text: String, unit: String): Double? {
    val value = parseQty(text) ?: return null
    return if (smallUnit(unit) == null) value else value / 1000
}

/** A stored quantity for display. Under 1 kg (or 1 litre) it shows grams (or ml). */
fun Double.stockText(unit: String): String {
    val small = smallUnit(unit)
    return if (small != null && abs(this) < 1.0) {
        "${(this * 1000).qty()} $small"
    } else {
        "${qty()} $unit"
    }
}

/** A price for display, for example "₹120.00 per kg". */
fun Long.perUnit(unit: String): String = "${money()} per $unit"

/** Common amounts shown as buttons. Empty for counted units such as pc. */
fun quickAmounts(unit: String): List<Pair<String, Double>> = when (unit) {
    "kg" -> listOf("50 g" to 0.05, "100 g" to 0.1, "250 g" to 0.25, "500 g" to 0.5, "1 kg" to 1.0)
    "litre" -> listOf("100 ml" to 0.1, "250 ml" to 0.25, "500 ml" to 0.5, "1 litre" to 1.0)
    else -> emptyList()
}
