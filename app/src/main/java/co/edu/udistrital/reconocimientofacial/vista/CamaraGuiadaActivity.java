package co.edu.udistrital.reconocimientofacial.vista;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import co.edu.udistrital.reconocimientofacial.R;
import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.excepciones.ModeloNoDisponibleException;
import co.edu.udistrital.reconocimientofacial.modelo.ExtractorMobileFaceNet;
import co.edu.udistrital.reconocimientofacial.modelo.Fotograma;
import co.edu.udistrital.reconocimientofacial.modelo.TipoPose;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorPose;
import co.edu.udistrital.reconocimientofacial.modelo.VerificadorLiveness;

/**
 * Pantalla reutilizable de camara guiada con CameraX, ML Kit y MobileFaceNet.
 * Soporta captura de 3 poses para registro y prueba de vida (liveness) para verificacion.
 */
public class CamaraGuiadaActivity extends AppCompatActivity {

    public static final String EXTRA_MODO = "extra_modo";
    public static final String MODO_REGISTRO = "REGISTRO";
    public static final String MODO_VERIFICACION = "VERIFICACION";
    public static final String EXTRA_EMBEDDING_FRONTAL = "extra_embedding_frontal";
    public static final String EXTRA_EMBEDDING_IZQUIERDA = "extra_embedding_izquierda";
    public static final String EXTRA_EMBEDDING_DERECHA = "extra_embedding_derecha";
    public static final String EXTRA_LIVENESS_SUPERADO = "extra_liveness_superado";
    public static final String EXTRA_DESAFIO_LIVENESS = "extra_desafio_liveness";
    public static final String EXTRA_EMBEDDING_VERIFICACION = "extra_embedding_verificacion";
    public static final String EXTRA_TIEMPO_DETECCION_MS = "extra_tiempo_deteccion_ms";
    public static final String EXTRA_TIEMPO_INFERENCIA_MS = "extra_tiempo_inferencia_ms";
    public static final String EXTRA_YAW = "extra_yaw";
    public static final String EXTRA_ROLL = "extra_roll";
    public static final String EXTRA_OJO_IZQ = "extra_ojo_izq";
    public static final String EXTRA_OJO_DER = "extra_ojo_der";
    public static final String EXTRA_RUTA_PREVIEW = "extra_ruta_preview";

    private static final int TAMANO_ENTRADA_MODELO = 112;
    private static final int FRAMES_ESTABLES_REGISTRO = 3;
    private static final int FRAMES_ESTABLES_VERIFICACION = 2;
    private static final String ARCHIVO_PREVIEW_VERIFICACION = "ultima_captura_verificacion.jpg";

    private PreviewView previewViewCamara;
    private TextView txtTituloModoCamara;
    private TextView txtInstruccionCamara;
    private TextView txtDetalleCamara;
    private Button btnReintentarCamara;

    private FaceDetector faceDetector;
    private ExtractorMobileFaceNet extractor;
    private ExecutorService cameraExecutor;
    private final Object bloqueoRecursos = new Object();
    private final Object bloqueoLiveness = new Object();

    private final ValidadorPose validadorPose = new ValidadorPose();
    private VerificadorLiveness verificadorLiveness;
    private String modoActual = MODO_REGISTRO;

    private final TipoPose[] posesRegistro = {
            TipoPose.FRONTAL,
            TipoPose.IZQUIERDA,
            TipoPose.DERECHA
    };
    private int indicePoseActual = 0;
    private int framesEstables = 0;
    private float[] embeddingFrontal;
    private float[] embeddingIzquierda;
    private float[] embeddingDerecha;

    private boolean livenessSuperadoFase1 = false;
    private volatile boolean capturaCompletada = false;
    private volatile boolean actividadCerrada = false;
    private final AtomicBoolean procesandoCaptura = new AtomicBoolean(false);

    private final ActivityResultLauncher<String> lanzadorPermisoCamara =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedido -> {
                if (concedido) {
                    iniciarCamara();
                } else {
                    Toast.makeText(
                            this,
                            "Se requiere el permiso de cámara para continuar.",
                            Toast.LENGTH_LONG
                    ).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_camara_guiada);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootCamaraGuiada), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String modoExtra = getIntent().getStringExtra(EXTRA_MODO);
        modoActual = MODO_VERIFICACION.equals(modoExtra) ? MODO_VERIFICACION : MODO_REGISTRO;

