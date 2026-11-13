package com.sistemasdeinformacion.proyecto.sync

import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.NovedadEntity
import com.sistemasdeinformacion.proyecto.data.local.RevisionEntity
import com.sistemasdeinformacion.proyecto.data.local.Sincronizable
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad
import com.sistemasdeinformacion.proyecto.data.remote.aDto
import com.sistemasdeinformacion.proyecto.data.remote.aEntidad
import com.sistemasdeinformacion.proyecto.data.remote.aSubida
import com.sistemasdeinformacion.proyecto.data.remote.comoDtoBajada
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SincronizadorTest {

    private val local = LocalEnMemoria()
    private val remoto = RemotoEnMemoria()
    private var marcaGuardada: String? = null
    private var chofer: String? = "c1"
    private val sync = Sincronizador(local, remoto, { marcaGuardada }, { marcaGuardada = it }, { chofer })

    @Test fun sube_enOrden_viajesAntesQueHijos() = runTest {
        // Se cargan al revés para que el orden no dependa de cómo se insertaron.
        local.adjuntos.poner(adjunto("a1", "v1"))
        local.novedades.poner(novedad("n1", "v1"))
        local.gastos.poner(gasto("g1", "v1"))
        local.eventos.poner(evento("e1", "v1"))
        local.revisiones.poner(revision("r1", "v1"))
        local.viajes.poner(viaje("v1"))

        val r = sync.sincronizar()

        assertEquals(
            listOf("viajes:v1", "revisiones:r1", "eventos:e1", "gastos:g1", "novedades:n1", "archivo:a1", "adjuntos:a1"),
            remoto.llamadas,
        )
        assertEquals(ResultadoSync.Ok(enviados = 6, recibidos = 0, rechazados = emptyList()), r)
        assertEquals(0, local.pendientes().total)
    }

    @Test fun reintento_noDuplica() = runTest {
        local.viajes.poner(viaje("v1"))
        local.gastos.poner(gasto("g1", "v1"))
        local.gastos.poner(gasto("g2", "v1"))

        sync.sincronizar()
        sync.sincronizar()

        assertEquals(1, remoto.filas(Tablas.VIAJES).size)
        assertEquals(setOf("g1", "g2"), remoto.filas(Tablas.GASTOS).keys)
        assertEquals("cada fila se sube una sola vez", 3, remoto.llamadas.size)
        assertEquals(0, local.pendientes().total)
    }

    @Test fun subidaFallaAMitad_reintentaSinDuplicar() = runTest {
        local.viajes.poner(viaje("v1"))
        local.eventos.poner(evento("e1", "v1"))
        local.gastos.poner(gasto("g1", "v1"))
        remoto.fallarUnaVezEn = Tablas.GASTOS

        assertEquals(ResultadoSync.SinConexion, sync.sincronizar())
        assertFalse(local.viajes["v1"].syncPendiente)
        assertFalse(local.eventos["e1"].syncPendiente)
        assertTrue(local.gastos["g1"].syncPendiente)

        assertTrue(sync.sincronizar() is ResultadoSync.Ok)
        assertEquals(1, remoto.filas(Tablas.VIAJES).size)
        assertEquals(1, remoto.filas(Tablas.EVENTOS).size)
        assertEquals(1, remoto.filas(Tablas.GASTOS).size)
        assertEquals(1, remoto.llamadas.count { it == "viajes:v1" })
        assertEquals(1, remoto.llamadas.count { it == "eventos:e1" })
        assertEquals(0, local.pendientes().total)
    }

    @Test fun cambioDuranteSubida_quedaPendiente() = runTest {
        local.viajes.poner(viaje("v1"))
        val original = gasto("g1", "v1", monto = 5_000, modificado = 1)
        local.gastos.poner(original)
        // El chofer corrige el monto justo mientras el gasto viaja al servidor.
        remoto.alSubir = { tabla, _ ->
            if (tabla == Tablas.GASTOS) local.gastos.poner(original.copy(monto = 9_000, modificadoLocal = 2))
        }

        sync.sincronizar()

        assertTrue(local.gastos["g1"].syncPendiente)
        assertEquals(9_000L, local.gastos["g1"].monto)

        remoto.alSubir = { _, _ -> }
        sync.sincronizar()

        assertFalse(local.gastos["g1"].syncPendiente)
        assertEquals(9_000L, (remoto.filas(Tablas.GASTOS)["g1"] as GastoEntity).monto)
    }

    @Test fun viajeAprobado_rechazado_seDescartaYSeInforma() = runTest {
        marcaGuardada = "2026-11-01T00:00:00+00:00"
        val editado = viaje("v1", cliente = "Editado sin señal")
        local.viajes.poner(editado)
        local.gastos.poner(gasto("g1", "v1"))
        local.viajes.poner(viaje("v2"))
        remoto.aprobados += "v1"
        val delServidor = viaje("v1", cliente = "Original").aSubida().comoDtoBajada()
            .copy(validacion = "aprobado", actualizadoEn = "2026-11-09T15:00:00+00:00")
        remoto.cambios = CambiosRemotos(viajes = listOf(delServidor))

        val r = sync.sincronizar()

        assertEquals(listOf("v1"), (r as ResultadoSync.Ok).rechazados)
        assertEquals(listOf("v1"), local.descartados)
        assertFalse("los registros del viaje aprobado no se suben", "gastos:g1" in remoto.llamadas)
        assertTrue("v2" in remoto.filas(Tablas.VIAJES))
        assertNull("se pide todo para recuperar la versión del servidor", remoto.marcasPedidas.single())
        val restaurado = local.viajes["v1"]
        assertEquals("Original", restaurado.cliente)
        assertEquals(EstadoValidacion.APROBADO, restaurado.validacion)
        assertFalse(restaurado.syncPendiente)
        assertEquals(0, local.pendientes().total)
    }

    @Test fun bajada_noPisaPendientes() = runTest {
        local.viajes.poner(viaje("v1", cliente = "Antes"))
        // Mientras se sube, el chofer vuelve a editar el viaje: sigue pendiente.
        remoto.alSubir = { tabla, _ ->
            if (tabla == Tablas.VIAJES) local.viajes.poner(viaje("v1", cliente = "Local", modificado = 2))
        }
        remoto.cambios = CambiosRemotos(
            viajes = listOf(
                viaje("v1", cliente = "Servidor").aSubida().comoDtoBajada().copy(actualizadoEn = "2026-11-10T12:00:00+00:00"),
                viaje("v2", cliente = "Otro").aSubida().comoDtoBajada().copy(actualizadoEn = "2026-11-10T12:00:00+00:00"),
            ),
        )

        val r = sync.sincronizar()

        assertEquals(2, (r as ResultadoSync.Ok).recibidos)
        assertEquals("Local", local.viajes["v1"].cliente)
        assertTrue(local.viajes["v1"].syncPendiente)
        assertEquals("Otro", local.viajes["v2"].cliente)
        assertFalse(local.viajes["v2"].syncPendiente)
    }

    @Test fun bajada_guardaMarcaDelServidor() = runTest {
        marcaGuardada = "2026-11-01T00:00:00+00:00"
        local.viajes.poner(viaje("v9", pendiente = false))
        remoto.cambios = CambiosRemotos(
            viajes = listOf(viaje("v9").aSubida().comoDtoBajada().copy(actualizadoEn = "2026-11-10T10:00:00.5+00:00")),
            eventos = listOf(evento("e9", "v9").aDto().copy(actualizadoEn = "2026-11-10T12:00:00.123456+00:00")),
            gastos = listOf(gasto("g9", "v9").aDto().copy(actualizadoEn = "2026-11-10T11:59:59+00:00")),
        )

        sync.sincronizar()
        remoto.cambios = CambiosRemotos()
        sync.sincronizar()

        assertEquals("2026-11-10T12:00:00.123456+00:00", marcaGuardada)
        assertEquals(listOf("2026-11-01T00:00:00+00:00", "2026-11-10T12:00:00.123456+00:00"), remoto.marcasPedidas)
    }

    @Test fun adjunto_subeArchivoAntesQueFila() = runTest {
        local.viajes.poner(viaje("v1", pendiente = false))
        local.adjuntos.poner(adjunto("a1", "v1"))
        local.adjuntos.poner(adjunto("a2", "v1", tipo = TipoAdjunto.FIRMA, rutaRemota = "v1/a2.png"))

        sync.sincronizar()

        assertEquals(listOf("archivo:a1", "adjuntos:a1", "adjuntos:a2"), remoto.llamadas)
        assertEquals("v1/a1.jpg", (remoto.filas(Tablas.ADJUNTOS)["a1"] as AdjuntoEntity).rutaRemota)
        assertEquals("v1/a2.png", (remoto.filas(Tablas.ADJUNTOS)["a2"] as AdjuntoEntity).rutaRemota)
        assertEquals("v1/a1.jpg", local.adjuntos["a1"].rutaRemota)
        assertEquals(0, local.pendientes().total)
    }

    @Test fun adjunto_rutaRemotaQuedaEnElTelefonoAunqueFalleLaBajada() = runTest {
        local.viajes.poner(viaje("v1", pendiente = false))
        local.adjuntos.poner(adjunto("a1", "v1"))
        remoto.fallarBajada = IOException("Sin señal")

        assertEquals(ResultadoSync.SinConexion, sync.sincronizar())

        val a1 = local.adjuntos["a1"]
        assertEquals("v1/a1.jpg", a1.rutaRemota)
        assertFalse("la ruta no cambia modificadoLocal: el envío se confirma igual", a1.syncPendiente)
        assertEquals(1L, a1.modificadoLocal)
    }

    @Test fun adjunto_sinArchivo_quedaPendienteYNoFrenaLaBajada() = runTest {
        local.viajes.poner(viaje("v1", pendiente = false))
        local.adjuntos.poner(adjunto("a1", "v1"))
        local.gastos.poner(gasto("g1", "v1"))
        remoto.sinArchivo += "a1"

        val r = sync.sincronizar()

        assertEquals(1, (r as ResultadoSync.Ok).enviados)
        assertFalse("adjuntos:a1" in remoto.llamadas)
        assertTrue(local.adjuntos["a1"].syncPendiente)
        assertEquals(1, remoto.marcasPedidas.size)
    }

    @Test fun falla_informaSoloElErrorSinDetallesDeLaPeticion() = runTest {
        local.viajes.poner(viaje("v1"))
        local.gastos.poner(gasto("g1", "v1"))
        remoto.rechazarIds["g1"] =
            IllegalStateException("permission denied\nURL: https://x.supabase.co\nHeaders: [Authorization=Bearer abc]")

        assertEquals(ResultadoSync.Falla("permission denied"), sync.sincronizar())
        assertTrue(local.gastos["g1"].syncPendiente)
    }

    @Test fun filaRechazadaPorElServidor_noFrenaLoDemasNiLaBajada() = runTest {
        local.viajes.poner(viaje("v1"))
        local.gastos.poner(gasto("g1", "v1"))
        local.gastos.poner(gasto("g2", "v1"))
        local.novedades.poner(novedad("n1", "v1"))
        local.viajes.poner(viaje("v2"))
        local.gastos.poner(gasto("g3", "v2"))
        remoto.rechazarIds["g1"] = IllegalStateException("new row violates check constraint")
        remoto.rechazarIds["v2"] = IllegalStateException("new row violates row-level security policy")
        remoto.cambios = CambiosRemotos(
            viajes = listOf(
                viaje("v9", pendiente = false).aSubida().comoDtoBajada()
                    .copy(validacion = "aprobado", actualizadoEn = "2026-11-10T12:00:00+00:00"),
            ),
        )

        val r = sync.sincronizar()

        assertTrue(r is ResultadoSync.Falla)
        assertTrue(local.gastos["g1"].syncPendiente)
        assertFalse(local.gastos["g2"].syncPendiente)
        assertFalse(local.novedades["n1"].syncPendiente)
        assertTrue(local.viajes["v2"].syncPendiente)
        assertFalse("los registros de un viaje que no subió esperan", "gastos:g3" in remoto.llamadas)
        assertTrue(local.gastos["g3"].syncPendiente)
        assertEquals(1, remoto.marcasPedidas.size)
        assertEquals(EstadoValidacion.APROBADO, local.viajes["v9"].validacion)
        assertEquals("2026-11-10T12:00:00+00:00", marcaGuardada)
    }

    @Test fun sinSesion_noHaceNada() = runTest {
        chofer = null
        local.viajes.poner(viaje("v1"))

        val r = sync.sincronizar()

        assertEquals(ResultadoSync.Ok(0, 0, emptyList()), r)
        assertTrue(remoto.llamadas.isEmpty())
        assertTrue(remoto.marcasPedidas.isEmpty())
        assertTrue(local.viajes["v1"].syncPendiente)
        assertNull(marcaGuardada)
    }

    // ---------- datos de ejemplo ----------

    private val fecha = 1_794_312_000_000L

    private fun viaje(id: String, cliente: String = "Agrícola Sur", modificado: Long = 1, pendiente: Boolean = true) = ViajeEntity(
        id = id, choferId = "c1", camionId = null, cliente = cliente, origen = "Santiago", destino = "Concepción",
        ruta = null, tipoCarga = "Fruta", pesoKg = 1000.0, requiereRefrigeracion = false, precio = 500_000,
        fechaSalida = fecha, fechaLlegada = null, kmInicial = 100, kmFinal = null, estado = EstadoViaje.EN_CURSO,
        observaciones = null, modificadoLocal = modificado, syncPendiente = pendiente,
    )

    private fun revision(id: String, viajeId: String) =
        RevisionEntity(id, viajeId, true, true, true, true, true, null, false, 1, true)

    private fun evento(id: String, viajeId: String) = EventoEntity(
        id = id, viajeId = viajeId, tipo = TipoEvento.PARADA, fechaHora = fecha, latitud = null, longitud = null,
        direccion = null, km = null, nota = null, modificadoLocal = 1, syncPendiente = true,
    )

    private fun gasto(id: String, viajeId: String, monto: Long = 5_000, modificado: Long = 1) =
        GastoEntity(id, viajeId, TipoGasto.PEAJE, monto, null, fecha, null, false, modificado, true)

    private fun novedad(id: String, viajeId: String) =
        NovedadEntity(id, viajeId, TipoNovedad.RETRASO, Gravedad.BAJA, "Taco en el peaje", fecha, null, null, false, 1, true)

    private fun adjunto(id: String, viajeId: String, tipo: TipoAdjunto = TipoAdjunto.FOTO, rutaRemota: String? = null) =
        AdjuntoEntity(id, viajeId, tipo, "/data/fotos/$id", rutaRemota, null, fecha, false, 1, true)
}

