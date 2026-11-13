package ar.jornadas.ui

import ar.jornadas.data.Charla
import org.junit.Assert.assertEquals
import org.junit.Test

class CarteleraTest {

    private val charlas = listOf(Charla("11", "Compose en producción", "Android", 1, "09:00", "Auditorio", "Paz Vega", "…"))

    @Test
    fun `con edicion previa, una falla muestra la lista e informa datos guardados`() {
        assertEquals(Cartelera.Edicion(charlas, datosGuardados = true),
            Cartelera.desde(hayEdicion = true, charlas = charlas, ultimaFallo = true))
    }

    @Test
    fun `con edicion previa y descarga exitosa, muestra la lista sin aviso`() {
        assertEquals(Cartelera.Edicion(charlas, datosGuardados = false),
            Cartelera.desde(hayEdicion = true, charlas = charlas, ultimaFallo = false))
    }

    @Test
    fun `sin descarga previa, una falla ofrece reintento`() {
        assertEquals(Cartelera.SinDescarga,
            Cartelera.desde(hayEdicion = false, charlas = emptyList(), ultimaFallo = true))
    }

    @Test
    fun `sin descarga previa y sin respuesta todavia, espera`() {
        assertEquals(Cartelera.Esperando,
            Cartelera.desde(hayEdicion = false, charlas = emptyList(), ultimaFallo = false))
    }

    @Test
    fun `v4 exitosa es una edicion valida vacia, no un error`() {
        assertEquals(Cartelera.Edicion(emptyList(), datosGuardados = false),
            Cartelera.desde(hayEdicion = true, charlas = emptyList(), ultimaFallo = false))
    }

    @Test
    fun `v4 guardada y reabierta sin senal sigue siendo una edicion valida`() {
        assertEquals(Cartelera.Edicion(emptyList(), datosGuardados = true),
            Cartelera.desde(hayEdicion = true, charlas = emptyList(), ultimaFallo = true))
    }
}
