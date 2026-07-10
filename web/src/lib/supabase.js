// supabase client -- url + publishable (anon) key are safe for the browser, baked in at build.
// values mirror ../../../supabase/config.json (kept out of web/ so vite's dev-server fs.allow
// boundary and our "web/ only" ownership both stay clean -- resync by hand if that file changes).
import { createClient } from '@supabase/supabase-js'

const SUPABASE_URL = 'https://twpehiqfdjfprtwsiwqp.supabase.co'
const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_BgNHkPrbLh6LVZWOg1wm3g_gARFjZ_g'

export const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY)
