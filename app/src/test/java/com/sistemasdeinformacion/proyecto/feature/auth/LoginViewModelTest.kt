package com.sistemasdeinformacion.proyecto.feature.auth

import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import com.sistemasdeinformacion.proyecto.data.repo.AuthRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAuth
import com.sistemasdeinformacion.proyecto.data.repo.EstadoSesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val repo = AuthFalso()

    @Before fun antes() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun despues() { Dispatchers.resetMain() }

    @Test fun login_emailInvalido_noLlamaAlServidor() = runTest {
        val vm = LoginViewModel(repo)
        vm.cambiarEmail("juan@correo")
        vm.cambiarContrasena("clave1234")

        vm.entrar()

        assertEquals("Escribe un correo válido.", vm.estado.value.errorEmail)
        assertEquals(0, repo.intentos)
    }

    @Test fun login_sinContrasena_noLlamaAlServidor() = runTest {
        val vm = LoginViewModel(repo)
        vm.cambiarEmail("juan@correo.cl")

        vm.entrar()

        assertEquals("Escribe tu contraseña.", vm.estado.value.errorContrasena)
        assertEquals(0, repo.intentos)
    }

    @Test fun login_credencialesMalas_muestraMensaje() = runTest {
        // El adaptador de Supabase traduce el 400 de /token a CredencialesInvalidas.
        repo.resultado = Result.failure(ErrorAuth.CredencialesInvalidas)
        val vm = LoginViewModel(repo)
        vm.cambiarEmail("juan@correo.cl")
        vm.cambiarContrasena("clave1234")

        vm.entrar()

        assertEquals(1, repo.intentos)
        assertEquals("Correo o contraseña incorrectos.", vm.estado.value.error)
        assertFalse(vm.estado.value.cargando)
    }

    @Test fun login_sinRed_muestraMensaje() = runTest {
        repo.resultado = Result.failure(ErrorAuth.SinConexion)
        val vm = LoginViewModel(repo)
        vm.cambiarEmail("juan@correo.cl")
        vm.cambiarContrasena("clave1234")

        vm.entrar()

        assertEquals("Necesitas conexión para iniciar sesión la primera vez.", vm.estado.value.error)
    }

    @Test fun login_ok_sinErrores() = runTest {
        val vm = LoginViewModel(repo)
        vm.cambiarEmail("juan@correo.cl")
        vm.cambiarContrasena("clave1234")

        vm.entrar()

        assertEquals(1, repo.intentos)
        assertNull(vm.estado.value.error)
    }

    @Test fun escribir_limpiaElError() = runTest {
        repo.resultado = Result.failure(ErrorAuth.CredencialesInvalidas)
        val vm = LoginViewModel(repo)
        vm.cambiarEmail("juan@correo.cl")
        vm.cambiarContrasena("clave1234")
        vm.entrar()

        vm.cambiarContrasena("clave12345")

        assertNull(vm.estado.value.error)
    }
}

class AuthFalso : AuthRepositorio {
    val juan = Perfil("u1", "Juan Pérez", "juan@correo.cl", Rol.CHOFER)
    var resultado: Result<Perfil> = Result.success(juan)
    var intentos = 0
    override val estado: Flow<EstadoSesion> = flowOf(EstadoSesion.SinSesion)
    override suspend fun iniciarSesion(email: String, contrasena: String): Result<Perfil> { intentos++; return resultado }
    override suspend fun registrar(nombre: String, email: String, contrasena: String): Result<Perfil> { intentos++; return resultado }
    override suspend fun enviarEnlaceRecuperacion(email: String): Result<Unit> { intentos++; return resultado.map { } }
    override suspend fun cambiarContrasena(nueva: String): Result<Unit> { intentos++; return resultado.map { } }
    override suspend fun cerrarSesion() {}
}
