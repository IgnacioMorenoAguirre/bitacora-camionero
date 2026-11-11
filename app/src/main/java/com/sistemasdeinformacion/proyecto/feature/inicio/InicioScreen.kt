package com.sistemasdeinformacion.proyecto.feature.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.core.Reloj
import com.sistemasdeinformacion.proyecto.core.RelojSistema
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.AlertaHoras
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.domain.CalculosViaje
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.feature.ruta.HojaRegistro
import com.sistemasdeinformacion.proyecto.feature.ruta.HojasDeRegistro
import com.sistemasdeinformacion.proyecto.sync.EstadoSync
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSync
import com.sistemasdeinformacion.proyecto.ui.componentes.AccionRapida
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.EstadoVacio
import com.sistemasdeinformacion.proyecto.ui.componentes.IndicadorSync
import com.sistemasdeinformacion.proyecto.ui.componentes.LetreroRuta
import com.sistemasdeinformacion.proyecto.ui.navegacion.HojaDeRuta
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.navegacion.NuevoViaje
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class EstadoInicio(
    val cargando: Boolean = true,
    val nombre: String = "",
    val enCurso: ViajeEntity? = null,
    val kmActual: Int? = null,
    val enRutaMs: Long = 0,
    val manejoContinuoMs: Long = 0,
    val alerta: AlertaHoras = AlertaHoras.NORMAL,
    val descansoAbiertoId: String? = null,
    val kmMes: Int = 0,
    val gastosMes: Long = 0,
    val viajesMes: Int = 0,
    val sync: EstadoSync = EstadoSync(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class InicioViewModel(
    nombre: String,
    repo: ViajesRepositorio,
    programador: ProgramadorSync,
    private val reloj: Reloj = RelojSistema,
    zona: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val tic = flow { while (true) { emit(reloj.ahora()); delay(30_000) } }

    private val inicioMes = LocalDate.now(zona).withDayOfMonth(1).atStartOfDay(zona).toInstant().toEpochMilli()

    private val delMes = combine(repo.viajes(), repo.gastosDelPeriodo(inicioMes, Long.MAX_VALUE)) { viajes, gastos ->
        val mes = viajes.filter { it.fechaSalida >= inicioMes }
        Triple(mes.sumOf { v -> if (v.kmInicial != null && v.kmFinal != null) v.kmFinal - v.kmInicial else 0 }, gastos.sumOf { it.monto }, mes.size)
    }

    private val eventosEnCurso = repo.enCurso().flatMapLatest { v -> if (v == null) flowOf(v to emptyList()) else repo.detalle(v.id).map { v to it.eventos } }

    val estado: StateFlow<EstadoInicio> = combine(eventosEnCurso, delMes, programador.estado, tic) { (viaje, eventos), (km, gastos, n), sync, ahora ->
        val manejo = viaje?.let { CalculosViaje.manejoContinuoMs(it.fechaSalida, eventos, ahora) } ?: 0
        EstadoInicio(
            cargando = false,
            nombre = nombre,
            enCurso = viaje,
            kmActual = eventos.lastOrNull { it.km != null }?.km ?: viaje?.kmInicial,
            enRutaMs = viaje?.let { ahora - it.fechaSalida } ?: 0,
            manejoContinuoMs = manejo,
            alerta = if (viaje == null) AlertaHoras.NORMAL else CalculosViaje.alerta(manejo),
            descansoAbiertoId = eventos.lastOrNull { it.tipo == com.sistemasdeinformacion.proyecto.data.model.TipoEvento.DESCANSO && it.fechaFin == null }?.id,
            kmMes = km, gastosMes = gastos, viajesMes = n, sync = sync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoInicio())
}

@Composable
fun InicioScreen(nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { InicioViewModel(container.perfil.nombre, container.viajes, container.programadorSync) }
    val e by vm.estado.collectAsStateWithLifecycle()
    var hoja by remember { mutableStateOf<HojaRegistro?>(null) }
    val c = Bitacora.colores

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text("Hola,", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        Text(e.nombre, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        IndicadorSync(e.sync)
        Spacer(Modifier.height(18.dp))

        val viaje = e.enCurso
        if (e.cargando) {
            Spacer(Modifier.height(200.dp))
        } else if (viaje == null) {
            EstadoVacio(
                titulo = "Aún no tienes un viaje en curso",
                texto = "Crea el viaje antes de salir. Funciona aunque no tengas señal.",
                accion = "Crear viaje",
                onAccion = { nav.ir(NuevoViaje) },
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LetreroRuta(
                ruta = viaje.ruta,
                origen = viaje.origen,
                destino = viaje.destino,
                detalle = buildString {
                    append(viaje.tipoCarga)
                    if (viaje.requiereRefrigeracion) append(" refrigerada")
                    append(", ${Formatos.numero(viaje.pesoKg.toLong())} kg")
                },
                textoIzquierda = "${Formatos.km(((e.kmActual ?: 0) - (viaje.kmInicial ?: e.kmActual ?: 0)).coerceAtLeast(0))} recorridos",
                textoDerecha = "${Formatos.duracion(e.enRutaMs)} en ruta",
                onClick = { nav.ir(HojaDeRuta(viaje.id)) },
            )
            when (e.alerta) {
                AlertaHoras.PROXIMA -> Aviso(
                    "Llevas ${Formatos.duracion(e.manejoContinuoMs)} manejando. Te quedan ${Formatos.duracion(CalculosViaje.LIMITE_MANEJO_MS - e.manejoContinuoMs)} para descansar.",
                    c.acento, Modifier.padding(top = 12.dp), Icons.Outlined.Timer,
                )
                AlertaHoras.EXCEDIDA -> Aviso(
                    "Llevas ${Formatos.duracion(e.manejoContinuoMs)} manejando sin descanso. Detente y registra un descanso.",
                    c.peligro, Modifier.padding(top = 12.dp), Icons.Outlined.Timer,
                )
                AlertaHoras.NORMAL -> Unit
            }
            Spacer(Modifier.height(16.dp))
            if (e.descansoAbiertoId != null) {
                BotonPrincipal("Terminar descanso", { hoja = HojaRegistro.TerminarDescanso(e.descansoAbiertoId!!) })
            } else {
                BotonPrincipal("Registrar parada", { hoja = HojaRegistro.Parada })
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccionRapida("Combustible", Icons.Outlined.LocalGasStation, { hoja = HojaRegistro.Gasto(TipoGasto.COMBUSTIBLE) }, Modifier.weight(1f))
                AccionRapida("Peaje", Icons.Outlined.Toll, { hoja = HojaRegistro.Gasto(TipoGasto.PEAJE) }, Modifier.weight(1f))
                AccionRapida("Novedad", Icons.Outlined.ReportProblem, { hoja = HojaRegistro.Novedad }, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            buildAnnotatedString {
                val fuerte = SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontFamily = com.sistemasdeinformacion.proyecto.ui.theme.BarlowCondensed, fontSize = MaterialTheme.typography.headlineSmall.fontSize)
                withStyle(fuerte) { append(Formatos.km(e.kmMes)) }
                append(" recorridos este mes, ")
                withStyle(fuerte) { append(Formatos.pesos(e.gastosMes)) }
                append(" en gastos y ")
                withStyle(fuerte) { append(e.viajesMes.toString()) }
                append(if (e.viajesMes == 1) " viaje." else " viajes.")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = c.textoSuave,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }

    e.enCurso?.let { v -> HojasDeRegistro(hoja, v.id, e.kmActual, onCerrar = { hoja = null }) }
}
