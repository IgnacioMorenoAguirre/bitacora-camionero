package com.sistemasdeinformacion.proyecto.data.local

import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.sync.rutaRemotaDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchivosAdjuntosTest {

    @get:Rule val carpeta = TemporaryFolder()

    @Test fun colocar_dejaElNombreIgualQueLaRutaDelBucket() {
        val archivos = ArchivosAdjuntos(carpeta.root)
        TipoAdjunto.entries.forEach { tipo ->
            val tmp = archivos.temporal("v1", tipo).apply { writeBytes(byteArrayOf(7)) }

            val final = ArchivosAdjuntos.colocar(tmp, "a-${tipo.valor}", tipo)

            val fila = AdjuntoEntity("a-${tipo.valor}", "v1", tipo, final.absolutePath, null, null, 0, modificadoLocal = 0, syncPendiente = true)
            assertEquals(File(carpeta.root, rutaRemotaDe(fila).let { "adjuntos/$it" }), final)
            assertTrue(final.isFile)
            assertFalse(tmp.exists())
        }
    }

    @Test fun carpetaDe_rechazaRutasFueraDeLaCarpeta() {
        val archivos = ArchivosAdjuntos(carpeta.root)
        assertThrows(IllegalArgumentException::class.java) { archivos.carpetaDe("../otro") }
        assertThrows(IllegalArgumentException::class.java) { archivos.carpetaDe("..") }
    }

    @Test fun borrarTodo_eliminaFotosYFirmas() {
        val archivos = ArchivosAdjuntos(carpeta.root)
        archivos.temporal("v1", TipoAdjunto.FOTO).writeBytes(byteArrayOf(1))

        archivos.borrarTodo()

        assertFalse(File(carpeta.root, "adjuntos").exists())
    }
}
