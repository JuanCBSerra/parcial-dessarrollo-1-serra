package ar.jornadas

import android.app.Application

/**
 * Contenedor de dependencias de la app (pedido 44). Vive lo mismo que el proceso, así que hay una
 * sola base Room, un solo Retrofit, un solo repositorio y un solo servicio de prueba (con su contador)
 * por proceso, sin importar cuántas veces se recree la Activity o su ViewModel.
 */
class JornadasApp : Application() {
    val servicios by lazy { Servicios(this) }
}
