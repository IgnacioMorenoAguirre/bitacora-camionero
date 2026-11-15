package com.sistemasdeinformacion.proyecto.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.remote.PerfilDto
import com.sistemasdeinformacion.proyecto.data.remote.ResumenViajeDto
import com.sistemasdeinformacion.proyecto.data.repo.AdminRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAdmin
import com.sistemasdeinformacion.proyecto.data.repo.FiltroViajes
import com.sistemasdeinformacion.proyecto.data.repo.LIMITE_LISTA_ADMIN
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Rango de fechas de salida que ofrece la lista. */
enum class Periodo(val etiqueta: String, val dias: Long?) {
    TODO("Todo", null),
    HOY("Hoy", 0),
    SEMANA("7 días", 6),
    MES("30 días", 29),
}

data class FilaViaje(val resumen: ResumenViajeDto, val excedioHoras: Boolean)

sealed interface ListaViajes {
    data object Cargando : ListaViajes
    data object SinConexion : ListaViajes
    data class Error(val mensaje: String) : ListaViajes
    /** [limiteAlcanzado]: hay más viajes que los que se muestran; conviene filtrar. */
    data class Lista(val filas: List<FilaViaje>, val limiteAlcanzado: Boolean) : ListaViajes
}

data class EstadoAdminViajes(
    val lista: ListaViajes = ListaViajes.Cargando,
    val periodo: Periodo = Periodo.TODO,
    val filtro: FiltroViajes = FiltroViajes(),
    val choferes: List<PerfilDto> = emptyList(),
)

/** Lista de viajes de todos los choferes, en vivo (Realtime) y con filtros. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViajesViewModel(
    private val repo: AdminRepositorio,
    conexion: Flow<Boolean>,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val zona: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val periodo = MutableStateFlow(Periodo.TODO)
    private val filtro = MutableStateFlow(FiltroViajes())
    private val choferes = MutableStateFlow<List<PerfilDto>>(emptyList())
    private val intento = MutableStateFlow(0)

    private val lista: Flow<ListaViajes> = combine(conexion, filtro, intento) { hay, f, _ -> hay to f }.flatMapLatest { (hay, f) ->
        if (!hay) flowOf(ListaViajes.SinConexion)
        else repo.viajesEnVivo(f)
            .map<List<ResumenViajeDto>, ListaViajes> { viajes ->
                // La marca de horas es un extra: si falla, la lista se muestra igual.
                val excesos = try { repo.excesosDeHoras(viajes) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
                ListaViajes.Lista(viajes.map { FilaViaje(it, it.id in excesos) }, viajes.size >= LIMITE_LISTA_ADMIN)
            }
            .catch { e ->
                emit(if (e is ErrorAdmin.SinConexion) ListaViajes.SinConexion else ListaViajes.Error((e as? ErrorAdmin)?.message ?: ErrorAdmin.Desconocido.message!!))
            }
    }

    val estado: StateFlow<EstadoAdminViajes> = combine(lista, periodo, filtro, choferes) { l, p, f, c -> EstadoAdminViajes(l, p, f, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoAdminViajes())

    init {
        viewModelScope.launch { cargarChoferes() }
    }

    private suspend fun cargarChoferes() {
        if (choferes.value.isNotEmpty()) return
        choferes.value = try { repo.choferes() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    fun reintentar() {
        intento.update { it + 1 }
        viewModelScope.launch { cargarChoferes() }
    }

    fun elegirChofer(id: String?) = filtro.update { it.copy(choferId = id) }
    fun elegirEstado(e: EstadoViaje?) = filtro.update { it.copy(estado = e) }
    fun elegirValidacion(v: EstadoValidacion?) = filtro.update { it.copy(validacion = v) }

    fun elegirPeriodo(p: Periodo) {
        periodo.value = p
        val desde = p.dias?.let { dias ->
            Instant.ofEpochMilli(ahora()).atZone(zona).toLocalDate().minusDays(dias).atStartOfDay(zona).toInstant().toEpochMilli()
        }
        filtro.update { it.copy(desde = desde, hasta = null) }
    }
}
