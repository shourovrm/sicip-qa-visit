// finish-trip dialog: shows the primary visit's auto category + points, lets the officer
// override it or nudge the end date, then marks every attached visit done and the trip
// finished, and kicks a sync so the change reaches the server right away.
package bd.sicip.qavisit.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.domain.autoCategory
import bd.sicip.qavisit.domain.points
import bd.sicip.qavisit.domain.primaryVisit
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.showDatePicker
import kotlinx.coroutines.launch
import java.time.Instant

private val CATEGORIES = listOf("A**", "A++", "A+", "A", "B", "C", "D", "E", "N/A")

@Composable
fun FinishTripDialog(trip: Trip, visits: List<Visit>, db: AppDb, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = remember(visits) { primaryVisit(visits) { it.isAdditional } }
    var endDate by remember(primary) { mutableStateOf(primary?.endDate ?: "") }
    val autoCat = primary?.let { autoCategory(it.startDate, endDate, it.district, it.dhakaMetro) } ?: "N/A"
    var overrideCategory by remember { mutableStateOf<String?>(null) }
    val finalCategory = overrideCategory ?: autoCat

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish trip") },
        text = {
            Column {
                if (primary == null) {
                    Text("No visits attached -- this trip will finish with none.")
                } else {
                    Text("Primary visit: ${primary.institute}")
                    OutlinedButton(onClick = { showDatePicker(context, endDate) { endDate = it } }) {
                        Text("End date: $endDate")
                    }
                    PickerDropdown(
                        label = "Category (auto: $autoCat)",
                        options = CATEGORIES,
                        selected = finalCategory,
                        onSelect = { overrideCategory = if (it == autoCat) null else it },
                    )
                    Text("Points: ${points(finalCategory)}")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    finishTrip(db, trip, visits, primary, endDate, finalCategory, overrideCategory != null)
                    SyncNow.enqueue(context)
                    onFinished()
                }
            }) { Text("Finish") }
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
    db.tripDao().upsert(trip.copy(status = "finished", finishedAt = now, updatedAt = now, dirty = true))
}
