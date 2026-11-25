package co.edu.udistrital.reconocimientofacial;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import co.edu.udistrital.reconocimientofacial.controlador.ControladorBiometrico;
import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.excepciones.ModeloNoDisponibleException;
import co.edu.udistrital.reconocimientofacial.modelo.DetectorRostroMLKit;
import co.edu.udistrital.reconocimientofacial.modelo.ExtractorMobileFaceNet;
import co.edu.udistrital.reconocimientofacial.modelo.Fotograma;
import co.edu.udistrital.reconocimientofacial.modelo.GestorSesion;
import co.edu.udistrital.reconocimientofacial.modelo.IDetectorRostro;
import co.edu.udistrital.reconocimientofacial.modelo.IExtractorCaracteristicas;
import co.edu.udistrital.reconocimientofacial.modelo.RegistroTelemetria;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstandar;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstricto;
import co.edu.udistrital.reconocimientofacial.persistencia.IRepositorioHistorial;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioHistorialArchivo;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioTelemetriaCsv;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioUsuarioArchivo;
import co.edu.udistrital.reconocimientofacial.vista.CamaraGuiadaActivity;
import co.edu.udistrital.reconocimientofacial.vista.IVistaBiometrica;
import co.edu.udistrital.reconocimientofacial.vista.LoginActivity;

/**
 * Vista principal (MainActivity) en la arquitectura MVC.
 * Implementa IVistaBiometrica y delega toda la logica de negocio al ControladorBiometrico.
 */
public class MainActivity extends AppCompatActivity implements IVistaBiometrica {

    private ImageView imgRostroReferencia;
    private ImageView imgRostroValidacion;
    private TextView txtMetaReferencia;
    private TextView txtMetaValidacion;
    private TextView txtEstadoProgreso;
    private TextView txtVeredictoPrincipal;
    private TextView txtDetalleMetricas;
    private RadioGroup radioGroupIluminacion;
    private RadioGroup radioGroupTipoIntento;

    private ControladorBiometrico controlador;
    private RepositorioUsuarioArchivo repositorioUsuario;
    private RepositorioTelemetriaCsv repositorioTelemetria;
    private double tamanoModeloMb;

    private Fotograma ultimoFotogramaGuiado;
    private long ultimoTiempoDeteccionMs;
    private String ultimoDesafioLiveness = "";
    private boolean referenciaManualCargada = false;
    private boolean capturandoParaReferencia = true;

