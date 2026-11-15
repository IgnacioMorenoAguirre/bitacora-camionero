package com.sistemasdeinformacion.proyecto.feature.admin

import com.sistemasdeinformacion.proyecto.data.remote.CamionDto
import com.sistemasdeinformacion.proyecto.data.remote.EventoDto
import com.sistemasdeinformacion.proyecto.data.remote.PerfilDto
import com.sistemasdeinformacion.proyecto.data.remote.ResumenViajeDto
import com.sistemasdeinformacion.proyecto.data.remote.ViajeDto
import com.sistemasdeinformacion.proyecto.data.remote.aIso
import com.sistemasdeinformacion.proyecto.data.repo.AdminRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViajeRemoto
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAdmin
import com.sistemasdeinformacion.proyecto.data.repo.FiltroViajes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminDetalleViewModelTest {

    private val salida = 1_700_000_000_000L
    private val hora = 60 * 60_000L
    private val conexion = MutableStateFlow(true)

    @Before fun antes() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun despues() { Dispatchers.resetMain() }

    @Test fun observar_sinComentario_muestraError() = runTest {
        val repo = AdminFalso(detalle(estado = "finalizado", llegada = salida + 3 * hora))
        val vm = AdminDetalleViewModel("v1", repo, conexion) { salida + 4 * hora }

        vm.cambiarComentario("   ")
        vm.observar()

        assertEquals("Escribe qué debe corregir el chofer.", vm.estado.value.errorComentario)
        assertTrue(repo.validaciones.isEmpty())
    }

    @Test fun aprobar_viajeEnCurso_noPermitido() = runTest {
        val repo = AdminFalso(detalle(estado = "en_curso", llegada = null))
        val vm = AdminDetalleViewModel("v1", repo, conexion) { salida + 2 * hora }

        assertFalse(vm.estado.value.puedeAprobar)
        assertFalse(vm.estado.value.puedeObservar)
        assertEquals("Solo se aprueban viajes finalizados.", vm.estado.value.motivoSinValidar)

        vm.aprobar()

        assertTrue(repo.validaciones.isEmpty())
    }

    @Test fun excedioHoras_seMarca() = runTest {
        // 6 h manejando sin descanso: superó el límite de 5 h aunque el viaje ya terminó.
        val sinDescanso = AdminFalso(detalle(estado = "finalizado", llegada = salida + 6 * hora))
        val vm = AdminDetalleViewModel("v1", sinDescanso, conexion) { salida + 8 * hora }
        assertTrue(vm.estado.value.excedioHoras)

        // Mismo viaje con un descanso de 40 min a las 3 h: nunca pasó de 5 h seguidas.
        val descanso = EventoDto(
            id = "d1", viajeId = "v1", tipo = "descanso",
            fechaHora = (salida + 3 * hora).aIso(), fechaFin = (salida + 3 * hora + 40 * 60_000L).aIso(),
        )
        val conDescanso = AdminFalso(detalle(estado = "finalizado", llegada = salida + 6 * hora, eventos = listOf(descanso)))
        val vm2 = AdminDetalleViewModel("v1", conDescanso, conexion) { salida + 8 * hora }
        assertFalse(vm2.estado.value.excedioHoras)
    }

    @Test fun observar_conComentario_enviaAlServidor() = runTest {
        val repo = AdminFalso(detalle(estado = "finalizado", llegada = salida + 3 * hora))
        val vm = AdminDetalleViewModel("v1", repo, conexion) { salida + 4 * hora }

        vm.cambiarComentario("Falta el peaje de Angostura.")
        vm.observar()

        assertEquals(listOf(Triple("v1", false, "Falta el peaje de Angostura.")), repo.validaciones)
        assertNull(vm.estado.value.errorComentario)
    }

    @Test fun aprobar_ocultaLosControlesDeRevision() = runTest {
        val repo = AdminFalso(detalle(estado = "finalizado", llegada = salida + 3 * hora))
        val vm = AdminDetalleViewModel("v1", repo, conexion) { salida + 4 * hora }
        assertTrue(vm.estado.value.muestraRevision)

        vm.aprobar()

        assertFalse(vm.estado.value.muestraRevision)
        assertFalse(vm.estado.value.puedeAprobar)
        assertFalse(vm.estado.value.puedeObservar)
        assertEquals("Bitácora aprobada.", vm.estado.value.mensaje)
    }

    @Test fun observado_esperaQueElChoferCorrija() = runTest {
        val repo = AdminFalso(detalle(estado = "finalizado", llegada = salida + 3 * hora, validacion = "observado"))
        val vm = AdminDetalleViewModel("v1", repo, conexion) { salida + 4 * hora }

        assertFalse(vm.estado.value.muestraRevision)
        assertEquals("Esperando la corrección del chofer.", vm.estado.value.motivoSinValidar)
        vm.aprobar()
        assertTrue(repo.validaciones.isEmpty())
    }

    @Test fun observado_reenviadoPorElChofer_sePuedeAprobar() = runTest {
        // El trigger del servidor lo devuelve a pendiente cuando el chofer corrige.
        val repo = AdminFalso(detalle(estado = "finalizado", llegada = salida + 3 * hora, validacion = "pendiente"))
        val vm = AdminDetalleViewModel("v1", repo, conexion) { salida + 4 * hora }

        assertTrue(vm.estado.value.muestraRevision)
        assertTrue(vm.estado.value.puedeAprobar)
    }

    @Test fun sinConexion_muestraEstadoSinRed() = runTest {
        conexion.value = false
        val repo = AdminFalso(detalle(estado = "finalizado", llegada = salida + 3 * hora))
        val vm = AdminDetalleViewModel("v1", repo, conexion) { salida + 4 * hora }

        assertTrue(vm.estado.value.sinConexion)
    }

    private fun detalle(estado: String, llegada: Long?, eventos: List<EventoDto> = emptyList(), validacion: String = "pendiente"): DetalleViajeRemoto {
        val viaje = ViajeDto(
            id = "v1", choferId = "c1", cliente = "Agrícola Sur", origen = "Santiago", destino = "Temuco",
            tipoCarga = "Fruta", pesoKg = 12_000.0, requiereRefrigeracion = true, precio = 850_000,
            fechaSalida = salida.aIso(), fechaLlegada = llegada?.aIso(), kmInicial = 1000, kmFinal = llegada?.let { 1600 },
            estado = estado, validacion = validacion,
        )
        val inicio = EventoDto(id = "e0", viajeId = "v1", tipo = "inicio", fechaHora = salida.aIso(), km = 1000)
        return DetalleViajeRemoto.armar(
            viaje = viaje, chofer = "Juan Pérez", camion = null, revision = null,
            eventos = listOf(inicio) + eventos, gastos = emptyList(), novedades = emptyList(), adjuntos = emptyList(),
            ahora = salida + 8 * hora,
        )
    }
}

