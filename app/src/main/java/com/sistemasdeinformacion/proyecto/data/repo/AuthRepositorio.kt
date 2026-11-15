package com.sistemasdeinformacion.proyecto.data.repo

import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Activa(val perfil: Perfil) : EstadoSesion
}

/** Errores de autenticación ya traducidos; cada pantalla elige el mensaje para el chofer. */
sealed class ErrorAuth : Exception() {
    data object CredencialesInvalidas : ErrorAuth()
    data object SinConexion : ErrorAuth()
    data object CorreoRegistrado : ErrorAuth()
    data object ContrasenaDebil : ErrorAuth()
    data object MismaContrasena : ErrorAuth()
    data object DemasiadosIntentos : ErrorAuth()
    data object CuentaInactiva : ErrorAuth()
    data object EnlaceVencido : ErrorAuth()
    data object SinServidor : ErrorAuth()
    class Desconocido(override val cause: Throwable?) : ErrorAuth()
}

interface AuthRepositorio {
    val estado: Flow<EstadoSesion>
    suspend fun iniciarSesion(email: String, contrasena: String): Result<Perfil>
    suspend fun registrar(nombre: String, email: String, contrasena: String): Result<Perfil>
    /** Manda el correo con el enlace bitacora://recuperar. */
    suspend fun enviarEnlaceRecuperacion(email: String): Result<Unit>
    /**
     * Se llama justo antes de importar la sesión que trae el enlace de recuperación: mientras no se
     * fije la contraseña nueva, esa sesión no deja entrar a la app (aunque haya un perfil guardado).
     */
    suspend fun prepararRecuperacion()
    /** Con la sesión que trajo el enlace de recuperación: fija la contraseña nueva y entra. */
    suspend fun cambiarContrasena(nueva: String): Result<Unit>
    suspend fun cerrarSesion()
}

data class Usuario(val id: String, val email: String)

/** Llamadas a Supabase Auth. Las implementaciones lanzan [ErrorAuth] (ya traducido). */
interface AuthApi {
    /** id del usuario de la sesión guardada en el teléfono (aunque el token esté vencido); null sin sesión. */
    val usuarioDeSesion: Flow<String?>
    suspend fun iniciarSesion(email: String, contrasena: String): Usuario
    suspend fun registrar(nombre: String, email: String, contrasena: String): Usuario
    suspend fun enviarEnlaceRecuperacion(email: String)
    suspend fun cambiarContrasena(nueva: String)
    suspend fun usuarioActual(): Usuario?
    /** Borra la sesión local; avisar al servidor es opcional (sin red no se puede). */
    suspend fun cerrarSesion()
}

/** Lee la fila de `perfiles` del usuario; lanza [ErrorAuth.CuentaInactiva] si el administrador la desactivó. */
fun interface FuentePerfil {
    suspend fun perfil(usuario: Usuario): Perfil
}

/** Perfil guardado en el teléfono (lo implementa Preferencias). */
interface PerfilLocal {
    val perfil: Flow<Perfil?>
    /** true entre que llega un enlace de recuperación y se fija la contraseña nueva. */
    val recuperando: Flow<Boolean>
    suspend fun guardarPerfil(perfil: Perfil?)
    suspend fun marcarRecuperacion(activa: Boolean)
    /** Borra perfil, marca de bajada, avisos y recuperación pendiente (conserva el tema). */
    suspend fun limpiar()
}

/** Borra la base local (AppDatabase.borrarTodo). */
fun interface DatosLocales {
    suspend fun borrarTodo()
}

/**
 * Corre [bloque] sin una sincronización a medias (toma el candado del Sincronizador). Así una sync
 * que estaba bajando no escribe filas ni la marca de bajada después de borrar los datos locales.
 */
fun interface ExclusionSync {
    suspend fun sinSincronizar(bloque: suspend () -> Unit)
}

