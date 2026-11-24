package co.edu.udistrital.reconocimientofacial;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import co.edu.udistrital.reconocimientofacial.excepciones.ModeloNoDisponibleException;
import co.edu.udistrital.reconocimientofacial.modelo.DetectorRostroMLKit;
import co.edu.udistrital.reconocimientofacial.modelo.ExtractorMobileFaceNet;
import co.edu.udistrital.reconocimientofacial.modelo.Fotograma;
import co.edu.udistrital.reconocimientofacial.modelo.IDetectorRostro;
import co.edu.udistrital.reconocimientofacial.modelo.IExtractorCaracteristicas;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstandar;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstricto;
import co.edu.udistrital.reconocimientofacial.persistencia.IRepositorioHistorial;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioHistorialArchivo;
import co.edu.udistrital.reconocimientofacial.vista.IVistaBiometrica;

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

    private ControladorBiometrico controlador;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        Button btnCamaraReferencia = findViewById(R.id.btnCamaraReferencia);
        Button btnGaleriaReferencia = findViewById(R.id.btnGaleriaReferencia);
        Button btnCamaraValidacion = findViewById(R.id.btnCamaraValidacion);
        Button btnGaleriaValidacion = findViewById(R.id.btnGaleriaValidacion);
        Button btnVerificarIdentidad = findViewById(R.id.btnVerificarIdentidad);
        Button btnVerHistorial = findViewById(R.id.btnVerHistorial);
        RadioGroup radioGroupValidador = findViewById(R.id.radioGroupValidador);

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
            if (controlador != null) {
                controlador.ejecutarVerificacion();
            }
        });

        btnVerHistorial.setOnClickListener(v -> {
            if (controlador != null) {
                controlador.consultarHistorial();
            }
        });
    }

    private void inicializarArquitecturaMVC() {
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
        imgRostroReferencia.setImageBitmap(fotograma.getRostroRecortado());
        txtMetaReferencia.setText(fotograma.obtenerResumenMetadatos());
    }

    @Override
    public void mostrarFotogramaValidacion(Fotograma fotograma) {
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
