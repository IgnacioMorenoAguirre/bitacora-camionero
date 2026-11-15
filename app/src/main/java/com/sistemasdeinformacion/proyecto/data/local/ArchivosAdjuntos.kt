package com.sistemasdeinformacion.proyecto.data.local

import com.sistemasdeinformacion.proyecto.core.Ids
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import java.io.File

/**
 * Fotos y firmas de las entregas, en almacenamiento privado de la app: `{raiz}/adjuntos/{viajeId}/{id}.jpg|png`
 * (raiz = filesDir). El nombre coincide con la ruta del bucket (rutaRemotaDe en la sync).
 * La cámara y el panel de firma escriben primero en un temporal; al finalizar el viaje el repositorio
 * lo renombra con el id del adjunto ([colocar]).
 */
class ArchivosAdjuntos(private val raiz: File) {

    private val carpeta: File get() = File(raiz, CARPETA)

    fun carpetaDe(viajeId: String): File {
        require(viajeId.isNotBlank() && viajeId.none { it == '/' || it == '\\' } && viajeId != "." && viajeId != "..") {
            "Id de viaje no válido para una carpeta"
        }
        return File(carpeta, viajeId).apply { mkdirs() }
    }

    /** Archivo nuevo y vacío donde escribir una foto o firma del viaje. */
    fun temporal(viajeId: String, tipo: TipoAdjunto): File =
        File(carpetaDe(viajeId), "$PREFIJO_TEMPORAL${Ids.nuevo()}.${extension(tipo)}")

    /** Al cerrar sesión: se borran todas las fotos y firmas guardadas en el teléfono. */
    fun borrarTodo() {
        carpeta.deleteRecursively()
    }

    companion object {
        const val CARPETA = "adjuntos"
        private const val PREFIJO_TEMPORAL = "tmp-"

        /** La firma se exporta en PNG; la foto de la cámara es JPEG. */
        fun extension(tipo: TipoAdjunto): String = if (tipo == TipoAdjunto.FIRMA) "png" else "jpg"

        /** Deja [archivo] como `{id}.jpg|png` en su misma carpeta y devuelve el archivo final. */
        fun colocar(archivo: File, id: String, tipo: TipoAdjunto): File {
            val destino = File(archivo.parentFile, "$id.${extension(tipo)}")
            if (archivo.absoluteFile == destino.absoluteFile) return destino
            if (!archivo.renameTo(destino)) {
                archivo.copyTo(destino, overwrite = true)
                archivo.delete()
            }
            return destino
        }
    }
}
