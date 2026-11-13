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
