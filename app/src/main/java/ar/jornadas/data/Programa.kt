package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/**
 * Marca de que ya se descargó con éxito al menos una edición del programa (pedido 41).
 * Tiene una sola fila (id = 0). Hace falta porque una edición válida puede no tener charlas (v4):
 * "tabla de charlas vacía" no alcanza para distinguir "nunca descargué" de "descargué un programa vacío".
 */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 0)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    abstract fun hayEdicion(): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun registrarEdicion(edicion: Edicion)

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    @Query("SELECT * FROM charlas")
    protected abstract suspend fun todas(): List<Charla>

    @Query("DELETE FROM charlas")
    protected abstract suspend fun vaciar()

    @Insert
    protected abstract suspend fun insertar(fichas: List<Charla>)

    /**
     * Aplica una instantánea completa del programa (pedido 42).
     * Leer las marcas, fusionar y reescribir ocurre en UNA transacción: una marca que el usuario
     * cambió mientras viajaba la petición ya está commiteada cuando la leemos, y un toque que
     * llega durante la transacción espera a que termine. Ninguno de los dos se pierde.
     */
    @Transaction
    open suspend fun aplicarEdicion(entrantes: List<Charla>) {
        val fusionadas = EdicionPrograma.fusionar(todas(), entrantes)
        vaciar()
        insertar(fusionadas)
        registrarEdicion(Edicion())   // en la misma transacción: o quedan las charlas Y la marca, o nada
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

/**
 * v1 → v2: agrega la tabla `edicion` sin tocar `charlas`, así los usuarios que actualizan conservan
 * su programa y sus favoritas. Quien ya tenía charlas guardadas con la 1.2 ya había descargado
 * una edición, así que se la registra para que siga viendo sus datos al abrir sin señal.
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `edicion` (`id`) SELECT 0 WHERE EXISTS (SELECT 1 FROM `charlas`)")
    }
}
