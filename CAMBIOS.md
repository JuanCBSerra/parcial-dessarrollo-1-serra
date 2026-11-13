# Cambios · Guardia de mantenimiento

Cada pedido sigue el formato **problema → qué código cambié → qué riesgo resuelvo**, con evidencia y una explicación para defenderlo oralmente.

---

## Arreglo de compilación · OkHttp 4

### Problema
El proyecto **no compilaba tal como vino en el zip**. Al compilar, fallaba en `lab/ServicioLocal.kt`, el servicio de prueba:

```
e: ServicioLocal.kt:7:29  Unresolved reference: Companion
e: ServicioLocal.kt:32:48 Unresolved reference: toResponseBody
e: ServicioLocal.kt:32:82 Unresolved reference: toMediaType
```

`ServicioLocal` arma la respuesta falsa con `json.toResponseBody("application/json".toMediaType())`. Esas dos son **funciones de extensión de Kotlin que existen recién desde OkHttp 4**. Pero el proyecto no declaraba OkHttp: lo recibía "de rebote" a través de Retrofit, y **Retrofit 2.11.0 trae OkHttp 3.14.9**, la última versión escrita en Java, que no tiene esas funciones.

```
com.squareup.retrofit2:retrofit:2.11.0
\--- com.squareup.okhttp3:okhttp:3.14.9   ← la que llegaba
```

### Qué cambié
En `app/build.gradle.kts` agregué la dependencia explícita:

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

Gradle resuelve el conflicto quedándose con la versión más alta (4.12.0). Retrofit 2.11 funciona con OkHttp 4, porque la API que usa es la misma.

No toqué `ServicioLocal.kt`: es la herramienta de prueba que da la consigna, y el arreglo correcto es darle la dependencia que necesita, no reescribirla.

### Qué riesgo resuelvo
- **El proyecto no se podía compilar ni instalar.** Sin esto, ninguno de los pedidos se podía probar.
- **Depender de una versión que llega "de rebote" es frágil:** si Retrofit cambia la versión que trae, el código se rompe sin que nadie lo toque. Declarar explícitamente lo que el código usa evita eso.

### Evidencia
Compilación y tests (JDK 17 o 21 con `JAVA_HOME`; Gradle 8.7 no corre sobre Java 25):

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Resultado: **BUILD SUCCESSFUL**. Este arreglo va primero en el historial para que cada commit de pedido compile por sí solo. Los tests de cada pedido viven en su propio commit: al final son 17 en verde (`EdicionProgramaTest` 6, `CarteleraTest` 6, `FiltrosTest` 5).

---

## Pedido 42 · Agenda personal

### Contexto
La app es la agenda de las Jornadas del Sur. Descarga el programa de charlas de internet y te deja **marcar** las que te interesan para armar "Mi agenda". La organización publica **instantáneas completas** del programa: cada vez que cambia algo, manda la lista entera de nuevo. El **id** de cada charla es estable aunque cambien su título o su sala.

Analogía: te dan el programa impreso y vos resaltás con marcador las charlas que querés ver.

### Problema
Cada vez que llegaba un programa nuevo, la app **tiraba tu hoja resaltada y te daba una limpia**:

1. **Se perdían todas las marcas** en cada actualización, aunque no hubiera cambiado nada.
2. **Las charlas canceladas no se borraban**: seguían en el catálogo y en Mi agenda.

En el código:

```kotlin
// AgendaRepository.sincronizar(): se arma cada charla sin decir `favorita` → vale false
val fichas = api.consultar().map { Charla(it.id, it.title, …) }
dao.guardar(fichas)

// CarteleraDao
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun guardar(fichas: List<Charla>)
```

- `REPLACE` en SQLite **borra la fila vieja y escribe una nueva**. La nueva viene con `favorita = false`, así que pisa la marca del usuario.
- `@Insert` solo agrega: nada borraba la charla 11 cuando dejaba de publicarse en v3.

### Qué código cambié

Ahora la app hace lo que haría una persona: **mira su hoja vieja, anota los números de charla que tenía resaltados, resalta esos mismos números en la hoja nueva y recién ahí tira la vieja.**

