// Team screen: two tabs -- Status (per-officer derived status: on tour / on leave / in office)
// and Rank (points leaderboard). Rank has its own Overall | Last month sub-filter: Overall is the
// current all-time rank, Last month re-runs the same rank() over a cumulative snapshot -- every
// visit that started on or before the last day of the previous month (e.g. 30 June when today is
// in July) -- so it reads "where the team stood as of last month," not just that month's visits.
package bd.sicip.qavisit.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Leave
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.domain.LeaveFlag
import bd.sicip.qavisit.domain.RankOfficer
import bd.sicip.qavisit.domain.RankRow
import bd.sicip.qavisit.domain.TeamStatus
import bd.sicip.qavisit.domain.TripFlag
import bd.sicip.qavisit.domain.VisitScore
import bd.sicip.qavisit.domain.lastDayOfPreviousMonth
import bd.sicip.qavisit.domain.primaryVisit
import bd.sicip.qavisit.domain.rank
import bd.sicip.qavisit.domain.teamStatus
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.common.TwoTabRow
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import java.time.LocalDate
import kotlinx.coroutines.flow.combine

private data class TeamRow(val officer: Officer, val status: TeamStatus, val subtitle: String?)
private data class TeamUiState(
    val rows: List<TeamRow> = emptyList(),
    val rankedOverall: List<RankRow> = emptyList(),
    val rankedLastMonth: List<RankRow> = emptyList(),
)

@Composable
fun TeamScreen(officerId: String, db: AppDb) {
    var statusTab by remember { mutableStateOf(true) }
    var rankOverall by remember { mutableStateOf(true) } // true = Overall, false = Last month
    val context = LocalContext.current

    // fresh tours/leaves land via sync (RLS read-all pulls every officer's rows) -- kick a pull
    // on entry so the status list doesn't wait for the next periodic sync.
    LaunchedEffect(Unit) { SyncNow.enqueue(context) }

    val today = remember { LocalDate.now() }
    val state by remember(db) {
        combine(
            db.officerDao().allFlow(), // dao already orders by name -- alphabetical for free
            db.tripDao().activeTripsFlow(),
            db.leaveDao().startedFlow(),
            db.visitDao().allFlow(),
        ) { officers, activeTrips, startedLeaves, allVisits ->
            teamUiState(officers, activeTrips, startedLeaves, allVisits, today)
        }
    }.collectAsState(initial = TeamUiState())

    Column(modifier = Modifier.fillMaxSize()) {
        TwoTabRow("Status", "Rank", statusTab, { statusTab = it })

        if (!statusTab) {
            TwoTabRow("Overall", "Last month", rankOverall, { rankOverall = it })
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (statusTab) {
                items(state.rows) { row -> TeamStatusCard(row) }
            } else {
                val ranked = if (rankOverall) state.rankedOverall else state.rankedLastMonth
                items(ranked) { row -> RankRowCard(row, isMe = row.officerId == officerId) }
            }
        }
    }
}

// pure combine step: officers x active trips x started leaves x all visits -> screen state.
// only 9 officers -- an in-memory groupBy/map here beats N extra per-officer flows.
private fun teamUiState(
    officers: List<Officer>,
    activeTrips: List<Trip>,
    startedLeaves: List<Leave>,
    allVisits: List<Visit>,
    today: LocalDate,
): TeamUiState {
    val tripByOfficer = activeTrips.associateBy { it.officerId }
    val leavesByOfficer = startedLeaves.groupBy { it.officerId }
    val visitsByTrip = allVisits.groupBy { it.tripId }

    val rows = officers.map { officer ->
        val trip = tripByOfficer[officer.id]
        val tripFlags = listOfNotNull(trip).map { TripFlag(it.status, it.deleted, it.startedAt) }
        val leaveFlags = leavesByOfficer[officer.id].orEmpty()
            .map { LeaveFlag(it.type, it.status, it.deleted, it.startDate, it.endDate) }
        val status = teamStatus(tripFlags, leaveFlags)

        val subtitle = when (status) {
            is TeamStatus.OnVisit -> {
                val primary = trip?.let { t -> primaryVisit(visitsByTrip[t.id].orEmpty()) { v -> v.isAdditional } }
                val place = primary?.let { "${it.institute} · ${it.district} · " }.orEmpty()
                "${place}since ${status.since}"
            }
            is TeamStatus.OnLeave -> "${status.type} · until ${status.until}"
            TeamStatus.InOffice -> null
        }
        TeamRow(officer, status, subtitle)
    }

    // rank_summary view (SQL) only ranks active officers -- mirror that here so a
    // deactivated officer doesn't linger in the leaderboard with a stale points row.
    val rankOfficers = officers.filter { it.active }.map { RankOfficer(it.id, it.name) }
    val rankedOverall = rank(rankOfficers, allVisits.toScores())
    // cumulative snapshot: only visits that had already started by the end of last month,
    // fed through the same rank() -- "where the team stood as of last month."
    val cutoff = lastDayOfPreviousMonth(today)
    val rankedLastMonth = rank(rankOfficers, allVisits.filter { LocalDate.parse(it.startDate) <= cutoff }.toScores())

    return TeamUiState(rows, rankedOverall, rankedLastMonth)
}

@Composable
private fun TeamStatusCard(row: TeamRow) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // weighted + ellipsized so a long officer name can never squeeze the pill's
            // reserved width -- an unweighted Column here is what let "IN OFFICE" get pushed
            // into a sliver of space and wrap to three lines.
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    row.officer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val (label, colors) = when (row.status) {
                is TeamStatus.OnVisit -> "ON TOUR" to LocalStatusColors.current.onVisit
                is TeamStatus.OnLeave -> "ON LEAVE" to LocalStatusColors.current.onLeave
                TeamStatus.InOffice -> "IN OFFICE" to LocalStatusColors.current.office
            }
            StatusPill(label, colors)
        }
    }
}

@Composable
private fun RankRowCard(row: RankRow, isMe: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "#${row.position} ${row.name}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            StatusPill("${row.points} pts", LocalStatusColors.current.success)
        }
    }
}

private fun List<Visit>.toScores(): List<VisitScore> = map { VisitScore(it.officerId, it.category, it.deleted) }
