// compact time entry: hour box : minute box + AM/PM spinner + clock icon that falls back to
// the native showTimePicker. Split boxes (not one "h:mm" field) so it never overflows a dialog.
// hour/minute use BasicTextField+DecorationBox (not plain OutlinedTextField) because the sibling
// date box in Start tour's row only leaves ~150dp total -- OutlinedTextField's fixed 16dp side
// padding doesn't shrink, DecorationBox's contentPadding does.
package bd.sicip.qavisit.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// 24h "HH:mm" or "HH:mm:ss" -> (hour 1-12, minute, isPM). unparsable -> midnight.
fun to12h(time24: String): Triple<Int, Int, Boolean> {
    val (h, m) = parse24h(time24) ?: (0 to 0)
    val isPM = h >= 12
    val h12 = if (h % 12 == 0) 12 else h % 12
    return Triple(h12, m, isPM)
}

// hour(1-12) + minute + isPM -> "HH:mm:00" 24h.
fun to24h(hour: Int, minute: Int, isPM: Boolean): String {
    val h24 = when {
        hour == 12 && !isPM -> 0
        hour == 12 -> 12
        isPM -> hour + 12
        else -> hour
    }
    return "%02d:%02d:00".format(h24, minute)
}

// raw digits typed into an hour/minute box -> digits only, capped at 2 chars.
fun formatDigits(raw: String): String = raw.filter { it.isDigit() }.take(2)

fun parseHour(s: String): Int? = s.toIntOrNull()?.takeIf { it in 1..12 }
fun parseMinute(s: String): Int? = s.toIntOrNull()?.takeIf { it in 0..59 }

private fun parse24h(time24: String): Pair<Int, Int>? {
    val parts = time24.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

// narrow bordered 2-digit box: DecorationBox lets padding shrink below OutlinedTextField's fixed
// 16dp/side, which is what actually makes a sub-48dp numeric field possible.
@Composable
private fun DigitBox(value: String, onValueChange: (String) -> Unit, isError: Boolean, modifier: Modifier, onFocusLost: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.onFocusChanged { if (!it.isFocused) onFocusLost() },
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            )
        },
    )
}

@Composable
fun TimeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (validHour, validMinute, validIsPM) = remember(value) { to12h(value) }
    var hourText by remember(value) { mutableStateOf(validHour.toString()) }
    var minuteText by remember(value) { mutableStateOf(validMinute.toString().padStart(2, '0')) }
    var isPM by remember(value) { mutableStateOf(validIsPM) }
    var hourError by remember(value) { mutableStateOf(false) }
    var minuteError by remember(value) { mutableStateOf(false) }

    fun commit(h: Int?, m: Int?, pm: Boolean) {
        if (h != null && m != null) onChange(to24h(h, m, pm))
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        DigitBox(
            value = hourText,
            onValueChange = { raw ->
                hourText = formatDigits(raw)
                val h = parseHour(hourText)
                hourError = h == null
                commit(h, parseMinute(minuteText), isPM)
            },
            isError = hourError,
            modifier = Modifier.width(30.dp),
            onFocusLost = {
                if (hourError) {
                    hourText = validHour.toString()
                    hourError = false
                }
            },
        )
        Text(":", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        DigitBox(
            value = minuteText,
            onValueChange = { raw ->
                minuteText = formatDigits(raw)
                val m = parseMinute(minuteText)
                minuteError = m == null
                commit(parseHour(hourText), m, isPM)
            },
            isError = minuteError,
            modifier = Modifier.width(30.dp),
            onFocusLost = {
                if (minuteError) {
                    minuteText = validMinute.toString().padStart(2, '0')
                    minuteError = false
                }
            },
        )
        // vertical spinner: chevrons cycle the only two values, no wasted horizontal space
        Column(modifier = Modifier.width(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Switch to ${if (isPM) "AM" else "PM"}",
                modifier = Modifier.size(12.dp).clickable {
                    isPM = !isPM
                    commit(parseHour(hourText), parseMinute(minuteText), isPM)
                },
            )
            Text(if (isPM) "PM" else "AM", style = MaterialTheme.typography.labelSmall)
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Switch to ${if (isPM) "AM" else "PM"}",
                modifier = Modifier.size(12.dp).clickable {
                    isPM = !isPM
                    commit(parseHour(hourText), parseMinute(minuteText), isPM)
                },
            )
        }
        // plain clickable icon, not IconButton -- IconButton's built-in 48dp min touch target
        // guard ignores an outer Modifier.size() override and blew the budget in tight dialogs.
        Icon(
            Icons.Filled.Schedule,
            contentDescription = "Pick time",
            modifier = Modifier.size(18.dp).clickable { showTimePicker(context, value) { onChange(it) } },
        )
    }
}