**1. `data/EdicionPrograma.kt` (nuevo): la regla, como función pura**

```kotlin
fun fusionar(guardadas: List<Charla>, entrantes: List<Charla>): List<Charla> {
    val marcadas = guardadas.filter { it.favorita }.map { it.id }.toSet()   // números resaltados
    return entrantes.map { it.copy(favorita = it.id in marcadas) }           // resaltarlos en la nueva
}
```

| Caso del README | Qué hace `fusionar` |
|---|---|
| v1 → v2: la 11 cambia de título y sala | Viene con los datos nuevos y su id está en `marcadas` → **conserva la marca** |
| v3: se retira la 11 | No está en `entrantes` → **no sale en el resultado** |
| v3: entra la 90 | Su id no está en `marcadas` → **entra sin marcar** |
| La 11 se retira y después regresa | Al retirarse se fue su marca → **regresa sin marcar** |

**2. `data/Programa.kt`: la transacción en el DAO**

`CarteleraDao` pasa de `interface` a `abstract class` para poder tener una función con cuerpo. Se elimina `guardar` (el `REPLACE`).

```kotlin
@Transaction
open suspend fun aplicarEdicion(entrantes: List<Charla>) {
    val fusionadas = EdicionPrograma.fusionar(todas(), entrantes)  // mirar la hoja vieja y pasar las marcas
    vaciar()                                                       // tirar la hoja vieja
    insertar(fusionadas)                                           // guardar la nueva ya resaltada
}
```

`todas()`, `vaciar()` e `insertar()` son `protected`: nadie de afuera puede vaciar la tabla suelta.

**3. `data/AgendaRepository.kt`:** `dao.guardar(fichas)` → `dao.aplicarEdicion(fichas)`.

**4. `app/build.gradle.kts`:** `testImplementation("junit:junit:4.13.2")` para el test JVM.

### Marcar mientras se descarga
Una petición tarda entre 1,4 y 8 segundos. Las marcas se leen con `todas()` **dentro de la transacción, cuando la respuesta ya llegó**, y no antes de pedir:

```
t=0  tocás Actualizar → arranca la petición
t=3  marcás la charla 12 → se guarda en la base
t=8  llega la respuesta → aplicarEdicion: todas() ya ve la 12 marcada → se conserva
```

Si el toque llega justo durante la transacción, SQLite lo hace esperar a que termine y después se aplica sobre la fila nueva. Como las cuatro operaciones son atómicas, si algo falla a mitad de camino no queda una tabla vacía.

### Qué riesgo resuelvo
- **Pérdida de datos del usuario** en cada actualización del programa.
- **Charlas retiradas** que seguían en el catálogo y en Mi agenda.
- **Carrera** entre una marca hecha durante la petición y la respuesta.

### Por qué no hace falta migración
El README pide conservar los datos del usuario al actualizar. La tabla `charlas` tiene **las mismas columnas y tipos** que antes: solo cambió el DAO. El esquema no cambió, así que la base sigue en versión 1 y las favoritas de los usuarios que ya tienen la app instalada se conservan.

### Alternativas descartadas

**¿Cambiar `REPLACE` por `UPDATE`?** No alcanza. `UPDATE` solo modifica filas que ya existen:

| Qué pasa con la charla | Qué hace falta | ¿`UPDATE`? |
|---|---|---|
| Ya existía y cambió | modificarla | ✅ |
| Es nueva (la 90) | agregarla | ❌ modifica 0 filas, nunca aparece |
| La cancelaron (la 11 en v3) | borrarla | ❌ sigue apareciendo |

En la primera apertura la tabla está vacía, así que con solo `UPDATE` la app quedaría vacía para siempre.

**¿Un UPSERT?** Resuelve "nueva" y "cambió", pero **nunca borra**, así que igual haría falta un `DELETE`. Además:
- El UPSERT de SQLite (`INSERT … ON CONFLICT DO UPDATE`) existe desde SQLite 3.24, que viene con **Android 11 (API 30)**. El proyecto tiene `minSdk = 26` (SQLite 3.18), así que fallaría en Android 8, 9 y 10.
- El `@Upsert` de Room funciona en cualquier versión, pero **actualiza la fila entera, `favorita` incluida**, y vuelve el bug. Un `@Upsert` parcial sin `favorita` no puede insertar charlas nuevas, porque la columna no tiene valor por defecto en SQL (el `= false` es de Kotlin). Habría que migrar el esquema solo para eso.

