// QA visit pack for the institute (document checklist PDF + table templates Word file), bundled
// from shared/visit-kit/ as an asset. "Templates" on a QA visit card copies it to the cache and
// opens the share sheet (WhatsApp, email, Drive...). Same zip the web Home offers for download.
package bd.sicip.qavisit.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

private const val VISIT_KIT_ASSET = "SICIP-QA-visit-templates.zip"
// matches the authority declared in AndroidManifest.xml (bill and report PDFs use it too)
private const val FILE_PROVIDER_AUTHORITY = "bd.sicip.qavisit.fileprovider"

fun shareVisitKit(context: Context) {
    val file = File(context.cacheDir, VISIT_KIT_ASSET)
    context.assets.open(VISIT_KIT_ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Send QA visit templates"))
}

// one tappable line under a QA visit card's details
@Composable
fun VisitKitLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier.padding(top = 8.dp).clickable { shareVisitKit(context) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text("Templates (checklist + tables)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