/** Una tabla local en memoria con el mismo contrato que Room + SyncDao. */
class TablaLocal<T : Sincronizable>(private val enviado: (T) -> T) {
    val filas = linkedMapOf<String, T>()
    operator fun get(id: String): T = filas.getValue(id)
    fun poner(fila: T) { filas[fila.id] = fila }
    fun pendientes(): List<T> = filas.values.filter { it.syncPendiente }

    fun marcar(item: T) {
        val actual = filas[item.id] ?: return
        if (actual.modificadoLocal == item.modificadoLocal) filas[item.id] = enviado(actual)
    }

    fun aplicar(remota: T) {
        if (filas[remota.id]?.syncPendiente != true) filas[remota.id] = remota
    }
}

class LocalEnMemoria : OrigenLocal {
    val viajes = TablaLocal<ViajeEntity> { it.copy(syncPendiente = false) }
    val revisiones = TablaLocal<RevisionEntity> { it.copy(syncPendiente = false) }
    val eventos = TablaLocal<EventoEntity> { it.copy(syncPendiente = false) }
    val gastos = TablaLocal<GastoEntity> { it.copy(syncPendiente = false) }
    val novedades = TablaLocal<NovedadEntity> { it.copy(syncPendiente = false) }
    val adjuntos = TablaLocal<AdjuntoEntity> { it.copy(syncPendiente = false) }
    val descartados = mutableListOf<String>()

