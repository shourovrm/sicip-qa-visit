// travel legs for the active tour, view/edit/delete mid-tour (used to be bill-prep only, which
// needed a FINISHED tour). edit/add/delete all replace the sheet body in place -- no dialog
// stacked on top of the sheet. reuses LegForm.kt's draft/fields/toEntity, same as BillScreen.
package bd.sicip.qavisit.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.domain.formatFare
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val LEG_DATE_FMT = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelsSheet(tripId: String, legs: List<TravelLeg>, db: AppDb, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    // null editingLeg + adding=true -> new leg; non-null editingLeg -> editing that row.
    var editingLeg by remember { mutableStateOf<TravelLeg?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var places by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { places = db.travelLegDao().distinctPlaces() }

    // Home passes a reactive (byTripFlow) list -- an outer recompose is enough there. TripScreen
    // passes a one-shot snapshot, so a write inside this sheet needs its own reload or the list
    // stays stale until the sheet is closed and reopened. Local copy covers both: synced from
    // the incoming prop on every change, and force-refreshed right after our own writes.
    var shownLegs by remember { mutableStateOf(legs) }
    LaunchedEffect(legs) { shownLegs = legs }
    suspend fun reloadLegs() { shownLegs = db.travelLegDao().byTrip(tripId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val editing = editingLeg
        if (editing != null || adding) {
            val draft = if (editing != null) rememberLegDraft(editing) else rememberLegDraft()
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)
                    .heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (editing != null) "Edit travel" else "Add travel", style = MaterialTheme.typography.titleMedium)
                LegFormFields(draft, places)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { editingLeg = null; adding = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        enabled = draft.valid,
                        onClick = {
                            scope.launch {
                                val id = editing?.id ?: UUID.randomUUID().toString()
                                db.travelLegDao().upsert(draft.toEntity(tripId, id))
                                SyncNow.enqueue(context)
                                reloadLegs()
                                editingLeg = null
                                adding = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save") }
                }
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Text("Travels", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${shownLegs.size} travels · ${formatFare(shownLegs.sumOf { it.fare })} · tap a row to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (shownLegs.isEmpty()) {
                    Text("No travel logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // legs already dep_date/dep_time ascending -- TravelLegDao.byTripFlow's ORDER BY.
                shownLegs.forEach { leg ->
                    if (deleteConfirmId == leg.id) {
                        DeleteConfirmRow(
                            leg = leg,
                            onCancel = { deleteConfirmId = null },
                            onConfirm = {
                                scope.launch {
                                    db.travelLegDao().softDelete(leg.id, Instant.now().toString())
                                    SyncNow.enqueue(context)
                                    reloadLegs()
                                    deleteConfirmId = null
                                }
                            },
                        )
                    } else {
                        TravelLegRow(leg, onClick = { editingLeg = leg }, onDelete = { deleteConfirmId = leg.id })
                    }
                }
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("+ Add travel")
                }
            }
        }
    }
}

// no `private` -- TripScreen's TRAVELS section reuses this same row.
@Composable
fun TravelLegRow(leg: TravelLeg, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${leg.depPlace} → ${leg.arrPlace}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(formatFare(leg.fare), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            val dateStr = runCatching { LocalDate.parse(leg.depDate).format(LEG_DATE_FMT) }.getOrDefault(leg.depDate)
            val modeStr = leg.mode + (leg.travelClass?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            val ticketed = splitTicketRemark(leg.remarks).second
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$dateStr ${leg.depTime.take(5)} – ${leg.arrTime.take(5)} · $modeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ticketed) {
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusPill("Ticket", LocalStatusColors.current.success)
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete travel")
        }
    }
}

@Composable
private fun DeleteConfirmRow(leg: TravelLeg, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text(
            "Delete ${leg.depPlace} → ${leg.arrPlace}?",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        }
    }
}
