// dialog: choose the TMS institute for a report. tranche = newest active (selector only when
// several are active), partner pre-selected from the visit's association, search box pre-filled
// with the report's institute text. an exact single name match is pre-selected, never linked
// without the officer tapping "Link".
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.tms.TmsEntity
import bd.sicip.qavisit.data.tms.TmsInstitute
import bd.sicip.qavisit.data.tms.TmsLink
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.data.tms.TmsTranche
import bd.sicip.qavisit.data.tms.autoLinkCandidate
import bd.sicip.qavisit.data.tms.defaultTranche
import bd.sicip.qavisit.data.tms.matchPartner
import bd.sicip.qavisit.data.tms.searchInstitutes
import bd.sicip.qavisit.ui.common.PickerDropdown
import kotlinx.coroutines.CancellationException

@Composable
fun TmsInstitutePicker(
    association: String,
    instituteText: String,
    onPick: (TmsLink) -> Unit,
    onDismiss: () -> Unit,
) {
    val lookup = TmsServices.get(LocalContext.current).lookup
    var activeTranches by remember { mutableStateOf<List<TmsTranche>>(emptyList()) }
    var trancheId by remember { mutableStateOf<Long?>(null) }
    var entities by remember { mutableStateOf<List<TmsEntity>>(emptyList()) }
    var entity by remember { mutableStateOf<TmsEntity?>(null) }
    var institutes by remember { mutableStateOf<List<TmsInstitute>>(emptyList()) }
    var query by remember { mutableStateOf(instituteText) }
    var selected by remember { mutableStateOf<TmsInstitute?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val tranches = lookup.tranches()
            activeTranches = tranches.filter { it.active }.ifEmpty { listOfNotNull(defaultTranche(tranches)) }
            trancheId = defaultTranche(tranches)?.id
            entities = lookup.entities()
            entity = matchPartner(entities, association)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "TMS request failed"
        }
        loading = false
    }

    LaunchedEffect(entity, trancheId) {
        val partner = entity ?: return@LaunchedEffect
        val tranche = trancheId ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            institutes = lookup.institutes(partner.id, tranche)
            selected = autoLinkCandidate(institutes, instituteText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            institutes = emptyList()
            error = e.message ?: "TMS request failed"
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link TMS institute") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeTranches.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeTranches.forEach { tranche ->
                            FilterChip(
                                selected = trancheId == tranche.id,
                                onClick = { trancheId = tranche.id },
                                label = { Text(tranche.label) },
                            )
                        }
                    }
                }
                PickerDropdown(
                    label = "Partner",
                    options = entities.map { it.shortName },
                    selected = entity?.shortName ?: "",
                    onSelect = { name -> entity = entities.firstOrNull { it.shortName == name } },
                    searchable = true,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search name, short name or address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else if (entity == null && error == null) {
                    Text("Pick the partner to see its institutes.", style = MaterialTheme.typography.bodySmall)
                }
                val shown = searchInstitutes(institutes, query)
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(shown, key = { it.id }) { institute ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selected = institute },
                        ) {
                            RadioButton(selected = selected?.id == institute.id, onClick = { selected = institute })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(institute.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOf(institute.shortName, institute.address).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && entity != null && trancheId != null,
                onClick = {
                    val institute = selected ?: return@TextButton
                    val partner = entity ?: return@TextButton
                    val tranche = trancheId ?: return@TextButton
                    onPick(TmsLink(tranche, partner.id, institute.id, institute.instituteNo, institute.name, institute.address))
                },
            ) { Text("Link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
