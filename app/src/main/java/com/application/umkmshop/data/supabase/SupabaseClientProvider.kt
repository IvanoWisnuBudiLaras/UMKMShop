package com.application.umkmshop.data.supabase

import com.application.umkmshop.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseClientProvider {
    const val MISSING_CONFIG_MESSAGE = "Konfigurasi Supabase belum tersedia."

    val hasValidConfig: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.startsWith("sb_publishable_")

    val client by lazy {
        require(hasValidConfig) { MISSING_CONFIG_MESSAGE }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            httpEngine = OkHttp.create()
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
