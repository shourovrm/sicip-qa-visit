// add/edit a leave. same screen both ways: leaveId != null -> prefill + update in place.
// cancelling is a status flip (status=cancelled), never a delete -- a leave stays visible in
// history (and on the Team tab) once colleagues have seen it.
package bd.sicip.qavisit.ui.leaves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Leave
import bd.sicip.qavisit.data.db.LeaveDao
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.showDatePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private val LEAVE_TYPES = listOf("Casual", "Sick", "Emergency", "Others")
private const val NO_COLLEAGUE = "None (no one)"

@Composable
fun LeaveForm(officerId: String, db: AppDb, leaveId: String? = null, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var existing by remember { mutableStateOf<Leave?>(null) }
    var loaded by remember { mutableStateOf(leaveId == null) }
    var colleagues by remember { mutableStateOf<List<Officer>>(emptyList()) }

    var type by remember { mutableStateOf(LEAVE_TYPES.first()) }
    var reason by remember { mutableStateOf("") }
    var informedOfficerId by remember { mutableStateOf<String?>(null) }
    var startDate by remember { mutableStateOf(Instant.now().toString().take(10)) }
    var endDate by remember { mutableStateOf(Instant.now().toString().take(10)) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(officerId) {
        colleagues = db.officerDao().all().filter { it.id != officerId }
    }

    LaunchedEffect(leaveId) {
        if (leaveId != null) {
            val row = db.leaveDao().byId(leaveId)
            existing = row
            row?.let {
                type = it.type
                reason = it.reason ?: ""
                informedOfficerId = it.informedOfficerId
                startDate = it.startDate
                endDate = it.endDate
            }
            loaded = true
        }
    }

    if (!loaded) return

    val informedOptions = listOf(NO_COLLEAGUE) + colleagues.map { it.name }
    val informedLabel = colleagues.firstOrNull { it.id == informedOfficerId }?.name ?: NO_COLLEAGUE
    val cancelled = existing?.status == "cancelled"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (leaveId == null) "Add leave" else "Edit leave",
            style = MaterialTheme.typography.titleLarge,
        )

        PickerDropdown("Type", LEAVE_TYPES, type, { type = it })

        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        PickerDropdown(
            label = "Informed colleague (optional)",
            options = informedOptions,
            selected = informedLabel,
            onSelect = { name -> informedOfficerId = colleagues.firstOrNull { it.name == name }?.id },
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

        Button(
            onClick = {
                scope.launch {
                    saveLeave(
                        dao = db.leaveDao(),
                        existing = existing,
                        officerId = officerId,
                        type = type,
                        reason = reason.ifBlank { null },
                        informedOfficerId = informedOfficerId,
                        startDate = startDate,
                        endDate = endDate,
                    )
                    onDone()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
            shape = RoundedCornerShape(99),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Save") }

        // cancelling only makes sense on an existing, not-yet-cancelled leave.
        if (existing != null && !cancelled) {
            OutlinedButton(
                onClick = { showCancelConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Cancel leave") }
        }
    }

    if (showCancelConfirm) {
        val target = existing ?: return
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel leave?") },
            text = { Text("This marks the ${target.type.lowercase()} leave (${target.startDate} – ${target.endDate}) as cancelled.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            db.leaveDao().upsert(target.copy(status = "cancelled", updatedAt = Instant.now().toString(), dirty = true))
                            onDone()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Cancel leave") }
            },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("Keep it") } },
        )
    }
}

suspend fun saveLeave(
    dao: LeaveDao,
    existing: Leave?,
    officerId: String,
    type: String,
    reason: String?,
    informedOfficerId: String?,
    startDate: String,
    endDate: String,
) {
    val now = Instant.now().toString()
    dao.upsert(
        Leave(
            id = existing?.id ?: UUID.randomUUID().toString(),
            officerId = officerId,
            type = type,
            reason = reason,
            informedOfficerId = informedOfficerId,
            startDate = startDate,
            endDate = endDate,
            status = existing?.status ?: "scheduled",
            updatedAt = now,
            deleted = existing?.deleted ?: false,
            dirty = true,
        ),
    )
}