**¿Hacerlo en SQL con `DELETE` + `INSERT OR IGNORE` + `UPDATE` parcial?** Es válido y más eficiente con tablas grandes, pero:
- la regla queda repartida en tres consultas;
- solo se puede testear contra una base real (test instrumentado o Robolectric).

Elegí la función pura porque **la regla queda en un solo lugar y se prueba con un test JVM**, que es la evidencia que pide la consigna. Lo que se resigna: reescribe la tabla entera en cada sincronización (con 12 charlas es irrelevante) y, si mañana se agrega otra columna que edita el usuario, `fusionar` también tendría que copiarla.

### ¿Respeta MVVM?
Sí. El cambio está **solo en la capa de datos**: las pantallas y el ViewModel siguen pidiendo "dame las charlas" y "marcá esta" sin enterarse de nada. El proyecto es MVVM plano, sin capa `domain`, y en un parcial no se agregan capas nuevas. Por eso la regla vive en `data`, como función pura, al lado de `Charla` y del DAO que la usa. Lo discutible es que el DAO llame a una regla de negocio. Está ahí porque la lectura de marcas tiene que estar en la misma transacción que la escritura.

### Evidencia
- **Test JVM:** `app/src/test/java/ar/jornadas/data/EdicionProgramaTest.kt` cubre v1→v2, v2→v3, retirada y regreso, edición vacía (v4), primera descarga y una marca que viene del servidor (se ignora: manda la del usuario).
  ```bash
  ./gradlew test
  ```
- **Manual, ediciones:** en Mesa elegí v1 → Actualizar → marcá la 11 «Compose en producción». Pasá a v2 → Actualizar: la 11 dice «Compose en producción avanzada» · «Sala Puerto» y sigue «En mi agenda». Pasá a v3 → Actualizar: la 11 desaparece del Programa y de Mi agenda, y aparece la 90 sin marcar. Volvé a v2 → Actualizar: la 11 vuelve sin marcar.
- **Manual, carrera:** activá la demora larga, tocá Actualizar, entrá a una ficha vigente y marcala antes de los 8 s. Al llegar la respuesta sigue marcada.

### Para defenderlo oralmente
> "`REPLACE` borraba y reescribía cada fila con la marca en falso, así que cada actualización vaciaba la agenda, y como solo insertaba, las charlas retiradas nunca se iban. Lo reemplacé por una función pura que toma la instantánea nueva como lo único vigente y le copia las marcas por id. La ejecuto dentro de una transacción de Room, leyendo las marcas recién cuando llega la respuesta, así no se pierde ninguna marca hecha durante la petición. No usé `UPDATE` porque no inserta ni borra, ni UPSERT porque no existe en API 26 y el de Room pisa la marca. El esquema no cambió, así que no hace falta migración."

---

## Pedido 41 · Programa sin señal

### Contexto
La app descarga el programa de internet y lo guarda en el teléfono (base de datos Room). Si después no hay señal, debería poder seguir mostrando lo último que descargó. Hay tres situaciones que la app tiene que distinguir:

| Situación | Qué pide el README |
|---|---|
| Ya descargué alguna vez y ahora falla | Seguir mostrando la lista, el detalle y las favoritas, **y avisar** que son datos guardados |
| Nunca descargué y falla | No hay nada que mostrar → **ofrecer reintento** |
| Descargué v4, que viene vacía | Mostrar «sin charlas programadas». **Es una edición válida**: si reabro sin señal, sigue siendo eso y no un error |

### Problema
**1. Un error tapaba la lista.** El catálogo hacía esto:

```kotlin
if (aviso.isNotEmpty()) {
    Text(aviso); OutlinedButton(onClick = model::recargar) { Text("Reintentar") }
} else {
    Grilla(fichas…)
}
```

