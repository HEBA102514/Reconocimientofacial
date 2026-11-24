# Guía de Sustentación — Sistema de Verificación de Identidad Biométrica Facial 1:1

**Estudiante:** Samuel Santiago Ramos Pedraza (Código: 20261007053)  
**Docente:** David Gustavo Rosero Bernal  
**Asignatura:** Programación Orientada a Objetos (Segundo Semestre)  
**Universidad Distrital Francisco José de Caldas**

---

## PARTE 1: Cómo se entienden y cómo se aplicaron las dos fuentes al proyecto

### 1.1 Fuente [1]: Ma, H., Guo, D., Wang, J., et al. (2026)
* **Título:** *Real-time face recognition algorithm based on lightweight neural network in the field of computer vision* (*Discover Applied Sciences*, 8, 33).
* **¿De qué trata el artículo (en palabras sencillas)?**
  * **El problema que ataca:** Los modelos gigantes de reconocimiento facial (como los que corren en servidores con tarjetas gráficas) son demasiado pesados para un teléfono celular: gastan mucha batería, calientan el procesador y tardan demasiado en responder. Además, enviar fotos o video a un servidor en internet cuesta dinero, consume datos y pone en riesgo la privacidad.
  * **La solución que propone:** Diseñar y usar una **red neuronal ligera** pensada específicamente para correr dentro del celular (en el borde / *Edge Computing*). Para hacerla ligera sin perder precisión, usan convoluciones separables en profundidad, atención por canales y exportan el modelo a **TensorFlow Lite (`.tflite`)**.
  * **Qué demuestra y qué límites reconoce:** Demuestra que un celular puede transformar la foto de un rostro en un vector numérico de características (*embedding*) en pocos milisegundos, pero advierte que los modelos ligeros son sensibles a **cambios bruscos de iluminación y giros grandes de la cabeza (pose)**.
* **¿Cómo se aplicó exactamente la Fuente [1] en mi proyecto?**
  1. **Cambio de arquitectura Edge-Cloud a 100 % en el teléfono (*On-Device*):** Siguiendo a Ma et al. (2026), eliminamos la dependencia de un servidor externo. Toda la inferencia ocurre localmente usando el archivo `app/src/main/assets/mobilefacenet.tflite`.
  2. **Clase `ExtractorMobileFaceNet` (implementa `IExtractorCaracteristicas`):**
     * Toma el rostro ya recortado a $112 \times 112$ píxeles desde el objeto `Fotograma`.
     * Normaliza cada canal de color RGB del rango $[0, 255]$ al rango $[-1.0, 1.0]$ con la fórmula $(P - 127.5) / 128.0$.
     * Ejecuta el intérprete de **TensorFlow Lite (`Interpreter`)** en el teléfono y **mide el tiempo exacto de inferencia en milisegundos** (`tiempoInferenciaMs`), tal como hacen los autores de [1] para comprobar la baja latencia.
     * Normaliza el vector de salida en **norma $L_2$** (longitud unitaria $= 1.0$) y lo guarda encapsulado dentro del `Fotograma`.

---

### 1.2 Fuente [2]: El Fadel, N. (2025)
* **Título:** *Facial Recognition Algorithms: A Systematic Literature Review* (*Journal of Imaging*, 11(2), 58).
* **¿De qué trata el artículo (en palabras sencillas)?**
  * **El propósito del artículo:** Es una revisión sistemática que ordena cómo funciona cualquier sistema biométrico facial moderno y cuáles son sus puntos débiles en el mundo real.
  * **El flujo estándar de 4 etapas que define:**
    1. **Captura de la imagen** (adquisición).
    2. **Detección, alineación y recorte del rostro** (separar la cara del fondo y medir su orientación).
    3. **Extracción de características** (convertir el rostro recortado en un vector numérico o *embedding*).
    4. **Comparación y decisión** (medir la similitud matemática entre dos vectores y compararla contra un **umbral $\tau$**).
  * **Las métricas y advertencias clave:** Explica el compromiso entre dos errores biométricos opuestos al mover el umbral $\tau$:
    * **FAR (*False Acceptance Rate* / Tasa de Falsa Aceptación):** Aceptar por error a un impostor (ocurre si el umbral es muy bajo).
    * **FRR (*False Rejection Rate* / Tasa de Falso Rechazo):** Rechazar por error a la persona legítima (ocurre si el umbral es muy alto o si la foto tiene mala pose/ojos cerrados).
    * Advierte sobre el impacto de **condiciones no controladas** (cabeza girada, ojos cerrados, iluminación) y la importancia de proteger la **privacidad** de los datos biométricos.
