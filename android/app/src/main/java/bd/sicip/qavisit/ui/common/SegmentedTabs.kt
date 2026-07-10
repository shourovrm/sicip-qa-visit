// two-tab segmented control, shared by Visits (Personal|Team) and Team (Status|Rank).
package bd.sicip.qavisit.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TwoTabRow(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        SegmentedButton(
            selected = leftSelected,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(leftLabel) }
        SegmentedButton(
            selected = !leftSelected,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(rightLabel) }
    }
}