Si fallaba la red, se mostraba **el error en lugar de la lista**, aunque las charlas estuvieran guardadas en el teléfono. El usuario veía "No se pudo consultar el programa" y no podía ver nada, cuando los datos estaban ahí.

**2. No había forma de saber si "ya descargué alguna vez".** La app solo tenía la tabla de charlas. Una tabla vacía puede significar dos cosas muy distintas:
- **nunca descargué** → corresponde ofrecer reintento;
- **descargué v4, que vino vacía** → corresponde mostrar «sin charlas programadas».

Mirando solo las charlas, no hay forma de distinguirlas.

### Qué código cambié

**1. `data/Programa.kt`: una tabla nueva que registra "ya descargué una edición"**

```kotlin
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 0)
```

Tiene una sola fila. Si existe, hubo al menos una descarga exitosa, aunque haya traído cero charlas. El DAO suma:
- `hayEdicion(): Flow<Boolean>`, que observa si esa fila existe;
- `registrarEdicion()`, que se llama **adentro de `aplicarEdicion`**, en la misma transacción del pedido 42. Así no puede pasar que se guarden las charlas y no la marca, ni al revés.

**2. Migración v1 → v2 (`MIGRACION_1_2`)**

Agregar una tabla cambia el esquema de la base, y la app ya tiene usuarios instalados con charlas y favoritas. Por eso hice una migración incremental, sin borrar nada:

```kotlin
db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, PRIMARY KEY(`id`))")
db.execSQL("INSERT INTO `edicion` (`id`) SELECT 0 WHERE EXISTS (SELECT 1 FROM `charlas`)")
```

- La primera línea crea la tabla nueva. La tabla `charlas` **no se toca**, así que se conservan el programa y las favoritas.
- La segunda resuelve un caso fino: un usuario de la versión 1.2 que ya tenía charlas guardadas **ya había descargado una edición**, solo que la app vieja no lo anotaba. Si no se la registrara, al actualizar y abrir sin señal vería "nunca descargaste", con sus charlas guardadas ahí mismo.

En `Servicios.kt` se registra con `.addMigrations(MIGRACION_1_2)`. No se usa `fallbackToDestructiveMigration()`, que borraría todo.

**3. `ui/Cartelera.kt` (nuevo): la regla de qué mostrar, como función pura**

```kotlin
fun desde(hayEdicion: Boolean, charlas: List<Charla>, ultimaFallo: Boolean): Cartelera = when {
    hayEdicion  -> Edicion(charlas, datosGuardados = ultimaFallo)  // hay algo guardado: se muestra
    ultimaFallo -> SinDescarga                                     // nada guardado y falló: reintento
    else        -> Esperando                                       // nada guardado, esperando la primera respuesta
}
```

`Cartelera` es un `sealed interface` con tres estados. La pantalla hace un `when` sobre ellos y **no puede olvidarse de ninguno**: si faltara uno, no compila.

**4. `ui/ProgramaModel.kt`: el ViewModel arma la cartelera**

```kotlin
val cartelera = combine(repo.hayEdicion, charlas, ultimaFallo) { … Cartelera.desde(…) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, Cartelera.Esperando)
```

- `combine` junta tres fuentes y recalcula la cartelera cuando cualquiera cambia: si hay edición (Room), las charlas (Room) y si la última petición falló.
- Se eliminó `aviso` (un texto suelto) y se reemplazó por `ultimaFallo`, un dato privado. Las pantallas no deciden con un texto: deciden con un estado.
- `ultimaFallo` **solo cambia cuando termina una petición**, no al empezar. Así, mientras se reintenta, el aviso de datos guardados no parpadea: sigue visible hasta que llega la respuesta.
- `cargando` pasó a ser privado (`_cargando`) con un `StateFlow` público de solo lectura. Ya que lo tocaba, quedó con la *backing property* que pide la cátedra.

**5. `ui/Catalogo.kt`: la pantalla solo dibuja**

