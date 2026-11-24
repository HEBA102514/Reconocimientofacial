# Goldfish Annotated Review Report

Status: resolved
**Target:** `docs/designs/verificacion_biometrica_facial.md` (`full file`)

## Prioritized Findings & AI Suggestions

### 1. Target Design Artifact Written to Disk [`egm-structural`] - Severity: Blocker
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** Written to `docs/designs/verificacion_biometrica_facial.md` and `design_draft.md`.

### 2. Asynchronous `IDetectorRostro` Contract [`egm-implementation-readiness`] - Severity: Blocker
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `IDetectorRostro` defines `CallbackDeteccion` (`onRostroDetectado` / `onErrorDeteccion`) so ML Kit runs asynchronously without blocking the UI thread.

### 3. `MainActivity` Package Consistency with `AndroidManifest.xml` [`egm-codebase-details`] - Severity: Blocker
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `MainActivity` remains in `co.edu.udistrital.reconocimientofacial` and implements `co.edu.udistrital.reconocimientofacial.vista.IVistaBiometrica`.

### 4. Clamped ML Kit Bounding Box (`Rect`) [`egm-implementation-readiness`] - Severity: Major
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `DetectorRostroMLKit` clamps `left, top, right, bottom` to `[0, width]` and `[0, height]` before calling `Bitmap.createBitmap`.

### 5. Bundled `mobilefacenet.tflite` Asset [`egm-implementation-readiness`] - Severity: Major
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `app/src/main/assets/mobilefacenet.tflite` is downloaded and configured with `noCompress += "tflite"`, and `ExtractorMobileFaceNet` dynamically inspects tensor dimensions.

### 6. Inference Latency and Null-Safe Eye Probabilities in `Fotograma` [`egm-design`] - Severity: Major
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `Fotograma` encapsulates `tiempoInferenciaMs` and maps `null` eye probabilities from ML Kit to `-1.0f`.

### 7. Locale-Safe CSV Formatting [`egm-comprehension`] - Severity: Major
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `ResultadoVerificacion.toLineaCsv()` formats numbers with `Locale.US` and uses `;` as delimiter.

### 8. Zero-Norm and Dimension Guards in `calcularSimilitudCoseno` [`egm-zoom-out`] - Severity: Nit
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `ValidadorFacial.calcularSimilitudCoseno` validates vector lengths and guards against zero norm (`denominador < 1e-10`).

### 9. Constructor Dependency Injection [`egm-style`] - Severity: Nit
*   **AI Suggestion:** PROPOSE FIX (Accepted & Applied)
*   **Justification:** `ControladorBiometrico` receives `IDetectorRostro`, `IExtractorCaracteristicas`, `ValidadorFacial`, `IRepositorioHistorial`, and `IVistaBiometrica` via constructor injection.
