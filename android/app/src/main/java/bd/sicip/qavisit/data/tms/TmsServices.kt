// app-wide TMS objects: one TmsAuth so login state is shared by settings, banners and editors.
package bd.sicip.qavisit.data.tms

import android.content.Context
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.remote.SupabaseClient

class TmsServices private constructor(context: Context) {
    val auth = TmsAuth(TmsVault(context))
    val api = TmsApi(auth, reporter = TmsErrorReporter(SupabaseClient(), SessionStore(context)))
    val catalog = TmsCatalog(api)
    val lookup = TmsLookup(catalog)

    companion object {
        @Volatile private var instance: TmsServices? = null

        fun get(context: Context): TmsServices = instance ?: synchronized(this) {
            instance ?: TmsServices(context.applicationContext).also { instance = it }
        }
    }
}
