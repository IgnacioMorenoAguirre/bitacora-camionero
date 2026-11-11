package com.sistemasdeinformacion.proyecto.feature.entrega

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sistemasdeinformacion.proyecto.data.repo.KmIncoherenteException
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.navegacion.Resumen
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.launch

/** Entrega y cierre del viaje. La foto o firma de quien recibe se agrega en la Task 12. */
@Composable
fun EntregaScreen(viajeId: String, nav: Navegador) {
    val container = LocalContainer.current
    val detalle by remember(viajeId) { container.viajes.detalle(viajeId) }.collectAsStateWithLifecycle(null)
    val alcance = rememberCoroutineScope()
    var km by remember { mutableStateOf("") }
    var receptor by remember { mutableStateOf("") }
    var errorKm by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val c = Bitacora.colores
    val v = detalle?.viaje
    val ultimoKm = detalle?.eventos?.lastOrNull { it.km != null }?.km ?: v?.kmInicial

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
            Text("Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text("Entrega", style = MaterialTheme.typography.headlineLarge)
            v?.let {
                Text("Cierra el viaje a ${it.destino} para ${it.cliente}.", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
            }
            Spacer(Modifier.height(18.dp))
            CampoTexto(
                km, { km = it; errorKm = null }, "Kilometraje al llegar", error = errorKm, teclado = KeyboardType.Number,
                sufijo = ultimoKm?.let { "último: ${Formatos.numero(it.toLong())}" },
            )
            Spacer(Modifier.height(12.dp))
            CampoTexto(receptor, { receptor = it }, "Quién recibe la carga (opcional)", mayusculas = KeyboardCapitalization.Words)
            Spacer(Modifier.height(12.dp))
            Text("La hora y la ubicación de la entrega se registran solas.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        }
        BotonPrincipal(
            "Finalizar viaje",
            cargando = guardando,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            onClick = {
                val kmNum = Validaciones.parsearEntero(km)
                if (kmNum == null) { errorKm = "Escribe el kilometraje del odómetro."; return@BotonPrincipal }
                if (ultimoKm != null && kmNum < ultimoKm) {
                    errorKm = "No puede ser menor que el último registrado (${Formatos.km(ultimoKm)})."
                    return@BotonPrincipal
                }
                guardando = true
                alcance.launch {
                    container.viajes.finalizar(viajeId, kmNum, receptor)
                        .onSuccess { nav.raizCon(Resumen(viajeId)) }
                        .onFailure {
                            guardando = false
                            errorKm = if (it is KmIncoherenteException) "No puede ser menor que el inicial (${Formatos.km(it.kmInicial)})." else "No se pudo cerrar el viaje. Intenta de nuevo."
                        }
                }
            },
        )
    }
}
