package com.sistemasdeinformacion.proyecto.core

import java.util.UUID

/** IDs definitivos generados en el teléfono: permiten crear registros sin señal y sincronizarlos sin duplicar. */
object Ids {
    fun nuevo(): String = UUID.randomUUID().toString()
}

/** Reloj inyectable para poder probar cálculos que dependen de la hora. */
fun interface Reloj {
    fun ahora(): Long
}

object RelojSistema : Reloj {
    override fun ahora(): Long = System.currentTimeMillis()
}
