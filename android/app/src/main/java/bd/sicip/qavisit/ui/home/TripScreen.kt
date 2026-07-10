// tour detail: attached visits (primary + N/A-pilled ad-hoc adds), activity notes, and the
// add-visit / end-tour actions. Travel entry lives in bill prep now (BillScreen), not here.
// `initialAction` lets the home hero's "End tour" shortcut drop straight into the finish
// dialog on arrival.
package bd.sicip.qavisit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.Activity
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.domain.dayNumber
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@Composable
fun TripScreen(
    tripId: String,
    initialAction: String?,
    db: AppDb,
    onScheduleAdhocVisit: (tripId: String, hasPrimary: Boolean) -> Unit,
    onEditVisit: (String) -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var trip by remember { mutableStateOf<Trip?>(null) }
    var visits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var activities by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var showFinish by remember { mutableStateOf(initialAction == "finish") }
    var note by remember { mutableStateOf("") }

    suspend fun reload() {
        trip = db.tripDao().byId(tripId)
        visits = db.visitDao().byTrip(tripId)
        activities = db.activityDao().byTrip(tripId)
    }

    LaunchedEffect(tripId) { reload() }

    val currentTrip = trip ?: return

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Text("Tour · Day ${dayNumber(currentTrip.startedAt)}", style = MaterialTheme.typography.titleLarge) }

        item { Text("VISITS", style = MaterialTheme.typography.labelSmall) }
        items(visits) { visit ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), onClick = { onEditVisit(visit.id) }) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // weighted + ellipsized so a long institute name can never squeeze the pill offscreen.
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(visit.institute, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${visit.purpose} · ${visit.district}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (visit.isAdditional) StatusPill("N/A", LocalStatusColors.current.office)
                }
            }
        }

        item { Text("ACTIVITIES", style = MaterialTheme.typography.labelSmall) }
        items(activities) { activity ->
            Text("${activity.at.take(16).replace("T", " ")} — ${activity.note}", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Quick note") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (note.isNotBlank()) {
                        scope.launch {
                            val now = Instant.now().toString()
                            db.activityDao().upsert(Activity(id = UUID.randomUUID().toString(), tripId = tripId, at = now, note = note, updatedAt = now, dirty = true))
                            note = ""
                            reload()
                        }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add note") }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onScheduleAdhocVisit(tripId, visits.any { !it.isAdditional }) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Add visit") }
                Button(
                    onClick = { showFinish = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("End tour") }
            }
        }
    }

    if (showFinish) {
        FinishTripDialog(
            trip = currentTrip,
            visits = visits,
            db = db,
            onDismiss = { showFinish = false },
            onFinished = onDone,
        )
    }
}
