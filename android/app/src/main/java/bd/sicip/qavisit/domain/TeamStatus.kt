// Team screen's per-officer status: on-visit beats in-office.
// pure kotlin, no android deps -- same rule as Scoring.kt/TripMath.kt/Rank.kt.
package bd.sicip.qavisit.domain

// minimal shape teamStatus needs -- keeps this file free of the Room Trip entity.
data class TripFlag(val status: String, val deleted: Boolean, val startedAt: String)

sealed class TeamStatus {
    data class OnVisit(val since: String) : TeamStatus()
    object InOffice : TeamStatus()
}

// one officer's derived status. an active trip means "on visit", anything else "in office".
fun teamStatus(trips: List<TripFlag>): TeamStatus {
    val activeTrip = trips.firstOrNull { it.status == "active" && !it.deleted }
    if (activeTrip != null) return TeamStatus.OnVisit(since = activeTrip.startedAt.take(10))

    return TeamStatus.InOffice
}
