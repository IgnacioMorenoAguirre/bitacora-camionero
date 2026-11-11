package com.sistemasdeinformacion.proyecto.di

import android.content.Context
import com.sistemasdeinformacion.proyecto.core.RelojSistema
import com.sistemasdeinformacion.proyecto.data.local.AppDatabase
import com.sistemasdeinformacion.proyecto.data.local.CamionEntity
import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import com.sistemasdeinformacion.proyecto.data.prefs.Preferencias
import com.sistemasdeinformacion.proyecto.data.prefs.observarConexion
import com.sistemasdeinformacion.proyecto.data.repo.UbicacionRepositorioFused
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorioRoom
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSync
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSyncLocal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** Sin Supabase todavía (Task 6): solo informa pendientes y conexión. */
    val programadorSync: ProgramadorSync by lazy { ProgramadorSyncLocal(db.syncDao().contarPendientes(), conexion) }

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
