// visits list: Personal (own, tap to edit) / Team (everyone, read-only + officer filter).
// grouped by month header, newest first; category pill colored by whether it's actually scored.
// below the tabs, a scrollable FilterChip row narrows by period/district/category/purpose
// (VisitFilter.kt, pure fn) plus -- Team only -- the existing officer filter.
package bd.sicip.qavisit.ui.visits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.seed.DISTRICTS
import bd.sicip.qavisit.data.seed.PURPOSES
import bd.sicip.qavisit.domain.POINTS
import bd.sicip.qavisit.domain.points
import bd.sicip.qavisit.ui.common.FilterChipDropdown
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.common.TwoTabRow
import bd.sicip.qavisit.ui.common.showDatePicker
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val ALL_OFFICERS = "All officers"
private val DISTRICT_OPTIONS = listOf(FILTER_ALL) + DISTRICTS
private val CATEGORY_OPTIONS = listOf(FILTER_ALL) + POINTS.keys
private val PURPOSE_OPTIONS = listOf(FILTER_ALL) + PURPOSES

@Composable
fun VisitsScreen(officerId: String, db: AppDb, onEditVisit: (String) -> Unit, onOpenBill: () -> Unit) {
    var personal by remember { mutableStateOf(true) }
    var myVisits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var allVisits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var officers by remember { mutableStateOf<List<Officer>>(emptyList()) }
    var officerFilter by remember { mutableStateOf(ALL_OFFICERS) }
    var filter by remember { mutableStateOf(VisitFilter()) }

    LaunchedEffect(Unit) {
        myVisits = db.visitDao().byOfficer(officerId) // already deleted=0, start_date desc
        allVisits = db.visitDao().all()
        officers = db.officerDao().all()
    }

    val nameById = officers.associate { it.id to it.name }
    val tabVisits = if (personal) {
        myVisits
    } else {
        allVisits.filter { officerFilter == ALL_OFFICERS || nameById[it.officerId] == officerFilter }
    }
    val visits = filterVisits(tabVisits, filter)
    val totalPts = visits.sumOf { points(it.category) }
    // both lists already come back start_date DESC from the dao -- groupBy keeps first-seen
    // order for its keys, so month headers land newest-first too, for free.
    val grouped = visits.groupBy { monthLabel(it.startDate) }

    Column(modifier = Modifier.fillMaxSize()) {
        TwoTabRow("Personal", "Team", personal, { personal = it })

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item { PeriodFilterChip(filter.period) { filter = filter.copy(period = it) } }
            item {
                FilterChipDropdown(
                    label = "District",
                    options = DISTRICT_OPTIONS,
                    selected = filter.district,
                    onSelect = { filter = filter.copy(district = it) },
                    allValue = FILTER_ALL,
                )
            }
            item {
                FilterChipDropdown(
                    label = "Category",
                    options = CATEGORY_OPTIONS,
                    selected = filter.category,
                    onSelect = { filter = filter.copy(category = it) },
                    allValue = FILTER_ALL,
                )
            }
            item {
                FilterChipDropdown(
                    label = "Purpose",
                    options = PURPOSE_OPTIONS,
                    selected = filter.purpose,
                    onSelect = { filter = filter.copy(purpose = it) },
                    allValue = FILTER_ALL,
                )
            }
            if (!personal) {
                item {
                    FilterChipDropdown(
                        label = "Officer",
                        options = listOf(ALL_OFFICERS) + officers.map { it.name },
                        selected = officerFilter,
                        onSelect = { officerFilter = it },
                        allValue = ALL_OFFICERS,
                    )
                }
            }
        }

        if (personal) {
            OutlinedButton(
                onClick = onOpenBill,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("TA/DA Bill") }
        }

        Text(
            "${visits.size} visits · $totalPts pts",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

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

private val PERIOD_OPTIONS = listOf("All time", "This month", "Last 3 months", "This year", "Custom range")

// period chip: same look as FilterChipDropdown, but "Custom range" branches into two native date
// pickers instead of picking a plain string -- kept as its own small composable rather than
// stretching the generic chip to cover a shape it wasn't built for.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodFilterChip(period: Period, onChange: (Period) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val label = periodLabel(period)
    Box {
        FilterChip(
            selected = label != null,
            onClick = { expanded = true },
            label = { Text(label ?: "Period") },
            trailingIcon = if (label != null) {
                {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear Period",
                        modifier = Modifier.size(FilterChipDefaults.IconSize).clickable { onChange(Period.AllTime) },
                    )
                }
            } else {
                null
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PERIOD_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        when (option) {
                            "All time" -> onChange(Period.AllTime)
                            "This month" -> onChange(Period.ThisMonth)
                            "Last 3 months" -> onChange(Period.Last3Months)
                            "This year" -> onChange(Period.ThisYear)
                            "Custom range" -> showDatePicker(context, "") { start ->
                                showDatePicker(context, start) { end ->
                                    onChange(Period.Custom(start = start, end = end))
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VisitRow(visit: Visit, officerName: String?, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(16.dp)
    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // weighted + ellipsized so a long institute name can never squeeze the pill offscreen.
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                if (officerName != null) {
                    Text(
                        officerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(visit.institute, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${visit.purpose} · ${visit.district} · ${visit.startDate} – ${visit.endDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
