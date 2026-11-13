package ar.jornadas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdicionProgramaTest {

    private fun charla(id: String, titulo: String = "Charla $id", sala: String = "Auditorio", favorita: Boolean = false) =
        Charla(id, titulo, "Android", 1, "09:00", sala, "Ponente", "Resumen", favorita)

    @Test
    fun `v1 a v2 - la 11 conserva la marca y toma titulo y sala nuevos`() {
        val v1 = listOf(charla("11", "Compose en producción", favorita = true), charla("12"))
        val v2 = listOf(charla("11", "Compose en producción avanzada", "Sala Puerto"), charla("12"))

        val resultado = EdicionPrograma.fusionar(v1, v2).associateBy { it.id }

        assertTrue(resultado.getValue("11").favorita)
        assertEquals("Compose en producción avanzada", resultado.getValue("11").titulo)
        assertEquals("Sala Puerto", resultado.getValue("11").sala)
        assertFalse(resultado.getValue("12").favorita)
    }

    @Test
    fun `v2 a v3 - se retira la 11, entra la 90 sin marcar y las vigentes conservan su marca`() {
        val v2 = listOf(charla("11", favorita = true), charla("12", favorita = true), charla("13"))
        val v3 = listOf(charla("12"), charla("13"), charla("90"))

        val resultado = EdicionPrograma.fusionar(v2, v3).associateBy { it.id }

        assertEquals(setOf("12", "13", "90"), resultado.keys)
        assertTrue(resultado.getValue("12").favorita)
        assertFalse(resultado.getValue("13").favorita)
        assertFalse(resultado.getValue("90").favorita)
    }

    @Test
    fun `una charla retirada que regresa vuelve sin marcar`() {
        val v2 = listOf(charla("11", favorita = true))
        val v3 = EdicionPrograma.fusionar(v2, listOf(charla("12")))
        val vuelta = EdicionPrograma.fusionar(v3, listOf(charla("11"), charla("12")))

        assertFalse(vuelta.single { it.id == "11" }.favorita)
    }

    @Test
    fun `edicion vacia retira todo`() {
        val guardadas = listOf(charla("11", favorita = true), charla("12"))

        assertTrue(EdicionPrograma.fusionar(guardadas, emptyList()).isEmpty())
    }

    @Test
    fun `primera descarga sin datos previos entra todo sin marcar`() {
        val resultado = EdicionPrograma.fusionar(emptyList(), listOf(charla("11"), charla("12")))

        assertTrue(resultado.none { it.favorita })
    }

    @Test
    fun `la marca que viene del servidor se ignora, manda la del usuario`() {
        val resultado = EdicionPrograma.fusionar(listOf(charla("11")), listOf(charla("11", favorita = true)))

        assertFalse(resultado.single().favorita)
    }
}
