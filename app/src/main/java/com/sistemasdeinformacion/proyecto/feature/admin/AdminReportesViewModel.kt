package com.sistemasdeinformacion.proyecto.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.repo.AdminRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAdmin
import com.sistemasdeinformacion.proyecto.domain.Reporte
import com.sistemasdeinformacion.proyecto.domain.Reportes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class EstadoAdminReportes(
    val mes: YearMonth,
    val mesActual: YearMonth,
    val cargando: Boolean = true,
    val sinConexion: Boolean = false,
    val error: String? = null,
    val reporte: Reporte? = null,
) {
    /** No hay reportes de meses que aún no empiezan. */
    val puedeAvanzar: Boolean get() = mes < mesActual
}

/** Reporte mensual del administrador: baja los viajes finalizados del mes y los agrupa con [Reportes]. */
class AdminReportesViewModel(
    private val repo: AdminRepositorio,
    conexion: Flow<Boolean>,
    ahora: () -> Long = System::currentTimeMillis,
    private val zona: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val actual = YearMonth.from(Instant.ofEpochMilli(ahora()).atZone(zona))
    private val _estado = MutableStateFlow(EstadoAdminReportes(mes = actual, mesActual = actual))
    val estado: StateFlow<EstadoAdminReportes> = _estado.asStateFlow()
    private var carga: Job? = null

    init {
        // Sin red se muestra el aviso; al volver, se recarga solo.
        viewModelScope.launch {
            conexion.collect { hay ->
                if (!hay) _estado.update { it.copy(sinConexion = true, cargando = false) }
                else if (_estado.value.sinConexion || _estado.value.reporte == null) cargar()
            }
        }
    }

    fun mesAnterior() = irA(_estado.value.mes.minusMonths(1))

    fun mesSiguiente() {
        if (_estado.value.puedeAvanzar) irA(_estado.value.mes.plusMonths(1))
    }

    fun reintentar() = cargar()

    private fun irA(mes: YearMonth) {
        _estado.update { it.copy(mes = mes, reporte = null) }
        cargar()
    }

    private fun cargar() {
        carga?.cancel()
        val mes = _estado.value.mes
        _estado.update { it.copy(cargando = true, sinConexion = false, error = null) }
        carga = viewModelScope.launch {
            try {
                val desde = mes.atDay(1).atStartOfDay(zona).toInstant().toEpochMilli()
                val hasta = mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant().toEpochMilli()
                val filas = repo.finalizadosEntre(desde, hasta)
                val reporte = Reportes.mensual(filas, mes.year, mes.monthValue, zona)
                _estado.update { it.copy(cargando = false, reporte = reporte) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update {
                    if (e is ErrorAdmin.SinConexion) it.copy(cargando = false, sinConexion = true)
                    else it.copy(cargando = false, error = (e as? ErrorAdmin)?.message ?: ErrorAdmin.Desconocido.message)
                }
            }
        }
    }
}
