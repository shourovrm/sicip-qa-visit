// start a tour: pick a start date+time, optionally attach scheduled visits (or create one
// inline via VisitForm -- it auto-appears checked), optionally inform a colleague. No travel
// form here -- travel entry now happens in bill prep (see BillScreen). Creates Trip(active).
package bd.sicip.qavisit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.showDatePicker
import bd.sicip.qavisit.ui.common.showTimePicker
import bd.sicip.qavisit.ui.visits.VisitForm
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

private suspend fun loadCandidates(db: AppDb, officerId: String): List<Visit> =
    db.visitDao().byOfficer(officerId).filter { it.status == "scheduled" && it.tripId == null }

@Composable
fun StartTrip(officerId: String, db: AppDb, preselectedVisitId: String? = null, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var colleagues by remember { mutableStateOf<List<Officer>>(emptyList()) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var informedOfficerId by remember { mutableStateOf<String?>(null) }
    var showNewVisit by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(Instant.now().toString().take(10)) }
    var startTime by remember { mutableStateOf(String.format("%02d:%02d:00", LocalTime.now().hour, LocalTime.now().minute)) }

    LaunchedEffect(officerId) {
        candidates = loadCandidates(db, officerId)
        colleagues = db.officerDao().all().filter { it.id != officerId }
        if (preselectedVisitId != null) selected.value = selected.value + preselectedVisitId
    }

    // "+ New visit" opens the existing VisitForm inline (same composition, so `selected`
    // survives) -- on save, reload candidates and auto-check whatever is new.
    if (showNewVisit) {
        VisitForm(
            officerId = officerId,
            visitDao = db.visitDao(),
            onDone = {
                scope.launch {
                    val prevIds = candidates.map { it.id }.toSet()
                    val fresh = loadCandidates(db, officerId)
                    candidates = fresh
                    selected.value = selected.value + (fresh.map { it.id }.toSet() - prevIds)
                    showNewVisit = false
                }
            },
        )
        return
    }

    val informedOptions = listOf("None (no one)") + colleagues.map { it.name }
    val informedLabel = colleagues.firstOrNull { it.id == informedOfficerId }?.name ?: "None (no one)"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Start tour", style = MaterialTheme.typography.titleLarge)

        Text("Start", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDatePicker(context, startDate) { startDate = it } }, modifier = Modifier.height(48.dp)) {
                Text(startDate)
            }
            OutlinedButton(onClick = { showTimePicker(context, startTime) { startTime = it } }, modifier = Modifier.height(48.dp)) {
                Text(startTime.take(5))
            }
        }

        Text("Attach scheduled visits (optional)", style = MaterialTheme.typography.labelSmall)
        candidates.forEach { visit ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = selected.value.contains(visit.id),
                    onCheckedChange = { checked ->
                        selected.value = if (checked) selected.value + visit.id else selected.value - visit.id
                    },
                )
                Text("${visit.institute} · ${visit.district} · ${visit.startDate}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = { showNewVisit = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ New visit")
        }

        Text("Inform a colleague (optional)", style = MaterialTheme.typography.labelSmall)
        PickerDropdown(
            label = "Colleague",
            options = informedOptions,
            selected = informedLabel,
            onSelect = { name -> informedOfficerId = colleagues.firstOrNull { it.name == name }?.id },
        )

        Button(
            onClick = {
                scope.launch {
                    val now = Instant.now().toString()
                    val tripId = UUID.randomUUID().toString()
                    db.tripDao().upsert(
                        Trip(
                            id = tripId,
                            officerId = officerId,
                            status = "active",
                            startedAt = "${startDate}T${startTime}Z",
                            informedOfficerId = informedOfficerId,
                            updatedAt = now,
                            dirty = true,
                        ),
                    )
                    selected.value.forEach { visitId ->
                        val v = candidates.first { it.id == visitId }
                        db.visitDao().upsert(v.copy(tripId = tripId, updatedAt = now, dirty = true))
                    }
                    onDone()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
            shape = RoundedCornerShape(99),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Start tour") }
    }
}
