package com.sistemasdeinformacion.proyecto.data.remote

import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.model.desdeValor
import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import com.sistemasdeinformacion.proyecto.data.repo.AuthApi
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAuth
import com.sistemasdeinformacion.proyecto.data.repo.FuentePerfil
import com.sistemasdeinformacion.proyecto.data.repo.Usuario
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** Supabase Auth detrás de [AuthApi]. Los errores salen ya traducidos a [ErrorAuth]. */
class AuthApiSupabase(private val supabase: SupabaseClient, private val sesion: SesionCifrada) : AuthApi {

    private val auth get() = supabase.auth

    override val usuarioDeSesion: Flow<String?> = sesion.usuarioId

    override suspend fun iniciarSesion(email: String, contrasena: String): Usuario = traducirErrores(alIniciarSesion = true) {
        auth.signInWith(Email) {
            this.email = email
            password = contrasena
        }
        usuario()
    }

    override suspend fun registrar(nombre: String, email: String, contrasena: String): Usuario = traducirErrores {
        // El trigger de la base crea la fila de perfiles (rol chofer) con este nombre.
        auth.signUpWith(Email) {
            this.email = email
            password = contrasena
            data = buildJsonObject { put("nombre_completo", nombre) }
        }
        // Sin confirmación de correo el registro ya deja la sesión iniciada; si no, se entra a mano.
        if (auth.currentSessionOrNull() == null) {
            auth.signInWith(Email) {
                this.email = email
                password = contrasena
            }
        }
        usuario()
    }

    override suspend fun enviarEnlaceRecuperacion(email: String) = traducirErrores {
        auth.resetPasswordForEmail(email, ENLACE_RECUPERAR)
    }

    override suspend fun cambiarContrasena(nueva: String) = traducirErrores {
        if (auth.currentSessionOrNull() == null) throw ErrorAuth.EnlaceVencido
        auth.updateUser { password = nueva }
        Unit
    }

    override suspend fun usuarioActual(): Usuario? =
        auth.currentUserOrNull()?.aUsuario()
            ?: auth.currentSessionOrNull()?.let { traducirErrores { auth.retrieveUserForCurrentSession(updateSession = true).aUsuario() } }

    override suspend fun cerrarSesion() {
        try {
            // Avisar al servidor es cortesía: sin red no se espera más de unos segundos.
            withTimeoutOrNull(5_000) { auth.signOut(SignOutScope.LOCAL) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            runCatching { auth.clearSession() }
            sesion.deleteSession()
        }
    }

    private suspend fun usuario(): Usuario =
        auth.currentUserOrNull()?.aUsuario() ?: auth.retrieveUserForCurrentSession(updateSession = true).aUsuario()

    private fun UserInfo.aUsuario() = Usuario(id, email.orEmpty())
}

/** Lee el perfil del usuario desde la tabla `perfiles` (la seguridad por fila deja leer el propio). */
class FuentePerfilSupabase(private val supabase: SupabaseClient) : FuentePerfil {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun perfil(usuario: Usuario): Perfil = traducirErrores {
        val filas = supabase.from("perfiles").select { filter { eq("id", usuario.id) } }
            .data.let { json.decodeFromString<List<PerfilDto>>(it) }
        val dto = filas.firstOrNull() ?: throw ErrorAuth.Desconocido(IllegalStateException("Usuario sin perfil"))
        if (!dto.activo) throw ErrorAuth.CuentaInactiva
        Perfil(dto.id, dto.nombreCompleto, usuario.email, desdeValor(dto.rol, Rol.CHOFER))
    }
}

/** Sin local.properties configurado: la app abre, pero no puede iniciar sesión. */
object AuthApiSinServidor : AuthApi {
    override val usuarioDeSesion: Flow<String?> = flowOf(null)
    override suspend fun iniciarSesion(email: String, contrasena: String): Usuario = throw ErrorAuth.SinServidor
    override suspend fun registrar(nombre: String, email: String, contrasena: String): Usuario = throw ErrorAuth.SinServidor
    override suspend fun enviarEnlaceRecuperacion(email: String) = throw ErrorAuth.SinServidor
    override suspend fun cambiarContrasena(nueva: String) = throw ErrorAuth.SinServidor
    override suspend fun usuarioActual(): Usuario? = null
    override suspend fun cerrarSesion() {}
}

/**
 * Traduce una respuesta de error de Supabase Auth. [codigo] es el error_code de GoTrue (p. ej.
 * "invalid_credentials"); los servidores antiguos solo mandan 400 al fallar /token.
 */
fun errorDeRespuesta(estadoHttp: Int, codigo: String?, alIniciarSesion: Boolean): ErrorAuth = when (codigo) {
    "invalid_credentials", "invalid_grant" -> ErrorAuth.CredencialesInvalidas
    "email_exists", "user_already_exists" -> ErrorAuth.CorreoRegistrado
    "weak_password" -> ErrorAuth.ContrasenaDebil
    "same_password" -> ErrorAuth.MismaContrasena
    "over_email_send_rate_limit", "over_request_rate_limit" -> ErrorAuth.DemasiadosIntentos
    "user_banned" -> ErrorAuth.CuentaInactiva
    "session_not_found", "session_expired", "otp_expired", "bad_jwt" -> ErrorAuth.EnlaceVencido
    else -> when {
        alIniciarSesion && estadoHttp == 400 -> ErrorAuth.CredencialesInvalidas
        estadoHttp == 429 -> ErrorAuth.DemasiadosIntentos
        else -> ErrorAuth.Desconocido(null)
    }
}

/** Sin señal, supabase-kt lanza HttpRequestException (una IOException). */
internal suspend fun <T> traducirErrores(alIniciarSesion: Boolean = false, bloque: suspend () -> T): T = try {
    bloque()
} catch (e: CancellationException) {
    throw e
} catch (e: ErrorAuth) {
    throw e
} catch (_: AuthWeakPasswordException) {
    throw ErrorAuth.ContrasenaDebil
} catch (e: AuthRestException) {
    throw errorDeRespuesta(e.statusCode, e.errorCode?.value ?: e.error, alIniciarSesion)
} catch (e: RestException) {
    throw errorDeRespuesta(e.statusCode, e.error, alIniciarSesion)
} catch (_: IOException) {
    throw ErrorAuth.SinConexion
} catch (e: Exception) {
    throw ErrorAuth.Desconocido(e)
}
