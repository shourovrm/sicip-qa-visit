// leaves list: Personal (own, tap to edit, "Add leave" cta) / Team (everyone, read-only).
// row shows type/reason/date-range/status, same card shape as VisitsScreen for consistency.
package bd.sicip.qavisit.ui.leaves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Leave
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import bd.sicip.qavisit.ui.theme.StatusPair
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun LeavesScreen(officerId: String, db: AppDb, onAddLeave: () -> Unit, onEditLeave: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var personal by remember { mutableStateOf(true) }
    var myLeaves by remember { mutableStateOf<List<Leave>>(emptyList()) }
    var allLeaves by remember { mutableStateOf<List<Leave>>(emptyList()) }
    var officers by remember { mutableStateOf<List<Officer>>(emptyList()) }

    suspend fun reload() {
        myLeaves = db.leaveDao().byOfficer(officerId) // already deleted=0, start_date desc
        allLeaves = db.leaveDao().all()
    }

    LaunchedEffect(Unit) {
        reload()
        officers = db.officerDao().all()
    }

    // scheduled -> started -> completed, own rows only. plain re-query after write -- no Flow here.
    fun advanceStatus(leave: Leave, next: String) {
        scope.launch {
            db.leaveDao().upsert(leave.copy(status = next, updatedAt = Instant.now().toString(), dirty = true))
            SyncNow.enqueue(context)
            reload()
        }
    }

    val nameById = officers.associate { it.id to it.name }
    val leaves = if (personal) myLeaves else allLeaves

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SegmentedButton(
                selected = personal,
                onClick = { personal = true },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Personal") }
            SegmentedButton(
                selected = !personal,
                onClick = { personal = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Team") }
        }

        if (personal) {
            Button(
                onClick = onAddLeave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                shape = RoundedCornerShape(99),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
            ) { Text("Add leave") }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (leaves.isEmpty()) {
                item {
                    Text(
                        if (personal) "No leave requests yet" else "No leaves recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(leaves) { leave ->
                LeaveRow(
                    leave = leave,
                    officerName = if (personal) null else nameById[leave.officerId],
                    onClick = if (personal) ({ onEditLeave(leave.id) }) else null,
                    onStart = if (personal && leave.status == "scheduled") ({ advanceStatus(leave, "started") }) else null,
                    onEnd = if (personal && leave.status == "started") ({ advanceStatus(leave, "completed") }) else null,
                )
            }
        }
    }
}

@Composable
private fun LeaveRow(
    leave: Leave,
    officerName: String?,
    onClick: (() -> Unit)?,
    onStart: (() -> Unit)?,
    onEnd: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(16.dp)
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // weighted + ellipsized so a long officer name/reason can never squeeze the pill offscreen.
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    if (officerName != null) {
                        Text(
                            officerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(leave.type, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    leave.reason?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${leave.startDate} – ${leave.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val (label, colors) = pillFor(leave.status)
                StatusPill(label, colors)
            }
            if (onStart != null || onEnd != null) {
                Button(
                    onClick = { onStart?.invoke(); onEnd?.invoke() },
                    shape = RoundedCornerShape(99),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(40.dp),
                ) { Text(if (onStart != null) "Start leave" else "End leave") }
            }
        }
    }
    // read-only (Team) rows get the plain Card; own (Personal) rows get the clickable overload.
    if (onClick != null) {
        Card(shape = shape, modifier = Modifier.fillMaxWidth(), onClick = onClick) { body() }
    } else {
        Card(shape = shape, modifier = Modifier.fillMaxWidth()) { body() }
    }
}

@Composable
private fun pillFor(status: String): Pair<String, StatusPair> = when (status) {
    "started" -> "STARTED" to LocalStatusColors.current.onVisit
    "completed", "availed" -> "COMPLETED" to LocalStatusColors.current.success // availed = legacy, pre-007 rows until synced
    "cancelled" -> "CANCELLED" to LocalStatusColors.current.office
    else -> "SCHEDULED" to LocalStatusColors.current.onVisit
}
