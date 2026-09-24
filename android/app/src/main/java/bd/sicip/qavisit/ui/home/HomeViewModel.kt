// home screen state: active trip (+ its visits/legs), upcoming scheduled visits, points/rank.
// plain class, no hilt/viewmodel-ktx -- screen owns it via `remember` and collects `state` as
// a Flow (collectAsState), so no manual refresh() call is needed: Room's Flow queries emit a
// fresh snapshot whenever a write (local edit or background SyncWorker pull) touches a table
// this screen reads, and the UI recomposes in place.
package bd.sicip.qavisit.ui.home

import android.content.Context
import bd.sicip.qavisit.BuildConfig
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.db.Report
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.data.remote.fetchAppMeta
import bd.sicip.qavisit.domain.MonthVisit
import bd.sicip.qavisit.domain.VisitScore
import bd.sicip.qavisit.domain.isNewer
import bd.sicip.qavisit.domain.monthSummary
import bd.sicip.qavisit.domain.rank
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportProgress
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.computeProgress
import bd.sicip.qavisit.ui.reports.surpriseTemplate
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

// one active tour visit's report line (B1 mockup): present only once a report exists for that
// visit -- a visit with none just shows "Start report" (HomeScreen decides that from absence,
// not from this class).
data class VisitReportInfo(val report: Report, val progress: ReportProgress)

data class HomeUiState(
    val loading: Boolean = true,
    val activeTrip: Trip? = null,
    val activeTripVisits: List<Visit> = emptyList(),
    val activeTripLegs: List<TravelLeg> = emptyList(),
    val activeTripReports: Map<String, VisitReportInfo> = emptyMap(), // keyed by visit id
    val upcoming: List<Visit> = emptyList(),
    val myPoints: Int = 0,
    val myRank: Int = 0,
    val officerCount: Int = 0,
    val myVisitCount: Int = 0,
    val monthVisitCount: Int = 0,
    val monthPoints: Int = 0,
)

// grouping just the 5-combine's raw inputs before the reports flow joins them below --
// kotlinx-coroutines' combine() only goes up to 5 flows of possibly-different types, and this
// screen now needs 6, so the base 5 nest inside one outer combine with the reports flow.
private data class HomeBase(
    val visits: List<Visit>,
    val legs: List<TravelLeg>,
    val myVisits: List<Visit>,
    val allVisits: List<Visit>,
    val officers: List<Officer>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val officerId: String,
    private val db: AppDb,
    private val sessionStore: SessionStore? = null,
    private val client: SupabaseClient = SupabaseClient(),
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    context: Context? = null,
) {
    // used only to read the packaged report template for the ongoing-visit progress line below;
    // null (e.g. a future non-UI test harness) just means that line never appears, home still works.
    private val reportTemplate: ReportTemplate? = context?.let { runCatching { surpriseTemplate(it) }.getOrNull() }

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
        // reactive so a background sync (or an edit in TravelsSheet) recomposes the hero's
        // travel count/fare and the sheet's own list, no manual reload needed.
        val tripLegs = trip?.let { db.travelLegDao().byTripFlow(it.id) } ?: flowOf(emptyList())
        val base = combine(
            tripVisits,
            tripLegs,
            db.visitDao().byOfficerFlow(officerId),
            db.visitDao().allFlow(),
            db.officerDao().allFlow(),
        ) { visits, legs, myVisits, allVisits, officers -> HomeBase(visits, legs, myVisits, allVisits, officers) }

        // reports join separately (see HomeBase's own comment) -- keyed by visit id so the
        // ONGOING row lookup below is O(1) per visit.
        combine(base, db.reportDao().byOfficerFlow(officerId)) { b, reports ->
            val (visits, legs, myVisits, allVisits, officers) = b
            // scheduled visits already attached to the running tour show in ONGOING instead --
            // exclude them here so they don't double-list.
            // exclude only visits attached to the ACTIVE tour (they show under ONGOING); when no
            // tour is active trip?.id is null and a null-tripId visit must still be upcoming
            val upcoming = myVisits.filter { it.status == "scheduled" && (trip == null || it.tripId != trip.id) }.sortedBy { it.startDate }

            val scores = allVisits.map { VisitScore(it.officerId, it.category, it.deleted, it.status == "done") }
            val ranked = rank(scores)
            val officerIds = officers.map { it.id }
            // ascending order: fewest points = #1, so officers with zero points (never show up
            // in `rank`'s groupBy) go FIRST, not last.
            val ordered = officerIds.filterNot { id -> ranked.any { it.first == id } } + ranked.map { it.first }

            val (monthVisitCount, monthPoints) = monthSummary(
                myVisits.map { MonthVisit(it.startDate, it.category, it.deleted, it.status == "done") },
                YearMonth.now().toString(),
            )

            // B1: "Surprise report · x of N" + flag chip on each ONGOING visit row. only the
            // active tour's own visits need this (upcoming/other rows don't show a report line).
            val template = reportTemplate
            val activeTripReports = if (template != null) {
                visits.mapNotNull { v ->
                    val report = reports.firstOrNull { it.visitId == v.id } ?: return@mapNotNull null
                    v.id to VisitReportInfo(report, computeProgress(template, ReportData.parse(report.data)))
                }.toMap()
            } else {
                emptyMap()
            }

            HomeUiState(
                loading = false,
                activeTrip = trip,
                activeTripVisits = visits,
                activeTripLegs = legs,
                activeTripReports = activeTripReports,
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
