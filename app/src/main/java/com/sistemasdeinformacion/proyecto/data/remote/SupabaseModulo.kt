package com.sistemasdeinformacion.proyecto.data.remote

import com.sistemasdeinformacion.proyecto.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/** true cuando local.properties tiene la URL y la llave pública del proyecto de Supabase. */
val supabaseConfigurado: Boolean
    get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

/** Bucket privado donde se guardan las fotos y firmas de entrega. */
const val BUCKET_ADJUNTOS = "adjuntos"

fun crearSupabase(sesion: SessionManager): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            sessionManager = sesion
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
