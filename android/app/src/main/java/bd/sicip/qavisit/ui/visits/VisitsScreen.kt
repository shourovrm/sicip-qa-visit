// visits list: Personal (own, tap to edit) / Team (everyone, read-only + officer filter).
// grouped by month header, newest first; category pill colored by whether it's actually scored.
package bd.sicip.qavisit.ui.visits

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val ALL_OFFICERS = "All officers"

@Composable
fun VisitsScreen(officerId: String, db: AppDb, onEditVisit: (String) -> Unit) {
    var personal by remember { mutableStateOf(true) }
    var myVisits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var allVisits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var officers by remember { mutableStateOf<List<Officer>>(emptyList()) }
    var officerFilter by remember { mutableStateOf(ALL_OFFICERS) }

    LaunchedEffect(Unit) {
        myVisits = db.visitDao().byOfficer(officerId) // already deleted=0, start_date desc
        allVisits = db.visitDao().all()
        officers = db.officerDao().all()
    }

    val nameById = officers.associate { it.id to it.name }
    val visits = if (personal) {
        myVisits
    } else {
        allVisits.filter { officerFilter == ALL_OFFICERS || nameById[it.officerId] == officerFilter }
    }
    // both lists already come back start_date DESC from the dao -- groupBy keeps first-seen
    // order for its keys, so month headers land newest-first too, for free.
    val grouped = visits.groupBy { monthLabel(it.startDate) }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SegmentedButton(
                selected = personal,
                onClick = { personal = true },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Personal") }
            SegmentedButton(
                selected = !personal,
                onClick = { personal = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Team") }
        }

        if (!personal) {
            PickerDropdown(
                label = "Officer",
                options = listOf(ALL_OFFICERS) + officers.map { it.name },
                selected = officerFilter,
                onSelect = { officerFilter = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            grouped.forEach { (month, rows) ->
                item {
                    Text(
                        month,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(rows) { visit ->
                    VisitRow(
                        visit = visit,
                        officerName = if (personal) null else nameById[visit.officerId],
                        onClick = if (personal) ({ onEditVisit(visit.id) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitRow(visit: Visit, officerName: String?, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(16.dp)
    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                if (officerName != null) {
                    Text(officerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(visit.institute, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${visit.purpose} · ${visit.district} · ${visit.startDate} – ${visit.endDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val scored = visit.category != "N/A"
            StatusPill(
                visit.category,
                if (scored) LocalStatusColors.current.success else LocalStatusColors.current.office,
            )
        }
    }
    // read-only (Team) rows get the plain Card; own (Personal) rows get the clickable overload.
    if (onClick != null) {
        Card(shape = shape, modifier = Modifier.fillMaxWidth(), onClick = onClick) { body() }
    } else {
        Card(shape = shape, modifier = Modifier.fillMaxWidth()) { body() }
    }
}

private fun monthLabel(isoDate: String): String {
    val date = LocalDate.parse(isoDate)
    return "${date.month.getDisplayName(TextStyle.FULL, Locale.US)} ${date.year}"
}
