package com.sistemasdeinformacion.proyecto.data.model

/**
 * Enumeraciones del dominio. [valor] es el texto que se guarda en Room y en PostgreSQL
 * (coincide con los tipos enum definidos en supabase/schema.sql).
 */
interface ValorEnum {
    val valor: String
    val etiqueta: String
}

inline fun <reified T> desdeValor(valor: String?, porDefecto: T): T
        where T : Enum<T>, T : ValorEnum =
    enumValues<T>().firstOrNull { it.valor == valor } ?: porDefecto

enum class Rol(override val valor: String, override val etiqueta: String) : ValorEnum {
    CHOFER("chofer", "Chofer"),
    ADMIN("admin", "Administrador"),
}

enum class EstadoViaje(override val valor: String, override val etiqueta: String) : ValorEnum {
    EN_CURSO("en_curso", "En curso"),
    FINALIZADO("finalizado", "Finalizado"),
    CANCELADO("cancelado", "Cancelado"),
}

enum class EstadoValidacion(override val valor: String, override val etiqueta: String) : ValorEnum {
    PENDIENTE("pendiente", "Pendiente"),
    APROBADO("aprobado", "Aprobado"),
    OBSERVADO("observado", "Observado"),
}

enum class TipoEvento(override val valor: String, override val etiqueta: String) : ValorEnum {
    INICIO("inicio", "Inicio de viaje"),
    PARADA("parada", "Parada"),
    CARGA("carga", "Carga"),
    DESCARGA("descarga", "Descarga"),
    FIN("fin", "Fin de viaje"),
}

enum class TipoGasto(override val valor: String, override val etiqueta: String) : ValorEnum {
    COMBUSTIBLE("combustible", "Combustible"),
    PEAJE("peaje", "Peaje"),
    ALIMENTACION("alimentacion", "Alimentación"),
    MANTENCION("mantencion", "Mantención"),
    OTRO("otro", "Otro"),
}

enum class TipoNovedad(override val valor: String, override val etiqueta: String) : ValorEnum {
    INCIDENTE("incidente", "Incidente"),
    FALLA_MECANICA("falla_mecanica", "Falla mecánica"),
    RETRASO("retraso", "Retraso"),
    ACCIDENTE("accidente", "Accidente"),
    CLIMA("clima", "Clima"),
    OTRO("otro", "Otro"),
}

enum class Gravedad(override val valor: String, override val etiqueta: String) : ValorEnum {
    BAJA("baja", "Baja"),
    MEDIA("media", "Media"),
    ALTA("alta", "Alta"),
}
