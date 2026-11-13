package ar.jornadas.ui

import ar.jornadas.data.Charla
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltrosTest {

    private fun charla(id: String, tema: String, dia: Int) =
        Charla(id, "Charla $id", tema, dia, "09:00", "Sala", "Ponente", "Resumen")

    // Mismo reparto que programa-1.json: 4 charlas por tema, 2 por día.
    private val programa = listOf("Android" to "1", "Datos" to "2", "Calidad" to "3").flatMap { (tema, prefijo) ->
        listOf(charla("${prefijo}1", tema, 1), charla("${prefijo}2", tema, 1),
            charla("${prefijo}3", tema, 2), charla("${prefijo}4", tema, 2))
    }

    @Test
    fun `sin filtros pasan todas`() {
        assertEquals(12, Filtros().aplicar(programa).size)
    }

    @Test
    fun `dia 2 y Datos deja las dos charlas del README`() {
        assertEquals(listOf("23", "24"), Filtros(dia = 2, tema = "Datos").aplicar(programa).map { it.id })
    }

    @Test
    fun `solo dia filtra por dia en todos los temas`() {
        assertEquals(6, Filtros(dia = 1).aplicar(programa).size)
    }

    @Test
    fun `solo tema filtra por tema en ambos dias`() {
        assertEquals(listOf("31", "32", "33", "34"), Filtros(tema = "Calidad").aplicar(programa).map { it.id })
    }

    @Test
    fun `un programa vacio sigue vacio con cualquier filtro`() {
        assertEquals(emptyList<Charla>(), Filtros(dia = 2, tema = "Datos").aplicar(emptyList()))
    }
}
