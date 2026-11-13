package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla

@Composable
fun Catalogo(model: ProgramaModel, abrir: (String) -> Unit) {
    LaunchedEffect(Unit) { model.recargar() }
    val filtros by model.filtros.collectAsStateWithLifecycle()
    val cartelera by model.cartelera.collectAsStateWithLifecycle()
    val cargando by model.cargando.collectAsStateWithLifecycle()
    Selector(listOf("Ambos días", "Día 1", "Día 2"), filtros.dia) { model.elegirDia(it) }
    val temas = listOf(Filtros.TODOS, "Android", "Datos", "Calidad")
    Selector(temas, temas.indexOf(filtros.tema)) { model.elegirTema(temas[it]) }
    TextButton(onClick = model::recargar) { Text("Actualizar") }
    if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
    when (val estado = cartelera) {
        Cartelera.Esperando -> Unit
        Cartelera.SinDescarga -> {
            Text("No se pudo descargar el programa. Revisá tu conexión.")
            OutlinedButton(onClick = model::recargar) { Text("Reintentar") }
        }
        is Cartelera.Edicion -> {
            if (estado.datosGuardados) Text("Sin conexión: estás viendo los datos guardados.")
            if (estado.charlas.isEmpty()) Text("Sin charlas programadas")
            else Grilla(filtros.aplicar(estado.charlas), abrir)
        }
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, abrir: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
