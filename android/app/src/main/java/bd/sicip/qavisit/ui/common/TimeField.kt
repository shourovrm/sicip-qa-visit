// compact time entry: "h:mm" text field (12h, numeric keys) + AM/PM toggle + clock icon that
// falls back to the native showTimePicker. Replaces the old "HH:mm" OutlinedButton everywhere.
package bd.sicip.qavisit.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// 24h "HH:mm" or "HH:mm:ss" -> ("h:mm" 12h display, isPM). unparsable -> midnight.
fun to12h(time24: String): Pair<String, Boolean> {
    val (h, m) = parse24h(time24) ?: (0 to 0)
    val isPM = h >= 12
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${m.toString().padStart(2, '0')}" to isPM
}

// "h:mm" 12h (hour 1-12) + isPM -> "HH:mm:00" 24h. invalid display -> noon/midnight per isPM.
fun to24h(display12: String, isPM: Boolean): String {
    val (h, m) = parseTime(display12) ?: (12 to 0)
    val h24 = when {
        h == 12 && !isPM -> 0
        h == 12 -> 12
        isPM -> h + 12
        else -> h
    }
    return "%02d:%02d:00".format(h24, m)
}

// raw digits typed into the text field -> "h:mm". <=2 digits: still typing the hour, no colon
// yet. >=3 digits: last 2 are minutes, whatever's left is the hour. caps at 4 digits (h:mm max).
fun formatDigits(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(4)
    if (digits.length <= 2) return digits
    return "${digits.dropLast(2)}:${digits.takeLast(2)}"
}

// "h:mm" -> (hour, minute) if hour in 1-12 and minute in 0-59, else null (isError trigger).
fun parseTime(display: String): Pair<Int, Int>? {
    val parts = display.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 1..12 || m !in 0..59) return null
    return h to m
}

private fun parse24h(time24: String): Pair<Int, Int>? {
    val parts = time24.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

@Composable
fun TimeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (validDisplay, validIsPM) = remember(value) { to12h(value) }
    var text by remember(value) { mutableStateOf(validDisplay) }
    var isPM by remember(value) { mutableStateOf(validIsPM) }
    var isError by remember(value) { mutableStateOf(false) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = formatDigits(raw)
                val parsed = parseTime(text)
                isError = parsed == null
                if (parsed != null) onChange(to24h(text, isPM))
            },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp).onFocusChanged { focus ->
                if (!focus.isFocused && isError) {
                    text = validDisplay // revert to last committed value
                    isError = false
                }
            },
        )
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !isPM,
                onClick = { isPM = false; parseTime(text)?.let { onChange(to24h(text, false)) } },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("AM") }
            SegmentedButton(
                selected = isPM,
                onClick = { isPM = true; parseTime(text)?.let { onChange(to24h(text, true)) } },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("PM") }
        }
        IconButton(onClick = { showTimePicker(context, value) { onChange(it) } }) {
            Icon(Icons.Filled.Schedule, contentDescription = "Pick time")
        }
    }
}
