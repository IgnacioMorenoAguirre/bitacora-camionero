package com.sistemasdeinformacion.proyecto.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sistemasdeinformacion.proyecto.sync.EstadoSync
import com.sistemasdeinformacion.proyecto.ui.theme.TemaBitacora

private val hitosEjemplo = listOf(
    Hito("1", 0, "06:10", "Inicio en Santiago", "Revisión del camión completa"),
    Hito("2", 142, "08:45", "Combustible", "180 L, $210.000"),
    Hito("3", 151, "09:05", "Peaje Angostura", "$4.100"),
    Hito("4", 214, "11:30, hace 12 min", "Descanso", "Llevas 5 h 20 min manejando hoy", actual = true),
)

@Composable
private fun Muestra(claro: Boolean) = TemaBitacora(claro) {
    Surface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IndicadorSync(EstadoSync(pendientes = 3, conectado = false))
            LetreroRuta("5 SUR", "Santiago", "Concepción", "Fruta refrigerada, 18.500 kg", "214 km", "5 h 20 min en ruta")
            BotonPrincipal("Registrar parada", {})
            CaminoRuta(hitosEjemplo, animarEntrada = false)
        }
    }
}

@Preview(name = "Oscuro", heightDp = 900) @Composable private fun PreviewOscuro() = Muestra(false)
@Preview(name = "Claro", heightDp = 900) @Composable private fun PreviewClaro() = Muestra(true)
