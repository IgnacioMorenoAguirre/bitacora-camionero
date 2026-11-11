package com.sistemasdeinformacion.proyecto.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad
import com.sistemasdeinformacion.proyecto.data.model.desdeValor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Converters {
    @TypeConverter fun deEstadoViaje(v: EstadoViaje): String = v.valor
    @TypeConverter fun aEstadoViaje(v: String): EstadoViaje = desdeValor(v, EstadoViaje.EN_CURSO)

    @TypeConverter fun deValidacion(v: EstadoValidacion): String = v.valor
    @TypeConverter fun aValidacion(v: String): EstadoValidacion = desdeValor(v, EstadoValidacion.PENDIENTE)

    @TypeConverter fun deTipoEvento(v: TipoEvento): String = v.valor
    @TypeConverter fun aTipoEvento(v: String): TipoEvento = desdeValor(v, TipoEvento.PARADA)

    @TypeConverter fun deTipoGasto(v: TipoGasto): String = v.valor
    @TypeConverter fun aTipoGasto(v: String): TipoGasto = desdeValor(v, TipoGasto.OTRO)

    @TypeConverter fun deTipoNovedad(v: TipoNovedad): String = v.valor
    @TypeConverter fun aTipoNovedad(v: String): TipoNovedad = desdeValor(v, TipoNovedad.OTRO)

    @TypeConverter fun deGravedad(v: Gravedad): String = v.valor
    @TypeConverter fun aGravedad(v: String): Gravedad = desdeValor(v, Gravedad.BAJA)

    @TypeConverter fun deTipoAdjunto(v: TipoAdjunto): String = v.valor
    @TypeConverter fun aTipoAdjunto(v: String): TipoAdjunto = desdeValor(v, TipoAdjunto.FOTO)
}

@Database(
    entities = [
        ViajeEntity::class, RevisionEntity::class, EventoEntity::class, GastoEntity::class,
        NovedadEntity::class, AdjuntoEntity::class, CamionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun viajeDao(): ViajeDao
    abstract fun registroDao(): RegistroDao
    abstract fun camionDao(): CamionDao
    abstract fun syncDao(): SyncDao

    companion object {
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "bitacora.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

/** Borra todos los datos locales (al cerrar sesión). */
suspend fun AppDatabase.borrarTodo() = withContext(Dispatchers.IO) { clearAllTables() }
