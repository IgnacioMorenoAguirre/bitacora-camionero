package com.sistemasdeinformacion.proyecto.sync

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VolvioLaRedTest {

    @Test fun avisaSoloAlPasarDeSinRedAConRed() = runTest {
        // Al abrir con red no avisa (para eso está el alGuardar inicial); repetir "con red" tampoco.
        val avisos = volvioLaRed(flowOf(true, false, false, true, true, false, true)).toList()
        assertEquals(2, avisos.size)
    }

    @Test fun abrirSinRedYRecuperarla_avisa() = runTest {
        assertEquals(1, volvioLaRed(flowOf(false, true)).toList().size)
    }
}
