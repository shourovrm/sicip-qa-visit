// compact time entry: ONE outlined container matching the sibling date OutlinedTextField --
// hour:minute borderless boxes + AM/PM segmented toggle + clock icon falling back to the
// native showTimePicker. Border lives on the outer Row now, not per-digit-box (v3).
package bd.sicip.qavisit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

// borderless 2-digit box -- outer Row owns the single visible border now, so this is a bare
// BasicTextField (no DecorationBox at all, unlike v2's per-box border).
@Composable
private fun DigitBox(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier,
    onFocusChanged: (Boolean) -> Unit,
    onFocusLost: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.onFocusChanged {
            onFocusChanged(it.isFocused)
            if (!it.isFocused) onFocusLost()
        },
    )
}

// one AM/PM cell: active = filled primaryContainer pill, inactive = plain text. Tap sets it
// directly (tapping the already-active cell is a no-op) -- replaces v2's chevron cycler.
@Composable
private fun AmPmCell(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(16.dp)
            .then(
                if (active) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    var hourFocused by remember { mutableStateOf(false) }
    var minuteFocused by remember { mutableStateOf(false) }

    fun commit(h: Int?, m: Int?, pm: Boolean) {
        if (h != null && m != null) onChange(to24h(h, m, pm))
    }

    fun setAmPm(pm: Boolean) {
        if (pm == isPM) return // tap on already-active cell = no-op
        isPM = pm
        commit(parseHour(hourText), parseMinute(minuteText), pm)
    }

    val borderColor = when {
        hourFocused || minuteFocused -> MaterialTheme.colorScheme.primary
        hourError || minuteError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    // 6dp gaps + 8dp padding: total ~134dp so the clock icon survives LegForm's ~140dp
    // dialog slot (10dp rhythm clipped it there)
    Row(
        modifier = modifier
            .height(52.dp)
            .border(1.dp, borderColor, OutlinedTextFieldDefaults.shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DigitBox(
            value = hourText,
            onValueChange = { raw ->
                hourText = formatDigits(raw)
                val h = parseHour(hourText)
                hourError = h == null
                commit(h, parseMinute(minuteText), isPM)
            },
            isError = hourError,
            modifier = Modifier.width(24.dp),
            onFocusChanged = { hourFocused = it },
            onFocusLost = {
                if (hourError) {
                    hourText = validHour.toString()
                    hourError = false
                }
            },
        )
        Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DigitBox(
            value = minuteText,
            onValueChange = { raw ->
                minuteText = formatDigits(raw)
                val m = parseMinute(minuteText)
                minuteError = m == null
                commit(parseHour(hourText), m, isPM)
            },
            isError = minuteError,
            modifier = Modifier.width(24.dp),
            onFocusChanged = { minuteFocused = it },
            onFocusLost = {
                if (minuteError) {
                    minuteText = validMinute.toString().padStart(2, '0')
                    minuteError = false
                }
            },
        )
        Column(modifier = Modifier.width(26.dp)) {
            AmPmCell("AM", active = !isPM, onClick = { setAmPm(false) })
            AmPmCell("PM", active = isPM, onClick = { setAmPm(true) })
        }
        // plain clickable icon, not IconButton -- IconButton's built-in 48dp min touch target
        // guard ignores an outer Modifier.size() override and blew the budget in tight dialogs.
        Icon(
            Icons.Filled.Schedule,
            contentDescription = "Pick time",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).clickable { showTimePicker(context, value) { onChange(it) } },
        )
    }
}
