package com.sistemasdeinformacion.proyecto.data.repo

import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import kotlinx.coroutines.CancellationException
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
    /** Con la sesión que trajo el enlace de recuperación: fija la contraseña nueva y entra. */
    suspend fun cambiarContrasena(nueva: String): Result<Unit>
    suspend fun cerrarSesion()
}

data class Usuario(val id: String, val email: String)

/** Llamadas a Supabase Auth. Las implementaciones lanzan [ErrorAuth] (ya traducido). */
interface AuthApi {
    /** true mientras haya una sesión guardada en el teléfono (aunque el token esté vencido). */
    val haySesion: Flow<Boolean>
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
    suspend fun guardarPerfil(perfil: Perfil?)
    suspend fun limpiar()
}

/** Borra la base local (AppDatabase.borrarTodo). */
fun interface DatosLocales {
    suspend fun borrarTodo()
}

/**
 * Sesión con Supabase Auth pensada para trabajar sin señal:
 *  - [estado] es Activa si el teléfono tiene el perfil y una sesión guardada. No depende de renovar
 *    el token: sin red la librería reintenta sola y la app sigue funcionando con lo local.
 *  - Al entrar se guarda el perfil y [alEntrar] pide la primera bajada (sync y camiones).
 *  - Si entra otra persona que la del perfil guardado, primero se borran los datos de la anterior.
 */
class AuthRepositorioSupabase(
    private val auth: AuthApi,
    private val fuentePerfil: FuentePerfil,
    private val prefs: PerfilLocal,
    private val db: DatosLocales,
    private val alEntrar: suspend () -> Unit = {},
) : AuthRepositorio {

    override val estado: Flow<EstadoSesion> =
        combine(prefs.perfil, auth.haySesion) { perfil, sesion ->
            if (perfil != null && sesion) EstadoSesion.Activa(perfil) else EstadoSesion.SinSesion
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

    override suspend fun cambiarContrasena(nueva: String): Result<Unit> = intentar {
        auth.cambiarContrasena(nueva)
        val usuario = auth.usuarioActual() ?: throw ErrorAuth.EnlaceVencido
        entrar(usuario)
        Unit
    }

    override suspend fun cerrarSesion() {
        try {
            auth.cerrarSesion()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red no se avisa al servidor; lo local se borra igual.
        }
        db.borrarTodo()
        prefs.limpiar()
    }

    private suspend fun entrar(usuario: Usuario): Perfil {
        val perfil = try {
            fuentePerfil.perfil(usuario)
        } catch (e: Exception) {
            // Sesión a medias (sin perfil): se descarta para no dejar un token huérfano.
            runCatching { auth.cerrarSesion() }
            throw e
        }
        val anterior = prefs.perfil.first()
        if (anterior != null && anterior.id != perfil.id) {
            db.borrarTodo()
            prefs.limpiar()
        }
        prefs.guardarPerfil(perfil)
        runCatching { alEntrar() }
        return perfil
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
