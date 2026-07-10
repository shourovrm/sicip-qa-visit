// one travel-leg form; used by bill prep's "Add travel" / edit-travel dialogs (BillScreen) --
// travel entry lives there now, not in start-tour or the tour detail screen.
package bd.sicip.qavisit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.seed.TRANSPORT
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.showDatePicker
import bd.sicip.qavisit.ui.common.showTimePicker
import java.time.Instant
import java.util.UUID

// mutable draft the form edits in place; caller reads it back on save.
class LegDraft {
    var depDate by mutableStateOf(Instant.now().toString().take(10))
    var depTime by mutableStateOf("09:00:00")
    var depPlace by mutableStateOf("")
    var arrDate by mutableStateOf(Instant.now().toString().take(10))
    var arrTime by mutableStateOf("09:00:00")
    var arrPlace by mutableStateOf("")
    var mode by mutableStateOf(TRANSPORT.keys.first())
    var travelClass by mutableStateOf(TRANSPORT.values.first().firstOrNull())
    var fareText by mutableStateOf("")
    var remarks by mutableStateOf("")

    val fare: Double get() = fareText.toDoubleOrNull() ?: 0.0
    val valid: Boolean get() = depPlace.isNotBlank() && arrPlace.isNotBlank()
}

@Composable
fun rememberLegDraft(): LegDraft = remember { LegDraft() }

private fun fareToText(fare: Double): String = if (fare == fare.toLong().toDouble()) fare.toLong().toString() else fare.toString()

// edit mode: prefill a draft from an existing row. keyed by id so switching which row is
// being edited (rare, but cheap to get right) starts a fresh draft instead of reusing state.
@Composable
fun rememberLegDraft(existing: TravelLeg): LegDraft = remember(existing.id) {
    LegDraft().also { d ->
        d.depDate = existing.depDate
        d.depTime = existing.depTime
        d.depPlace = existing.depPlace
        d.arrDate = existing.arrDate
        d.arrTime = existing.arrTime
        d.arrPlace = existing.arrPlace
        d.mode = existing.mode
        d.travelClass = existing.travelClass
        d.fareText = fareToText(existing.fare)
        d.remarks = existing.remarks ?: ""
    }
}

@Composable
fun LegFormFields(draft: LegDraft) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Departure")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDatePicker(context, draft.depDate) { draft.depDate = it } }, modifier = Modifier.height(48.dp)) {
                Text(draft.depDate)
            }
            OutlinedButton(onClick = { showTimePicker(context, draft.depTime) { draft.depTime = it } }, modifier = Modifier.height(48.dp)) {
                Text(draft.depTime.take(5))
            }
        }
        OutlinedTextField(
            value = draft.depPlace,
            onValueChange = { draft.depPlace = it },
            label = { Text("Departure place") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Arrival")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDatePicker(context, draft.arrDate) { draft.arrDate = it } }, modifier = Modifier.height(48.dp)) {
                Text(draft.arrDate)
            }
            OutlinedButton(onClick = { showTimePicker(context, draft.arrTime) { draft.arrTime = it } }, modifier = Modifier.height(48.dp)) {
                Text(draft.arrTime.take(5))
            }
        }
        OutlinedTextField(
            value = draft.arrPlace,
            onValueChange = { draft.arrPlace = it },
            label = { Text("Arrival place") },
            modifier = Modifier.fillMaxWidth(),
        )

        PickerDropdown(
            label = "Mode",
            options = TRANSPORT.keys.toList(),
            selected = draft.mode,
            onSelect = { draft.mode = it; draft.travelClass = TRANSPORT[it]?.firstOrNull() },
        )
        val classes = TRANSPORT[draft.mode].orEmpty()
        if (classes.isNotEmpty()) {
            PickerDropdown(
                label = "Class",
                options = classes,
                selected = draft.travelClass ?: classes.first(),
                onSelect = { draft.travelClass = it },
            )
        }

        OutlinedTextField(
            value = draft.fareText,
            onValueChange = { draft.fareText = it },
            label = { Text("Fare") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.remarks,
            onValueChange = { draft.remarks = it },
            label = { Text("Remarks (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun LegDraft.toEntity(tripId: String, id: String = UUID.randomUUID().toString()): TravelLeg {
    val now = Instant.now().toString()
    return TravelLeg(
        id = id,
        tripId = tripId,
        depDate = depDate,
        depTime = depTime,
        depPlace = depPlace,
        arrDate = arrDate,
        arrTime = arrTime,
        arrPlace = arrPlace,
        mode = mode,
        travelClass = travelClass,
        fare = fare,
        remarks = remarks.ifBlank { null },
        updatedAt = now,
        dirty = true,
    )
}
