package ar.jornadas.data

import androidx.room.*
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

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

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
    }
}

@Database(entities = [Charla::class], version = 1, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}
