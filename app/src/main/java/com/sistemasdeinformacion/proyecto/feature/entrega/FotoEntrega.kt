package com.sistemasdeinformacion.proyecto.feature.entrega

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.max

/** Ajustes de la foto de la guía antes de guardarla (corre fuera del hilo principal). */
object FotoEntrega {

    private const val LADO_MAXIMO = 1600
    private const val CALIDAD_JPEG = 82

    /**
     * Reduce la foto a [LADO_MAXIMO] px por lado y la endereza según su EXIF: el administrador la ve
     * derecha y pesa unos cientos de KB en vez de varios MB (se sube por datos móviles). Al volver a
     * comprimir se pierde el EXIF, incluida la ubicación GPS que algunas cámaras agregan.
     * Si algo falla, la foto original queda tal cual.
     */
    fun preparar(archivo: File) {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, limites)
        val ladoMayor = max(limites.outWidth, limites.outHeight)
        if (ladoMayor <= 0) return
        var muestra = 1
        while (ladoMayor / (muestra * 2) >= LADO_MAXIMO) muestra *= 2
        val leida = BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return

        val giro = when (ExifInterface(archivo.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val escala = (LADO_MAXIMO.toFloat() / max(leida.width, leida.height)).coerceAtMost(1f)
        val matriz = Matrix().apply {
            postScale(escala, escala)
            postRotate(giro)
        }
        val final = if (giro == 0f && escala == 1f) leida
        else Bitmap.createBitmap(leida, 0, 0, leida.width, leida.height, matriz, true)

        val temporal = File(archivo.parentFile, archivo.name + ".tmp")
        try {
            temporal.outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, it) }
            if (temporal.length() > 0 && !temporal.renameTo(archivo)) {
                temporal.copyTo(archivo, overwrite = true)
            }
        } finally {
            temporal.delete()
            if (final !== leida) final.recycle()
            leida.recycle()
        }
    }

    /** Miniatura para la vista previa en la pantalla de entrega (ya enderezada por [preparar]). */
    fun miniatura(archivo: File, anchoMax: Int = 720): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, limites)
        if (limites.outWidth <= 0) return null
        var muestra = 1
        while (limites.outWidth / (muestra * 2) >= anchoMax) muestra *= 2
        return BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra })
    }
}
