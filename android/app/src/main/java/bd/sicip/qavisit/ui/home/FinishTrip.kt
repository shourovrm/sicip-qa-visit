// end-tour dialog: asks the tour's end date+time (prefilled now, editable, stored in the
// existing finished_at column), then shows the primary visit's auto category + points and
// lets the officer override it, then marks every attached visit done and the trip finished,
// and kicks a sync so the change reaches the server right away.
package bd.sicip.qavisit.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.domain.CATEGORY_LABELS
import bd.sicip.qavisit.domain.POINTS
import bd.sicip.qavisit.domain.autoCategory
import bd.sicip.qavisit.domain.daysAndNights
import bd.sicip.qavisit.domain.points
import bd.sicip.qavisit.domain.primaryVisit
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.TimeField
import bd.sicip.qavisit.ui.common.showDatePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime

private val CATEGORIES = POINTS.keys.toList()

@Composable
fun FinishTripDialog(trip: Trip, visits: List<Visit>, db: AppDb, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = remember(visits) { primaryVisit(visits) { it.isAdditional } }
    var endDate by remember { mutableStateOf(Instant.now().toString().take(10)) }
    var endTime by remember { mutableStateOf(String.format("%02d:%02d:00", LocalTime.now().hour, LocalTime.now().minute)) }
    val finishedAt = "${endDate}T${endTime}Z"
    val autoCat = primary?.let {
        val (days, nights) = daysAndNights(trip.startedAt, finishedAt)
        autoCategory(days, nights, it.district, it.dhakaMetro)
    } ?: "N/A"
    var overrideCategory by remember { mutableStateOf<String?>(null) }
    val finalCategory = overrideCategory ?: autoCat

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End tour") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // weight(1f)+widthIn floor: TimeField's clock icon needs ~134dp or it clips
                    OutlinedButton(onClick = { showDatePicker(context, endDate) { endDate = it } }, modifier = Modifier.weight(1f)) {
                        Text(endDate, maxLines = 1)
                    }
                    TimeField(value = endTime, onChange = { endTime = it }, modifier = Modifier.widthIn(min = 134.dp))
                }
                if (primary == null) {
                    Text("No visits attached -- this tour will end with none.")
                } else {
                    Text("Primary visit: ${primary.institute}")
                    PickerDropdown(
                        label = "Category (auto: $autoCat)",
                        options = CATEGORIES,
                        selected = finalCategory,
                        onSelect = { overrideCategory = if (it == autoCat) null else it },
                        displayLabel = { CATEGORY_LABELS[it] ?: it },
                    )
                    Text("Points: ${points(finalCategory)}")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    finishTrip(db, trip, visits, primary, endDate, finalCategory, overrideCategory != null, finishedAt)
                    SyncNow.enqueue(context)
                    onFinished()
                }
            }) { Text("End tour") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

suspend fun finishTrip(
    db: AppDb,
    trip: Trip,
    visits: List<Visit>,
    primary: Visit?,
    primaryEndDate: String,
    primaryCategory: String,
    primaryCategoryOverride: Boolean,
    finishedAt: String,
) {
    val now = Instant.now().toString()
    visits.forEach { v ->
        val isPrimary = v.id == primary?.id
        db.visitDao().upsert(
            v.copy(
                endDate = if (isPrimary) primaryEndDate else v.endDate,
                category = if (isPrimary) primaryCategory else v.category,
                categoryOverride = if (isPrimary) primaryCategoryOverride else v.categoryOverride,
                status = "done",
                updatedAt = now,
                dirty = true,
            ),
        )
    }
    db.tripDao().upsert(trip.copy(status = "finished", finishedAt = finishedAt, updatedAt = now, dirty = true))
}
