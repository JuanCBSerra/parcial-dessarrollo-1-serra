package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.JornadasApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = (app as JornadasApp).servicios
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Los filtros se guardan en el SavedStateHandle: sobreviven a la rotación (el ViewModel sigue vivo)
    // y también a que Android mate el proceso (se restauran del estado guardado de la tarea).
    // Una tarea nueva no trae estado guardado, así que arranca con los valores por defecto.
    val filtros: StateFlow<Filtros> = combine(
        estado.getStateFlow(CLAVE_DIA, 0),
        estado.getStateFlow(CLAVE_TEMA, Filtros.TODOS),
    ) { dia, tema -> Filtros(dia, tema) }
        .stateIn(viewModelScope, SharingStarted.Eagerly,
            Filtros(estado.get<Int>(CLAVE_DIA) ?: 0, estado.get<String>(CLAVE_TEMA) ?: Filtros.TODOS))

    fun elegirDia(dia: Int) { estado[CLAVE_DIA] = dia }
    fun elegirTema(tema: String) { estado[CLAVE_TEMA] = tema }

    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    // Resultado de la última petición TERMINADA. No se limpia al empezar otra: mientras se reintenta,
    // el aviso de datos guardados (o el botón de reintento) sigue visible hasta que llega la respuesta.
    private val ultimaFallo = MutableStateFlow(false)

    val cartelera: StateFlow<Cartelera> = combine(repo.hayEdicion, charlas, ultimaFallo) { hayEdicion, lista, fallo ->
            Cartelera.desde(hayEdicion, lista, fallo)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Cartelera.Esperando)

    // La petición en curso, si hay una. Mientras siga activa, recargar() no lanza otra (pedido 44).
    private var carga: Job? = null

    init {
        // Carga automática: una por proceso. Rotar recrea la Activity pero no este ViewModel, así que
        // no vuelve a pasar por acá; y si la Activity se recrea con un ViewModel nuevo en el mismo
        // proceso, el repositorio ya dio su única carga automática y no da otra.
        if (repo.tomarCargaAutomatica()) recargar()
    }

    /** Actualización explícita. Si ya hay una petición en curso, se ignora: dos toques = un intento. */
    fun recargar() {
        if (carga?.isActive == true) return
        carga = viewModelScope.launch {
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

    private companion object {
        const val CLAVE_DIA = "filtro_dia"
        const val CLAVE_TEMA = "filtro_tema"
    }
}
