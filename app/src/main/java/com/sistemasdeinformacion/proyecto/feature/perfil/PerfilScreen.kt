package com.sistemasdeinformacion.proyecto.feature.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sistemasdeinformacion.proyecto.sync.EstadoSync
import com.sistemasdeinformacion.proyecto.ui.componentes.IndicadorSync
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.launch

/** Perfil básico. Cerrar sesión y "Sincronizar ahora" llegan con las Tasks 6 y 8. */
@Composable
fun PerfilScreen() {
    val container = LocalContainer.current
    val claro by container.preferencias.temaClaro.collectAsStateWithLifecycle(false)
    val sync by remember { container.programadorSync.estado }.collectAsStateWithLifecycle(EstadoSync())
    val alcance = rememberCoroutineScope()
    val c = Bitacora.colores
    val p = container.perfil

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Perfil", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Bloque {
            Text(p.nombre, style = MaterialTheme.typography.titleLarge)
            Text(p.email, style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
            Text(p.rol.etiqueta, style = MaterialTheme.typography.labelLarge, color = c.acento, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Bloque {
            Text("Sincronización", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            IndicadorSync(sync)
            Spacer(Modifier.height(6.dp))
            Text(
                "Todo lo que registras se guarda primero en este teléfono y se envía solo cuando hay conexión.",
                style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
            )
        }
        Spacer(Modifier.height(12.dp))
        Bloque {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tema claro", style = MaterialTheme.typography.titleMedium)
                    Text("Útil a pleno sol.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
                }
                Switch(
                    checked = claro,
                    onCheckedChange = { valor -> alcance.launch { container.preferencias.guardarTemaClaro(valor) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.acento, checkedThumbColor = c.sobreAcento),
                )
            }
        }
    }
}

@Composable
private fun Bloque(contenido: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) { contenido() }
}
