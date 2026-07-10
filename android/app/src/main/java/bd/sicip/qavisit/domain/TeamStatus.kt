// Team screen's per-officer status: on-visit beats on-leave beats in-office.
// pure kotlin, no android deps -- same rule as Scoring.kt/TripMath.kt/Rank.kt.
package bd.sicip.qavisit.domain

import java.time.LocalDate

// minimal shapes teamStatus needs -- keeps this file free of the Room Trip/Leave entities.
data class TripFlag(val status: String, val deleted: Boolean, val startedAt: String)
data class LeaveFlag(
    val type: String,
    val status: String,
    val deleted: Boolean,
    val startDate: String,
    val endDate: String,
)

sealed class TeamStatus {
    data class OnVisit(val since: String) : TeamStatus()
    data class OnLeave(val type: String, val until: String) : TeamStatus()
    object InOffice : TeamStatus()
}

// one officer's derived status. `today` boundary is inclusive both ends (start <= today <= end),
// mirroring LeaveDao.overlapping's sql. an active trip always wins over an overlapping leave --
// can't be both, but if the data's ever inconsistent, "on visit" is the more useful signal.
fun teamStatus(
    trips: List<TripFlag>,
    leaves: List<LeaveFlag>,
    today: LocalDate = LocalDate.now(),
): TeamStatus {
    val activeTrip = trips.firstOrNull { it.status == "active" && !it.deleted }
    if (activeTrip != null) return TeamStatus.OnVisit(since = activeTrip.startedAt.take(10))

    val activeLeave = leaves.firstOrNull { leave ->
        !leave.deleted && leave.status != "cancelled" &&
            !LocalDate.parse(leave.startDate).isAfter(today) &&
            !LocalDate.parse(leave.endDate).isBefore(today)
    }
    if (activeLeave != null) return TeamStatus.OnLeave(type = activeLeave.type, until = activeLeave.endDate)

    return TeamStatus.InOffice
}
