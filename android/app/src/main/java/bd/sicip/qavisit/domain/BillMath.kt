// TA/DA bill math: night-stay/food-day span totals, TA sum, amount in words.
// pure kotlin, no android deps.
package bd.sicip.qavisit.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// one itinerary row; depDate used only for per-leg day-grouping defaults
data class Leg(val fare: Double, val depDate: String = "")

data class BillTotals(val ta: Double, val accommodation: Double, val food: Double, val net: Double)

private fun spanDays(startDate: String, endDate: String): Long =
    ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1

// nights away = span days minus the last (no-stay) day
fun tripNights(startDate: String, endDate: String): Int = (spanDays(startDate, endDate) - 1).toInt()

// full day for every day except the last travel day, which is half
fun tripFoodDays(startDate: String, endDate: String): Double = (spanDays(startDate, endDate) - 1) + 0.5

fun ta(legs: List<Leg>): Double = legs.sumOf { it.fare }

fun billTotals(legs: List<Leg>, startDate: String, endDate: String): BillTotals {
    val taTotal = ta(legs)
    val accommodation = tripNights(startDate, endDate) * 2000.0
    val food = tripFoodDays(startDate, endDate) * 1500.0
    return BillTotals(taTotal, accommodation, food, taTotal + accommodation + food)
}

// per-leg (nightStay, foodDay) defaults for the itinerary preview, day-grouped like the
// official bill: only the first leg of a calendar day carries that day's value, so summing
// them double-counts nothing; last day of the trip is halved/zeroed.
fun legDefaults(legs: List<Leg>, startDate: String, endDate: String): List<Pair<Int, Double>> {
    val seenDays = mutableSetOf<String>()
    return legs.map { leg ->
        if (!seenDays.add(leg.depDate)) return@map 0 to 0.0
        if (leg.depDate == endDate) 0 to 0.5 else 1 to 1.0
    }
}

private val ONES = arrayOf(
    "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
    "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
)
private val TENS = arrayOf(
    "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety",
)

// 0..99 in words; empty string for 0 (caller decides whether to show it)
private fun twoDigitWords(n: Long): String = when {
    n == 0L -> ""
    n < 20 -> ONES[n.toInt()]
    n % 10 == 0L -> TENS[(n / 10).toInt()]
    else -> "${TENS[(n / 10).toInt()]} ${ONES[(n % 10).toInt()]}"
}

// BD-style numbering: crore (1e7), lakh (1e5), thousand (1e3), hundred (1e2).
// ponytail: crore group capped at 2 digits (up to 99 crore) — plenty for a TA/DA bill.
fun amountInWords(net: Long): String {
    if (net == 0L) return "Zero"
    var n = net
    val crore = n / 10_000_000; n %= 10_000_000
    val lakh = n / 100_000; n %= 100_000
    val thousand = n / 1_000; n %= 1_000
    val hundred = n / 100; n %= 100
    val rest = n

    val parts = mutableListOf<String>()
    if (crore > 0) parts += "${twoDigitWords(crore)} Crore"
    if (lakh > 0) parts += "${twoDigitWords(lakh)} Lakh"
    if (thousand > 0) parts += "${twoDigitWords(thousand)} Thousand"
    if (hundred > 0) parts += "${ONES[hundred.toInt()]} Hundred"
    if (rest > 0) parts += twoDigitWords(rest)
    return parts.joinToString(" ")
}
