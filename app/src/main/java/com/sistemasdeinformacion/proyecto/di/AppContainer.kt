package com.sistemasdeinformacion.proyecto.di

import android.content.Context
import com.sistemasdeinformacion.proyecto.core.RelojSistema
import com.sistemasdeinformacion.proyecto.data.local.AppDatabase
import com.sistemasdeinformacion.proyecto.data.local.CamionEntity
import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import com.sistemasdeinformacion.proyecto.data.prefs.Preferencias
import com.sistemasdeinformacion.proyecto.data.prefs.observarConexion
import com.sistemasdeinformacion.proyecto.data.remote.SesionCifrada
import com.sistemasdeinformacion.proyecto.data.remote.crearSupabase
import com.sistemasdeinformacion.proyecto.data.remote.supabaseConfigurado
import com.sistemasdeinformacion.proyecto.data.repo.UbicacionRepositorioFused
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorioRoom
import com.sistemasdeinformacion.proyecto.sync.DestinoSupabase
import com.sistemasdeinformacion.proyecto.sync.OrigenLocalRoom
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSync
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSyncLocal
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSyncWorkManager
import com.sistemasdeinformacion.proyecto.sync.Sincronizador
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Inyección manual de dependencias: aquí se construyen la base local, el cliente remoto y los repositorios. */
class AppContainer(val context: Context) {

    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy { AppDatabase.crear(context) }
    val preferencias by lazy { Preferencias(context) }
    val conexion by lazy { observarConexion(context) }
    val ubicaciones by lazy { UbicacionRepositorioFused(context) }

    /**
     * Perfil de demostración mientras no existe el login (Task 8). Se reemplaza por el perfil
     * guardado tras iniciar sesión.
     */
    val perfil = Perfil(id = "demo-chofer", nombre = "Juan Pérez", email = "chofer@demo.cl", rol = Rol.CHOFER)

    /** Cliente de Supabase; solo se crea si local.properties trae la URL y la llave del proyecto. */
    val supabase: SupabaseClient by lazy { crearSupabase(SesionCifrada(context)) }

    /** Último error de sincronización (lo escribe el SyncWorker, lo muestra el indicador). */
    val errorSync = MutableStateFlow<String?>(null)

    /** null sin Supabase configurado: los datos quedan en el teléfono hasta que exista el proyecto. */
    val sincronizador: Sincronizador? by lazy {
        if (!supabaseConfigurado) return@lazy null
        Sincronizador(
            local = OrigenLocalRoom(db.syncDao()),
            remoto = DestinoSupabase(supabase),
            marca = { preferencias.marcaBajada() },
            guardarMarca = { preferencias.guardarMarcaBajada(it) },
            // Solo el chofer sincroniza su bitácora; sin sesión iniciada no se hace nada.
            choferId = { preferencias.perfil.first()?.takeIf { it.rol == Rol.CHOFER }?.id },
        )
    }

    val programadorSync: ProgramadorSync by lazy {
        val pendientes = db.syncDao().contarPendientes()
        if (supabaseConfigurado) {
            ProgramadorSyncWorkManager(context, pendientes, conexion, errorSync, preferencias.viajesRestaurados, alcance).also {
                it.periodico()
                it.alGuardar() // por si quedaron pendientes de una sesión anterior
            }
        } else {
            ProgramadorSyncLocal(pendientes, conexion)
        }
    }

    val viajes: ViajesRepositorio by lazy {
        ViajesRepositorioRoom(db, { perfil.id }, RelojSistema, ubicaciones, programadorSync)
    }

    init {
        // Camiones de ejemplo para la demo; en la versión conectada se bajan desde Supabase.
        alcance.launch {
            val dao = db.camionDao()
            if (dao.observarTodos().first().isEmpty()) {
                dao.guardar(
                    listOf(
                        CamionEntity("camion-1", "KFZT21", "Volvo", "FH 460", 3.0),
                        CamionEntity("camion-2", "HJRP54", "Scania", "R 450", 3.2),
                        CamionEntity("camion-3", "BBCL87", "Mercedes-Benz", "Actros 2645", 2.9),
                    )
                )
            }
        }
    }
}
