// home screen state: active trip (+ its visits/legs), upcoming scheduled visits, points/rank.
// plain class, no hilt/viewmodel-ktx -- screen owns it via `remember` and collects `state` as
// a Flow (collectAsState), so no manual refresh() call is needed: Room's Flow queries emit a
// fresh snapshot whenever a write (local edit or background SyncWorker pull) touches a table
// this screen reads, and the UI recomposes in place.
package bd.sicip.qavisit.ui.home

import bd.sicip.qavisit.BuildConfig
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.data.remote.fetchAppMeta
import bd.sicip.qavisit.domain.MonthVisit
import bd.sicip.qavisit.domain.VisitScore
import bd.sicip.qavisit.domain.isNewer
import bd.sicip.qavisit.domain.monthSummary
import bd.sicip.qavisit.domain.rank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.YearMonth

data class UpdateNotice(val latestVersion: String, val apkUrl: String)

data class HomeUiState(
    val loading: Boolean = true,
    val activeTrip: Trip? = null,
    val activeTripVisits: List<Visit> = emptyList(),
    val upcoming: List<Visit> = emptyList(),
    val myPoints: Int = 0,
    val myRank: Int = 0,
    val officerCount: Int = 0,
    val myVisitCount: Int = 0,
    val monthVisitCount: Int = 0,
    val monthPoints: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val officerId: String,
    private val db: AppDb,
    private val sessionStore: SessionStore? = null,
    private val client: SupabaseClient = SupabaseClient(),
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {
    // not an androidx ViewModel (see file header), so it owns its own scope for the
    // fire-and-forget update check below. no cancellation needed -- one cheap single-row
    // select that either finishes or is silently dropped with the composable's `remember`.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _updateNotice = MutableStateFlow<UpdateNotice?>(null)
    val updateNotice: StateFlow<UpdateNotice?> = _updateNotice.asStateFlow()

    init {
        val store = sessionStore
        if (store != null) {
            scope.launch {
                try {
                    val token = store.current()?.accessToken ?: return@launch
                    val meta = client.fetchAppMeta(token)
                    if (isNewer(meta.latestVersion, currentVersion) && meta.apkUrl.isNotBlank()) {
                        _updateNotice.value = UpdateNotice(meta.latestVersion, meta.apkUrl)
                    }
                } catch (e: Exception) {
                    // offline (IOException) or any other hiccup (expired token, bad json) --
                    // no banner this load, next Home visit tries again. never worth surfacing.
                }
            }
        }
    }

    fun dismissUpdateNotice() {
        _updateNotice.value = null
    }

    val state: Flow<HomeUiState> = db.tripDao().activeTripFlow(officerId).flatMapLatest { trip ->
        val tripVisits = trip?.let { db.visitDao().byTripFlow(it.id) } ?: flowOf(emptyList())
        combine(
            tripVisits,
            db.visitDao().byOfficerFlow(officerId),
            db.visitDao().allFlow(),
            db.officerDao().allFlow(),
        ) { visits, myVisits, allVisits, officers ->
            // scheduled visits already attached to the running tour show in ONGOING instead --
            // exclude them here so they don't double-list.
            val upcoming = myVisits.filter { it.status == "scheduled" && it.tripId != trip?.id }.sortedBy { it.startDate }

            val scores = allVisits.map { VisitScore(it.officerId, it.category, it.deleted) }
            val ranked = rank(scores)
            val officerIds = officers.map { it.id }
            // officers with zero points never show up in `rank`'s groupBy -- give them a
            // last-place slot instead of dropping them off the leaderboard entirely.
            val ordered = ranked.map { it.first } + officerIds.filterNot { id -> ranked.any { it.first == id } }

            val (monthVisitCount, monthPoints) = monthSummary(
                myVisits.map { MonthVisit(it.startDate, it.category, it.deleted) },
                YearMonth.now().toString(),
            )

            HomeUiState(
                loading = false,
                activeTrip = trip,
                activeTripVisits = visits,
                upcoming = upcoming,
                myPoints = ranked.firstOrNull { it.first == officerId }?.second ?: 0,
                myRank = (ordered.indexOf(officerId) + 1).coerceAtLeast(1),
                officerCount = officerIds.size,
                myVisitCount = myVisits.size,
                monthVisitCount = monthVisitCount,
                monthPoints = monthPoints,
            )
        }
    }
}