* **¿Cómo se aplicó exactamente la Fuente [2] en mi proyecto?**
  1. **Arquitectura de clases calcada de las 4 etapas de El Fadel (2025):**
     * **Etapa 1 (Captura):** `MainActivity` pide permiso de cámara y captura las dos muestras (Referencia y Validación).
     * **Etapa 2 (Detección y Recorte):** `IDetectorRostro` y `DetectorRostroMLKit` localizan la cara, validan que haya exactamente un rostro, recortan la caja delimitadora y guardan los ángulos de la cabeza y la apertura de ojos en la clase `Fotograma`.
     * **Etapa 3 (Extracción):** `IExtractorCaracteristicas` y `ExtractorMobileFaceNet` obtienen el *embedding*.
     * **Etapa 4 (Comparación por Similitud Coseno y Umbral):** La clase abstracta `ValidadorFacial` calcula la similitud coseno $\cos(\theta) = \frac{\mathbf{A}\cdot\mathbf{B}}{\|\mathbf{A}\|\|\mathbf{B}\|}$ y la compara con el umbral.
  2. **Polimorfismo inspirado en el equilibrio FAR vs. FRR y las condiciones no controladas:**
     * Creamos dos subclases polimórficas de `ValidadorFacial`:
       * `ValidadorEstandar` ($\tau = 0.70$): Pensado para reducir el **FRR** (falso rechazo) en fotos cotidianas tomadas a mano alzada.
       * `ValidadorEstricto` ($\tau = 0.82$): Pensado para reducir el **FAR** (falsa aceptación) y controlar las **condiciones no controladas** que menciona El Fadel (2025): revisa los datos de ML Kit guardados en `Fotograma` y lanza `CalidadInsuficienteException` si la cabeza está girada más de $18^\circ$ o si la persona tiene los ojos cerrados ($< 50\%$).

---

## PARTE 2: Funcionamiento del Código por Capas (POO y Patrón MVC)

El proyecto está dividido en **5 paquetes** bajo `co.edu.udistrital.reconocimientofacial`, cumpliendo el patrón **MVC (Modelo-Vista-Controlador)** y todos los pilares de POO (encapsulamiento, interfaces, clases abstractas, polimorfismo, excepciones propias y persistencia en archivos).

### 2.1 Paquete `modelo` (La lógica biométrica y los datos)

1. **`Fotograma.java` (Encapsulamiento fuerte):**
   * Representa una fotografía ya procesada por el detector facial.
   * Todos sus atributos son `private`: el `Bitmap` original, el `Bitmap` del rostro recortado a $112 \times 112$, el `Rect` de la caja delimitadora, los ángulos Euler de la cabeza (`anguloYawY`, `anguloRollZ`), la probabilidad de apertura de ambos ojos, el tiempo de inferencia en milisegundos y el arreglo `float[] embedding`.
   * **Detalle clave de POO para mencionar al profesor:** En `getEmbedding()` y `setEmbedding(float[])` usamos **copias defensivas** (`Arrays.copyOf`). Así ninguna clase externa puede alterar las posiciones del vector numérico rompiendo el encapsulamiento.
2. **`ResultadoVerificacion.java` (Objeto de valor inmutable):**
   * Guarda el resultado final de comparar dos fotogramas: similitud coseno, porcentaje ($0\text{--}100\%$), umbral aplicado, nombre del validador usado, latencia total en ms, fecha/hora y el booleano `coincide`. Además tiene el método `toLineaCsv()` para exportar el registro al archivo plano.
3. **`IDetectorRostro.java` y `DetectorRostroMLKit.java` (Abstracción por Interfaz — Etapa 2):**
   * `IDetectorRostro` es una interfaz que define qué debe hacer cualquier detector facial sin importar la librería.
   * `DetectorRostroMLKit` implementa esa interfaz usando Google ML Kit Face Detection en modo preciso y con clasificación de ojos/pose activada.
   * Si detecta $0$ rostros lanza `RostroNoDetectadoException`; si detecta $>1$ rostro lanza `MultiplesRostrosException`; si detecta $1$ rostro, recorta la cara asegurando que las coordenadas no se salgan del borde de la imagen, la escala a $112 \times 112$ y devuelve el objeto `Fotograma`.
4. **`IExtractorCaracteristicas.java` y `ExtractorMobileFaceNet.java` (Abstracción por Interfaz — Etapa 3):**
   * `IExtractorCaracteristicas` define el contrato `extraerEmbedding(Fotograma)`.
   * `ExtractorMobileFaceNet` carga `mobilefacenet.tflite` desde `assets/`, convierte los píxeles $112 \times 112$ a un `ByteBuffer` de números flotantes normalizados en $[-1, 1]$, corre la red neuronal, normaliza el vector en norma $L_2$ y lo guarda en el `Fotograma`.
