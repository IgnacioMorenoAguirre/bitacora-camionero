package com.sistemasdeinformacion.proyecto.data.repo

import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import com.sistemasdeinformacion.proyecto.data.remote.errorDeRespuesta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AuthRepositorioTest {

    private val juan = Perfil("u1", "Juan Pérez", "juan@correo.cl", Rol.CHOFER)

    private val auth = AuthEnMemoria()
    private val prefs = PerfilEnMemoria()
    private var borrados = 0
    private var entradas = 0
    private var fallaAlEntrar: Exception? = null
    private val errores = mutableListOf<String>()
    private var enExclusion = false
    private val borradosEnExclusion = mutableListOf<Boolean>()
    private val perfilesServidor = mutableMapOf("u1" to juan)
    private var fallaPerfil: Exception? = null
    private var pendientes = 0
    private val repo = AuthRepositorioSupabase(
        auth = auth,
        fuentePerfil = { u -> fallaPerfil?.let { throw it }; perfilesServidor[u.id] ?: throw ErrorAuth.Desconocido(null) },
        prefs = prefs,
        db = { borrados++; borradosEnExclusion += enExclusion },
        alEntrar = { entradas++; fallaAlEntrar?.let { throw it } },
        exclusion = { bloque -> enExclusion = true; try { bloque() } finally { enExclusion = false } },
        registrarError = { msj, _ -> errores += msj },
        pendientes = { pendientes },
    )

    init { prefs.dentroDeExclusion = { enExclusion } }

    private suspend fun estadoFinal() = repo.estado.first { it !is EstadoSesion.Cargando }

    @Test fun sesionGuardada_sinRed_quedaActiva() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = "u1"
        auth.sinRed = true // renovar el token fallaría: no debe importar

        assertEquals(EstadoSesion.Activa(juan), estadoFinal())
    }

    @Test fun sinSesion_esSinSesion() = runTest {
        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun perfilSinSesionGuardada_esSinSesion() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = null

        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun cerrarSesion_borraDatosLocales() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = "u1"

        repo.cerrarSesion()

        assertEquals(1, borrados)
        assertEquals(1, prefs.limpiezas)
        assertEquals(1, auth.cierres)
        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun cerrarSesion_sinRed_igualBorra() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = "u1"
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

    @Test fun iniciarSesion_otroUsuario_conRegistrosSinEnviarDelAnterior_noBorraYAvisa() = runTest {
        // Al chofer anterior se le cerró la sesión sin que alcanzara a enviar lo del día.
        prefs.perfil.value = Perfil("u2", "Pedro Soto", "pedro@correo.cl", Rol.CHOFER)
        pendientes = 3

        val r = repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertEquals(ErrorAuth.DatosSinEnviar("Pedro Soto"), r.exceptionOrNull())
        assertEquals(0, borrados)
        assertEquals("u2", prefs.perfil.value?.id)
        assertEquals("la sesión de la persona nueva no queda a medias", 1, auth.cierres)
        assertEquals(0, entradas)
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
        auth.sesion.value = "u1"
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

    @Test fun sesionDeOtroUsuario_noEsActiva() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = "u2"

        assertEquals(EstadoSesion.SinSesion, estadoFinal())
    }

    @Test fun recuperacion_conPerfilGuardadoSinSesion_noEntraHastaCambiarContrasena() = runTest {
        // Quedó el perfil (la librería borró la sesión que el servidor rechazó) y llega el enlace.
        prefs.perfil.value = juan
        repo.prepararRecuperacion()
        auth.sesion.value = "u1" // supabase-kt importa la sesión de recuperación
        auth.usuario = Usuario("u1", "juan@correo.cl")

        assertEquals(EstadoSesion.SinSesion, estadoFinal())

        repo.cambiarContrasena("nueva1234")

        assertEquals(EstadoSesion.Activa(juan), estadoFinal())
        assertEquals(0, borrados)
    }

    @Test fun recuperacion_deOtraCuenta_borraLosDatosDelPerfilViejo() = runTest {
        prefs.perfil.value = Perfil("u2", "Otra", "otra@correo.cl", Rol.CHOFER)
        repo.prepararRecuperacion()
        auth.sesion.value = "u1"
        auth.usuario = Usuario("u1", "juan@correo.cl")

        assertEquals(EstadoSesion.SinSesion, estadoFinal())
        repo.cambiarContrasena("nueva1234")

        assertEquals(1, borrados)
        assertEquals(EstadoSesion.Activa(juan), estadoFinal())
    }

    @Test fun iniciarSesion_sinPerfilPrevio_reiniciaLaMarcaDeBajada() = runTest {
        // Una sync que terminó tras cerrar sesión pudo dejar la marca de la persona anterior.
        prefs.marca = "2026-11-01T10:00:00Z"

        repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertNull(prefs.marca)
    }

    @Test fun iniciarSesion_otroUsuario_reiniciaLaMarcaDeBajada() = runTest {
        prefs.perfil.value = Perfil("u2", "Otra", "otra@correo.cl", Rol.CHOFER)
        prefs.marca = "2026-11-01T10:00:00Z"

        repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertNull(prefs.marca)
    }

    @Test fun iniciarSesion_mismoUsuario_conservaLaMarca() = runTest {
        prefs.perfil.value = juan
        prefs.marca = "2026-11-01T10:00:00Z"

        repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertEquals("2026-11-01T10:00:00Z", prefs.marca)
    }

    @Test fun cerrarSesion_borraSinUnaSyncEnCurso() = runTest {
        prefs.perfil.value = juan
        auth.sesion.value = "u1"

        repo.cerrarSesion()

        assertEquals(listOf(true), borradosEnExclusion)
        assertEquals(listOf(true), prefs.limpiezasEnExclusion)
    }

    @Test fun alEntrar_falla_igualEntraYQuedaRegistrado() = runTest {
        fallaAlEntrar = IllegalStateException("RLS de camiones")

        val r = repo.iniciarSesion("juan@correo.cl", "clave1234")

        assertEquals(juan, r.getOrNull())
        assertEquals(1, errores.size)
    }

    @Test fun iniciarSesion_cancelado_noCierraLaSesionEnElServidor() = runTest {
        fallaPerfil = CancellationException("pantalla cerrada")

        try {
            repo.iniciarSesion("juan@correo.cl", "clave1234")
            fail("debía propagar la cancelación")
        } catch (_: CancellationException) {
        }
        assertEquals(0, auth.cierres)
    }

    @Test fun entrar_aunqueSeCanceleAlNavegar_ejecutaAlEntrar() = runTest {
        // Al guardar el perfil la navegación cambia de pila y cancela al que llamó (el ViewModel).
        val job = launch { repo.iniciarSesion("juan@correo.cl", "clave1234") }
        prefs.despuesDeGuardar = { job.cancel(); yield() }

        job.join()

        assertEquals(juan, prefs.perfil.value)
        assertEquals(1, entradas)
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
    /** id del usuario de la sesión guardada; null sin sesión. */
    val sesion = MutableStateFlow<String?>(null)
    override val usuarioDeSesion = sesion
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
        return Usuario("u1", email).also { usuario = it; sesion.value = "u1" }
    }

    override suspend fun registrar(nombre: String, email: String, contrasena: String): Usuario {
        red()
        ultimoNombre = nombre
        return Usuario("u1", email).also { usuario = it; sesion.value = "u1" }
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
        sesion.value = null
        // Sin red, supabase-kt no avisa al servidor pero sí borra la sesión local.
        if (sinRed) throw IOException("sin red")
    }
}

class PerfilEnMemoria : PerfilLocal {
    override val perfil = MutableStateFlow<Perfil?>(null)
    override val recuperando = MutableStateFlow(false)
    var marca: String? = null
    var limpiezas = 0
    /** Si cada limpieza ocurrió dentro de la exclusión con la sync. */
    val limpiezasEnExclusion = mutableListOf<Boolean>()
    var dentroDeExclusion: () -> Boolean = { false }
    /** Corre justo después de guardar el perfil (para simular la cancelación que provoca la navegación). */
    var despuesDeGuardar: suspend () -> Unit = {}
    override suspend fun guardarPerfil(perfil: Perfil?) { this.perfil.value = perfil; despuesDeGuardar() }
    override suspend fun marcarRecuperacion(activa: Boolean) { recuperando.value = activa }
    override suspend fun limpiar() {
        limpiezas++
        limpiezasEnExclusion += dentroDeExclusion()
        perfil.value = null; marca = null; recuperando.value = false
    }
}
