# Sistema de Verificación de Identidad Biométrica Facial 1:1 en Dispositivo (Android Java / POO)

* **Author:** Samuel Santiago Ramos Pedraza (HEBA102514)
* **Date:** 2026-11-08

## Overview

Este documento define el diseño orientado a objetos y el plan de implementación para la primera versión entregable (en menos de 24 horas) de una aplicación Android en Java (`namespace co.edu.udistrital.reconocimientofacial`) para el curso de Programación Orientada a Objetos (segundo semestre) en la Universidad Distrital Francisco José de Caldas, a cargo del docente David Gustavo Rosero Bernal. La aplicación realiza verificación biométrica facial 1:1 en el propio teléfono, sin servidores externos ni servicios en la nube.

El diseño toma como base y ruta las dos fuentes de la propuesta:
1. **Ma et al. (2026, DOI: `10.1007/s42452-025-08004-y`):** Fundamenta la extracción de vectores de características (*embeddings*) en teléfonos móviles mediante una red neuronal convolucional ligera preentrenada (**MobileFaceNet**) exportada a **TensorFlow Lite (`.tflite`)**, ejecutando la inferencia en la CPU del dispositivo en milisegundos sin enviar imágenes por red.
2. **El Fadel (2025, DOI: `10.3390/jimaging11020058`):** Define el flujo biométrico en cuatro etapas secuenciales (captura de imagen, detección y recorte espacial de la región facial con **Google ML Kit Face Detection**, extracción de características y comparación por **similitud coseno** frente a un umbral $\tau$ calibrable), e incorpora el análisis de la Tasa de Falsa Aceptación (**FAR**), la Tasa de Falso Rechazo (**FRR**) y los fallos por condiciones no controladas (giro de cabeza u ojos cerrados).

Para cumplir los criterios de evaluación de POO, la aplicación separa las responsabilidades mediante el patrón **MVC (Modelo-Vista-Controlador)** e implementa **Interfaces**, **Clases Abstractas**, **Polimorfismo**, **Encapsulamiento** (centrado en la clase `Fotograma`), **Excepciones propias del dominio** y **Persistencia en archivos planos** para el historial de verificaciones.

```mermaid
flowchart LR
    subgraph Vista ["Vista (UI Android)"]
        MA["MainActivity (Implementa IVistaBiometrica)"]
    end

    subgraph Controlador ["Controlador"]
        CB["ControladorBiometrico"]
    end

    subgraph Modelo ["Modelo Biométrico (POO)"]
        F1["Fotograma Referencia"]
        F2["Fotograma Validación"]
        ID["«interface» IDetectorRostro"]
        IE["«interface» IExtractorCaracteristicas"]
        VF["«abstract» ValidadorFacial"]
        VE["ValidadorEstandar (Equilibrio FAR/FRR)"]
        VS["ValidadorEstricto (Umbral Alto + Pose/Ojos)"]
    end

    subgraph Persistencia ["Persistencia en Archivos"]
        IR["«interface» IRepositorioHistorial"]
        RA["RepositorioHistorialArchivo (historial_verificaciones.csv)"]
    end

    MA <-->|"Eventos / Actualización UI"| CB
    CB -->|"1. Detecta y recorta"| ID
    ID -->|"Construye"| F1 & F2
    CB -->|"2. Extrae float[]"| IE
    CB -->|"3. Polimorfismo"| VF
    VF <|-- VE
    VF <|-- VS
    CB -->|"4. Registra auditoría"| IR
    IR <|.. RA
```

## Analysis

### 1. Lente de Diseño Orientado a Objetos (`egm-design`)
* **Riesgo de herencia cosmética sin polimorfismo real:** Si `ValidadorFacial` solo tiene una subclase, no es posible demostrar polimorfismo en tiempo de ejecución durante la sustentación. Para resolverlo, `ControladorBiometrico` mantiene una referencia del tipo abstracto `ValidadorFacial`. La clase abstracta `ValidadorFacial` implementa el cálculo matemático común (`calcularSimilitudCoseno(float[] v1, float[] v2)`) mediante el patrón *Template Method*, y delega en dos subclases concretas (`ValidadorEstandar` y `ValidadorEstricto`) la definición del umbral $\tau$ y las reglas de pre-validación de calidad del `Fotograma`.
* **Encapsulamiento en `Fotograma`:** La clase `Fotograma` declara con visibilidad `private` la imagen original (`Bitmap`), el rostro recortado y escalado a $112 \times 112$ (`Bitmap`), la caja delimitadora (`Rect`), los indicadores de calidad entregados por ML Kit (ángulos de pose Euler $Y, Z$ y probabilidades de ojos abiertos), la marca de tiempo (`long timestamp`) y el vector de características (`float[] embedding`). Los métodos `getEmbedding()` y `setEmbedding(float[])` crean copias defensivas (`Arrays.copyOf`) para impedir modificaciones externas al arreglo interno.
* **Separación MVC:** El paquete `modelo` no importa clases de interfaz gráfica de Android (`Button`, `TextView`, `ImageView`) ni conoce a [`MainActivity`](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/java/co/edu/udistrital/reconocimientofacial/MainActivity.java). El controlador se comunica con la vista únicamente a través de la interfaz `IVistaBiometrica`.

