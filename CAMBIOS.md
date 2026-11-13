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
