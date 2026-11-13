package com.sistemasdeinformacion.proyecto.data.repo

import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import com.sistemasdeinformacion.proyecto.data.remote.errorDeRespuesta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AuthRepositorioTest {

    private val juan = Perfil("u1", "Juan Pérez", "juan@correo.cl", Rol.CHOFER)

    private val auth = AuthEnMemoria()
    private val prefs = PerfilEnMemoria()
    private var borrados = 0
    private var entradas = 0
    private val perfilesServidor = mutableMapOf("u1" to juan)
    private val repo = AuthRepositorioSupabase(
        auth = auth,
        fuentePerfil = { u -> perfilesServidor[u.id] ?: throw ErrorAuth.Desconocido(null) },
        prefs = prefs,
        db = { borrados++ },
        alEntrar = { entradas++ },
    )

    private suspend fun estadoFinal() = repo.estado.first { it !is EstadoSesion.Cargando }

    @Test fun sesionGuardada_sinRed_quedaActiva() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = true
        auth.sinRed = true // renovar el token fallaría: no debe importar

        assertEquals(EstadoSesion.Activa(juan), estadoFinal())
    }

    @Test fun sinSesion_esSinSesion() = runTest {
        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun perfilSinSesionGuardada_esSinSesion() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = false

        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun cerrarSesion_borraDatosLocales() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = true

        repo.cerrarSesion()

        assertEquals(1, borrados)
        assertEquals(1, prefs.limpiezas)
        assertEquals(1, auth.cierres)
        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun cerrarSesion_sinRed_igualBorra() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = true
        auth.sinRed = true

        repo.cerrarSesion()

        assertEquals(1, borrados)
        assertEquals(1, prefs.limpiezas)
        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun iniciarSesion_guardaPerfil_yPideLaPrimeraBajada() = runTest {
        val r = repo.iniciarSesion(" juan@correo.cl ", "clave1234")

        assertEquals(juan, r.getOrNull())
        assertEquals("juan@correo.cl", auth.ultimoEmail)
        assertEquals(juan, prefs.perfil.value)
        assertEquals(1, entradas)
        assertEquals(0, borrados)
        assertEquals(EstadoSesion.Activa(juan), estadoFinal())
    }

    @Test fun iniciarSesion_otroUsuario_borraLosDatosDelAnterior() = runTest {
        // Quedó el perfil de otra persona (p. ej. su sesión venció en el servidor).
        prefs.perfil.value = Perfil("u2", "Otra", "otra@correo.cl", Rol.CHOFER)

        repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertEquals(1, borrados)
        assertEquals(juan, prefs.perfil.value)
    }

    @Test fun iniciarSesion_mismoUsuario_conservaLosDatos() = runTest {
        prefs.perfil.value = juan

        repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertEquals(0, borrados)
    }

    @Test fun iniciarSesion_sinRed_esSinConexion() = runTest {
        auth.sinRed = true

        val r = repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertEquals(ErrorAuth.SinConexion, r.exceptionOrNull())
        assertNull(prefs.perfil.value)
        assertEquals(0, entradas)
    }

    @Test fun iniciarSesion_sinPerfilEnServidor_cierraLaSesionAMedias() = runTest {
        perfilesServidor.clear()

        val r = repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertTrue(r.isFailure)
        assertEquals(1, auth.cierres)
        assertNull(prefs.perfil.value)
    }

    @Test fun registrar_mandaElNombre_yEntra() = runTest {
        val r = repo.registrar(" Juan Pérez ", "juan@correo.cl", "clave1234")

        assertEquals(juan, r.getOrNull())
        assertEquals("Juan Pérez", auth.ultimoNombre)
        assertEquals(juan, prefs.perfil.value)
    }

    @Test fun cambiarContrasena_conSesionDeRecuperacion_entra() = runTest {
        auth.sesion.value = true
        auth.usuario = Usuario("u1", "juan@correo.cl")

        val r = repo.cambiarContrasena("nueva1234")

        assertTrue(r.isSuccess)
        assertEquals("nueva1234", auth.ultimaContrasenaNueva)
        assertEquals(juan, prefs.perfil.value)
        assertEquals(1, entradas)
    }

    @Test fun enviarEnlace_sinRed_esSinConexion() = runTest {
        auth.sinRed = true

        assertEquals(ErrorAuth.SinConexion, repo.enviarEnlaceRecuperacion("juan@correo.cl").exceptionOrNull())
    }

    @Test fun error400_alIniciarSesion_esCredencialesInvalidas() {
        assertEquals(ErrorAuth.CredencialesInvalidas, errorDeRespuesta(400, null, alIniciarSesion = true))
        assertEquals(ErrorAuth.CredencialesInvalidas, errorDeRespuesta(400, "invalid_credentials", alIniciarSesion = true))
        assertEquals(ErrorAuth.CorreoRegistrado, errorDeRespuesta(422, "user_already_exists", alIniciarSesion = false))
        assertEquals(ErrorAuth.ContrasenaDebil, errorDeRespuesta(422, "weak_password", alIniciarSesion = false))
        assertEquals(ErrorAuth.DemasiadosIntentos, errorDeRespuesta(429, "over_email_send_rate_limit", alIniciarSesion = false))
    }
}

/** AuthApi falsa: con [sinRed] cada llamada al servidor lanza IOException, como supabase-kt sin señal. */
class AuthEnMemoria : AuthApi {
    val sesion = MutableStateFlow(false)
    override val haySesion = sesion
    var sinRed = false
    var usuario: Usuario? = null
    var cierres = 0
    var ultimoEmail: String? = null
    var ultimoNombre: String? = null
    var ultimaContrasenaNueva: String? = null

    private fun red() { if (sinRed) throw ErrorAuth.SinConexion }

    override suspend fun iniciarSesion(email: String, contrasena: String): Usuario {
        red()
        ultimoEmail = email
        return Usuario("u1", email).also { usuario = it; sesion.value = true }
    }

    override suspend fun registrar(nombre: String, email: String, contrasena: String): Usuario {
        red()
        ultimoNombre = nombre
        return Usuario("u1", email).also { usuario = it; sesion.value = true }
    }

    override suspend fun enviarEnlaceRecuperacion(email: String) { red() }

    override suspend fun cambiarContrasena(nueva: String) {
        red()
        ultimaContrasenaNueva = nueva
    }

    override suspend fun usuarioActual(): Usuario? = usuario

    override suspend fun cerrarSesion() {
        cierres++
        usuario = null
        sesion.value = false
        // Sin red, supabase-kt no avisa al servidor pero sí borra la sesión local.
        if (sinRed) throw IOException("sin red")
    }
}

class PerfilEnMemoria : PerfilLocal {
    override val perfil = MutableStateFlow<Perfil?>(null)
    var limpiezas = 0
    override suspend fun guardarPerfil(perfil: Perfil?) { this.perfil.value = perfil }
    override suspend fun limpiar() { limpiezas++; perfil.value = null }
}