    override suspend fun pendientes() = LotePendiente(
        viajes.pendientes(), revisiones.pendientes(), eventos.pendientes(),
        gastos.pendientes(), novedades.pendientes(), adjuntos.pendientes(),
    )

    override suspend fun marcarEnviado(item: Sincronizable) {
        when (item) {
            is ViajeEntity -> viajes.marcar(item)
            is RevisionEntity -> revisiones.marcar(item)
            is EventoEntity -> eventos.marcar(item)
            is GastoEntity -> gastos.marcar(item)
            is NovedadEntity -> novedades.marcar(item)
            is AdjuntoEntity -> adjuntos.marcar(item)
        }
    }

    override suspend fun guardarRutaRemota(adjuntoId: String, ruta: String) {
        adjuntos.filas[adjuntoId]?.let { adjuntos.poner(it.copy(rutaRemota = ruta)) }
    }

    override suspend fun aplicarRemotos(c: CambiosRemotos) {
        c.viajes.forEach { viajes.aplicar(it.aEntidad()) }
        c.revisiones.forEach { revisiones.aplicar(it.aEntidad()) }
        c.eventos.forEach { eventos.aplicar(it.aEntidad()) }
        c.gastos.forEach { gastos.aplicar(it.aEntidad()) }
        c.novedades.forEach { novedades.aplicar(it.aEntidad()) }
        c.adjuntos.forEach { adjuntos.aplicar(it.aEntidad(adjuntos.filas[it.id]?.rutaLocal ?: "")) }
    }

