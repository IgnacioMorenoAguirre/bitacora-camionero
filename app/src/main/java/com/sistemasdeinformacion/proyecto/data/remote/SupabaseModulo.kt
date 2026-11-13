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

/** Enlace del correo de recuperación (debe estar en Authentication → URL Configuration de Supabase). */
const val ESQUEMA_ENLACE = "bitacora"
const val HOST_RECUPERAR = "recuperar"
const val ENLACE_RECUPERAR = "$ESQUEMA_ENLACE://$HOST_RECUPERAR"

fun crearSupabase(sesion: SessionManager): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            sessionManager = sesion
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            // handleDeeplinks solo acepta enlaces con este esquema y host (flujo implícito: la sesión
            // de recuperación llega en el fragmento del enlace).
            scheme = ESQUEMA_ENLACE
            host = HOST_RECUPERAR
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
