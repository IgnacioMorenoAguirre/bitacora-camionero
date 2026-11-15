package com.sistemasdeinformacion.proyecto.feature.entrega

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.local.ArchivosAdjuntos
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.repo.AdjuntoNoDisponibleException
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViaje
import com.sistemasdeinformacion.proyecto.data.repo.KmIncoherenteException
import com.sistemasdeinformacion.proyecto.data.repo.NuevoAdjunto
import com.sistemasdeinformacion.proyecto.data.repo.ViajeSoloLecturaException
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/** Foto o firma lista para adjuntar (el archivo ya tiene contenido). */
data class AdjuntoListo(val tipo: TipoAdjunto, val archivo: File)

fun msjKmMenorQueInicial(km: Int) = "El km final no puede ser menor que el inicial (${Formatos.km(km)})."

/**
 * Cierre del viaje: km final, quién recibe y, si se quiere, una foto de la guía o la firma (una u otra).
 * La ruta de la foto en curso se guarda en [guardado]: si Android cierra la app mientras la cámara
 * está abierta, al volver se recupera.
 */
class EntregaViewModel(
    private val viajeId: String,
    private val repo: ViajesRepositorio,
    private val archivos: ArchivosAdjuntos,
    private val guardado: SavedStateHandle,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Reduce y endereza la foto de la cámara (en la app, [FotoEntrega.preparar]); falla sin romper nada. */
    private val prepararFoto: (File) -> Unit = {},
) : ViewModel() {

    val detalle: StateFlow<DetalleViaje?> = repo.detalle(viajeId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var km by mutableStateOf("")
    var receptor by mutableStateOf("")
    var errorKm by mutableStateOf<String?>(null)
        private set
    var errorAdjunto by mutableStateOf<String?>(null)
    var guardando by mutableStateOf(false)
        private set
    var adjunto by mutableStateOf(recuperarAdjunto())
        private set
    /** Mientras se reduce la foto o se guarda la firma no se puede finalizar (se perdería el respaldo). */
    var preparandoAdjunto by mutableStateOf(false)
        private set
    private var terminado = false

    /** Último km anotado en el viaje (el inicial si no hay otro). */
    val ultimoKm: Int? get() = detalle.value?.let { d -> d.eventos.lastOrNull { it.km != null }?.km ?: d.viaje?.kmInicial }

    fun cambiarKm(v: String) { km = v; errorKm = null }

    /** Archivo vacío para que la cámara escriba la foto (se pasa por el FileProvider). */
    fun archivoParaFoto(): File {
        pendienteDeCamara()?.delete()
        val f = archivos.temporal(viajeId, TipoAdjunto.FOTO)
        guardado[CLAVE_FOTO_EN_CURSO] = f.absolutePath
        errorAdjunto = null
        return f
    }

    /** Resultado de la cámara: si se canceló o no escribió nada, se descarta el temporal. */
    fun fotoTomada(ok: Boolean) {
        val f = pendienteDeCamara() ?: return
        guardado.remove<String>(CLAVE_FOTO_EN_CURSO)
        if (!(ok && f.isFile && f.length() > 0)) { f.delete(); return }
        preparandoAdjunto = true
        viewModelScope.launch {
            withContext(io) { runCatching { prepararFoto(f) } }
            reemplazarAdjunto(AdjuntoListo(TipoAdjunto.FOTO, f))
            preparandoAdjunto = false
        }
    }

    /** No se pudo abrir la cámara: se borra el temporal y se explica qué hacer. */
    fun camaraNoDisponible() {
        pendienteDeCamara()?.delete()
        guardado.remove<String>(CLAVE_FOTO_EN_CURSO)
        errorAdjunto = "No se pudo abrir la cámara. Pide la firma o finaliza sin foto."
    }

    /** Guarda la firma en PNG; [escribir] recibe el archivo abierto (la pantalla comprime el Bitmap). */
    fun usarFirma(escribir: (OutputStream) -> Unit) {
        preparandoAdjunto = true
        viewModelScope.launch {
            val f = archivos.temporal(viajeId, TipoAdjunto.FIRMA)
            val ok = withContext(io) {
                runCatching { f.outputStream().use(escribir) }.isSuccess && f.length() > 0
            }
            if (ok) {
                reemplazarAdjunto(AdjuntoListo(TipoAdjunto.FIRMA, f))
            } else {
                withContext(io) { f.delete() }
                errorAdjunto = "No se pudo guardar la firma. Pídela de nuevo."
            }
            preparandoAdjunto = false
        }
    }

    fun quitarAdjunto() {
        adjunto?.archivo?.delete()
        adjunto = null
        guardado.remove<String>(CLAVE_ADJUNTO)
    }

    fun finalizar(alTerminar: () -> Unit) {
        if (guardando || preparandoAdjunto) return
        val kmNum = Validaciones.parsearEntero(km)
        if (kmNum == null) { errorKm = "Escribe el kilometraje del odómetro."; return }
        val inicial = detalle.value?.viaje?.kmInicial
        if (inicial != null && kmNum < inicial) { errorKm = msjKmMenorQueInicial(inicial); return }
        val ultimo = ultimoKm
        if (ultimo != null && kmNum < ultimo) {
            errorKm = "El km final no puede ser menor que el último registrado (${Formatos.km(ultimo)})."
            return
        }
        guardando = true
        val quienRecibe = receptor.trim().ifBlank { null }
        val nuevo = adjunto?.let { NuevoAdjunto(it.tipo, it.archivo, quienRecibe) }
        viewModelScope.launch {
            repo.finalizar(viajeId, kmNum, quienRecibe, nuevo)
                .onSuccess {
                    terminado = true
                    guardado.remove<String>(CLAVE_ADJUNTO)
                    alTerminar()
                }
                .onFailure { e ->
                    guardando = false
                    when (e) {
                        is KmIncoherenteException -> errorKm = msjKmMenorQueInicial(e.kmInicial)
                        is AdjuntoNoDisponibleException -> { adjunto = null; errorAdjunto = e.message }
                        is ViajeSoloLecturaException -> errorKm = e.message
                        else -> errorKm = "No se pudo cerrar el viaje. Vuelve a intentar."
                    }
                }
        }
    }

    private fun reemplazarAdjunto(nuevo: AdjuntoListo) {
        adjunto?.takeIf { it.archivo != nuevo.archivo }?.archivo?.delete()
        adjunto = nuevo
        errorAdjunto = null
        guardado[CLAVE_ADJUNTO] = "${nuevo.tipo.valor}|${nuevo.archivo.absolutePath}"
    }

    private fun pendienteDeCamara(): File? = guardado.get<String>(CLAVE_FOTO_EN_CURSO)?.let(::File)

    private fun recuperarAdjunto(): AdjuntoListo? {
        val (tipo, ruta) = guardado.get<String>(CLAVE_ADJUNTO)?.split('|', limit = 2)?.takeIf { it.size == 2 } ?: return null
        val f = File(ruta).takeIf { it.isFile } ?: return null
        return AdjuntoListo(if (tipo == TipoAdjunto.FIRMA.valor) TipoAdjunto.FIRMA else TipoAdjunto.FOTO, f)
    }

    /** Si el chofer sale sin finalizar, no quedan fotos huérfanas en el teléfono. */
    override fun onCleared() {
        if (!terminado) {
            adjunto?.archivo?.delete()
            pendienteDeCamara()?.delete()
        }
    }

    private companion object {
        const val CLAVE_FOTO_EN_CURSO = "fotoEnCurso"
        const val CLAVE_ADJUNTO = "adjunto"
    }
}