    override suspend fun descartarViaje(viajeId: String) {
        descartados += viajeId
        viajes.filas.remove(viajeId)
        revisiones.filas.values.removeIf { it.viajeId == viajeId }
        eventos.filas.values.removeIf { it.viajeId == viajeId }
        gastos.filas.values.removeIf { it.viajeId == viajeId }
        novedades.filas.values.removeIf { it.viajeId == viajeId }
        adjuntos.filas.values.removeIf { it.viajeId == viajeId }
    }
}

/** Servidor en memoria: upsert por id y registro del orden de las llamadas. */
class RemotoEnMemoria : DestinoRemoto {
    val llamadas = mutableListOf<String>()
    private val tablas = mutableMapOf<String, MutableMap<String, Sincronizable>>()
    val aprobados = mutableSetOf<String>()
    val sinArchivo = mutableSetOf<String>()
    /** Filas que el servidor rechaza siempre (RLS, CHECK…): el lote que las trae falla entero. */
    val rechazarIds = mutableMapOf<String, Exception>()
    var fallarBajada: Exception? = null
    var fallarUnaVezEn: String? = null
    var errorAlFallar: Exception = IOException("Sin señal")
    var alSubir: (tabla: String, items: List<Sincronizable>) -> Unit = { _, _ -> }
    var cambios = CambiosRemotos()
    val marcasPedidas = mutableListOf<String?>()

