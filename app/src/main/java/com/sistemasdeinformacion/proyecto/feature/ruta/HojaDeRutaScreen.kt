package com.sistemasdeinformacion.proyecto.feature.ruta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.core.RelojSistema
import com.sistemasdeinformacion.proyecto.data.model.AlertaHoras
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViaje
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.domain.CalculosViaje
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.ui.componentes.AccionRapida
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonSecundario
import com.sistemasdeinformacion.proyecto.ui.componentes.CaminoRuta
import com.sistemasdeinformacion.proyecto.ui.componentes.EscudoRuta
import com.sistemasdeinformacion.proyecto.ui.componentes.Hito
import com.sistemasdeinformacion.proyecto.ui.navegacion.Entrega
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.navegacion.Resumen
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class EstadoHojaDeRuta(
    val cargando: Boolean = true,
    val detalle: DetalleViaje? = null,
    val hitos: List<Hito> = emptyList(),
    val manejoContinuoMs: Long = 0,
    val alerta: AlertaHoras = AlertaHoras.NORMAL,
    val descansoAbiertoId: String? = null,
    val kmActual: Int? = null,
) {
    val enCurso: Boolean get() = detalle?.viaje?.estado == EstadoViaje.EN_CURSO
    val soloLectura: Boolean get() = detalle?.viaje?.validacion == EstadoValidacion.APROBADO
}

class HojaDeRutaViewModel(viajeId: String, repo: ViajesRepositorio) : ViewModel() {
    private val tic = flow { while (true) { emit(RelojSistema.ahora()); delay(30_000) } }

    val estado: StateFlow<EstadoHojaDeRuta> = combine(repo.detalle(viajeId), tic) { d, ahora ->
        val v = d.viaje
        val manejo = if (v != null && v.estado == EstadoViaje.EN_CURSO) CalculosViaje.manejoContinuoMs(v.fechaSalida, d.eventos, ahora) else 0
        EstadoHojaDeRuta(
            cargando = false,
            detalle = d,
            hitos = construirHitos(d, ahora),
            manejoContinuoMs = manejo,
            alerta = if (v?.estado == EstadoViaje.EN_CURSO) CalculosViaje.alerta(manejo) else AlertaHoras.NORMAL,
            descansoAbiertoId = d.eventos.lastOrNull { it.tipo == TipoEvento.DESCANSO && it.fechaFin == null }?.id,
            kmActual = d.eventos.lastOrNull { it.km != null }?.km ?: v?.kmInicial,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoHojaDeRuta())
}

@Composable
fun HojaDeRutaScreen(viajeId: String, nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { HojaDeRutaViewModel(viajeId, container.viajes) }
    val e by vm.estado.collectAsStateWithLifecycle()
    var hoja by remember { mutableStateOf<HojaRegistro?>(null) }
    val c = Bitacora.colores
    val v = e.detalle?.viaje

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
            Text("Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Text("Hoja de ruta", style = MaterialTheme.typography.headlineLarge)
            if (v != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    v.ruta?.let { EscudoRuta(it) }
                    Text(
                        "${v.origen} a ${v.destino}, ${e.detalle?.camion?.patente ?: "sin camión"}, salió ${Formatos.hora(v.fechaSalida)}",
                        style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
                    )
                }
                if (v.validacion == EstadoValidacion.OBSERVADO && v.comentarioValidacion != null) {
                    Aviso("Observado por el administrador: ${v.comentarioValidacion}", c.acento, Modifier.padding(top = 12.dp))
                }
                if (e.soloLectura) {
                    Aviso("Bitácora aprobada. Ya no se puede modificar.", c.textoSuave, Modifier.padding(top = 12.dp))
                }
                when (e.alerta) {
                    AlertaHoras.PROXIMA -> Aviso("Llevas ${Formatos.duracion(e.manejoContinuoMs)} manejando. Planifica tu descanso.", c.acento, Modifier.padding(top = 12.dp), Icons.Outlined.Timer)
                    AlertaHoras.EXCEDIDA -> Aviso("Superaste las 5 horas de manejo continuo. Registra un descanso.", c.peligro, Modifier.padding(top = 12.dp), Icons.Outlined.Timer)
                    AlertaHoras.NORMAL -> Unit
                }
            }
            Spacer(Modifier.height(18.dp))
            if (!e.cargando) CaminoRuta(e.hitos, animarEntrada = true)
            Spacer(Modifier.height(12.dp))
        }

        if (v != null) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                if (e.enCurso && !e.soloLectura) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccionRapida("Combustible", Icons.Outlined.LocalGasStation, { hoja = HojaRegistro.Gasto(TipoGasto.COMBUSTIBLE) }, Modifier.weight(1f))
                            AccionRapida("Peaje", Icons.Outlined.Toll, { hoja = HojaRegistro.Gasto(TipoGasto.PEAJE) }, Modifier.weight(1f))
                            AccionRapida("Novedad", Icons.Outlined.ReportProblem, { hoja = HojaRegistro.Novedad }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        if (e.descansoAbiertoId != null) {
                            BotonPrincipal("Terminar descanso", { hoja = HojaRegistro.TerminarDescanso(e.descansoAbiertoId!!) })
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BotonPrincipal("Registrar parada", { hoja = HojaRegistro.Parada }, Modifier.weight(1.3f))
                                BotonSecundario("Finalizar", { nav.ir(Entrega(viajeId)) }, Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    BotonPrincipal("Ver resumen del viaje", { nav.ir(Resumen(viajeId)) })
                }
            }
        }
    }

    HojasDeRegistro(hoja, viajeId, e.kmActual, onCerrar = { hoja = null })
}
