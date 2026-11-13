package ar.jornadas.ui

import ar.jornadas.data.Charla

/**
 * Filtros del catálogo (pedido 43). Es estado de la pantalla, así que vive en el ViewModel y se guarda
 * en el SavedStateHandle; acá solo está la regla de qué charlas pasan, como función pura y testeable.
 *
 * @param dia 0 = ambos días, 1 o 2 = ese día.
 * @param tema [TODOS] o el nombre de un tema ("Android", "Datos", "Calidad").
 */
data class Filtros(val dia: Int = 0, val tema: String = TODOS) {
    fun aplicar(charlas: List<Charla>): List<Charla> =
        charlas.filter { (dia == 0 || it.dia == dia) && (tema == TODOS || it.tema == tema) }

    companion object {
        const val TODOS = "Todos"
    }
}
