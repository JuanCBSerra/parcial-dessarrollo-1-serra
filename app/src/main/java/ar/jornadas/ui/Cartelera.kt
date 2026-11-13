package ar.jornadas.ui

import ar.jornadas.data.Charla

/**
 * Qué muestra el catálogo según lo que hay guardado y cómo salió la última descarga (pedido 41).
 * La decisión vive en una función pura para poder testearla en la JVM, y la pantalla solo dibuja.
 */
sealed interface Cartelera {
    /** Nunca se descargó nada y todavía no hubo respuesta: la primera carga está en curso. */
    data object Esperando : Cartelera

    /** Nunca se descargó nada y la última petición falló: no hay qué mostrar, se ofrece reintento. */
    data object SinDescarga : Cartelera

    /**
     * Hay una edición guardada (aunque esté vacía, como v4).
     * [datosGuardados] es true si la última petición falló y se está mostrando lo que ya había.
     */
    data class Edicion(val charlas: List<Charla>, val datosGuardados: Boolean) : Cartelera

    companion object {
        fun desde(hayEdicion: Boolean, charlas: List<Charla>, ultimaFallo: Boolean): Cartelera = when {
            hayEdicion -> Edicion(charlas, datosGuardados = ultimaFallo)
            ultimaFallo -> SinDescarga
            else -> Esperando
        }
    }
}
