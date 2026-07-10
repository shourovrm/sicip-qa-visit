// one dropdown for every picklist in the app (district/association/purpose/mode/class/officer).
// searchable = true adds a type-to-filter text field; the 64-district and association lists
// need it, short lists (purpose, transport mode/class) don't but it's harmless either way.
package bd.sicip.qavisit.ui.common

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    searchable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(selected) { mutableStateOf(selected) }
    val filtered = if (searchable && query.isNotBlank()) {
        options.filter { it.contains(query, ignoreCase = true) }
    } else {
        options
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            readOnly = !searchable,
            label = { Text(label) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; query = selected }) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); query = option; expanded = false },
                )
            }
        }
    }
}
