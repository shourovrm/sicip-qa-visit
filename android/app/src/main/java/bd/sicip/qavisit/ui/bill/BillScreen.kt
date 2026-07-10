// TA/DA bill flow: New bill (pick own finished trips (step 1), edit night/food per trip +
// preview live totals (step 2), Submit bill freezes a snapshot into the immutable archive) and
// Previous bills (read-only list + detail of everything already submitted). The official-
// template PDF (pdf/BillHtml.kt + pdf/BillPrinter.kt) is generated either from the live preview (Generate PDF, still
// editable up to that point) or deterministically from a frozen snapshot (Submit bill / View
// PDF on a previous bill) -- see domain/BillSnapshot.kt. everything here is plain material3 --
// only the PDF itself is styled to look like the government form.
package bd.sicip.qavisit.ui.bill

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Bill
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.domain.BillSnapshot
import bd.sicip.qavisit.domain.BillTotals
import bd.sicip.qavisit.domain.Leg
import bd.sicip.qavisit.domain.SnapshotLeg
import bd.sicip.qavisit.domain.SnapshotTrip
import bd.sicip.qavisit.domain.amountInWords
import bd.sicip.qavisit.domain.billTotals
import bd.sicip.qavisit.domain.formatFare
import bd.sicip.qavisit.domain.legDefaults
import bd.sicip.qavisit.domain.primaryVisit
import bd.sicip.qavisit.domain.renderableFromSnapshot
import bd.sicip.qavisit.domain.snapshotBill
import bd.sicip.qavisit.domain.toBillTrips
import bd.sicip.qavisit.domain.tripFoodDays
import bd.sicip.qavisit.domain.tripNights
import bd.sicip.qavisit.pdf.BillLeg
import bd.sicip.qavisit.pdf.BillTrip
import bd.sicip.qavisit.pdf.buildBillHtml
import bd.sicip.qavisit.pdf.purposeDate
import bd.sicip.qavisit.pdf.renderBillPdf
import bd.sicip.qavisit.ui.common.TwoTabRow
import bd.sicip.qavisit.ui.common.showDatePicker
import bd.sicip.qavisit.ui.home.LegFormFields
import bd.sicip.qavisit.ui.home.rememberLegDraft
import bd.sicip.qavisit.ui.home.toEntity
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import bd.sicip.qavisit.domain.Trip as DomainTrip

private val BILL_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
private const val FILE_PROVIDER_AUTHORITY = "bd.sicip.qavisit.fileprovider"

private enum class Step { PICKER, PREVIEW, DETAIL }

// one finished trip with everything the bill needs pulled ahead of time -- purpose line
// comes from the primary visit only (additional visits on the same trip don't get their own
// bill line, matching how the official sample batches per-trip, not per-visit).
private data class TripCandidate(val trip: Trip, val primary: Visit, val legs: List<TravelLeg>)

private suspend fun loadUnsubmitted(db: AppDb, officerId: String): List<TripCandidate> =
    db.tripDao().finishedUnsubmittedByOfficer(officerId).mapNotNull { trip ->
        val visits = db.visitDao().byTrip(trip.id)
        val primary = primaryVisit(visits) { it.isAdditional } ?: return@mapNotNull null
        TripCandidate(trip, primary, db.travelLegDao().byTrip(trip.id))
    }

@Composable
fun BillScreen(officerId: String, db: AppDb, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.PICKER) }
    var showPrevious by remember { mutableStateOf(false) }
    var unsubmitted by remember { mutableStateOf<List<TripCandidate>>(emptyList()) }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var officerName by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var viewingBill by remember { mutableStateOf<Bill?>(null) }

    suspend fun reload() {
        unsubmitted = loadUnsubmitted(db, officerId)
        bills = db.billDao().byOfficer(officerId)
    }

    LaunchedEffect(officerId) {
        officerName = db.officerDao().byId(officerId)?.name ?: ""
        reload()
    }

    when (step) {
        Step.PICKER -> Column(modifier = Modifier.fillMaxSize()) {
            TwoTabRow(
                leftLabel = "New bill",
                rightLabel = "Previous bills",
                leftSelected = !showPrevious,
                onSelect = { left -> showPrevious = !left },
            )
            if (showPrevious) {
                PreviousBillsList(bills = bills, onOpen = { viewingBill = it; step = Step.DETAIL })
            } else {
                NewBillPicker(
                    candidates = unsubmitted,
                    selected = selected.value,
                    onToggle = { id, on -> selected.value = if (on) selected.value + id else selected.value - id },
                    onNext = { step = Step.PREVIEW },
                )
            }
        }
        Step.PREVIEW -> BillPreviewStep(
            officerId = officerId,
            officerName = officerName,
            trips = unsubmitted.filter { it.trip.id in selected.value },
            db = db,
            onBack = { step = Step.PICKER },
            onGeneratedPreview = onDone,
            onSubmitted = {
                scope.launch {
                    selected.value = emptySet()
                    reload()
                }
                step = Step.PICKER
            },
        )
        Step.DETAIL -> PreviousBillDetail(bill = viewingBill!!, onBack = { step = Step.PICKER })
    }
}