```kotlin
when (val estado = cartelera) {
    Cartelera.Esperando   -> Unit
    Cartelera.SinDescarga -> { Text("No se pudo descargar…"); OutlinedButton(…) { Text("Reintentar") } }
    is Cartelera.Edicion  -> {
        if (estado.datosGuardados) Text("Sin conexión: estás viendo los datos guardados.")
        if (estado.charlas.isEmpty()) Text("Sin charlas programadas") else Grilla(…)
    }
}
```

La diferencia clave con el código anterior: **el aviso de datos guardados va arriba de la lista, no en su lugar.**

El detalle de una charla y Mi agenda no necesitaron cambios: leen de Room, que sigue teniendo los datos aunque falle la red. Lo único que los "tapaba" era el catálogo.

### Qué riesgo resuelvo
- **El usuario sin señal se quedaba sin programa**, aunque lo tuviera guardado.
- **Un programa vacío se confundía con "nunca descargué"**, o al revés.
- **Actualizar la app no borra datos:** la migración conserva charlas y favoritas, y reconoce la descarga previa de quien venía de la 1.2.

### ¿Respeta MVVM?
- **Datos:** Room guarda si hubo edición, dentro de la misma transacción que las charlas.
- **ViewModel:** combina las fuentes y expone **un único estado** (`cartelera`), de solo lectura.
- **Vista:** hace un `when` exhaustivo y dibuja. No decide nada.
- **Regla pura:** `Cartelera.desde` no depende de Android y se testea en la JVM.

Respeta la regla de la cátedra *"distinguir lista vacía de error: nunca mostrar un error como lista vacía"*, y también al revés: un error no tapa una lista que existe.

### Evidencia
- **Test JVM:** `app/src/test/java/ar/jornadas/ui/CarteleraTest.kt` cubre los seis casos: edición + falla, edición + éxito, sin descarga + falla, sin descarga + esperando, v4 exitosa y v4 reabierta sin señal.
- **Manual, con edición previa:** Mesa → v1 → Actualizar → marcá una charla. Apagá «Servicio disponible» → Actualizar. Se ve la lista con «Sin conexión: estás viendo los datos guardados.», la ficha abre y la marca sigue en Mi agenda.
- **Manual, sin descarga previa:**
  ```bash
  adb shell pm clear ar.jornadas
  adb shell am start -n ar.jornadas/.MainActivity --ez servicio_disponible false
  ```
  Se ve «No se pudo descargar el programa» con el botón Reintentar. Prendé el servicio en Mesa, volvé a Programa → Reintentar y aparece la lista.
- **Manual, v4:** Mesa → v4 → Actualizar → «Sin charlas programadas». Apagá el servicio, cerrá la app desde Recientes y reabrila: sigue «Sin charlas programadas» con el aviso de datos guardados, y **no** el botón de reintento.
- **Manual, migración:** instalá el APK del commit inicial (base v1), descargá y marcá charlas. Instalá encima esta versión sin desinstalar, apagá el servicio y abrí: siguen las charlas y las marcas, con el aviso de datos guardados.

### Para defenderlo oralmente
> "El error reemplazaba a la lista, así que sin señal no veías nada aunque el programa estuviera guardado. Además, con solo la tabla de charlas no se distingue 'nunca descargué' de 'descargué un programa vacío'. Agregué una tabla `edicion` que se escribe en la misma transacción que las charlas, con una migración incremental que no borra nada y reconoce a quien ya tenía datos. La decisión de qué mostrar es una función pura con tres estados; el ViewModel la expone como un único `StateFlow` y la pantalla solo hace un `when`. Ahora el aviso va arriba de la lista, no en su lugar."

---

## Pedido 43 · Volver al punto de lectura

### Contexto
En el catálogo hay dos filtros: **día** (Ambos días, Día 1, Día 2) y **tema** (Todos, Android, Datos, Calidad). El README pide que, si elegís "Día 2 + Datos", abrís una charla y volvés, **los filtros sigan puestos** en estas situaciones:

| Situación | Qué tiene que pasar |
|---|---|
| Abrir una ficha y volver | Siguen Día 2 + Datos → 2 resultados |
| Rotar el teléfono | Siguen los filtros |
| App al fondo y Android **mata el proceso** para liberar memoria; volvés desde Recientes | Vuelve a la ficha abierta y, al volver, **los dos filtros** siguen puestos |
| Tarea nueva (abrir la app de cero) | Arranca **sin filtros** |

