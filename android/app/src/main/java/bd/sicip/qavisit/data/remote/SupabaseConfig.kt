// project url + anon("publishable") key. public-safe by design: RLS on the backend
// guards every table, this key only unlocks what policies allow. see supabase/config.json.
package bd.sicip.qavisit.data.remote

object SupabaseConfig {
    const val URL = "https://twpehiqfdjfprtwsiwqp.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_BgNHkPrbLh6LVZWOg1wm3g_gARFjZ_g"
}
