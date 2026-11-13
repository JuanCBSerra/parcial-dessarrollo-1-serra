package ar.jornadas.data

/**
 * Regla de negocio del pedido 42, como función pura (sin Room ni Android) para poder testearla en la JVM.
 *
 * La organización publica instantáneas COMPLETAS del programa: lo que llega es lo único vigente.
 * - Una charla que sigue vigente toma los datos nuevos (título, sala…) pero conserva la marca del usuario,
 *   porque el id es estable aunque cambie el título.
 * - Una charla que no viene en la instantánea se retira, y su marca se va con ella.
 * - Una charla nueva, o una que había sido retirada y regresa, entra sin marcar.
 */
object EdicionPrograma {
    fun fusionar(guardadas: List<Charla>, entrantes: List<Charla>): List<Charla> {
        val marcadas = guardadas.filter { it.favorita }.map { it.id }.toSet()
        return entrantes.map { it.copy(favorita = it.id in marcadas) }
    }
}
