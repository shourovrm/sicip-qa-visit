// native date/time pickers (rung 4: platform dialogs beat hand-rolled compose ones).
// plain functions, not composables -- call from a click handler with LocalContext.current.
package bd.sicip.qavisit.ui.common

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

// isoDate: "" or unparsable -> today. callback gets an ISO "yyyy-MM-dd" string.
fun showDatePicker(context: Context, isoDate: String, onPicked: (String) -> Unit) {
    val base = runCatching { LocalDate.parse(isoDate) }.getOrDefault(LocalDate.now())
    DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d).toString()) },
        base.year, base.monthValue - 1, base.dayOfMonth,
    ).show()
}

// isoTime: "" or unparsable -> now. callback gets "HH:mm:00" (postgres `time` column shape).
fun showTimePicker(context: Context, isoTime: String, onPicked: (String) -> Unit) {
    val base = runCatching { LocalTime.parse(isoTime) }.getOrDefault(LocalTime.now())
    TimePickerDialog(
        context,
        { _, h, min -> onPicked(String.format("%02d:%02d:00", h, min)) },
        base.hour, base.minute, true,
    ).show()
}
