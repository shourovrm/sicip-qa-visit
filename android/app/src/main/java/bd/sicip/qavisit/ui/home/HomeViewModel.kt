// home screen state: active trip (+ its visits/legs), upcoming scheduled visits, points/rank.
// plain class, no hilt/viewmodel-ktx -- screen owns it via `remember` and calls refresh().
package bd.sicip.qavisit.ui.home

import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.domain.VisitScore
import bd.sicip.qavisit.domain.rank
import kotlinx.coroutines.flow.MutableStateFlow

data class HomeUiState(
    val loading: Boolean = true,
    val activeTrip: Trip? = null,
    val activeTripVisits: List<Visit> = emptyList(),
    val activeTripLegs: List<TravelLeg> = emptyList(),
    val upcoming: List<Visit> = emptyList(),
    val myPoints: Int = 0,
    val myRank: Int = 0,
    val officerCount: Int = 0,
    val myVisitCount: Int = 0,
)

class HomeViewModel(private val officerId: String, private val db: AppDb) {
    val state = MutableStateFlow(HomeUiState())

    suspend fun refresh() {
        val trip = db.tripDao().activeTrip(officerId)
        val tripVisits = trip?.let { db.visitDao().byTrip(it.id) } ?: emptyList()
        val tripLegs = trip?.let { db.travelLegDao().byTrip(it.id) } ?: emptyList()

        val myVisits = db.visitDao().byOfficer(officerId)
        val upcoming = myVisits.filter { it.status == "scheduled" }.sortedBy { it.startDate }

        val scores = db.visitDao().all().map { VisitScore(it.officerId, it.category, it.deleted) }
        val ranked = rank(scores)
        val officerIds = db.officerDao().all().map { it.id }
        // officers with zero points never show up in `rank`'s groupBy -- give them a last-place
        // slot instead of dropping them off the leaderboard entirely.
        val ordered = ranked.map { it.first } + officerIds.filterNot { id -> ranked.any { it.first == id } }

        state.value = HomeUiState(
            loading = false,
            activeTrip = trip,
            activeTripVisits = tripVisits,
            activeTripLegs = tripLegs,
            upcoming = upcoming,
            myPoints = ranked.firstOrNull { it.first == officerId }?.second ?: 0,
            myRank = (ordered.indexOf(officerId) + 1).coerceAtLeast(1),
            officerCount = officerIds.size,
            myVisitCount = myVisits.size,
        )
    }
}
