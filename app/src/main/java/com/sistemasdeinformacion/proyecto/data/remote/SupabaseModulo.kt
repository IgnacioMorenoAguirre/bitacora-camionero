package com.sistemasdeinformacion.proyecto.data.remote

import com.sistemasdeinformacion.proyecto.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
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
            // handleDeeplinks solo acepta enlaces con este esquema y host.
            scheme = ESQUEMA_ENLACE
            host = HOST_RECUPERAR
            // PKCE: el enlace del correo trae un código de un solo uso (?code=...) que solo sirve junto
            // al verificador guardado en este teléfono. Con el flujo implícito el enlace llevaría los
            // tokens, y otra app que registre el esquema bitacora:// podría robarlos. Iniciar sesión
            // con correo y contraseña no cambia.
            flowType = FlowType.PKCE
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