@Composable
private fun NewBillPicker(
    candidates: List<TripCandidate>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Select finished tours to batch onto one TA/DA bill.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(candidates, key = { it.trip.id }) { c ->
                val fareSum = c.legs.sumOf { it.fare }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onToggle(c.trip.id, c.trip.id !in selected) },
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.trip.id in selected, onCheckedChange = { onToggle(c.trip.id, it) })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(c.primary.institute, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${c.primary.startDate} – ${c.primary.endDate} · ${c.legs.size} travels · Σ ${formatFare(fareSum)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (candidates.isEmpty()) {
                item {
                    Text(
                        "No finished, unsubmitted tours right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        Button(
            onClick = onNext,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
        ) { Text("Next (${selected.size} selected)") }
    }
}

@Composable
private fun PreviousBillsList(bills: List<Bill>, onOpen: (Bill) -> Unit) {
    if (bills.isEmpty()) {
        Text(
            "No submitted bills yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(bills, key = { it.id }) { bill ->
            val tourCount = remember(bill.id) { bill.parseSnapshot().trips.size }
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpen(bill) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(bill.billDate.format(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatFare(bill.net)} · $tourCount tour(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// read-only: totals + per-tour nights/food as plain text, no edit fields -- this bill is
// frozen. PDF is re-rendered from the same snapshot on demand (domain.toBillTrips), never
// recalculated from the live trip/leg tables.
@Composable
private fun PreviousBillDetail(bill: Bill, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot = remember(bill.id) { bill.parseSnapshot() }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item { Text("Bill date: ${snapshot.billDate.format()}", style = MaterialTheme.typography.titleMedium) }
            items(snapshot.trips, key = { it.tripId }) { t ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.purposeLine, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Nights: ${t.nights}   Food days: ${numText(t.foodDays)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { TotalsCard(snapshot.totals) }
        }
        Button(
            onClick = {
                scope.launch {
                    val html = buildBillHtml(
                        officerName = snapshot.officerName,
                        billDate = snapshot.billDate,
                        trips = snapshot.toBillTrips(),
                        totals = snapshot.totals,
                    )
                    val file = renderBillPdf(context, html)
                    shareBillPdf(context, file)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
        ) { Text("View PDF") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        ) { Text("Back") }
    }
}

private fun Bill.parseSnapshot(): BillSnapshot = renderableFromSnapshot(Json.parseToJsonElement(data).jsonObject)

// per-selected-trip editable state: nights/food default from the span rule (BillMath), the
// officer can override either (e.g. the official sample zeroed a same-day Dhaka trip, or a
// Dhaka-inside-metro tour which always defaults to 0/0 regardless of span). legs start from
// what was loaded but stay independently mutable so add/edit/delete in bill prep (the only
// place travel rows are entered) reflects immediately in totals + the PDF.
private class TripEdit(val candidate: TripCandidate) {
    private val metro = candidate.primary.district == "Dhaka" && candidate.primary.dhakaMetro == true
    var nightsText by mutableStateOf(tripNights(candidate.primary.startDate, candidate.primary.endDate, metro).toString())
    var foodText by mutableStateOf(numText(tripFoodDays(candidate.primary.startDate, candidate.primary.endDate, metro)))
    var legs by mutableStateOf(candidate.legs)
    val nights: Int get() = nightsText.toIntOrNull() ?: 0
    val foodDays: Double get() = foodText.toDoubleOrNull() ?: 0.0
}

private fun numText(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

@Composable
private fun BillPreviewStep(
    officerId: String,
    officerName: String,
    trips: List<TripCandidate>,
    db: AppDb,
    onBack: () -> Unit,
    onGeneratedPreview: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var billDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    val edits = remember(trips) { trips.map { TripEdit(it) } }

    val domainTrips = edits.map {
        DomainTrip(
            legs = it.legs.map { leg -> Leg(fare = leg.fare, depDate = leg.depDate) },
            startDate = it.candidate.primary.startDate,
            endDate = it.candidate.primary.endDate,
            nights = it.nights,
            food = it.foodDays,
        )
    }
    val totals = billTotals(domainTrips)

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                OutlinedButton(onClick = { showDatePicker(context, billDate) { billDate = it } }) {
                    Text("Bill date: ${billDate.format()}")
                }
            }
            items(edits, key = { it.candidate.trip.id }) { edit -> TripEditCard(edit, db) }
            item { TotalsCard(totals) }
        }
        Button(
            onClick = {
                scope.launch {
                    val html = buildBillHtml(
                        officerName = officerName,
                        billDate = billDate,
                        trips = edits.map { it.toBillTrip() },
                        totals = totals,
                    )
                    val file = renderBillPdf(context, html)
                    val saved = saveToDownloads(context, file)
                    Toast.makeText(
                        context,
                        if (saved) "Saved to Downloads" else "Couldn't save to Downloads",
                        Toast.LENGTH_SHORT,
                    ).show()
                    shareBillPdf(context, file)
                    onGeneratedPreview()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
        ) { Text("Generate PDF") }
        Button(
            onClick = { showSubmitConfirm = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
        ) { Text("Submit bill") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 0.dp),
        ) { Text("Back") }
    }

    // freezes the current preview into an immutable bills row + marks every batched trip
    // submitted, then prints the final PDF from that same frozen snapshot (not the live
    // editable state) so the archived PDF and the archived data can never drift apart.
    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Submit bill?") },
            text = { Text("Submit this bill? Values freeze and the tours move to Previous bills. This cannot be edited later.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val now = Instant.now().toString()
                        val snapshotTrips = edits.map { it.toSnapshotTrip() }
                        val json = snapshotBill(billDate, officerName, snapshotTrips, totals)
                        val snapshot = BillSnapshot(billDate, officerName, snapshotTrips, totals)
                        db.billDao().upsert(
                            Bill(
                                id = UUID.randomUUID().toString(),
                                officerId = officerId,
                                billDate = billDate,
                                data = json.toString(),
                                net = totals.net,
                                createdAt = now,
                                updatedAt = now,
                                dirty = true,
                            ),
                        )
                        edits.forEach { db.tripDao().markSubmitted(it.candidate.trip.id, now) }
                        SyncNow.enqueue(context) // push the new bill + submitted trips promptly, don't wait for the 15-min periodic job
                        val html = buildBillHtml(
                            officerName = officerName,
                            billDate = billDate,
                            trips = snapshot.toBillTrips(),
                            totals = totals,
                        )
                        val file = renderBillPdf(context, html)
                        saveToDownloads(context, file)
                        shareBillPdf(context, file)
                        showSubmitConfirm = false
                        onSubmitted()
                    }
                }) { Text("Submit bill") }
            },
            dismissButton = { TextButton(onClick = { showSubmitConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TripEditCard(edit: TripEdit, db: AppDb) {
    val scope = rememberCoroutineScope()
    var editingLeg by remember { mutableStateOf<TravelLeg?>(null) }
    var showTravelForm by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf<List<String>>(emptyList()) }

    suspend fun reloadLegs() {
        edit.legs = db.travelLegDao().byTrip(edit.candidate.trip.id)
    }

    LaunchedEffect(Unit) { places = db.travelLegDao().distinctPlaces() }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(edit.candidate.primary.institute, style = MaterialTheme.typography.titleMedium)
            Text(
                purposeLine(edit.candidate.primary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = edit.nightsText,
                    onValueChange = { edit.nightsText = it },
                    label = { Text("Nights") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = edit.foodText,
                    onValueChange = { edit.foodText = it },
                    label = { Text("Food days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Text("TRAVEL DETAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            edit.legs.forEach { leg ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${leg.depPlace} → ${leg.arrPlace} · ${leg.depDate}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${leg.mode}${leg.travelClass?.let { " · $it" } ?: ""} · ${formatFare(leg.fare)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editingLeg = leg; showTravelForm = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit travel")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            db.travelLegDao().softDelete(leg.id, Instant.now().toString())
                            reloadLegs()
                        }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete travel") }
                }
            }
            OutlinedButton(
                onClick = { editingLeg = null; showTravelForm = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add travel") }
        }
    }

    if (showTravelForm) {
        val editing = editingLeg
        val draft = if (editing != null) rememberLegDraft(editing) else rememberLegDraft()
        AlertDialog(
            onDismissRequest = { showTravelForm = false },
            title = { Text(if (editing != null) "Edit travel" else "Add travel") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    LegFormFields(draft, places)
                }
            },
            confirmButton = {
                Button(
                    enabled = draft.valid,
                    onClick = {
                        scope.launch {
                            val id = editing?.id ?: UUID.randomUUID().toString()
                            db.travelLegDao().upsert(draft.toEntity(edit.candidate.trip.id, id))
                            reloadLegs()
                            showTravelForm = false
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTravelForm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TotalsCard(totals: BillTotals) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Totals", style = MaterialTheme.typography.titleMedium)
            Text("Travel Allowance: ${formatFare(totals.ta)}")
            Text("Accommodation: ${formatFare(totals.accommodation)}")
            Text("Food: ${formatFare(totals.food)}")
            Text("Net claim: ${formatFare(totals.net)}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${amountInWords(totals.net.toLong())} Only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// "Purpose: {purpose} - {association} (Ref: {ref_no or —}, {ref_date or start_date})" -- one
// line per trip's primary visit, printed as the purpose band above that trip's itinerary rows.
// date-selection rule (ref_date over start_date) lives in pdf/BillHtml.purposeDate, see there.
private fun purposeLine(v: Visit): String =
    "Purpose: ${v.purpose} - ${v.association} (Ref: ${v.refNo ?: "—"}, ${purposeDate(v.startDate, v.refDate)})"

private fun String.format(): String = runCatching { LocalDate.parse(this).format(BILL_DATE_FMT) }.getOrDefault(this)

// per-leg night/food display, day-grouped like the template (domain.legDefaults). a trip
// claimed as fully zero (same-day trip with no accommodation/food claimed at all) shows "-"
// on every leg instead of just the first day's slot.
private fun TripEdit.toBillTrip(): BillTrip {
    val defaults = legDefaults(legs.map { Leg(fare = it.fare, depDate = it.depDate) }, candidate.primary.endDate)
    val zeroed = nights == 0 && foodDays == 0.0
    val billLegs = legs.zip(defaults).map { (leg, d) ->
        val (n, f) = if (zeroed) 0 to 0.0 else d
        BillLeg(
            depDate = leg.depDate, depTime = leg.depTime, depPlace = leg.depPlace,
            arrDate = leg.arrDate, arrTime = leg.arrTime, arrPlace = leg.arrPlace,
            mode = leg.mode, travelClass = leg.travelClass, fare = leg.fare, remarks = leg.remarks,
            nightStay = n, foodDay = f,
        )
    }
    return BillTrip(purposeLine = purposeLine(candidate.primary), legs = billLegs, nights = nights, foodDays = foodDays)
}

// same trip, shaped for the frozen archive instead of a live PDF render -- raw leg fields
// only, no derived night/food-per-leg (see domain/BillSnapshot.kt doc comment).
private fun TripEdit.toSnapshotTrip(): SnapshotTrip = SnapshotTrip(
    tripId = candidate.trip.id,
    purposeLine = purposeLine(candidate.primary),
    nights = nights,
    foodDays = foodDays,
    legs = legs.map { leg ->
        SnapshotLeg(
            depDate = leg.depDate, depTime = leg.depTime, depPlace = leg.depPlace,
            arrDate = leg.arrDate, arrTime = leg.arrTime, arrPlace = leg.arrPlace,
            mode = leg.mode, travelClass = leg.travelClass, fare = leg.fare, remarks = leg.remarks,
        )
    },
)

private fun shareBillPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share TA/DA bill"))
}

private fun saveToDownloads(context: Context, file: File): Boolean = runCatching {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        file.copyTo(File(dir, file.name), overwrite = true)
    }
    true
}.getOrDefault(false)
