package ar.jornadas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.jornadas.ui.*

class MainActivity : ComponentActivity() {
    private val model: ProgramaModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La Mesa se configura ANTES de tocar el ViewModel: al crearse, el ViewModel dispara la carga
        // automática, y esa primera petición tiene que salir ya con el servicio apagado si así se pidió.
        if (savedInstanceState == null && intent.hasExtra("servicio_disponible")) {
            (application as JornadasApp).servicios.mesa.disponible =
                intent.getBooleanExtra("servicio_disponible", true)
            intent.removeExtra("servicio_disponible")
        }
        setContent { Portada(model) }
    }
}
