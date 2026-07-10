// status pill: rounded 99dp chip in LocalStatusColors tones (DESIGN.md "derived state only").
package bd.sicip.qavisit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.ui.theme.StatusPair

@Composable
fun StatusPill(text: String, colors: StatusPair, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = colors.ink,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(colors.bg, RoundedCornerShape(99))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
