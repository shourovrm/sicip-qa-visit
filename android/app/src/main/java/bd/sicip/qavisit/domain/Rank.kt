// team leaderboard: officers ranked by total scored points (Scoring.points), ties handled by
// competition ranking. pure kotlin, no android deps -- same rule as Scoring.kt/TripMath.kt.
package bd.sicip.qavisit.domain

import java.time.LocalDate

// minimal shape rank needs from an officer -- keeps this file free of the Room Officer entity.
data class RankOfficer(val id: String, val name: String)

data class RankRow(val officerId: String, val name: String, val points: Int, val position: Int)

// competition ranking (1,2,2,4): tied officers share one position, and the next distinct score
// jumps straight past however many rows tied above it -- two people tied for 2nd push the next
// row to 4th, never renumbered down to a false 3rd. (the alternative, dense ranking / 1,2,2,3,
// is what medal tables use; a standings list reads more like sport-style competition ranking.)
// falls out for free from a plain sort: a row's position is just "index of its first occurrence + 1".
fun rank(officers: List<RankOfficer>, visits: List<VisitScore>): List<RankRow> {
    val pointsByOfficer = visits.filterNot { it.deleted }
        .groupBy { it.officerId }
        .mapValues { (_, rows) -> rows.sumOf { points(it.category) } }

    val sorted = officers
        .map { it to (pointsByOfficer[it.id] ?: 0) }
        .sortedWith(compareByDescending<Pair<RankOfficer, Int>> { it.second }.thenBy { it.first.name })

    var position = 0
    var lastPoints: Int? = null
    return sorted.mapIndexed { index, (officer, pts) ->
        if (pts != lastPoints) position = index + 1
        lastPoints = pts
        RankRow(officer.id, officer.name, pts, position)
    }
}

// "Last month" rank snapshot cutoff: the last day of the month before `today`. Jan rolls back
// into Dec 31 of the prior year; every other month lands on its own last day (28/29/30/31) --
// withDayOfMonth(1).minusDays(1) gets both for free, leap Feb included, no manual month-length table.
fun lastDayOfPreviousMonth(today: LocalDate): LocalDate = today.withDayOfMonth(1).minusDays(1)
