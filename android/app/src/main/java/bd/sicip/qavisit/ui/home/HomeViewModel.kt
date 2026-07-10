// home screen state: active trip (+ its visits/legs), upcoming scheduled visits, points/rank.
// plain class, no hilt/viewmodel-ktx -- screen owns it via `remember` and collects `state` as
// a Flow (collectAsState), so no manual refresh() call is needed: Room's Flow queries emit a
// fresh snapshot whenever a write (local edit or background SyncWorker pull) touches a table
// this screen reads, and the UI recomposes in place.
package bd.sicip.qavisit.ui.home

import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.domain.VisitScore
import bd.sicip.qavisit.domain.rank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val officerId: String, private val db: AppDb) {
    val state: Flow<HomeUiState> = db.tripDao().activeTripFlow(officerId).flatMapLatest { trip ->
        val tripVisits = trip?.let { db.visitDao().byTripFlow(it.id) } ?: flowOf(emptyList())
        val tripLegs = trip?.let { db.travelLegDao().byTripFlow(it.id) } ?: flowOf(emptyList())
        combine(
            tripVisits,
            tripLegs,
            db.visitDao().byOfficerFlow(officerId),
            db.visitDao().allFlow(),
            db.officerDao().allFlow(),
        ) { visits, legs, myVisits, allVisits, officers ->
            val upcoming = myVisits.filter { it.status == "scheduled" }.sortedBy { it.startDate }

            val scores = allVisits.map { VisitScore(it.officerId, it.category, it.deleted) }
            val ranked = rank(scores)
            val officerIds = officers.map { it.id }
            // officers with zero points never show up in `rank`'s groupBy -- give them a
            // last-place slot instead of dropping them off the leaderboard entirely.
            val ordered = ranked.map { it.first } + officerIds.filterNot { id -> ranked.any { it.first == id } }

            HomeUiState(
                loading = false,
                activeTrip = trip,
                activeTripVisits = visits,
                activeTripLegs = legs,
                upcoming = upcoming,
                myPoints = ranked.firstOrNull { it.first == officerId }?.second ?: 0,
                myRank = (ordered.indexOf(officerId) + 1).coerceAtLeast(1),
                officerCount = officerIds.size,
                myVisitCount = myVisits.size,
            )
        }
    }
}
