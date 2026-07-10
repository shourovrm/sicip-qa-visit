// one dropdown for every picklist in the app (district/association/purpose/mode/class/officer).
// searchable = true adds a type-to-filter text field; the 64-district and association lists
// need it, short lists (purpose, transport mode/class) don't but it's harmless either way.
package bd.sicip.qavisit.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
    // set for fields where free typing (not just picking a suggestion) is a valid value, e.g.
    // ref no -- every keystroke commits to the caller's state, tapping a suggestion still goes
    // through onSelect so callers can attach extra side effects (autofill) to the tap only.
    onTextChange: ((String) -> Unit)? = null,
    // options/selected stay the stored value (e.g. a category code); this only controls what's
    // shown for them (e.g. the category's full "code — span (pts)" explanation).
    displayLabel: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(selected) { mutableStateOf(displayLabel(selected)) }
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
            onValueChange = { query = it; expanded = true; onTextChange?.invoke(it) },
            readOnly = !searchable,
            label = { Text(label) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; query = displayLabel(selected) }) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayLabel(option)) },
                    onClick = { onSelect(option); query = displayLabel(option); expanded = false },
                )
            }
        }
    }
}

// filter-row chip: shows [label] while at its default (allValue), or the current value with a
// trailing X to clear once picked. tap opens a plain dropdown menu of the options -- used for the
// Visits filter row (district/category/purpose/officer), one instance per filter.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    allValue: String = "All",
    displayText: String = selected,
    // options/selected stay the stored value; this only controls what's shown for them.
    displayLabel: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = selected != allValue
    Box(modifier) {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(if (active) displayLabel(displayText) else label) },
            trailingIcon = if (active) {
                {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear $label",
                        modifier = Modifier.size(FilterChipDefaults.IconSize).clickable { onSelect(allValue) },
                    )
                }
            } else {
                null
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(displayLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
