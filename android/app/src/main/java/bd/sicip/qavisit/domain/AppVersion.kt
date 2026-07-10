// semver-ish numeric compare for the update-notice banner. pure kotlin, no android deps --
// same rule as Scoring.kt/TripMath.kt/Rank.kt.
package bd.sicip.qavisit.domain

// true iff `latest` is a strictly newer version than `current`. compares dot-separated numeric
// segments left to right ("1.10.0" > "1.9.9"); a shorter string is padded with zero segments
// ("1.2" == "1.2.0"). either string failing to parse as all-numeric segments is treated as
// "can't tell" -> false, so garbage server data never shows a bogus banner.
fun isNewer(latest: String, current: String): Boolean {
    val l = latest.trim().split(".").map { it.toIntOrNull() }
    val c = current.trim().split(".").map { it.toIntOrNull() }
    if (l.any { it == null } || c.any { it == null } || l.isEmpty() || c.isEmpty()) return false

    val len = maxOf(l.size, c.size)
    for (i in 0 until len) {
        val lv = l.getOrElse(i) { 0 } ?: 0
        val cv = c.getOrElse(i) { 0 } ?: 0
        if (lv != cv) return lv > cv
    }
    return false
}