        inicializarVistas();
        if (!inicializarComponentesBiometricos()) {
            return;
        }
        verificarPermisoEIniciarCamara();
    }

    private void inicializarVistas() {
        previewViewCamara = findViewById(R.id.previewViewCamara);
        txtTituloModoCamara = findViewById(R.id.txtTituloModoCamara);
        txtInstruccionCamara = findViewById(R.id.txtInstruccionCamara);
        txtDetalleCamara = findViewById(R.id.txtDetalleCamara);
        btnReintentarCamara = findViewById(R.id.btnReintentarCamara);
        Button btnCancelarCamara = findViewById(R.id.btnCancelarCamara);

        if (MODO_VERIFICACION.equals(modoActual)) {
            verificadorLiveness = new VerificadorLiveness();
            txtTituloModoCamara.setText("Verificación con Prueba de Vida");
            txtInstruccionCamara.setText(verificadorLiveness.getDesafioActual().getInstruccion());
            txtDetalleCamara.setText("Tiempo restante: " + verificadorLiveness.obtenerSegundosRestantes() + " s");
        } else {
            txtTituloModoCamara.setText("Registro Guiado (1/3)");
            txtInstruccionCamara.setText(posesRegistro[0].getInstruccion());
            txtDetalleCamara.setText("Ubique su rostro frente a la cámara");
        }

        btnReintentarCamara.setOnClickListener(v -> reiniciarDesafioLiveness());
        btnCancelarCamara.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private boolean inicializarComponentesBiometricos() {
        FaceDetectorOptions opciones = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build();
        faceDetector = FaceDetection.getClient(opciones);

        try {
            extractor = new ExtractorMobileFaceNet(this);
        } catch (ModeloNoDisponibleException e) {
            Toast.makeText(this, "Error al cargar el modelo facial: " + e.getMessage(), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return false;
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        return true;
    }

    private void verificarPermisoEIniciarCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara();
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void iniciarCamara() {
        if (MODO_VERIFICACION.equals(modoActual) && verificadorLiveness != null) {
            verificadorLiveness.reiniciar();
            livenessSuperadoFase1 = false;
            framesEstables = 0;
            actualizarUiVerificacion(
                    "Verificación con Prueba de Vida",
                    verificadorLiveness.getDesafioActual().getInstruccion(),
                    "Tiempo restante: " + verificadorLiveness.obtenerSegundosRestantes() + " s",
                    false
            );
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            if (actividadCerrada || isFinishing() || isDestroyed()) {
                return;
            }
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                vincularCasosDeUsoCamara(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "No se pudo iniciar la cámara: " + e.getMessage(), Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void vincularCasosDeUsoCamara(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewViewCamara.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analizarFotograma);

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalysis
        );
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analizarFotograma(ImageProxy imageProxy) {
        if (actividadCerrada || capturaCompletada || procesandoCaptura.get()) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        int rotacion = imageProxy.getImageInfo().getRotationDegrees();
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotacion);
        long inicioDeteccionNs = System.nanoTime();

        faceDetector.process(inputImage)
                .addOnCompleteListener(cameraExecutor, task -> {
                    try {
                        if (actividadCerrada || capturaCompletada) {
                            return;
                        }
                        if (!task.isSuccessful()) {
                            return;
                        }
                        long tiempoDeteccionMs = Math.max(1L, (System.nanoTime() - inicioDeteccionNs) / 1_000_000L);
                        List<Face> rostros = task.getResult();
                        if (MODO_VERIFICACION.equals(modoActual)) {
                            procesarModoVerificacion(imageProxy, rostros, tiempoDeteccionMs);
                        } else {
                            procesarModoRegistro(imageProxy, rostros);
                        }
                    } finally {
                        imageProxy.close();
                    }
                });
    }

    private void procesarModoRegistro(ImageProxy imageProxy, List<Face> rostros) {
        if (indicePoseActual >= posesRegistro.length) {
            return;
        }

        TipoPose poseActual = posesRegistro[indicePoseActual];
        int cantidadRostros = rostros != null ? rostros.size() : 0;
        String titulo = "Registro Guiado (" + (indicePoseActual + 1) + "/3)";

        if (!validadorPose.validarCantidadRostros(cantidadRostros)) {
            framesEstables = 0;
            String guia = validadorPose.obtenerGuia(cantidadRostros, poseActual, 0f, 0f);
            actualizarUiTexto(titulo, guia, "Debe aparecer exactamente 1 rostro");
            return;
        }

        Face rostro = rostros.get(0);
        float yaw = rostro.getHeadEulerAngleY();
        float roll = rostro.getHeadEulerAngleZ();
        String guia = validadorPose.obtenerGuia(cantidadRostros, poseActual, yaw, roll);

        if (validadorPose.cumplePose(poseActual, yaw, roll)) {
            framesEstables++;
        } else {
            framesEstables = 0;
        }

        String detalle = String.format(
                Locale.US,
                "Pose: %s | Yaw: %.1f° | Estabilidad: %d/%d",
                poseActual.getEtiqueta(),
                yaw,
                Math.min(framesEstables, FRAMES_ESTABLES_REGISTRO),
                FRAMES_ESTABLES_REGISTRO
        );
        actualizarUiTexto(titulo, guia, detalle);

        if (framesEstables >= FRAMES_ESTABLES_REGISTRO && procesandoCaptura.compareAndSet(false, true)) {
            try {
                Fotograma fotograma = extraerFotogramaDeRostro(imageProxy, rostro);
                if (fotograma == null) {
                    framesEstables = 0;
                    return;
                }
                float[] embedding = ejecutarExtraccionSegura(fotograma);
                if (embedding == null) {
                    framesEstables = 0;
                    return;
                }
                guardarEmbeddingPorPose(poseActual, embedding);
                framesEstables = 0;
                indicePoseActual++;

                if (indicePoseActual >= posesRegistro.length) {
                    capturaCompletada = true;
                    entregarResultadoRegistro();
                } else {
                    TipoPose siguiente = posesRegistro[indicePoseActual];
                    actualizarUiTexto(
                            "Registro Guiado (" + (indicePoseActual + 1) + "/3)",
                            siguiente.getInstruccion(),
                            "Pose " + poseActual.getEtiqueta() + " capturada ✓"
                    );
                }
            } catch (BiometriaException e) {
                framesEstables = 0;
                actualizarUiTexto(titulo, "Error al capturar rostro", e.getMessage());
            } finally {
                procesandoCaptura.set(false);
            }
        }
    }

    private void procesarModoVerificacion(ImageProxy imageProxy, List<Face> rostros, long tiempoDeteccionMs) {
        int cantidadRostros = rostros != null ? rostros.size() : 0;
        float yaw = 0f;
        float roll = 0f;
        float ojoIzq = -1.0f;
        float ojoDer = -1.0f;

        if (cantidadRostros >= 1) {
            Face primerRostro = rostros.get(0);
            yaw = primerRostro.getHeadEulerAngleY();
            roll = primerRostro.getHeadEulerAngleZ();
            ojoIzq = primerRostro.getLeftEyeOpenProbability() != null
                    ? primerRostro.getLeftEyeOpenProbability() : -1.0f;
            ojoDer = primerRostro.getRightEyeOpenProbability() != null
                    ? primerRostro.getRightEyeOpenProbability() : -1.0f;
        }

        if (!livenessSuperadoFase1) {
            VerificadorLiveness.Estado estado;
            String instruccion;
            long segundos;
            synchronized (bloqueoLiveness) {
                estado = verificadorLiveness.evaluarFotograma(cantidadRostros, yaw, ojoIzq, ojoDer);
                instruccion = verificadorLiveness.getDesafioActual().getInstruccion();
                segundos = verificadorLiveness.obtenerSegundosRestantes();
                if (estado == VerificadorLiveness.Estado.SUPERADO) {
                    livenessSuperadoFase1 = true;
                    framesEstables = 0;
                }
            }

            if (estado == VerificadorLiveness.Estado.EXPIRADO) {
                actualizarUiVerificacion(
                        "Verificación con Prueba de Vida",
                        "Prueba de vida expirada (8 s). Posible foto o falta de movimiento.",
                        "Presione Reintentar para generar un nuevo desafío",
                        true
                );
                return;
            }

            if (estado == VerificadorLiveness.Estado.EN_PROGRESO) {
                String detalle;
                if (cantidadRostros != 1) {
                    detalle = "Advertencia: debe aparecer solo 1 rostro | Tiempo restante: " + segundos + " s";
                } else {
                    detalle = String.format(Locale.US, "Tiempo restante: %d s | Yaw: %.1f°", segundos, yaw);
                }
                actualizarUiVerificacion("Verificación con Prueba de Vida", instruccion, detalle, false);
                return;
            }

            // Estado SUPERADO -> pasa automaticamente a Fase 2
            actualizarUiVerificacion(
                    "Verificación - Captura Frontal",
                    "¡Prueba de vida superada! Mire de frente para capturar...",
                    "Mantenga 1 rostro de frente a la cámara",
                    false
            );
            return;
        }

        // Fase 2: Captura frontal tras superar prueba de vida
        if (!validadorPose.validarCantidadRostros(cantidadRostros)) {
            framesEstables = 0;
            actualizarUiVerificacion(
                    "Verificación - Captura Frontal",
                    "¡Prueba de vida superada! Mire de frente para capturar...",
                    validadorPose.obtenerGuia(cantidadRostros, TipoPose.FRONTAL, yaw, roll),
                    false
            );
            return;
        }

        Face rostro = rostros.get(0);
        if (validadorPose.cumplePose(TipoPose.FRONTAL, yaw, roll)) {
            framesEstables++;
        } else {
            framesEstables = 0;
        }

        actualizarUiVerificacion(
                "Verificación - Captura Frontal",
                "¡Prueba de vida superada! Mire de frente para capturar...",
                String.format(
                        Locale.US,
                        "Yaw: %.1f° | Estabilidad: %d/%d",
                        yaw,
                        Math.min(framesEstables, FRAMES_ESTABLES_VERIFICACION),
                        FRAMES_ESTABLES_VERIFICACION
                ),
                false
        );

        if (framesEstables >= FRAMES_ESTABLES_VERIFICACION && procesandoCaptura.compareAndSet(false, true)) {
            try {
                Fotograma fotograma = extraerFotogramaDeRostro(imageProxy, rostro);
                if (fotograma == null) {
                    framesEstables = 0;
                    return;
                }
                float[] embedding = ejecutarExtraccionSegura(fotograma);
                if (embedding == null) {
                    framesEstables = 0;
                    return;
                }
                String rutaPreview = guardarPreviewCache(fotograma.getRostroRecortado());
                capturaCompletada = true;
                entregarResultadoVerificacion(
                        embedding,
                        tiempoDeteccionMs,
                        fotograma.getTiempoInferenciaMs(),
                        yaw,
                        roll,
                        ojoIzq,
                        ojoDer,
                        rutaPreview
                );
            } catch (BiometriaException | IOException e) {
                framesEstables = 0;
                actualizarUiVerificacion(
                        "Verificación - Captura Frontal",
                        "Error al capturar rostro",
                        e.getMessage(),
                        false
                );
            } finally {
                procesandoCaptura.set(false);
            }
        }
    }

    private Fotograma extraerFotogramaDeRostro(ImageProxy imageProxy, Face rostro) {
        Bitmap frameOriginal = imageProxy.toBitmap();
        int gradosRotacion = imageProxy.getImageInfo().getRotationDegrees();
        Bitmap frameRotado = rotarBitmap(frameOriginal, gradosRotacion);

        Rect bounds = rostro.getBoundingBox();
        int left = Math.max(0, bounds.left);
        int top = Math.max(0, bounds.top);
        int right = Math.min(frameRotado.getWidth(), bounds.right);
        int bottom = Math.min(frameRotado.getHeight(), bounds.bottom);
        if (right <= left || bottom <= top) {
            return null;
        }

        Bitmap recorte = Bitmap.createBitmap(frameRotado, left, top, right - left, bottom - top);
        Bitmap rostro112 = Bitmap.createScaledBitmap(
                recorte,
                TAMANO_ENTRADA_MODELO,
                TAMANO_ENTRADA_MODELO,
                true
        );

        float yaw = rostro.getHeadEulerAngleY();
        float roll = rostro.getHeadEulerAngleZ();
        float ojoIzq = rostro.getLeftEyeOpenProbability() != null
                ? rostro.getLeftEyeOpenProbability() : -1.0f;
        float ojoDer = rostro.getRightEyeOpenProbability() != null
                ? rostro.getRightEyeOpenProbability() : -1.0f;

        return new Fotograma(
                frameRotado,
                rostro112,
                new Rect(left, top, right, bottom),
                yaw,
                roll,
                ojoIzq,
                ojoDer
        );
    }

    private Bitmap rotarBitmap(Bitmap origen, int gradosRotacion) {
        if (gradosRotacion == 0) {
            return origen;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(gradosRotacion);
        return Bitmap.createBitmap(
                origen,
                0,
                0,
                origen.getWidth(),
                origen.getHeight(),
                matrix,
                true
        );
    }

    private float[] ejecutarExtraccionSegura(Fotograma fotograma) throws BiometriaException {
        synchronized (bloqueoRecursos) {
            if (actividadCerrada || extractor == null) {
                return null;
            }
            return extractor.extraerEmbedding(fotograma);
        }
    }

    private void guardarEmbeddingPorPose(TipoPose pose, float[] embedding) {
        if (pose == TipoPose.FRONTAL) {
            embeddingFrontal = embedding;
        } else if (pose == TipoPose.IZQUIERDA) {
            embeddingIzquierda = embedding;
        } else if (pose == TipoPose.DERECHA) {
            embeddingDerecha = embedding;
        }
    }

    private String guardarPreviewCache(Bitmap rostro112) throws IOException {
        File archivoPreview = new File(getCacheDir(), ARCHIVO_PREVIEW_VERIFICACION);
        try (FileOutputStream fos = new FileOutputStream(archivoPreview)) {
            rostro112.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
        }
        return archivoPreview.getAbsolutePath();
    }

    private void entregarResultadoRegistro() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            Intent resultado = new Intent();
            resultado.putExtra(EXTRA_EMBEDDING_FRONTAL, embeddingFrontal);
            resultado.putExtra(EXTRA_EMBEDDING_IZQUIERDA, embeddingIzquierda);
            resultado.putExtra(EXTRA_EMBEDDING_DERECHA, embeddingDerecha);
            setResult(RESULT_OK, resultado);
            finish();
        });
    }

    private void entregarResultadoVerificacion(
            float[] embedding,
            long tiempoDeteccionMs,
            long tiempoInferenciaMs,
            float yaw,
            float roll,
            float ojoIzq,
            float ojoDer,
            String rutaPreview
    ) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            Intent resultado = new Intent();
            resultado.putExtra(EXTRA_LIVENESS_SUPERADO, true);
            resultado.putExtra(EXTRA_DESAFIO_LIVENESS, verificadorLiveness.getDesafioActual().name());
            resultado.putExtra(EXTRA_EMBEDDING_VERIFICACION, embedding);
            resultado.putExtra(EXTRA_TIEMPO_DETECCION_MS, tiempoDeteccionMs);
            resultado.putExtra(EXTRA_TIEMPO_INFERENCIA_MS, tiempoInferenciaMs);
            resultado.putExtra(EXTRA_YAW, yaw);
            resultado.putExtra(EXTRA_ROLL, roll);
            resultado.putExtra(EXTRA_OJO_IZQ, ojoIzq);
            resultado.putExtra(EXTRA_OJO_DER, ojoDer);
            resultado.putExtra(EXTRA_RUTA_PREVIEW, rutaPreview);
            setResult(RESULT_OK, resultado);
            finish();
        });
    }

    private void reiniciarDesafioLiveness() {
        if (verificadorLiveness == null) {
            return;
        }
        String nuevaInstruccion;
        long segundosRestantes;
        synchronized (bloqueoLiveness) {
            verificadorLiveness.reiniciar();
            livenessSuperadoFase1 = false;
            framesEstables = 0;
            nuevaInstruccion = verificadorLiveness.getDesafioActual().getInstruccion();
            segundosRestantes = verificadorLiveness.obtenerSegundosRestantes();
        }
        btnReintentarCamara.setVisibility(View.GONE);
        txtTituloModoCamara.setText("Verificación con Prueba de Vida");
        txtInstruccionCamara.setText(nuevaInstruccion);
        txtDetalleCamara.setText("Tiempo restante: " + segundosRestantes + " s");
    }

    private void actualizarUiTexto(String titulo, String instruccion, String detalle) {
        actualizarUiVerificacion(titulo, instruccion, detalle, false);
    }

    private void actualizarUiVerificacion(
            String titulo,
            String instruccion,
            String detalle,
            boolean mostrarReintentar
    ) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            txtTituloModoCamara.setText(titulo);
            txtInstruccionCamara.setText(instruccion);
            txtDetalleCamara.setText(detalle);
            btnReintentarCamara.setVisibility(mostrarReintentar ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onDestroy() {
        actividadCerrada = true;
        synchronized (bloqueoRecursos) {
            if (faceDetector != null) {
                faceDetector.close();
            }
            if (extractor != null) {
                extractor.cerrar();
            }
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }
}
