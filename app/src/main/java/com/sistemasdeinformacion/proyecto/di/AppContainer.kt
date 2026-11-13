package com.sistemasdeinformacion.proyecto.di

import android.content.Context
import com.sistemasdeinformacion.proyecto.core.RelojSistema
import com.sistemasdeinformacion.proyecto.data.local.AppDatabase
import com.sistemasdeinformacion.proyecto.data.local.borrarTodo
import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.prefs.Preferencias
import com.sistemasdeinformacion.proyecto.data.prefs.observarConexion
import com.sistemasdeinformacion.proyecto.data.remote.AuthApiSinServidor
import com.sistemasdeinformacion.proyecto.data.remote.AuthApiSupabase
import com.sistemasdeinformacion.proyecto.data.remote.FuentePerfilSupabase
import com.sistemasdeinformacion.proyecto.data.remote.SesionCifrada
import com.sistemasdeinformacion.proyecto.data.remote.bajarCamiones
import com.sistemasdeinformacion.proyecto.data.remote.crearSupabase
import com.sistemasdeinformacion.proyecto.data.remote.supabaseConfigurado
import com.sistemasdeinformacion.proyecto.data.repo.AuthRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.AuthRepositorioSupabase
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAuth
import com.sistemasdeinformacion.proyecto.data.repo.FuentePerfil
import com.sistemasdeinformacion.proyecto.data.repo.UbicacionRepositorioFused
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorioRoom
import com.sistemasdeinformacion.proyecto.feature.auth.EnlaceRecuperacion
import com.sistemasdeinformacion.proyecto.sync.DestinoSupabase
import com.sistemasdeinformacion.proyecto.sync.OrigenLocalRoom
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSync
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSyncLocal
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSyncWorkManager
import com.sistemasdeinformacion.proyecto.sync.Sincronizador
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Inyección manual de dependencias: aquí se construyen la base local, el cliente remoto y los repositorios. */
class AppContainer(val context: Context) {

    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy { AppDatabase.crear(context) }
    val preferencias by lazy { Preferencias(context) }
    val conexion by lazy { observarConexion(context) }
    val ubicaciones by lazy { UbicacionRepositorioFused(context) }

    /** Sesión de Supabase cifrada con el Keystore; la comparten el cliente y el repositorio de auth. */
    private val sesion by lazy { SesionCifrada(context) }

    /** Cliente de Supabase; solo se crea si local.properties trae la URL y la llave del proyecto. */
    val supabase: SupabaseClient by lazy { crearSupabase(sesion) }

    /** Lo deja MainActivity al abrir un enlace bitacora://recuperar; lo consume la navegación. */
    val enlaceRecuperacion = MutableStateFlow(EnlaceRecuperacion.NINGUNO)

    /** Último error de sincronización (lo escribe el SyncWorker, lo muestra el indicador). */
    val errorSync = MutableStateFlow<String?>(null)

    val auth: AuthRepositorio by lazy {
        AuthRepositorioSupabase(
            auth = if (supabaseConfigurado) AuthApiSupabase(supabase, sesion) else AuthApiSinServidor,
            fuentePerfil = if (supabaseConfigurado) FuentePerfilSupabase(supabase) else FuentePerfil { throw ErrorAuth.SinServidor },
            prefs = preferencias,
            db = { db.borrarTodo() },
            alEntrar = {
                // Primera bajada: viajes del chofer (sync) y la lista de camiones.
                programadorSync.alGuardar()
                alcance.launch { actualizarCamiones() }
            },
        )
    }

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

    /** Las pantallas del chofer solo existen con sesión iniciada, así que el perfil ya está guardado. */
    val viajes: ViajesRepositorio by lazy {
        ViajesRepositorioRoom(db, { preferencias.perfil.filterNotNull().first().id }, RelojSistema, ubicaciones, programadorSync)
    }

    /** Copia local de los camiones activos; sin señal se queda con la última que bajó. */
    suspend fun actualizarCamiones() {
        if (!supabaseConfigurado) return
        try {
            bajarCamiones(supabase, db.camionDao())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    init {
        // Con sesión de antes, se refrescan los camiones al abrir (el administrador puede haber agregado).
        alcance.launch {
            if (preferencias.perfil.first() != null) actualizarCamiones()
        }
    }
}
