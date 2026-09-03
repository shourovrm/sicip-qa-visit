// read-only view of another officer's visit: same fields VisitForm edits, plain rows instead
// of inputs, no Save/Delete.
package bd.sicip.qavisit.ui.visits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.domain.CATEGORY_LABELS

@Composable
fun VisitDetail(visit: Visit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row2("Institute", visit.institute)
        Row2("Association", visit.association)
        Row2("District", if (visit.district == "Dhaka" && visit.dhakaMetro == true) "${visit.district} (metro)" else visit.district)
        Row2("Purpose", visit.purpose)
        if (visit.refNo != null) Row2("Ref no", visit.refNo)
        if (visit.refDate != null) Row2("Ref date", visit.refDate)
        Row2("Start date", visit.startDate)
        Row2("End date", visit.endDate)
        Row2("Category", CATEGORY_LABELS[visit.category] ?: visit.category)
        if (visit.remarks != null) Row2("Remarks", visit.remarks)
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