Analogía: estás leyendo el programa con dos **señaladores** (uno para el día y otro para el tema). Si te levantás de la mesa (rotar, abrir una ficha) o alguien guarda tu carpeta en un cajón para hacer lugar (Android mata el proceso), al volver los señaladores tienen que estar donde los dejaste. Si empezás una lectura nueva, arrancás sin señaladores.

### Problema
Cada filtro se guardaba en un lugar distinto, y ninguno era el correcto:

```kotlin
// ProgramaModel.kt
val dia = MutableStateFlow(0)                         // en el ViewModel, en memoria

// Catalogo.kt
var tema by remember { mutableStateOf("Todos") }      // en la pantalla, en memoria
```

**Dónde vive cada cosa:**

| Lugar | Sobrevive a abrir una ficha | Sobrevive a rotar | Sobrevive a que Android mate el proceso |
|---|---|---|---|
| `remember` (en el composable) | ❌ | ❌ | ❌ |
| ViewModel en memoria | ✅ | ✅ | ❌ |
| `rememberSaveable` / `SavedStateHandle` | ✅ | ✅ | ✅ |

- **El tema (`remember`)** se perdía apenas abrías una ficha. `Portada` reemplaza el catálogo por la ficha, así que el composable `Catalogo` **sale de pantalla** y todo lo que guardaba con `remember` se borra. Al volver arranca de cero con "Todos". Lo mismo al rotar, porque la Activity se recrea.
- **El día (ViewModel)** sobrevivía a la ficha y a la rotación, porque el ViewModel sigue vivo. Pero si Android **mata el proceso**, el ViewModel muere con él y vuelve a "Ambos días".
- **La ficha abierta** ya estaba bien: `Portada` la guarda con `rememberSaveable`. Por eso tras matar el proceso volvías a la ficha, pero al tocar "Volver" los filtros ya no estaban.

### Qué código cambié

**1. `ui/Filtros.kt` (nuevo): los dos filtros juntos, con su regla**

```kotlin
data class Filtros(val dia: Int = 0, val tema: String = TODOS) {
    fun aplicar(charlas: List<Charla>): List<Charla> =
        charlas.filter { (dia == 0 || it.dia == dia) && (tema == TODOS || it.tema == tema) }
}
```

- Los dos filtros viajan juntos en un solo objeto.
- El `filter` que antes estaba escrito en el composable pasa a una función pura testeable. La pantalla ya no decide qué charlas pasan.
- `"Todos"` es una constante (`Filtros.TODOS`) y no un texto repetido en varios lugares.

**2. `ui/ProgramaModel.kt`: los filtros en el `SavedStateHandle`**

```kotlin
class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {

    val filtros: StateFlow<Filtros> = combine(
        estado.getStateFlow(CLAVE_DIA, 0),
        estado.getStateFlow(CLAVE_TEMA, Filtros.TODOS),
    ) { dia, tema -> Filtros(dia, tema) }
        .stateIn(…)

    fun elegirDia(dia: Int) { estado[CLAVE_DIA] = dia }
    fun elegirTema(tema: String) { estado[CLAVE_TEMA] = tema }
```

- **`SavedStateHandle`** es un "bolsillo" del ViewModel que Android guarda junto con la tarea. Cuando mata el proceso, guarda el bolsillo. Cuando volvés desde Recientes, crea un ViewModel nuevo y **le devuelve el bolsillo** con los valores que tenía.
- Una **tarea nueva** no tiene bolsillo guardado, así que los filtros arrancan en sus valores por defecto. Eso cumple "una tarea nueva arranca sin filtros" sin código extra.
- `getStateFlow` convierte cada valor del bolsillo en un flujo observable: cuando `elegirDia` escribe, el flujo emite y la pantalla se redibuja.
- No hizo falta tocar `MainActivity`. La fábrica por defecto de `viewModels()` ya sabe construir un `AndroidViewModel` que pide `(Application, SavedStateHandle)`.
- Se eliminó `val dia = MutableStateFlow(0)`, que era público y modificable. Ahora la pantalla **no escribe el estado**: llama a `elegirDia` o `elegirTema`, y el único que modifica es el ViewModel.

