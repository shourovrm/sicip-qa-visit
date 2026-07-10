// Team screen: two tabs -- Status (per-officer derived status: on tour / on leave / in office)
// and Rank (points leaderboard).
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.domain.LeaveFlag
import bd.sicip.qavisit.domain.RankOfficer
import bd.sicip.qavisit.domain.RankRow
import bd.sicip.qavisit.domain.TeamStatus
import bd.sicip.qavisit.domain.TripFlag
import bd.sicip.qavisit.domain.VisitScore
import bd.sicip.qavisit.domain.primaryVisit
import bd.sicip.qavisit.domain.rank
import bd.sicip.qavisit.domain.teamStatus
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.common.TwoTabRow
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import java.time.LocalDate

private data class TeamRow(val officer: Officer, val status: TeamStatus, val subtitle: String?)

@Composable
fun TeamScreen(officerId: String, db: AppDb) {
    var statusTab by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<TeamRow>>(emptyList()) }
    var ranked by remember { mutableStateOf<List<RankRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        val officers = db.officerDao().all() // dao already orders by name -- alphabetical for free
        val today = LocalDate.now()
        // one query covers every officer's overlapping leave instead of 9 separate lookups.
        val overlappingLeaves = db.leaveDao().overlapping(today.toString())

        rows = officers.map { officer ->
            val trip = db.tripDao().activeTrip(officer.id) // only 9 officers -- a per-officer loop is fine
            val tripFlags = listOfNotNull(trip).map { TripFlag(it.status, it.deleted, it.startedAt) }
            val leaveFlags = overlappingLeaves.filter { it.officerId == officer.id }
                .map { LeaveFlag(it.type, it.status, it.deleted, it.startDate, it.endDate) }
            val status = teamStatus(tripFlags, leaveFlags, today)

            val subtitle = when (status) {
                is TeamStatus.OnVisit -> {
                    val primary = trip?.let { t -> primaryVisit(db.visitDao().byTrip(t.id)) { v -> v.isAdditional } }
                    val place = primary?.let { "${it.institute} · ${it.district} · " }.orEmpty()
                    "${place}since ${status.since}"
                }
                is TeamStatus.OnLeave -> "${status.type} · until ${status.until}"
                TeamStatus.InOffice -> null
            }
            TeamRow(officer, status, subtitle)
        }

        val scores = db.visitDao().all().map { VisitScore(it.officerId, it.category, it.deleted) }
        ranked = rank(officers.map { RankOfficer(it.id, it.name) }, scores)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TwoTabRow("Status", "Rank", statusTab, { statusTab = it })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (statusTab) {
                items(rows) { row -> TeamStatusCard(row) }
            } else {
                items(ranked) { row -> RankRowCard(row, isMe = row.officerId == officerId) }
            }
        }
    }
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
