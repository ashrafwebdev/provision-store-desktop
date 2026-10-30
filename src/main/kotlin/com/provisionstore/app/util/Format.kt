package com.provisionstore.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

const val DAY_MS = 24 * 60 * 60 * 1000L

/** Paise to a display string, for example 1250 becomes "₹12.50". */
fun Long.money(): String = "₹" + String.format(Locale.US, "%.2f", this / 100.0)

/** Paise to a plain number for edit fields, for example 1250 becomes "12.50". */
fun Long.moneyInput(): String = String.format(Locale.US, "%.2f", this / 100.0)

/** Drops trailing zeros: 2.0 becomes "2", 1.250 becomes "1.25". */
fun Double.qty(): String =
    if (this % 1.0 == 0.0) toLong().toString()
    else String.format(Locale.US, "%.3f", this).trimEnd('0').trimEnd('.')

fun parseMoney(text: String): Long? =
    text.trim().toDoubleOrNull()?.let { (it * 100).roundToLong() }

fun parseQty(text: String): Double? = text.trim().toDoubleOrNull()

fun dateTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(millis)

fun timeOnly(millis: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(millis)

/** "Today" for the current day, otherwise the full date. */
fun dayLabel(dayStart: Long): String =
    if (dayStart == startOfToday()) {
        "Today"
    } else {
        SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(dayStart)
    }

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