**3. `ui/Catalogo.kt`: la pantalla lee y avisa, no guarda**

```kotlin
// ANTES
val dia by model.dia.collectAsStateWithLifecycle()
var tema by remember { mutableStateOf("Todos") }
Selector(…, dia) { model.dia.value = it }                  // la pantalla escribía en el ViewModel
Selector(temas, temas.indexOf(tema)) { tema = temas[it] }  // y guardaba el tema ella misma
Grilla(fichas.filter { (dia == 0 || it.dia == dia) && (tema == "Todos" || it.tema == tema) }, abrir)

// DESPUÉS
val filtros by model.filtros.collectAsStateWithLifecycle()
Selector(…, filtros.dia) { model.elegirDia(it) }
Selector(temas, temas.indexOf(filtros.tema)) { model.elegirTema(temas[it]) }
Grilla(filtros.aplicar(estado.charlas), abrir)
```

### Por qué `SavedStateHandle` y no `rememberSaveable`
Las dos sobreviven a la muerte del proceso. `rememberSaveable` es lo que ya usa `Portada` para la sección y la ficha. Para los filtros elegí el ViewModel por dos razones:
- **Son estado del catálogo que usa el ViewModel**, no un detalle visual de la pantalla. La cátedra dice que el estado de pantalla (como un filtro activo) vive en el ViewModel y la vista solo lo dibuja.
- **Deja la puerta abierta al pedido 44**, donde el ViewModel va a necesitar los filtros al lado del estado de descarga.

### Qué riesgo resuelvo
- **Perder el punto de lectura** al abrir una ficha, rotar o volver tras la muerte del proceso.
- **La pantalla modificaba el estado del ViewModel directamente** (`model.dia.value = it`). Ahora es de solo lectura y se cambia con funciones.
- **La regla de filtrado estaba en el composable**, sin test. Ahora es pura y testeada.

### ¿Respeta MVVM?
- **Vista:** muestra los filtros y avisa cuando tocás un chip (`elegirDia`, `elegirTema`). No guarda nada.
- **ViewModel:** dueño de los filtros. Los persiste en el `SavedStateHandle` y los expone como `StateFlow` de solo lectura.
- **Regla pura:** `Filtros.aplicar`, sin Android, testeada en la JVM.

### Evidencia
- **Test JVM:** `app/src/test/java/ar/jornadas/ui/FiltrosTest.kt` arma un programa igual a `programa-1.json` y prueba sin filtros (12), **Día 2 + Datos (23 y 24, los dos del README)**, solo día, solo tema y programa vacío.
- **Manual, ficha y rotación:** Programa → Día 2 → Datos → abrí «Cachés para viajeros» → Volver: quedan 2 resultados (Cachés para viajeros y Consultas observables). Rotá: siguen los 2.
- **Manual, muerte del proceso:** Día 2 → Datos → abrí «Cachés para viajeros» y, con la ficha abierta:
  ```bash
  adb shell input keyevent KEYCODE_HOME
  adb shell am kill ar.jornadas
  ```
  Volvé desde Recientes: aparece la ficha de «Cachés para viajeros». Tocá Volver: Día 2 y Datos siguen seleccionados, con 2 resultados.
- **Manual, tarea nueva:** cerrá la app deslizándola en Recientes y abrila desde el ícono: arranca en Ambos días y Todos.

### Para defenderlo oralmente
> "El tema vivía en un `remember` del catálogo, que se borra cuando el catálogo sale de pantalla al abrir una ficha o al rotar. El día vivía en el ViewModel, que sobrevive a eso pero no a que Android mate el proceso. Pasé los dos filtros al `SavedStateHandle` del ViewModel, que Android guarda con la tarea y devuelve al recrearla; una tarea nueva arranca sin estado guardado y, por lo tanto, sin filtros. La pantalla ya no guarda ni escribe el estado: lo lee y llama a `elegirDia` o `elegirTema`. El filtrado pasó a una función pura con test."
