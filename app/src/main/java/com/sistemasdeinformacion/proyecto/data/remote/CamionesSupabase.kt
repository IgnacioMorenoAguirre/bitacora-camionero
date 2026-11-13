package com.sistemasdeinformacion.proyecto.data.remote

import com.sistemasdeinformacion.proyecto.data.local.CamionDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json

private val jsonCamiones = Json { ignoreUnknownKeys = true }

/** Reemplaza la copia local de camiones activos por la del servidor (los administra el administrador). */
suspend fun bajarCamiones(supabase: SupabaseClient, dao: CamionDao) {
    val camiones = supabase.from("camiones").select { filter { eq("activo", true) } }
        .data.let { jsonCamiones.decodeFromString<List<CamionDto>>(it) }
    dao.reemplazar(camiones.map { it.aEntidad() })
}
