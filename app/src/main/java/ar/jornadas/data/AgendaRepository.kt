package ar.jornadas.data

import java.util.concurrent.atomic.AtomicBoolean

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    val programa = dao.observar()
    val hayEdicion = dao.hayEdicion()

    // El repositorio vive lo mismo que el proceso (lo crea JornadasApp), así que esta marca también:
    // se pierde recién cuando Android mata el proceso, que es justo "una carga automática por sesión".
    private val cargaAutomaticaPedida = AtomicBoolean(false)

    /** true solo la primera vez que se llama en el proceso (pedido 44). */
    fun tomarCargaAutomatica(): Boolean = cargaAutomaticaPedida.compareAndSet(false, true)

    suspend fun sincronizar() {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.aplicarEdicion(fichas)
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
