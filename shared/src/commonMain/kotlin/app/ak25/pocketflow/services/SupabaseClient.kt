package app.ak25.pocketflow.services

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions

object SupabaseConfig {
    const val URL = "https://rbdwfyavuiltcqqlnwml.supabase.co"
    const val ANON_KEY = "sb_publishable_pIYlY_BPRdeLaD7Ig0wSBQ_o1DHFxpV"
    
    // Table names
    const val TABLE_WORKFLOWS = "workflows"
    const val TABLE_PRESENCE = "presence"
    const val BUCKET_IMAGES = "node-images"
}

val supabaseClient = createSupabaseClient(
    supabaseUrl = SupabaseConfig.URL,
    supabaseKey = SupabaseConfig.ANON_KEY
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
    install(Storage)
    install(Functions)
}