    private final ActivityResultLauncher<String> lanzadorPermisoCamara =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedido -> {
                if (concedido) {
                    abrirCamaraSegunObjetivo();
                } else {
                    mostrarError(
                            "Permiso de Camara Requerido",
                            "Debe conceder el permiso de camara para capturar fotografias, o bien utilizar el boton Galeria."
                    );
                }
            });

    private final ActivityResultLauncher<Void> lanzadorCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null && controlador != null) {
                    Bitmap copiaMutable = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    if (capturandoParaReferencia) {
                        controlador.procesarImagenReferencia(copiaMutable);
                    } else {
                        controlador.procesarImagenValidacion(copiaMutable);
                    }
                }
            });

    private final ActivityResultLauncher<String> lanzadorGaleria =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && controlador != null) {
                    try {
                        Bitmap bitmap = decodificarUriABitmap(uri);
                        if (capturandoParaReferencia) {
                            controlador.procesarImagenReferencia(bitmap);
                        } else {
                            controlador.procesarImagenValidacion(bitmap);
                        }
                    } catch (IOException e) {
                        mostrarError("Error de Imagen", "No se pudo leer la imagen seleccionada: " + e.getMessage());
                    }
                }
            });

    private final ActivityResultLauncher<Intent> lanzadorCamaraGuiadaValidacion =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    boolean livenessSuperado = data.getBooleanExtra(
                            CamaraGuiadaActivity.EXTRA_LIVENESS_SUPERADO, false);
                    String desafio = data.getStringExtra(CamaraGuiadaActivity.EXTRA_DESAFIO_LIVENESS);
                    String desafioTexto = desafio != null ? desafio : "";
                    if (livenessSuperado) {
                        long tiempoDet = data.getLongExtra(CamaraGuiadaActivity.EXTRA_TIEMPO_DETECCION_MS, 0L);
                        long tiempoInf = data.getLongExtra(CamaraGuiadaActivity.EXTRA_TIEMPO_INFERENCIA_MS, 0L);
                        float[] embedding = data.getFloatArrayExtra(CamaraGuiadaActivity.EXTRA_EMBEDDING_VERIFICACION);
                        float yaw = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_YAW, 0f);
                        float roll = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_ROLL, 0f);
                        float ojoIzq = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_OJO_IZQ, -1.0f);
                        float ojoDer = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_OJO_DER, -1.0f);
                        String rutaPreview = data.getStringExtra(CamaraGuiadaActivity.EXTRA_RUTA_PREVIEW);
                        Bitmap preview = null;
                        if (rutaPreview != null) {
                            preview = BitmapFactory.decodeFile(rutaPreview);
                            if (preview != null) {
                                imgRostroValidacion.setImageBitmap(preview);
                            }
                        }
                        txtMetaValidacion.setText(
                                "Liveness OK (" + desafioTexto + ") | Det: "
                                        + tiempoDet + " ms | TFLite: " + tiempoInf + " ms"
                        );

                        Fotograma fotogramaGuiado = new Fotograma(
                                null,
                                preview,
                                null,
                                yaw,
                                roll,
                                ojoIzq,
                                ojoDer
                        );
                        fotogramaGuiado.setEmbedding(embedding);
                        fotogramaGuiado.setTiempoInferenciaMs(tiempoInf);

                        this.ultimoFotogramaGuiado = fotogramaGuiado;
                        this.ultimoTiempoDeteccionMs = tiempoDet;
                        this.ultimoDesafioLiveness = desafioTexto;
                        verificarConPerfilRegistrado(fotogramaGuiado, tiempoDet, desafioTexto);
                    } else {
                        this.ultimoFotogramaGuiado = null;
                        registrarFalloLiveness(desafioTexto);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!GestorSesion.obtenerInstancia().isSesionActiva()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        inicializarComponentesVista();
        inicializarArquitecturaMVC();
    }

    private void inicializarComponentesVista() {
        imgRostroReferencia = findViewById(R.id.imgRostroReferencia);
        imgRostroValidacion = findViewById(R.id.imgRostroValidacion);
        txtMetaReferencia = findViewById(R.id.txtMetaReferencia);
        txtMetaValidacion = findViewById(R.id.txtMetaValidacion);
        txtEstadoProgreso = findViewById(R.id.txtEstadoProgreso);
        txtVeredictoPrincipal = findViewById(R.id.txtVeredictoPrincipal);
        txtDetalleMetricas = findViewById(R.id.txtDetalleMetricas);
        TextView txtUsuarioSesion = findViewById(R.id.txtUsuarioSesion);

        String usuarioActivo = GestorSesion.obtenerInstancia().getUsuarioActivo();
        String metodo = GestorSesion.obtenerInstancia().getMetodoAcceso();
        txtUsuarioSesion.setText(
                "Sesión activa: "
                        + (usuarioActivo != null ? usuarioActivo : "")
                        + (metodo != null ? " (" + metodo + ")" : "")
        );

        String resumenLogin = GestorSesion.obtenerInstancia().getUltimoResumenBiometrico();
        if (resumenLogin != null && !resumenLogin.isEmpty()) {
            txtVeredictoPrincipal.setText("ACCESO BIOMÉTRICO CONCEDIDO");
            txtVeredictoPrincipal.setTextColor(Color.parseColor("#2E7D32"));
            txtDetalleMetricas.setText(resumenLogin);
        }

        Button btnCerrarSesion = findViewById(R.id.btnCerrarSesion);
        Button btnCamaraReferencia = findViewById(R.id.btnCamaraReferencia);
        Button btnGaleriaReferencia = findViewById(R.id.btnGaleriaReferencia);
        Button btnCamaraValidacion = findViewById(R.id.btnCamaraValidacion);
        Button btnGaleriaValidacion = findViewById(R.id.btnGaleriaValidacion);
        Button btnCamaraGuiadaValidacion = findViewById(R.id.btnCamaraGuiadaValidacion);
        Button btnVerificarIdentidad = findViewById(R.id.btnVerificarIdentidad);
        Button btnVerHistorial = findViewById(R.id.btnVerHistorial);
        Button btnVerTelemetriaCsv = findViewById(R.id.btnVerTelemetriaCsv);
        RadioGroup radioGroupValidador = findViewById(R.id.radioGroupValidador);
        radioGroupIluminacion = findViewById(R.id.radioGroupIluminacion);
        radioGroupTipoIntento = findViewById(R.id.radioGroupTipoIntento);

        btnCamaraReferencia.setOnClickListener(v -> {
            capturandoParaReferencia = true;
            verificarPermisoYAbrirCamara();
        });

        btnGaleriaReferencia.setOnClickListener(v -> {
            capturandoParaReferencia = true;
            lanzadorGaleria.launch("image/*");
        });

        btnCamaraValidacion.setOnClickListener(v -> {
            capturandoParaReferencia = false;
            verificarPermisoYAbrirCamara();
        });

        btnGaleriaValidacion.setOnClickListener(v -> {
            capturandoParaReferencia = false;
            lanzadorGaleria.launch("image/*");
        });

        btnCamaraGuiadaValidacion.setOnClickListener(v -> {
            Intent intent = new Intent(this, CamaraGuiadaActivity.class);
            intent.putExtra(CamaraGuiadaActivity.EXTRA_MODO, CamaraGuiadaActivity.MODO_VERIFICACION);
            lanzadorCamaraGuiadaValidacion.launch(intent);
        });

        radioGroupValidador.setOnCheckedChangeListener((group, checkedId) -> {
            if (controlador == null) {
                return;
            }
            if (checkedId == R.id.radioValidadorEstricto) {
                controlador.setValidadorActual(new ValidadorEstricto());
            } else {
                controlador.setValidadorActual(new ValidadorEstandar());
            }
        });

        btnVerificarIdentidad.setOnClickListener(v -> {
            if (controlador == null) {
                return;
            }
            if (ultimoFotogramaGuiado != null && !referenciaManualCargada) {
                verificarConPerfilRegistrado(
                        ultimoFotogramaGuiado,
                        ultimoTiempoDeteccionMs,
                        ultimoDesafioLiveness
                );
            } else {
                controlador.ejecutarVerificacion();
            }
        });

        btnVerHistorial.setOnClickListener(v -> {
            if (controlador != null) {
                controlador.consultarHistorial();
            }
        });

        btnVerTelemetriaCsv.setOnClickListener(v -> mostrarDialogoTelemetriaCsv());

        btnCerrarSesion.setOnClickListener(v -> cerrarSesionUsuario());
    }

    private void cerrarSesionUsuario() {
        GestorSesion.obtenerInstancia().cerrarSesion();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void inicializarArquitecturaMVC() {
        repositorioUsuario = new RepositorioUsuarioArchivo(this);
        repositorioTelemetria = new RepositorioTelemetriaCsv(this);
        tamanoModeloMb = calcularTamanoModeloMb();
        try {
            IDetectorRostro detector = new DetectorRostroMLKit();
            IExtractorCaracteristicas extractor = new ExtractorMobileFaceNet(this);
            IRepositorioHistorial repositorio = new RepositorioHistorialArchivo(this);
            this.controlador = new ControladorBiometrico(
                    detector,
                    extractor,
                    new ValidadorEstandar(),
                    repositorio,
                    this
            );
        } catch (ModeloNoDisponibleException e) {
            mostrarError("Error de Modelo TFLite (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private double calcularTamanoModeloMb() {
        try (AssetFileDescriptor afd = getAssets().openFd("mobilefacenet.tflite")) {
            return afd.getLength() / (1024.0 * 1024.0);
        } catch (IOException e) {
            return 0.0;
        }
    }

    private double obtenerRamUsadaMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
    }

    private String obtenerIluminacionSeleccionada() {
        if (radioGroupIluminacion == null) {
            return "Buena";
        }
        int id = radioGroupIluminacion.getCheckedRadioButtonId();
        if (id == R.id.radioLuzBaja) {
            return "Baja";
        }
        if (id == R.id.radioLuzContraluz) {
            return "Contraluz";
        }
        return "Buena";
    }

    private String obtenerTipoIntentoSeleccionado() {
        if (radioGroupTipoIntento == null) {
            return "GENUINO";
        }
        int id = radioGroupTipoIntento.getCheckedRadioButtonId();
        if (id == R.id.radioIntentoImpostor) {
            return "IMPOSTOR";
        }
        if (id == R.id.radioIntentoSpoofing) {
            return "SPOOFING";
        }
        return "GENUINO";
    }

    private void verificarConPerfilRegistrado(
            Fotograma fotogramaGuiado,
            long tiempoDet,
            String desafio
    ) {
        if (controlador == null || fotogramaGuiado == null) {
            return;
        }
        try {
            UsuarioRegistrado usuario = repositorioUsuario.cargarUsuario();
            if (usuario == null || !usuario.tieneEmbeddingsCompletos()) {
                mostrarError(
                        "Perfil Incompleto",
                        "No hay un usuario registrado con sus 3 poses biométricas completas."
                );
                return;
            }
            ResultadoVerificacion res = controlador.ejecutarVerificacionConPerfil(usuario, fotogramaGuiado);
            if (res != null) {
                double ramMb = obtenerRamUsadaMb();
                String iluminacion = obtenerIluminacionSeleccionada();
                String tipoIntento = obtenerTipoIntentoSeleccionado();
                String veredicto = res.isCoincide() ? "COINCIDE" : "NO_COINCIDE";
                String desafioSeguro = desafio != null ? desafio : "";

                RegistroTelemetria registro = new RegistroTelemetria(
                        usuario.getNombreUsuario(),
                        tipoIntento,
                        iluminacion,
                        res.getNombreValidador(),
                        true,
                        desafioSeguro,
                        tiempoDet,
                        fotogramaGuiado.getTiempoInferenciaMs(),
                        ramMb,
                        tamanoModeloMb,
                        res.getSimilitudCoseno(),
                        res.getUmbralAplicado(),
                        veredicto
                );
                repositorioTelemetria.guardarRegistro(registro);

                txtDetalleMetricas.append(String.format(
                        Locale.US,
                        "\nDet: %d ms | RAM: %.1f MB | Modelo: %.2f MB | Luz: %s | Liveness: OK (%s)",
                        tiempoDet,
                        ramMb,
                        tamanoModeloMb,
                        iluminacion,
                        desafioSeguro
                ));
            }
        } catch (BiometriaException e) {
            mostrarError("Error de Verificación (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private void registrarFalloLiveness(String desafio) {
        String desafioSeguro = desafio != null ? desafio : "";
        String iluminacion = obtenerIluminacionSeleccionada();
        String tipoIntento = obtenerTipoIntentoSeleccionado();
        double ramMb = obtenerRamUsadaMb();
        String usuarioActivo = GestorSesion.obtenerInstancia().getUsuarioActivo();
        String nombreUsuario = usuarioActivo != null ? usuarioActivo : "";
        String modoValidador = controlador != null && controlador.getValidadorActual() != null
                ? controlador.getValidadorActual().getNombreModo()
                : "";
        double umbral = controlador != null && controlador.getValidadorActual() != null
                ? controlador.getValidadorActual().getUmbral()
                : 0.0;

        txtMetaValidacion.setText("Prueba de vida fallida (" + desafioSeguro + ")");
        txtVeredictoPrincipal.setText("RECHAZADO (Prueba de vida fallida)");
        txtVeredictoPrincipal.setTextColor(Color.parseColor("#C62828"));
        txtDetalleMetricas.setText(String.format(
                Locale.US,
                "Desafío: %s | Liveness: FALLIDO | Luz: %s | Tipo: %s | RAM: %.1f MB | Modelo: %.2f MB",
                desafioSeguro,
                iluminacion,
                tipoIntento,
                ramMb,
                tamanoModeloMb
        ));

        try {
            RegistroTelemetria registro = new RegistroTelemetria(
                    nombreUsuario,
                    tipoIntento,
                    iluminacion,
                    modoValidador,
                    false,
                    desafioSeguro,
                    0L,
                    0L,
                    ramMb,
                    tamanoModeloMb,
                    0.0,
                    umbral,
                    "RECHAZADO_LIVENESS"
            );
            repositorioTelemetria.guardarRegistro(registro);
        } catch (BiometriaException e) {
            mostrarError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private void mostrarDialogoTelemetriaCsv() {
        try {
            String ruta = repositorioTelemetria.getRutaArchivo();
            List<String> registros = repositorioTelemetria.leerRegistrosLegibles();
            StringBuilder sb = new StringBuilder();
            sb.append("Archivo: ").append(ruta).append("\n\n");
            if (registros == null || registros.isEmpty()) {
                sb.append("Aún no hay registros en telemetria_experimentos.csv.");
            } else {
                int limite = Math.min(registros.size(), 15);
                for (int i = 0; i < limite; i++) {
                    sb.append(i + 1).append(". ").append(registros.get(i)).append("\n\n");
                }
            }

            new AlertDialog.Builder(this)
                    .setTitle("Telemetría Experimental (CSV)")
                    .setMessage(sb.toString().trim())
                    .setPositiveButton("Compartir CSV", (d, w) -> compartirTelemetriaCsv())
                    .setNeutralButton("Limpiar CSV", (d, w) -> {
                        try {
                            repositorioTelemetria.limpiarRegistros();
                            mostrarDialogoTelemetriaCsv();
                        } catch (BiometriaException e) {
                            mostrarError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
                        }
                    })
                    .setNegativeButton("Cerrar", null)
                    .show();
        } catch (BiometriaException e) {
            mostrarError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private void compartirTelemetriaCsv() {
        try {
            String csv = repositorioTelemetria.leerCsvCompleto();
            if (csv == null || csv.trim().isEmpty()) {
                mostrarError("CSV Vacío", "Aún no hay registros de telemetría para compartir.");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Telemetría Biométrica CSV");
            intent.putExtra(Intent.EXTRA_TEXT, csv);
            startActivity(Intent.createChooser(intent, "Compartir Telemetría CSV"));
        } catch (BiometriaException e) {
            mostrarError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private void verificarPermisoYAbrirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamaraSegunObjetivo();
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void abrirCamaraSegunObjetivo() {
        lanzadorCamara.launch(null);
    }

    @SuppressWarnings("deprecation")
    private Bitmap decodificarUriABitmap(Uri uri) throws IOException {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) ->
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        } else {
            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
        return bitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    @Override
    public void mostrarFotogramaReferencia(Fotograma fotograma) {
        referenciaManualCargada = true;
        imgRostroReferencia.setImageBitmap(fotograma.getRostroRecortado());
        txtMetaReferencia.setText(fotograma.obtenerResumenMetadatos());
    }

    @Override
    public void mostrarFotogramaValidacion(Fotograma fotograma) {
        ultimoFotogramaGuiado = null;
        imgRostroValidacion.setImageBitmap(fotograma.getRostroRecortado());
        txtMetaValidacion.setText(fotograma.obtenerResumenMetadatos());
    }

    @Override
    public void mostrarResultado(ResultadoVerificacion resultado) {
        if (resultado.isCoincide()) {
            txtVeredictoPrincipal.setText(String.format(
                    Locale.US,
                    "COINCIDE (Similitud: %.1f%%)",
                    resultado.getPorcentajeSimilitud()
            ));
            txtVeredictoPrincipal.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            txtVeredictoPrincipal.setText(String.format(
                    Locale.US,
                    "NO COINCIDE (Similitud: %.1f%%)",
                    resultado.getPorcentajeSimilitud()
            ));
            txtVeredictoPrincipal.setTextColor(Color.parseColor("#C62828"));
        }
        txtDetalleMetricas.setText(resultado.getDetalleDiagnostico());
    }

    @Override
    public void mostrarError(String titulo, String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton("Aceptar", null)
                .show();
    }

    @Override
    public void mostrarProgreso(boolean visible, String mensajeEstado) {
        txtEstadoProgreso.setVisibility(visible ? View.VISIBLE : View.GONE);
        txtEstadoProgreso.setText(mensajeEstado);
    }

    @Override
    public void mostrarHistorial(List<String> registros) {
        String contenido;
        if (registros == null || registros.isEmpty()) {
            contenido = "Aun no hay verificaciones registradas en historial_verificaciones.csv.";
        } else {
            StringBuilder sb = new StringBuilder();
            int limite = Math.min(registros.size(), 15);
            for (int i = 0; i < limite; i++) {
                sb.append(i + 1).append(". ").append(registros.get(i)).append("\n\n");
            }
            contenido = sb.toString().trim();
        }

        new AlertDialog.Builder(this)
                .setTitle("Historial en Archivo (CSV)")
                .setMessage(contenido)
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Limpiar Archivo", (d, w) -> {
                    if (controlador != null) {
                        controlador.limpiarHistorial();
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controlador != null) {
            controlador.liberarRecursos();
        }
    }
}
