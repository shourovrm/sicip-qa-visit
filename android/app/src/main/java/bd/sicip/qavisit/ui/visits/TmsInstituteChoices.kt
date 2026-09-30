// Institute field source on the visit form: TMS institutes for Monitoring Visits when the
// officer is logged in to TMS, else the past-visits list. never blocks typing a name by hand.
package bd.sicip.qavisit.ui.visits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import bd.sicip.qavisit.data.tms.TmsApiChangeException
import bd.sicip.qavisit.data.tms.TmsAuthState
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.data.tms.TmsTransientException
import bd.sicip.qavisit.data.tms.defaultTranche
import bd.sicip.qavisit.data.tms.filterByDistrict
import bd.sicip.qavisit.data.tms.matchPartner
import kotlinx.coroutines.CancellationException

const val MONITORING_PURPOSE = "Monitoring Visit"

// tmsNames != null: the list came from TMS. note: small helper line under the field, if any.
class TmsInstituteChoices(val tmsNames: List<String>? = null, val note: String? = null, val noteIsError: Boolean = false)

fun tmsListUnavailableText(error: Throwable): String = when (error) {
    is TmsApiChangeException -> "TMS list unavailable (TMS has changed; the admin is informed) — type the institute name"
    is TmsTransientException -> "No connection to TMS — type the institute name"
    else -> "TMS list unavailable — type the institute name"
}

@Composable
fun rememberTmsInstituteChoices(purpose: String, association: String, district: String): TmsInstituteChoices {
    val services = TmsServices.get(LocalContext.current)
    val authState by services.auth.state.collectAsState()
    var choices by remember { mutableStateOf(TmsInstituteChoices()) }
    val loggedIn = authState is TmsAuthState.LoggedIn

    LaunchedEffect(purpose, association, district, loggedIn) {
        if (purpose != MONITORING_PURPOSE) {
            choices = TmsInstituteChoices()
            return@LaunchedEffect
        }
        if (!loggedIn) {
            choices = TmsInstituteChoices(note = "Log in to TMS in Settings to pick from TMS")
            return@LaunchedEffect
        }
        try {
            val partner = matchPartner(services.lookup.entities(), association)
            val tranche = defaultTranche(services.lookup.tranches())
            choices = if (partner == null || tranche == null) {
                TmsInstituteChoices() // e.g. "Others": no TMS partner, past visits only
            } else {
                val institutes = filterByDistrict(services.lookup.institutes(partner.id, tranche.id), district)
                TmsInstituteChoices(tmsNames = institutes.map { it.name }.filter { it.isNotBlank() }.distinct().sorted())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            choices = TmsInstituteChoices(note = tmsListUnavailableText(e), noteIsError = true)
        }
    }
    return choices
}
