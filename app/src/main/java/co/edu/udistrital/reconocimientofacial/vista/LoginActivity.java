package co.edu.udistrital.reconocimientofacial.vista;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import co.edu.udistrital.reconocimientofacial.MainActivity;
import co.edu.udistrital.reconocimientofacial.R;
import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.excepciones.CalidadInsuficienteException;
import co.edu.udistrital.reconocimientofacial.modelo.Fotograma;
import co.edu.udistrital.reconocimientofacial.modelo.GestorSesion;
import co.edu.udistrital.reconocimientofacial.modelo.RegistroTelemetria;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstandar;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstricto;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorFacial;
import co.edu.udistrital.reconocimientofacial.persistencia.CifradorAES;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioTelemetriaCsv;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioUsuarioArchivo;

/**
 * Pantalla unificada de inicio de sesion biometrico 1:1 (Liveness activo + comparacion
 * contra las 3 poses cifradas con AES + telemetria CSV) y respaldo por contrasena.
 * Aplica bloqueo temporal compartido de 30 s tras tres intentos fallidos.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String MODELO_TFLITE_ASSET = "mobilefacenet.tflite";

    private TextView txtEstadoRegistroLogin;
    private EditText edtUsuarioLogin;
    private EditText edtClaveLogin;
    private TextView txtMensajeLogin;
    private RadioGroup radioGroupValidadorLogin;
    private RadioGroup radioGroupLuzLogin;
    private RadioGroup radioGroupIntentoLogin;
    private Button btnIniciarBiometria;
    private Button btnIniciarSesion;

    private GestorSesion gestorSesion;
    private RepositorioUsuarioArchivo repositorioUsuario;
    private RepositorioTelemetriaCsv repositorioTelemetria;
    private double tamanoModeloMb;
    private CountDownTimer temporizadorBloqueo;
    private boolean mantenerMensajeEnResume = false;

    private final ActivityResultLauncher<Intent> lanzadorCamaraGuiadaVerificacion =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    procesarResultadoBiometrico(result.getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gestorSesion = GestorSesion.obtenerInstancia();
        if (gestorSesion.isSesionActiva()) {
            abrirPantallaPrincipal();
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLogin), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repositorioUsuario = new RepositorioUsuarioArchivo(this);
        repositorioTelemetria = new RepositorioTelemetriaCsv(this);
        tamanoModeloMb = calcularTamanoModeloMb();
        inicializarVistas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gestorSesion.isSesionActiva()) {
            abrirPantallaPrincipal();
            return;
        }

        actualizarEstadoRegistro();

        if (gestorSesion.estaBloqueado()) {
            mantenerMensajeEnResume = false;
            iniciarContadorBloqueo();
        } else {
            cancelarTemporizador();
            establecerBotonesAccesoHabilitados(true);
            if (mantenerMensajeEnResume) {
                mantenerMensajeEnResume = false;
            } else {
                txtMensajeLogin.setVisibility(View.GONE);
            }
        }
    }

    private void inicializarVistas() {
        txtEstadoRegistroLogin = findViewById(R.id.txtEstadoRegistroLogin);
        edtUsuarioLogin = findViewById(R.id.edtUsuarioLogin);
        edtClaveLogin = findViewById(R.id.edtClaveLogin);
        txtMensajeLogin = findViewById(R.id.txtMensajeLogin);
        radioGroupValidadorLogin = findViewById(R.id.radioGroupValidadorLogin);
        radioGroupLuzLogin = findViewById(R.id.radioGroupLuzLogin);
        radioGroupIntentoLogin = findViewById(R.id.radioGroupIntentoLogin);
        btnIniciarBiometria = findViewById(R.id.btnIniciarBiometria);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        Button btnIrARegistro = findViewById(R.id.btnIrARegistro);
        Button btnVerCsvLogin = findViewById(R.id.btnVerCsvLogin);

        btnIniciarBiometria.setOnClickListener(v -> intentarIniciarBiometria());
        btnIniciarSesion.setOnClickListener(v -> intentarIniciarSesion());
        btnIrARegistro.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegistroActivity.class);
            startActivity(intent);
        });
        btnVerCsvLogin.setOnClickListener(v -> mostrarDialogoTelemetriaCsv());
    }

    private void actualizarEstadoRegistro() {
        if (!repositorioUsuario.existeUsuarioRegistrado()) {
            txtEstadoRegistroLogin.setText("Sin usuario registrado. Cree una cuenta primero.");
            return;
        }

        try {
            UsuarioRegistrado usuario = repositorioUsuario.cargarUsuario();
            if (usuario != null && !usuario.getNombreUsuario().isEmpty()) {
                txtEstadoRegistroLogin.setText("Perfil cifrado activo: " + usuario.getNombreUsuario());
                if (edtUsuarioLogin.getText().toString().trim().isEmpty()) {
                    edtUsuarioLogin.setText(usuario.getNombreUsuario());
                }
            } else {
                txtEstadoRegistroLogin.setText("Sin usuario registrado. Cree una cuenta primero.");
            }
        } catch (BiometriaException e) {
            txtEstadoRegistroLogin.setText("Error al leer perfil cifrado: " + e.getMessage());
        }
    }

    private void intentarIniciarBiometria() {
        if (gestorSesion.estaBloqueado()) {
            iniciarContadorBloqueo();
            return;
        }

        if (!repositorioUsuario.existeUsuarioRegistrado()) {
            mostrarMensaje("Debe registrar un usuario antes de iniciar sesión.");
            return;
        }

        String usuarioIngresado = edtUsuarioLogin.getText().toString().trim();
        if (usuarioIngresado.isEmpty()) {
            mostrarMensaje("Ingrese el nombre de usuario para iniciar sesión biométrica.");
            return;
        }

        try {
            UsuarioRegistrado usuarioGuardado = repositorioUsuario.cargarUsuario();
            if (usuarioGuardado == null) {
                mostrarMensaje("No se encontró un perfil válido. Registre una cuenta primero.");
                return;
            }

            if (!usuarioGuardado.getNombreUsuario().equals(usuarioIngresado)) {
                gestorSesion.registrarFalloAcceso();
                if (gestorSesion.estaBloqueado()) {
                    iniciarContadorBloqueo();
                } else {
                    mostrarMensaje("Usuario no coincide con el perfil registrado. Intentos restantes: "
                            + gestorSesion.getIntentosRestantes() + " de 3.");
                }
                return;
            }

            if (!usuarioGuardado.tieneEmbeddingsCompletos()) {
                mostrarMensaje("El perfil registrado no tiene las 3 poses biométricas completas.");
                return;
            }

            Intent intent = new Intent(LoginActivity.this, CamaraGuiadaActivity.class);
            intent.putExtra(CamaraGuiadaActivity.EXTRA_MODO, CamaraGuiadaActivity.MODO_VERIFICACION);
            lanzadorCamaraGuiadaVerificacion.launch(intent);
        } catch (BiometriaException e) {
            mostrarMensaje("Error al leer perfil cifrado: " + e.getMessage());
        }
    }

    private void procesarResultadoBiometrico(Intent data) {
        boolean livenessSuperado = data.getBooleanExtra(CamaraGuiadaActivity.EXTRA_LIVENESS_SUPERADO, false);
        String desafioExtra = data.getStringExtra(CamaraGuiadaActivity.EXTRA_DESAFIO_LIVENESS);
        String desafio = desafioExtra != null ? desafioExtra : "";
        long tiempoDet = data.getLongExtra(CamaraGuiadaActivity.EXTRA_TIEMPO_DETECCION_MS, 0L);
        long tiempoInf = data.getLongExtra(CamaraGuiadaActivity.EXTRA_TIEMPO_INFERENCIA_MS, 0L);

        double ramMb = obtenerRamUsadaMb();
        ValidadorFacial validador = obtenerValidadorSeleccionado();
        String iluminacion = obtenerIluminacionSeleccionada();
        String tipoIntento = obtenerTipoIntentoSeleccionado();

        UsuarioRegistrado usuarioGuardado;
        try {
            usuarioGuardado = repositorioUsuario.cargarUsuario();
        } catch (BiometriaException e) {
            mantenerMensajeEnResume = true;
            mostrarMensaje("Error al cargar el perfil cifrado: " + e.getMessage());
            return;
        }

        if (usuarioGuardado == null) {
            mantenerMensajeEnResume = true;
            mostrarMensaje("No se encontró un perfil válido. Registre una cuenta primero.");
            return;
        }

        String nombreUsuario = usuarioGuardado.getNombreUsuario();

        if (!livenessSuperado) {
            gestorSesion.registrarFalloAcceso();
            guardarRegistroTelemetriaSeguro(new RegistroTelemetria(
                    nombreUsuario,
                    tipoIntento,
                    iluminacion,
                    validador.getNombreModo(),
                    false,
                    desafio,
                    tiempoDet,
                    tiempoInf,
                    ramMb,
                    tamanoModeloMb,
                    0.0,
                    validador.getUmbral(),
                    "RECHAZADO_LIVENESS"
            ));
            mantenerMensajeEnResume = true;
            if (gestorSesion.estaBloqueado()) {
                iniciarContadorBloqueo();
            } else {
                mostrarMensaje("Prueba de vida fallida (" + desafio + "). Intentos restantes: "
                        + gestorSesion.getIntentosRestantes() + " de 3.");
            }
            return;
        }

        float[] embedding = data.getFloatArrayExtra(CamaraGuiadaActivity.EXTRA_EMBEDDING_VERIFICACION);
        float yaw = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_YAW, 0f);
        float roll = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_ROLL, 0f);
        float ojoIzq = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_OJO_IZQ, -1.0f);
        float ojoDer = data.getFloatExtra(CamaraGuiadaActivity.EXTRA_OJO_DER, -1.0f);

        Fotograma fotograma = new Fotograma(null, null, null, yaw, roll, ojoIzq, ojoDer);
        fotograma.setEmbedding(embedding);
        fotograma.setTiempoInferenciaMs(tiempoInf);

        try {
            ResultadoVerificacion resultado = validador.verificarContraPerfil(usuarioGuardado, fotograma);
            String veredicto = resultado.isCoincide() ? "COINCIDE" : "NO_COINCIDE";
            guardarRegistroTelemetriaSeguro(new RegistroTelemetria(
                    nombreUsuario,
                    tipoIntento,
                    iluminacion,
                    validador.getNombreModo(),
                    true,
                    desafio,
                    tiempoDet,
                    tiempoInf,
                    ramMb,
                    tamanoModeloMb,
                    resultado.getSimilitudCoseno(),
                    resultado.getUmbralAplicado(),
                    veredicto
            ));

            if (resultado.isCoincide()) {
                String resumen = String.format(
                        Locale.US,
                        "%s\nLiveness: OK (%s) | Luz: %s | Tipo: %s\nDetección: %d ms | Inferencia TFLite: %d ms | RAM: %.1f MB | Modelo: %.2f MB",
                        resultado.getDetalleDiagnostico(),
                        desafio,
                        iluminacion,
                        tipoIntento,
                        tiempoDet,
                        tiempoInf,
                        ramMb,
                        tamanoModeloMb
                );
                if (gestorSesion.iniciarSesionBiometrica(usuarioGuardado, true, resumen)) {
                    abrirPantallaPrincipal();
                } else if (gestorSesion.estaBloqueado()) {
                    mantenerMensajeEnResume = true;
                    iniciarContadorBloqueo();
                }
            } else {
                gestorSesion.iniciarSesionBiometrica(usuarioGuardado, false, null);
                mantenerMensajeEnResume = true;
                if (gestorSesion.estaBloqueado()) {
                    iniciarContadorBloqueo();
                } else {
                    mostrarMensaje("Rostro NO coincide ("
                            + String.format(Locale.US, "%.1f%%", resultado.getPorcentajeSimilitud())
                            + "). Intentos restantes: "
                            + gestorSesion.getIntentosRestantes() + " de 3.");
                }
            }
        } catch (CalidadInsuficienteException e) {
            guardarRegistroTelemetriaSeguro(new RegistroTelemetria(
                    nombreUsuario,
                    tipoIntento,
                    iluminacion,
                    validador.getNombreModo(),
                    true,
                    desafio,
                    tiempoDet,
                    tiempoInf,
                    ramMb,
                    tamanoModeloMb,
                    0.0,
                    validador.getUmbral(),
                    "RECHAZADO_CALIDAD"
            ));
            gestorSesion.iniciarSesionBiometrica(usuarioGuardado, false, null);
            mantenerMensajeEnResume = true;
            if (gestorSesion.estaBloqueado()) {
                iniciarContadorBloqueo();
            } else {
                mostrarMensaje(e.getMessage() + " Intentos restantes: "
                        + gestorSesion.getIntentosRestantes() + " de 3.");
            }
        } catch (BiometriaException e) {
            mantenerMensajeEnResume = true;
            mostrarMensaje("Error de verificación (" + e.getCodigoError() + "): " + e.getMessage());
        }
    }

    private void intentarIniciarSesion() {
        if (gestorSesion.estaBloqueado()) {
            iniciarContadorBloqueo();
            return;
        }

        if (!repositorioUsuario.existeUsuarioRegistrado()) {
            mostrarMensaje("Debe registrar un usuario antes de iniciar sesión.");
            return;
        }

        String usuarioIngresado = edtUsuarioLogin.getText().toString().trim();
        String claveIngresada = edtClaveLogin.getText().toString();

        if (usuarioIngresado.isEmpty() || claveIngresada.isEmpty()) {
            mostrarMensaje("Complete el usuario y la contraseña para continuar.");
            return;
        }

        try {
            UsuarioRegistrado usuarioGuardado = repositorioUsuario.cargarUsuario();
            if (usuarioGuardado == null) {
                mostrarMensaje("No se encontró un perfil válido. Registre una cuenta primero.");
                return;
            }

            String hashIngresado = CifradorAES.sha256Hex(claveIngresada);
            boolean autenticado = gestorSesion.autenticar(
                    usuarioGuardado,
                    usuarioIngresado,
                    hashIngresado
            );

            if (autenticado) {
                abrirPantallaPrincipal();
            } else if (gestorSesion.estaBloqueado()) {
                iniciarContadorBloqueo();
            } else {
                mostrarMensaje("Credenciales incorrectas. Intentos restantes: "
                        + gestorSesion.getIntentosRestantes() + " de 3.");
            }
        } catch (BiometriaException e) {
            mostrarMensaje("Error al verificar credenciales: " + e.getMessage());
        }
    }

    private ValidadorFacial obtenerValidadorSeleccionado() {
        if (radioGroupValidadorLogin != null
                && radioGroupValidadorLogin.getCheckedRadioButtonId() == R.id.radioValidadorEstrictoLogin) {
            return new ValidadorEstricto();
        }
        return new ValidadorEstandar();
    }

    private String obtenerIluminacionSeleccionada() {
        if (radioGroupLuzLogin == null) {
            return "Buena";
        }
        int id = radioGroupLuzLogin.getCheckedRadioButtonId();
        if (id == R.id.radioLuzBajaLogin) {
            return "Baja";
        }
        if (id == R.id.radioLuzContraluzLogin) {
            return "Contraluz";
        }
        return "Buena";
    }

    private String obtenerTipoIntentoSeleccionado() {
        if (radioGroupIntentoLogin == null) {
            return "GENUINO";
        }
        int id = radioGroupIntentoLogin.getCheckedRadioButtonId();
        if (id == R.id.radioIntentoImpostorLogin) {
            return "IMPOSTOR";
        }
        if (id == R.id.radioIntentoSpoofingLogin) {
            return "SPOOFING";
        }
        return "GENUINO";
    }

    private double calcularTamanoModeloMb() {
        try (AssetFileDescriptor afd = getAssets().openFd(MODELO_TFLITE_ASSET)) {
            return afd.getLength() / (1024.0 * 1024.0);
        } catch (IOException e) {
            return 0.0;
        }
    }

    private double obtenerRamUsadaMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
    }

    private void guardarRegistroTelemetriaSeguro(RegistroTelemetria registro) {
        try {
            repositorioTelemetria.guardarRegistro(registro);
        } catch (BiometriaException e) {
            mostrarAlertaError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
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
                            mostrarAlertaError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
                        }
                    })
                    .setNegativeButton("Cerrar", null)
                    .show();
        } catch (BiometriaException e) {
            mostrarAlertaError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private void compartirTelemetriaCsv() {
        try {
            String csv = repositorioTelemetria.leerCsvCompleto();
            if (csv == null || csv.trim().isEmpty()) {
                mostrarAlertaError("CSV Vacío", "Aún no hay registros de telemetría para compartir.");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Telemetría Biométrica CSV");
            intent.putExtra(Intent.EXTRA_TEXT, csv);
            startActivity(Intent.createChooser(intent, "Compartir Telemetría CSV"));
        } catch (BiometriaException e) {
            mostrarAlertaError("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private void mostrarAlertaError(String titulo, String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton("Aceptar", null)
                .show();
    }

    private void iniciarContadorBloqueo() {
        cancelarTemporizador();

        long segundos = gestorSesion.obtenerSegundosRestantesBloqueo();
        if (segundos <= 0L) {
            establecerBotonesAccesoHabilitados(true);
            return;
        }

        establecerBotonesAccesoHabilitados(false);
        temporizadorBloqueo = new CountDownTimer(segundos * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long restantes = (long) Math.ceil(millisUntilFinished / 1000.0);
                mostrarMensaje(String.format(
                        Locale.US,
                        "Acceso bloqueado por 3 intentos fallidos. Espere %d s.",
                        restantes
                ));
            }

            @Override
            public void onFinish() {
                establecerBotonesAccesoHabilitados(true);
                mostrarMensaje("Bloqueo temporal finalizado. Puede intentar de nuevo.");
                temporizadorBloqueo = null;
            }
        }.start();
    }

    private void establecerBotonesAccesoHabilitados(boolean habilitados) {
        if (btnIniciarBiometria != null) {
            btnIniciarBiometria.setEnabled(habilitados);
        }
        if (btnIniciarSesion != null) {
            btnIniciarSesion.setEnabled(habilitados);
        }
    }

    private void mostrarMensaje(String mensaje) {
        txtMensajeLogin.setText(mensaje);
        txtMensajeLogin.setVisibility(View.VISIBLE);
    }

    private void abrirPantallaPrincipal() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void cancelarTemporizador() {
        if (temporizadorBloqueo != null) {
            temporizadorBloqueo.cancel();
            temporizadorBloqueo = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelarTemporizador();
    }
}