### 2. Lente de Estado Actual del Código (`egm-codebase-details`)
La revisión del repositorio actual muestra los puntos exactos que deben modificarse:
* **Configuración Gradle ([app/build.gradle.kts](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/build.gradle.kts#L1-L43) y [gradle/libs.versions.toml](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/gradle/libs.versions.toml#L1-L23)):**
  * El proyecto usa `AGP 9.5.1`, `compileSdk 37`, `minSdk 24`, `targetSdk 37` y `JavaVersion.VERSION_11`.
  * Solo están declaradas las dependencias básicas de interfaz (`appcompat`, `material`, `activity-ktx`, `constraintlayout`).
  * Se debe agregar `com.google.mlkit:face-detection:16.1.7` (modelo incluido en el APK para funcionar sin internet) y `org.tensorflow:tensorflow-lite:2.16.1`, además de configurar `androidResources { noCompress += "tflite" }` en [app/build.gradle.kts](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/build.gradle.kts#L5-L33) para permitir mapear el archivo `app/src/main/assets/mobilefacenet.tflite` en memoria con `MappedByteBuffer`.
* **Manifiesto ([AndroidManifest.xml](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/AndroidManifest.xml#L1-L26)):**
  * No tiene permisos configurados. Se debe añadir `<uses-permission android:name="android.permission.CAMERA" />` y `<uses-feature android:name="android.hardware.camera" android:required="false" />`.
* **Plantilla inicial ([MainActivity.java](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/java/co/edu/udistrital/reconocimientofacial/MainActivity.java#L1-L24) y [activity_main.xml](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/res/layout/activity_main.xml#L1-L19)):**
  * Solo contienen la plantilla `"Hello World!"`, por lo que los paquetes MVC se crearán desde cero bajo `co.edu.udistrital.reconocimientofacial`.

### 3. Lente de Alineación con las Fuentes [1] y [2] (`egm-zoom-out`)
* **Correspondencia con Ma et al. (2026) [1]:** La clase `ExtractorMobileFaceNet` (que implementa `IExtractorCaracteristicas`) ejecuta la inferencia local en el teléfono mediante el intérprete de TensorFlow Lite sobre un recorte facial RGB de $112 \times 112$ píxeles normalizado en $[-1, 1]$, mide el tiempo de inferencia en milisegundos (`System.nanoTime()`) y normaliza el vector de salida en norma $L_2$ ($\|\mathbf{e}\|_2 = 1$).
* **Correspondencia con El Fadel (2025) [2]:** Las clases del paquete `modelo` representan cada etapa del artículo de revisión (captura, detección/recorte, extracción y comparación) y materializan el compromiso entre **FAR** y **FRR** en las dos subclases de `ValidadorFacial`:
  * `ValidadorEstandar` ($\tau = 0.70$): Prioriza reducir el Falso Rechazo (FRR) en fotos tomadas a mano alzada.
  * `ValidadorEstricto` ($\tau = 0.82$): Prioriza reducir la Falsa Aceptación (FAR) exigiendo mayor similitud coseno y verificando que los ángulos de la cabeza y la apertura de ojos estén dentro de rangos controlados.

### 4. Lente de Claridad en la Interfaz y Sustentación (`egm-comprehension`)
* **Flujo visual en una sola pantalla:** En [activity_main.xml](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/res/layout/activity_main.xml) se dispondrán:
  1. Dos paneles (**Imagen 1: Referencia** e **Imagen 2: Validación**) que muestran el rostro recortado por ML Kit y sus datos de detección.
  2. Un selector de estrategia de validación (`Estándar - Equilibrio FAR/FRR` vs. `Estricto - Control Pose/Ojos + Umbral Alto`) que cambia polimórficamente la instancia de `ValidadorFacial`.
  3. Un panel de resultados con: porcentaje de similitud coseno, umbral $\tau$ aplicado, tiempo de inferencia en milisegundos (métrica de Ma et al. [1]), veredicto (`COINCIDE` / `NO COINCIDE`) y acceso al historial leído desde el archivo local.

### 5. Lente de Viabilidad para Mañana (`egm-implementation-readiness`)
* **Riesgo de bloqueo por CameraX:** Programar vistas previas de video con `CameraX` y convertir imágenes `YUV_420_888` suele fallar por rotaciones de sensor y manejo de hilos. Para asegurar que la app funcione mañana, se usará `ActivityResultContracts.RequestPermission` para pedir `Manifest.permission.CAMERA` y `ActivityResultContracts.TakePicturePreview` para recibir el `Bitmap` directamente de la cámara del sistema sin configurar `FileProvider`. Además, se incluirá la opción de cargar imagen desde la galería (`ActivityResultContracts.GetContent`) para facilitar pruebas rápidas con pares de fotos existentes.
* **Riesgo por diferencias en el archivo `mobilefacenet.tflite`:** Según el repositorio de origen, `mobilefacenet.tflite` puede devolver un tensor de `[1, 128]` o `[1, 192]` flotantes. `ExtractorMobileFaceNet` leerá el tamaño del tensor de salida dinámicamente con `interpreter.getOutputTensor(0).shape()[1]` al iniciar, funcionando con cualquiera de las dos variantes sin cambiar código.

### 6. Lente Estructural de Paquetes (`egm-structural`)
Estructura de paquetes bajo `co.edu.udistrital.reconocimientofacial`:
* `modelo/`:
  * `Fotograma.java` (clase encapsulada con recorte facial, vector de características y metadatos de pose/ojos)
  * `ResultadoVerificacion.java` (objeto de valor con similitud, umbral, latencia en ms, veredicto y detalle)
  * `IDetectorRostro.java` (interfaz) y `DetectorRostroMLKit.java` (implementación con ML Kit)
  * `IExtractorCaracteristicas.java` (interfaz) y `ExtractorMobileFaceNet.java` (implementación con TFLite)
  * `ValidadorFacial.java` (clase abstracta), `ValidadorEstandar.java` y `ValidadorEstricto.java` (subclases polimórficas)
* `excepciones/`:
  * `BiometriaException.java` (excepción base comprobada / *checked*)
  * `RostroNoDetectadoException.java`
  * `MultiplesRostrosException.java`
  * `CalidadInsuficienteException.java`
  * `ModeloNoDisponibleException.java`
* `persistencia/`:
  * `IRepositorioHistorial.java` (interfaz)
  * `RepositorioHistorialArchivo.java` (lectura y escritura de `historial_verificaciones.csv` con `BufferedReader` / `BufferedWriter`)
* `controlador/`:
  * `ControladorBiometrico.java` (coordina el flujo entre vista, modelo y persistencia)
* `vista/`:
  * `IVistaBiometrica.java` (interfaz de vista)
  * `MainActivity.java` (implementación de la interfaz gráfica Android)

### 7. Lente Catalizador Inventivo (`egm-inventive-catalyst`)
* **Eliminación de la contradicción entre «entregar mañana sin video en vivo» y «control de pose/vida futuro»:**
  * *Contradicción:* Procesar parpadeo en video continuo toma varios días de desarrollo, pero ignorar por completo los ángulos de pose y apertura ocular deja sin uso las capacidades de ML Kit mencionadas en la propuesta y reduce la conexión con las condiciones no controladas de El Fadel (2025) [2].
  * *Resolución:* Configurar `FaceDetectorOptions` en `DetectorRostroMLKit` con `CLASSIFICATION_MODE_ALL` sobre las **fotos estáticas**. ML Kit calcula en una sola fotografía la probabilidad de ojos abiertos (`getLeftEyeOpenProbability()`, `getRightEyeOpenProbability()`) y los ángulos Euler de giro e inclinación de la cabeza (`getHeadEulerAngleY()`, `getHeadEulerAngleZ()`). Esos valores se guardan en `Fotograma` y son verificados por `ValidadorEstricto` (lanzando `CalidadInsuficienteException` si la persona sale con los ojos cerrados o con el rostro girado más de $18^\circ$), resolviendo el control de presencia/calidad en fotos estáticas sin escribir código de video.

## Goals vs. Non-Goals

* **In-Scope Goals:**
  * **G1 — Flujo biométrico 1:1 en el dispositivo:** Capturar dos fotos estáticas (referencia y validación) con permiso de cámara, detectar y recortar el rostro con ML Kit en objetos `Fotograma`, extraer el *embedding* con `MobileFaceNet` (`.tflite`) y comparar por similitud coseno.
  * **G2 — Cumplimiento completo de los requisitos de POO:** Implementar interfaces, clase abstracta (`ValidadorFacial`), polimorfismo (`ValidadorEstandar` y `ValidadorEstricto`), encapsulamiento (`Fotograma`), jerarquía de excepciones propias (`BiometriaException`), patrón MVC y persistencia en archivo plano (`RepositorioHistorialArchivo`).
  * **G3 — Reflejo directo de las fuentes [1] y [2]:** Medir tiempo de inferencia y normalizar vectores en norma $L_2$ como en Ma et al. (2026) [1], y estructurar las etapas del flujo biométrico junto con el control de umbral FAR/FRR y validación de pose/ojos como en El Fadel (2025) [2].
  * **G4 — Implementación completable en menos de 24 horas:** Evitar librerías o flujos asíncronos complejos que pongan en riesgo la entrega de mañana.
* **Out-of-Scope Non-Goals:**
  * **NG1 — Procesamiento de video continuo en tiempo real (`CameraX`):** Descartado para esta primera entrega por el plazo de 24 horas.
  * **NG2 — Servidor en la nube o base de datos 1:N:** Excluido según el alcance ajustado de la propuesta.
  * **NG3 — Entrenamiento de modelos neuronales desde cero:** Se utiliza un modelo `MobileFaceNet` preentrenado en formato `.tflite`.

## Alternatives Weighed

* **Flujo de video continuo con `CameraX` (`ImageAnalysis`)**: Rechazado por el autor y la revisión de viabilidad debido al plazo de entrega de menos de 24 horas; el manejo de conversión YUV a Bitmap y rotación por sensor introduce fallos difíciles de depurar en un día, mientras que `TakePicturePreview` con permiso nativo de cámara resuelve la captura estática directamente.
* **Detección facial con OpenCV (`Haar Cascades`)**: Rechazado en favor de Google ML Kit Face Detection porque la propuesta compromete el uso de ML Kit, no requiere integrar binarios nativos C++ (`jniLibs`) y entrega directamente el recorte facial junto con ángulos de cabeza y probabilidad de apertura de ojos.
* **Distancia Euclidiana sin normalización**: Rechazada en favor de la similitud coseno sobre vectores normalizados en norma $L_2$, ya que la similitud coseno acota el resultado en $[-1, 1]$ y se alinea con la propuesta y las fuentes [1] y [2].
* **Base de datos SQLite / Room para persistencia**: Rechazada en favor de persistencia en archivo de texto CSV (`BufferedWriter` / `BufferedReader` en `RepositorioHistorialArchivo`), porque el manejo directo de archivos (`java.io`) se ajusta mejor al temario de POO de segundo semestre y requiere menos código de configuración.

## Key Decisions & Staging for Next Steps

### Decisiones Clave para el Diseño e Implementación
1. **Método Plantilla y Polimorfismo en `ValidadorFacial`:**
   * `ValidadorFacial` define el método `public final ResultadoVerificacion verificar(Fotograma referencia, Fotograma validacion) throws BiometriaException`, el cual invoca el método abstracto `validarCalidadFotograma(Fotograma f)`, calcula la similitud coseno y la compara con el método abstracto `getUmbral()`.
   * `ValidadorEstandar` usa $\tau = 0.70$ sin restricción estricta de pose; `ValidadorEstricto` usa $\tau = 0.82$ y exige rostro frontal ($|\text{yaw}| \le 18^\circ$, $|\text{roll}| \le 18^\circ$) y ojos abiertos ($\text{prob} \ge 0.50$).
2. **Jerarquía de Excepciones del Dominio (`excepciones/`):**
   * `BiometriaException` (clase base `Exception`) con subclases `RostroNoDetectadoException`, `MultiplesRostrosException`, `CalidadInsuficienteException` y `ModeloNoDisponibleException`.
3. **Plan de Construcción en 4 Pasos (Listo para Ejecución):**
   * **Paso 1:** Configurar dependencias en [gradle/libs.versions.toml](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/gradle/libs.versions.toml) y [app/build.gradle.kts](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/build.gradle.kts), declarar el permiso de cámara en [AndroidManifest.xml](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/AndroidManifest.xml) y descargar/colocar `mobilefacenet.tflite` en `app/src/main/assets/`.
   * **Paso 2:** Crear los paquetes `excepciones` y `modelo` (`Fotograma`, `ResultadoVerificacion`, interfaces y clases de detección con ML Kit, extracción con TFLite y jerarquía `ValidadorFacial`).
   * **Paso 3:** Crear el paquete `persistencia` (`IRepositorioHistorial` y `RepositorioHistorialArchivo`).
   * **Paso 4:** Crear el paquete `controlador` (`ControladorBiometrico`) y el paquete `vista` (`IVistaBiometrica`, diseño en [activity_main.xml](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/res/layout/activity_main.xml) e integración en [MainActivity.java](file:///c:/Users/User/AndroidStudioProjects/Reconocimientofacial/app/src/main/java/co/edu/udistrital/reconocimientofacial/MainActivity.java)).
