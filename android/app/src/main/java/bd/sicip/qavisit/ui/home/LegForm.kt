// one travel-leg form; used by bill prep's "Add travel" / edit-travel dialogs (BillScreen) --
// travel entry lives there now, not in start-tour or the tour detail screen.
package bd.sicip.qavisit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.seed.TICKET_REMARK
import bd.sicip.qavisit.data.seed.TRANSPORT
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.showDatePicker
import bd.sicip.qavisit.ui.common.showTimePicker
import java.time.Instant
import java.util.UUID

// resolves the mode dropdown + free-text "Other" field into the value actually stored on the
// leg: dropdown value as-is, unless it's "Other" -- then the typed name (falling back to
// "Other" itself if left blank).
fun resolveMode(dropdownValue: String, otherText: String): String =
    if (dropdownValue == "Other") otherText.ifBlank { "Other" } else dropdownValue

// inverse, for edit prefill: a stored mode that's one of the seed options selects itself with
// no free text; anything else (an old "Other" leg) selects "Other" with the stored value as
// the prefilled free text.
fun modeDropdownFor(storedMode: String, seedModes: Set<String> = TRANSPORT.keys): Pair<String, String> =
    if (storedMode in seedModes) storedMode to "" else "Other" to storedMode

// remarks text field + ticket tick box -> the single string stored on the leg. blank text with
// the box unticked stores nothing; ticked appends/sets the fixed TICKET_REMARK marker.
fun composeRemarks(userText: String, ticket: Boolean): String? {
    val trimmed = userText.trim()
    return when {
        !ticket -> trimmed.ifBlank { null }
        trimmed.isBlank() -> TICKET_REMARK
        else -> "$trimmed; $TICKET_REMARK"
    }
}

// inverse, for edit prefill: strips the TICKET_REMARK marker back off (with or without its
// "; " separator) and reports whether it was present.
fun splitTicketRemark(stored: String?): Pair<String, Boolean> {
    val s = stored?.trim().orEmpty()
    return when {
        s == TICKET_REMARK -> "" to true
        s.endsWith("; $TICKET_REMARK") -> s.removeSuffix("; $TICKET_REMARK").trim() to true
        else -> s to false
    }
}

// mutable draft the form edits in place; caller reads it back on save.
class LegDraft {
    var depDate by mutableStateOf(Instant.now().toString().take(10))
    var depTime by mutableStateOf("09:00:00")
    var depPlace by mutableStateOf("")
    var arrDate by mutableStateOf(Instant.now().toString().take(10))
    var arrTime by mutableStateOf("09:00:00")
    var arrPlace by mutableStateOf("")
    var mode by mutableStateOf(TRANSPORT.keys.first())
    var otherModeText by mutableStateOf("") // free text when mode == "Other"
    var travelClass by mutableStateOf(TRANSPORT.values.first().firstOrNull())
    var fareText by mutableStateOf("")
    var remarks by mutableStateOf("")
    var ticketAttached by mutableStateOf(false)

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
        val (dropdownMode, otherText) = modeDropdownFor(existing.mode)
        d.mode = dropdownMode
        d.otherModeText = otherText
        d.travelClass = existing.travelClass
        d.fareText = fareToText(existing.fare)
        val (userText, ticketed) = splitTicketRemark(existing.remarks)
        d.remarks = userText
        d.ticketAttached = ticketed
    }
}

@Composable
fun LegFormFields(
    draft: LegDraft,
    // dep/arr place autosuggest source (distinct places across every synced leg). Defaults to
    // empty so the existing BillScreen call site keeps compiling untouched -- it can pass the
    // real list once it's ready to wire TravelLegDao.distinctPlaces() through.
    places: List<String> = emptyList(),
) {
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
        PickerDropdown(
            label = "Departure place",
            options = places,
            selected = draft.depPlace,
            onSelect = { draft.depPlace = it },
            onTextChange = { draft.depPlace = it },
            searchable = true,
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
        PickerDropdown(
            label = "Arrival place",
            options = places,
            selected = draft.arrPlace,
            onSelect = { draft.arrPlace = it },
            onTextChange = { draft.arrPlace = it },
            searchable = true,
        )

        PickerDropdown(
            label = "Mode",
            options = TRANSPORT.keys.toList(),
            selected = draft.mode,
            onSelect = { draft.mode = it; draft.travelClass = TRANSPORT[it]?.firstOrNull() },
        )
        if (draft.mode == "Other") {
            OutlinedTextField(
                value = draft.otherModeText,
                onValueChange = { draft.otherModeText = it },
                label = { Text("Mode name") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val classes = TRANSPORT[draft.mode].orEmpty()
        if (classes.isNotEmpty()) {
            PickerDropdown(
                label = "Class",
                options = classes,
                selected = draft.travelClass ?: classes.first(),
                onSelect = { draft.travelClass = it },
            )
        }

        if (draft.mode != "N/A") {
            OutlinedTextField(
                value = draft.fareText,
                onValueChange = { draft.fareText = it },
                label = { Text("Fare") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = draft.remarks,
            onValueChange = { draft.remarks = it },
            label = { Text("Remarks (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.ticketAttached, onCheckedChange = { draft.ticketAttached = it })
            Text(TICKET_REMARK)
        }
    }
}

fun LegDraft.toEntity(tripId: String, id: String = UUID.randomUUID().toString()): TravelLeg {
    val now = Instant.now().toString()
    val resolvedMode = resolveMode(mode, otherModeText)
    val isNA = resolvedMode == "N/A" // no mode claimed -- wipe class/fare regardless of stray input
    return TravelLeg(
        id = id,
        tripId = tripId,
        depDate = depDate,
        depTime = depTime,
        depPlace = depPlace,
        arrDate = arrDate,
        arrTime = arrTime,
        arrPlace = arrPlace,
        mode = resolvedMode,
        travelClass = if (isNA) null else travelClass,
        fare = if (isNA) 0.0 else fare,
        remarks = composeRemarks(remarks, ticketAttached),
        updatedAt = now,
        dirty = true,
    )
}
