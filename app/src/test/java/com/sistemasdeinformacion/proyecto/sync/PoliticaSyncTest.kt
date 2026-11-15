package com.sistemasdeinformacion.proyecto.sync

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaSyncTest {

    @Test fun ok_sinPendientes_termina() =
        assertEquals(PasoSync.LISTO, siguientePaso(ResultadoSync.Ok(0, 3, emptyList()), pendientes = 0))

    @Test fun ok_conPendientesNuevos_daOtraVueltaAunqueNoHayaEnviadoNada() =
        // El chofer guardó un gasto mientras la sync solo bajaba: no espera al periódico.
        assertEquals(PasoSync.OTRA_VUELTA, siguientePaso(ResultadoSync.Ok(0, 2, emptyList()), pendientes = 1))

    @Test fun sinConexion_reintenta() =
        assertEquals(PasoSync.REINTENTAR, siguientePaso(ResultadoSync.SinConexion, pendientes = 4))

    @Test fun fallaPermanente_noEntraEnBackoff() =
        assertEquals(PasoSync.LISTO, siguientePaso(ResultadoSync.Falla("Sin permiso", reintentar = false), pendientes = 1))

    @Test fun fallaPasajera_reintenta() =
        assertEquals(PasoSync.REINTENTAR, siguientePaso(ResultadoSync.Falla("Error", reintentar = true), pendientes = 1))

    @Test fun alVolverLaRed_noCancelaUnaSyncQueEstaCorriendo() {
        assertFalse(reemplazarAlVolverLaRed(listOf(WorkInfo.State.RUNNING)))
        assertTrue(reemplazarAlVolverLaRed(listOf(WorkInfo.State.ENQUEUED)))
        assertTrue(reemplazarAlVolverLaRed(listOf(WorkInfo.State.SUCCEEDED)))
        assertTrue(reemplazarAlVolverLaRed(emptyList()))
    }
}
