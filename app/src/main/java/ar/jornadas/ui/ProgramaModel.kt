package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

class ProgramaModel(app: Application) : AndroidViewModel(app) {
    private val servicios = Servicios(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dia = MutableStateFlow(0)

    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    // Resultado de la última petición TERMINADA. No se limpia al empezar otra: mientras se reintenta,
    // el aviso de datos guardados (o el botón de reintento) sigue visible hasta que llega la respuesta.
    private val ultimaFallo = MutableStateFlow(false)

    val cartelera: StateFlow<Cartelera> = combine(repo.hayEdicion, charlas, ultimaFallo) { hayEdicion, lista, fallo ->
            Cartelera.desde(hayEdicion, lista, fallo)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Cartelera.Esperando)

    fun recargar() {
        viewModelScope.launch {
            _cargando.value = true
            try {
                repo.sincronizar()
                ultimaFallo.value = false
            } catch (e: IOException) {
                ultimaFallo.value = true
            } finally {
                _cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
