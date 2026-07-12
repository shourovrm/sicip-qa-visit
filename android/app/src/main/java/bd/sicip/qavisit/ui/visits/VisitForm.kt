// schedule/edit a visit. same screen both ways: visitId != null -> prefill + update in place.
// mirrors every field on the original paper/Google Form. Category only exists once a visit is
// done (assigned by the finish-trip flow) -- the dropdown only shows when editing a done visit;
// scheduled/new visits keep computing the autoCategory suggestion silently and save it, same as
// before, they just don't surface it until there's something to review.
package bd.sicip.qavisit.ui.visits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.db.VisitDao
import bd.sicip.qavisit.data.seed.ASSOCIATIONS
import bd.sicip.qavisit.data.seed.DISTRICTS
import bd.sicip.qavisit.data.seed.PURPOSES
import bd.sicip.qavisit.domain.CATEGORY_LABELS
import bd.sicip.qavisit.domain.POINTS
import bd.sicip.qavisit.domain.autoCategory
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.showDatePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private val CATEGORY_OPTIONS = POINTS.keys.toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitForm(
    officerId: String,
    visitDao: VisitDao,
    visitId: String? = null,
    tripId: String? = null,
    forceAdditional: Boolean = false,
    // prefill for a brand-new visit only (Home's "+ Visit" clones date+district from another
    // scheduled card); ignored once visitId != null -- the byId LaunchedEffect below wins.
    initialDistrict: String? = null,
    initialStartDate: String? = null,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var existing by remember { mutableStateOf<Visit?>(null) }
    var loaded by remember { mutableStateOf(visitId == null) }

    var institute by remember { mutableStateOf("") }
    var association by remember { mutableStateOf(ASSOCIATIONS.first()) }
    var district by remember { mutableStateOf(initialDistrict ?: DISTRICTS.first()) }
    var dhakaMetro by remember { mutableStateOf(false) }
    var purpose by remember { mutableStateOf(PURPOSES.first()) }
    var refNo by remember { mutableStateOf("") }
    var refDate by remember { mutableStateOf<String?>(null) }
    var startDate by remember { mutableStateOf(initialStartDate ?: Instant.now().toString().take(10)) }
    var endDate by remember { mutableStateOf(initialStartDate ?: Instant.now().toString().take(10)) }
    var category by remember { mutableStateOf("N/A") }
    var categoryTouched by remember { mutableStateOf(false) } // true once the user picks a value themselves
    var remarks by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var refOptions by remember { mutableStateOf(emptyList<String>()) }
    var instituteOptions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) { refOptions = visitDao.distinctRefs() }
    LaunchedEffect(Unit) { instituteOptions = visitDao.distinctInstitutes() }

    LaunchedEffect(visitId) {
        if (visitId != null) {
            val row = visitDao.byId(visitId)
            existing = row
            row?.let {
                institute = it.institute
                association = it.association
                district = it.district
                dhakaMetro = it.dhakaMetro ?: false
                purpose = it.purpose
                refNo = it.refNo ?: ""
                refDate = it.refDate
                startDate = it.startDate
                endDate = it.endDate
                category = it.category
                categoryTouched = it.categoryOverride
                remarks = it.remarks ?: ""
            }
        }
        loaded = true
    }

    if (!loaded) return

    // what the app would pick on its own right now; N/A for ad-hoc adds (scored only at trip
    // finish), else the span/district rule. picking anything else in the dropdown overrides it.
    val isAdditional = forceAdditional || (existing?.isAdditional ?: false)
    val auto = if (isAdditional) "N/A" else autoCategory(startDate, endDate, district, dhakaMetro)
    LaunchedEffect(auto) { if (!categoryTouched) category = auto }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (visitId == null) "Schedule visit" else "Edit visit",
            style = MaterialTheme.typography.titleLarge,
        )

        // institute: type-to-filter over every institute ever visited (any officer, synced),
        // free typing still commits (onTextChange) -- same pattern as the ref-no picker below.
        PickerDropdown(
            label = "Institute",
            options = instituteOptions,
            selected = institute,
            onSelect = { institute = it },
            onTextChange = { institute = it },
            searchable = true,
        )

        PickerDropdown("Association", ASSOCIATIONS, association, { association = it }, searchable = true)
        PickerDropdown("District", DISTRICTS, district, { district = it }, searchable = true)

        if (district == "Dhaka") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !dhakaMetro,
                    onClick = { dhakaMetro = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Outside metro") }
                SegmentedButton(
                    selected = dhakaMetro,
                    onClick = { dhakaMetro = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Inside metro") }
            }
        }

        PickerDropdown("Purpose", PURPOSES, purpose, { purpose = it })

        // ref no: type-to-filter over every ref_no ever used (distinctRefs), free typing still
        // commits (onTextChange), tapping a suggestion also pulls in its last ref_date.
        PickerDropdown(
            label = "Ref no (optional)",
            options = refOptions,
            selected = refNo,
            onSelect = { picked ->
                refNo = picked
                scope.launch { visitDao.refDateFor(picked)?.let { refDate = it } }
            },
            onTextChange = { refNo = it },
            searchable = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { showDatePicker(context, refDate ?: startDate) { refDate = it } },
                modifier = Modifier.height(48.dp),
            ) { Text(if (refDate != null) "Ref date: $refDate" else "Ref date (optional)") }
            if (refDate != null) {
                TextButton(onClick = { refDate = null }) { Text("Clear") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showDatePicker(context, startDate) { startDate = it } },
                modifier = Modifier.height(48.dp),
            ) { Text("Start: $startDate") }
            OutlinedButton(
                onClick = { showDatePicker(context, endDate) { endDate = it } },
                modifier = Modifier.height(48.dp),
            ) { Text("End: $endDate") }
        }

        // category only exists once a visit is done -- finish-trip assigns it. scheduled/new
        // visits keep computing+saving the auto suggestion (LaunchedEffect(auto) above), just
        // without a field to show it in.
        if (existing?.status == "done") {
            PickerDropdown(
                label = "Category",
                options = CATEGORY_OPTIONS,
                selected = category,
                onSelect = { category = it; categoryTouched = true },
                displayLabel = { CATEGORY_LABELS[it] ?: it },
            )
            Text(
                "Auto: $auto — change to override",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text("Remarks (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                scope.launch {
                    saveVisit(
                        dao = visitDao,
                        existing = existing,
                        officerId = officerId,
                        tripId = tripId,
                        forceAdditional = forceAdditional,
                        institute = institute,
                        association = association,
                        district = district,
                        dhakaMetro = if (district == "Dhaka") dhakaMetro else null,
                        purpose = purpose,
                        refNo = refNo.ifBlank { null },
                        refDate = refDate,
                        startDate = startDate,
                        endDate = endDate,
                        category = category,
                        categoryOverride = category != auto,
                        remarks = remarks.ifBlank { null },
                    )
                    onDone()
                }
            },
            enabled = institute.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
            shape = RoundedCornerShape(99),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Save") }

        // delete only makes sense on an existing row -- new/unsaved visits have nothing to soft-delete.
        if (existing != null) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Delete visit") }
        }
    }

    if (showDeleteConfirm) {
        val target = existing ?: return
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete visit?") },
            text = { Text("This removes \"${target.institute}\" from your visit list. This can't be undone from the app.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            visitDao.softDelete(target.id, Instant.now().toString())
                            onDone()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

// category/categoryOverride are resolved by the caller (dropdown value vs. the live auto
// suggestion) -- this just persists them, same as every other field.
suspend fun saveVisit(
    dao: VisitDao,
    existing: Visit?,
    officerId: String,
    tripId: String?,
    forceAdditional: Boolean,
    institute: String,
    association: String,
    district: String,
    dhakaMetro: Boolean?,
    purpose: String,
    refNo: String?,
    refDate: String?,
    startDate: String,
    endDate: String,
    category: String,
    categoryOverride: Boolean,
    remarks: String?,
) {
    val now = Instant.now().toString()
    val isAdditional = forceAdditional || (existing?.isAdditional ?: false)
    dao.upsert(
        Visit(
            id = existing?.id ?: UUID.randomUUID().toString(),
            officerId = officerId,
            tripId = tripId ?: existing?.tripId,
            institute = institute,
            association = association,
            district = district,
            dhakaMetro = dhakaMetro,
            purpose = purpose,
            refNo = refNo,
            refDate = refDate,
            startDate = startDate,
            endDate = endDate,
            category = category,
            categoryOverride = categoryOverride,
            isAdditional = isAdditional,
            status = existing?.status ?: "scheduled",
            remarks = remarks,
            source = existing?.source ?: "app",
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deleted = existing?.deleted ?: false,
            dirty = true,
        ),
    )
}