/**
 * Sesión con Supabase Auth pensada para trabajar sin señal:
 *  - [estado] es Activa si el teléfono tiene el perfil y una sesión guardada **del mismo usuario**, y
 *    no hay una recuperación de contraseña a medias. No depende de renovar el token: sin red la
 *    librería reintenta sola y la app sigue funcionando con lo local.
 *  - Al entrar se guarda el perfil y [alEntrar] pide la primera bajada (sync y camiones).
 *  - Si entra otra persona que la del perfil guardado, primero se borran los datos de la anterior; si
 *    no había perfil, igual se reinicia la marca de bajada (la primera sync baja todo el historial).
 */
class AuthRepositorioSupabase(
    private val auth: AuthApi,
    private val fuentePerfil: FuentePerfil,
    private val prefs: PerfilLocal,
    private val db: DatosLocales,
    private val alEntrar: suspend () -> Unit = {},
    private val exclusion: ExclusionSync = ExclusionSync { it() },
    private val registrarError: (String, Throwable) -> Unit = { _, _ -> },
) : AuthRepositorio {

    override val estado: Flow<EstadoSesion> =
        combine(prefs.perfil, auth.usuarioDeSesion, prefs.recuperando) { perfil, usuarioId, recuperando ->
            if (perfil != null && usuarioId == perfil.id && !recuperando) EstadoSesion.Activa(perfil) else EstadoSesion.SinSesion
        }
            .onStart<EstadoSesion> { emit(EstadoSesion.Cargando) }
            .distinctUntilChanged()

    override suspend fun iniciarSesion(email: String, contrasena: String): Result<Perfil> = intentar {
        entrar(auth.iniciarSesion(email.trim(), contrasena))
    }

    override suspend fun registrar(nombre: String, email: String, contrasena: String): Result<Perfil> = intentar {
        entrar(auth.registrar(nombre.trim(), email.trim(), contrasena))
    }

    override suspend fun enviarEnlaceRecuperacion(email: String): Result<Unit> = intentar {
        auth.enviarEnlaceRecuperacion(email.trim())
    }

    override suspend fun prepararRecuperacion() {
        prefs.marcarRecuperacion(true)
    }

    override suspend fun cambiarContrasena(nueva: String): Result<Unit> = intentar {
        auth.cambiarContrasena(nueva)
        val usuario = auth.usuarioActual() ?: throw ErrorAuth.EnlaceVencido
        entrar(usuario)
    }

    override suspend fun cerrarSesion() {
        descartarSesion("No se pudo avisar el cierre de sesión al servidor")
        exclusion.sinSincronizar {
            db.borrarTodo()
            prefs.limpiar()
        }
    }

    private suspend fun entrar(usuario: Usuario): Perfil {
        val perfil = try {
            fuentePerfil.perfil(usuario)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sesión a medias (sin perfil): se descarta para no dejar un token huérfano.
            descartarSesion("No se pudo descartar la sesión sin perfil")
            throw e
        }
        // Guardar el perfil activa la sesión y la navegación cancela la pantalla que llamó (su
        // viewModelScope). Lo local y alEntrar() son cortos y no usan red: se completan igual.
        withContext(NonCancellable) {
            exclusion.sinSincronizar {
                val anterior = prefs.perfil.first()
                if (anterior?.id != perfil.id) {
                    if (anterior != null) db.borrarTodo()
                    // Marca de bajada y avisos de otra persona (o de una sync que terminó tras cerrar sesión).
                    prefs.limpiar()
                }
                prefs.guardarPerfil(perfil)
                prefs.marcarRecuperacion(false)
            }
            try {
                alEntrar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // La sesión quedó bien; la bajada se reintenta con la sync, pero debe quedar rastro.
                registrarError("Falló la primera bajada al entrar", e)
            }
        }
        return perfil
    }

    private suspend fun descartarSesion(mensaje: String) {
        try {
            auth.cerrarSesion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin red no se avisa al servidor; lo local se borra igual.
            registrarError(mensaje, e)
        }
    }

    private suspend fun <T> intentar(bloque: suspend () -> T): Result<T> = try {
        Result.success(bloque())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ErrorAuth) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(ErrorAuth.Desconocido(e))
    }
}
