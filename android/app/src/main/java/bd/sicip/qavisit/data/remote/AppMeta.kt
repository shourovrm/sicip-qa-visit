// update-notice metadata: key/value rows in app_meta (latest_version, apk_url). single cheap
// select, no dedicated endpoint -- reuses SupabaseClient.select like everything else here.
package bd.sicip.qavisit.data.remote

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AppMeta(val latestVersion: String, val apkUrl: String)

suspend fun SupabaseClient.fetchAppMeta(accessToken: String): AppMeta {
    val rows = select("app_meta", mapOf("select" to "key,value"), accessToken)
    val values = rows.associate { row ->
        val obj = row.jsonObject
        obj.getValue("key").jsonPrimitive.content to obj.getValue("value").jsonPrimitive.content
    }
    return AppMeta(
        latestVersion = values["latest_version"] ?: "",
        apkUrl = values["apk_url"] ?: "",
    )
}
