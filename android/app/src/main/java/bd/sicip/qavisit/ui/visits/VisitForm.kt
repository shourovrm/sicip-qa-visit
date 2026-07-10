// schedule/edit a visit. same screen both ways: visitId != null -> prefill + update in place.
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
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.db.VisitDao
import bd.sicip.qavisit.data.seed.ASSOCIATIONS
import bd.sicip.qavisit.data.seed.DISTRICTS
import bd.sicip.qavisit.data.seed.PURPOSES
import bd.sicip.qavisit.domain.autoCategory
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.showDatePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitForm(
    officerId: String,
    visitDao: VisitDao,
    visitId: String? = null,
    tripId: String? = null,
    forceAdditional: Boolean = false,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var existing by remember { mutableStateOf<Visit?>(null) }
    var loaded by remember { mutableStateOf(visitId == null) }

    var institute by remember { mutableStateOf("") }
    var association by remember { mutableStateOf(ASSOCIATIONS.first()) }
    var district by remember { mutableStateOf(DISTRICTS.first()) }
    var dhakaMetro by remember { mutableStateOf(false) }
    var purpose by remember { mutableStateOf(PURPOSES.first()) }
    var refNo by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(Instant.now().toString().take(10)) }
    var endDate by remember { mutableStateOf(Instant.now().toString().take(10)) }
    var remarks by remember { mutableStateOf("") }

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
                startDate = it.startDate
                endDate = it.endDate
                remarks = it.remarks ?: ""
            }
            loaded = true
        }
    }

    if (!loaded) return

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

        OutlinedTextField(
            value = institute,
            onValueChange = { institute = it },
            label = { Text("Institute") },
            modifier = Modifier.fillMaxWidth(),
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

        OutlinedTextField(
            value = refNo,
            onValueChange = { refNo = it },
            label = { Text("Ref no (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

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
                        startDate = startDate,
                        endDate = endDate,
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
    }
}

// N/A for ad-hoc adds (no auto category, filled in only at trip finish for the primary visit);
// otherwise suggest from the span/district rule right away, same as the finish-trip dialog will.
// a previous override on an edited row always wins over a fresh suggestion.
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
    startDate: String,
    endDate: String,
    remarks: String?,
) {
    val now = Instant.now().toString()
    val isAdditional = forceAdditional || (existing?.isAdditional ?: false)
    val categoryOverride = existing?.categoryOverride ?: false
    val category = when {
        categoryOverride -> existing.category
        isAdditional -> "N/A"
        else -> autoCategory(startDate, endDate, district, dhakaMetro)
    }
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