/** Repositorio de administración en memoria. */
open class AdminFalso(var detalleViaje: DetalleViajeRemoto? = null) : AdminRepositorio {
    val validaciones = mutableListOf<Triple<String, Boolean, String?>>()
    val guardados = mutableListOf<CamionDto>()

    override fun viajesEnVivo(filtro: FiltroViajes): Flow<List<ResumenViajeDto>> = flowOf(emptyList())
    override suspend fun excesosDeHoras(viajes: List<ResumenViajeDto>): Set<String> = emptySet()
    override suspend fun choferes(): List<PerfilDto> = emptyList()
    override suspend fun detalle(viajeId: String): DetalleViajeRemoto = detalleViaje ?: throw ErrorAdmin.ViajeNoEncontrado
    override suspend fun finalizadosEntre(desde: Long, hasta: Long): List<ResumenViajeDto> = emptyList()
    override suspend fun validar(viajeId: String, aprobado: Boolean, comentario: String?): Result<Unit> {
        validaciones += Triple(viajeId, aprobado, comentario)
        // Como el servidor: la próxima carga trae la validación nueva.
        detalleViaje = detalleViaje?.let { d ->
            d.copy(viaje = d.viaje.copy(validacion = if (aprobado) "aprobado" else "observado", comentarioValidacion = comentario))
        }
        return Result.success(Unit)
    }
    override suspend fun urlAdjunto(ruta: String): String = "https://ejemplo/$ruta"
    override fun camiones(): Flow<List<CamionDto>> = flowOf(emptyList())
    override suspend fun guardarCamion(c: CamionDto): Result<Unit> {
        guardados += c
        return Result.success(Unit)
    }
}