5. **`ValidadorFacial.java`, `ValidadorEstandar.java` y `ValidadorEstricto.java` (Clase Abstracta, Herencia y Polimorfismo — Etapa 4):**
   * `ValidadorFacial` es una **clase abstracta** que aplica el patrón *Template Method* en el método `public final ResultadoVerificacion verificar(Fotograma ref, Fotograma val)`:
     1. Verifica que ambos fotogramas tengan *embedding*.
     2. Llama al método abstracto `validarCalidadFotograma(...)` (que cada hijo implementa a su manera).
     3. Calcula la similitud coseno con el método compartido `calcularSimilitudCoseno(v1, v2)`.
     4. Compara contra `getUmbral()` (también abstracto) y devuelve el `ResultadoVerificacion`.
   * **Polimorfismo en acción:** `ValidadorEstandar` define umbral $0.70$ y validación básica; `ValidadorEstricto` define umbral $0.82$ y exige rostro frontal ($\le 18^\circ$) y ojos abiertos ($\ge 50\%$).

---

### 2.2 Paquete `vista` y `MainActivity` (La interfaz gráfica de usuario)

1. **`IVistaBiometrica.java` (Interfaz de la Vista):**
   * Define los métodos que el controlador puede invocar para actualizar la pantalla: `mostrarFotogramaReferencia`, `mostrarFotogramaValidacion`, `mostrarResultado`, `mostrarError`, `mostrarProgreso` y `mostrarHistorial`.
2. **`MainActivity.java` y `activity_main.xml`:**
   * `MainActivity` implementa `IVistaBiometrica`.
   * **No contiene fórmulas matemáticas ni llamadas directas a TensorFlow Lite o archivos:** solo gestiona los permisos de cámara (`RequestPermission`), abre la cámara nativa (`TakePicturePreview`) o la galería (`GetContent`), y le pasa el `Bitmap` al `ControladorBiometrico`.
   * Cuando el usuario cambia el `RadioGroup` entre *Estándar* y *Estricto*, `MainActivity` llama a `controlador.setValidadorActual(new ValidadorEstricto())` o `new ValidadorEstandar()`, cambiando el comportamiento polimórficamente en vivo.

---

### 2.3 Paquete `controlador` (`ControladorBiometrico.java`)

* Es el intermediario que conecta la Vista con el Modelo y la Persistencia sin que el Modelo conozca nada de Android UI.
* Tiene referencias a las **interfaces** (`IDetectorRostro`, `IExtractorCaracteristicas`, `IRepositorioHistorial`, `IVistaBiometrica`) y a la **clase abstracta** (`ValidadorFacial`).
* Su flujo en tres métodos principales:
  1. `procesarImagenReferencia(Bitmap)` y `procesarImagenValidacion(Bitmap)`: ordena al detector recortar el rostro, luego al extractor calcular el *embedding*, guarda el `Fotograma` y actualiza la vista.
  2. `ejecutarVerificacion()`: invoca polimórficamente `validadorActual.verificar(fotogramaReferencia, fotogramaValidacion)`, guarda el resultado en el archivo CSV mediante `repositorioHistorial.guardarResultado(resultado)` y lo muestra en pantalla.
  3. Captura todas las excepciones de tipo `BiometriaException` en bloques `try-catch` y las envía a `vista.mostrarError(...)` para que la aplicación nunca se cierre inesperadamente.

---

### 2.4 Paquete `excepciones` (Jerarquía de Excepciones Propias)

* Creamos una jerarquía de excepciones comprobadas (*checked exceptions*) que heredan de `Exception` para obligar al programador a manejarlas con `try-catch`:
  * **`BiometriaException` (Clase padre):** Guarda un atributo privado `codigoError` (por ejemplo, `"BIO-001"`).
  * **Subclases específicas (Herencia):**
    * `RostroNoDetectadoException` (`BIO-001`): cuando no hay ninguna cara en la foto.
    * `MultiplesRostrosException` (`BIO-002`): cuando aparecen 2 o más personas en la foto (guarda además cuántas caras detectó con `getCantidadDetectada()`).
    * `CalidadInsuficienteException` (`BIO-003`): cuando en modo estricto la persona sale con los ojos cerrados o la cara muy girada.
    * `ModeloNoDisponibleException` (`BIO-004`): si el archivo `mobilefacenet.tflite` no se encuentra o falla al leerse.

---

### 2.5 Paquete `persistencia` (Manejo de Archivos en Java)

* **`IRepositorioHistorial.java` (Interfaz):** Define las operaciones `guardarResultado`, `leerHistorial` y `limpiarHistorial`.
* **`RepositorioHistorialArchivo.java` (Implementación con `java.io`):**
  * Crea y administra el archivo plano `historial_verificaciones.csv` dentro del almacenamiento interno privado de la aplicación (`context.getFilesDir()`).
  * Usa **`BufferedWriter` y `FileWriter`** dentro de un bloque *try-with-resources* (que cierra el archivo automáticamente) para agregar cada verificación como una nueva línea separada por punto y coma (`fecha;modo;similitud;umbral;latencia_ms;veredicto`).
  * Usa **`BufferedReader` y `FileReader`** para leer todas las líneas cuando el usuario presiona el botón **Ver Historial (Archivo CSV)** y las formatea para mostrarlas en un cuadro de diálogo. Si ocurre un fallo de disco (`IOException`), lo envuelve y relanza como una `BiometriaException`.