    fun filas(tabla: String): Map<String, Sincronizable> = tablas[tabla].orEmpty()

    override suspend fun subirViaje(v: ViajeEntity): ResultadoFila {
        rechazarIds[v.id]?.let { throw it }
        llamadas += "${Tablas.VIAJES}:${v.id}"
        if (v.id in aprobados) return ResultadoFila.RECHAZADO_APROBADO
        alSubir(Tablas.VIAJES, listOf(v))
        tablas.getOrPut(Tablas.VIAJES) { mutableMapOf() }[v.id] = v
        return ResultadoFila.OK
    }

    override suspend fun subirLote(tabla: String, items: List<Sincronizable>) {
        if (tabla == fallarUnaVezEn) {
            fallarUnaVezEn = null
            throw errorAlFallar
        }
        items.firstNotNullOfOrNull { rechazarIds[it.id] }?.let { throw it }
        items.forEach { llamadas += "$tabla:${it.id}" }
        alSubir(tabla, items)
        val destino = tablas.getOrPut(tabla) { mutableMapOf() }
        items.forEach { destino[it.id] = it }
    }

    override suspend fun subirArchivo(a: AdjuntoEntity): String {
        if (a.id in sinArchivo) throw ArchivoNoDisponibleException(a.rutaLocal)
        llamadas += "archivo:${a.id}"
        return rutaRemotaDe(a)
    }

    override suspend fun bajarDesde(marca: String?, choferId: String): CambiosRemotos {
        marcasPedidas += marca
        fallarBajada?.let { throw it }
        return cambios
    }
}
