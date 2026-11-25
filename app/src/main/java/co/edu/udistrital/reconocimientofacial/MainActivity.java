package co.edu.udistrital.reconocimientofacial;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.GestorSesion;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioTelemetriaCsv;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioUsuarioArchivo;
import co.edu.udistrital.reconocimientofacial.vista.LoginActivity;

/**
 * Panel principal posterior al inicio de sesion.
 * Muestra el resumen de la sesion autenticada, la informacion del perfil cifrado,
 * los registros recientes de telemetria CSV y los controles de cierre o eliminacion.
 */
public class MainActivity extends AppCompatActivity {

    private static final int MAX_REGISTROS_VISIBLES = 8;

    private TextView txtUsuarioSesion;
    private TextView txtInfoPerfilCifrado;
    private TextView txtResumenBiometricoLogin;
    private TextView txtRutaCsvMain;
    private TextView txtListaRegistrosMain;

    private RepositorioUsuarioArchivo repositorioUsuario;
    private RepositorioTelemetriaCsv repositorioTelemetria;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!GestorSesion.obtenerInstancia().isSesionActiva()) {
            irALogin();
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repositorioUsuario = new RepositorioUsuarioArchivo(this);
        repositorioTelemetria = new RepositorioTelemetriaCsv(this);

        inicializarVistas();
        cargarDatosSesionYPerfil();
        cargarRegistrosTelemetria();
    }

    private void inicializarVistas() {
        txtUsuarioSesion = findViewById(R.id.txtUsuarioSesion);
        txtInfoPerfilCifrado = findViewById(R.id.txtInfoPerfilCifrado);
        txtResumenBiometricoLogin = findViewById(R.id.txtResumenBiometricoLogin);
        txtRutaCsvMain = findViewById(R.id.txtRutaCsvMain);
        txtListaRegistrosMain = findViewById(R.id.txtListaRegistrosMain);

        Button btnCompartirCsvMain = findViewById(R.id.btnCompartirCsvMain);
        Button btnLimpiarCsvMain = findViewById(R.id.btnLimpiarCsvMain);
        Button btnCerrarSesion = findViewById(R.id.btnCerrarSesion);
        Button btnEliminarPerfil = findViewById(R.id.btnEliminarPerfil);

        btnCompartirCsvMain.setOnClickListener(v -> compartirCsv());
        btnLimpiarCsvMain.setOnClickListener(v -> confirmarLimpiezaCsv());
        btnCerrarSesion.setOnClickListener(v -> cerrarSesionYSalir());
        btnEliminarPerfil.setOnClickListener(v -> confirmarEliminacionPerfil());
    }

    private void cargarDatosSesionYPerfil() {
        GestorSesion gestor = GestorSesion.obtenerInstancia();
        String usuarioActivo = gestor.getUsuarioActivo();
        String metodo = gestor.getMetodoAcceso();

        String usuarioTexto = usuarioActivo != null && !usuarioActivo.isEmpty() ? usuarioActivo : "Desconocido";
        String metodoTexto = metodo != null && !metodo.isEmpty() ? metodo : "No especificado";
        txtUsuarioSesion.setText("Usuario: " + usuarioTexto + "\nMétodo de acceso: " + metodoTexto);

        String resumenBiometrico = gestor.getUltimoResumenBiometrico();
        if (resumenBiometrico != null && !resumenBiometrico.trim().isEmpty()) {
            txtResumenBiometricoLogin.setVisibility(View.VISIBLE);
            txtResumenBiometricoLogin.setText("Métricas del ingreso biométrico:\n" + resumenBiometrico);
        } else {
            txtResumenBiometricoLogin.setVisibility(View.GONE);
        }

        try {
            UsuarioRegistrado usuario = repositorioUsuario.cargarUsuario();
            if (usuario != null) {
                String fecha = !usuario.getFechaRegistro().isEmpty() ? usuario.getFechaRegistro() : "No registrada";
                String consentimiento = usuario.isConsentimientoAceptado() ? "Aceptado" : "No aceptado";
                String embeddings = usuario.tieneEmbeddingsCompletos()
                        ? "3 embeddings faciales cifrados con AES-256"
                        : "Embeddings biométricos incompletos";
                txtInfoPerfilCifrado.setText(
                        "Fecha de registro: " + fecha
                                + "\nConsentimiento informado: " + consentimiento
                                + "\nProtección local: " + embeddings
                );
            } else {
                txtInfoPerfilCifrado.setText("No se encontró un perfil cifrado almacenado en el dispositivo.");
            }
        } catch (BiometriaException e) {
            txtInfoPerfilCifrado.setText("Error al leer el perfil cifrado: " + e.getMessage());
        }
    }

    private void cargarRegistrosTelemetria() {
        txtRutaCsvMain.setText("Ruta: " + repositorioTelemetria.getRutaArchivo());
        try {
            List<String> registros = repositorioTelemetria.leerRegistrosLegibles();
            if (registros == null || registros.isEmpty()) {
                txtListaRegistrosMain.setText("Aún no hay registros de verificación.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            int limite = Math.min(registros.size(), MAX_REGISTROS_VISIBLES);
            for (int i = 0; i < limite; i++) {
                if (i > 0) {
                    sb.append("\n\n");
                }
                sb.append(i + 1).append(". ").append(registros.get(i));
            }
            txtListaRegistrosMain.setText(sb.toString());
        } catch (BiometriaException e) {
            txtListaRegistrosMain.setText("Error al leer registros CSV: " + e.getMessage());
        }
    }

    private void compartirCsv() {
        try {
            String csv = repositorioTelemetria.leerCsvCompleto();
            if (csv == null || csv.trim().isEmpty()) {
                mostrarMensaje("CSV Vacío", "Aún no hay registros de verificación para compartir.");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Telemetría Biométrica CSV");
            intent.putExtra(Intent.EXTRA_TEXT, csv);
            startActivity(Intent.createChooser(intent, "Compartir / Exportar CSV"));
        } catch (BiometriaException e) {
            mostrarMensaje("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    private void confirmarLimpiezaCsv() {
        new AlertDialog.Builder(this)
                .setTitle("Limpiar registros CSV")
                .setMessage("¿Desea eliminar todos los registros de telemetria_experimentos.csv?")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    try {
                        repositorioTelemetria.limpiarRegistros();
                        cargarRegistrosTelemetria();
                    } catch (BiometriaException e) {
                        mostrarMensaje("Error de Telemetría (" + e.getCodigoError() + ")", e.getMessage());
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void cerrarSesionYSalir() {
        GestorSesion.obtenerInstancia().cerrarSesion();
        irALogin();
    }

    private void confirmarEliminacionPerfil() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar perfil cifrado")
                .setMessage("Se eliminarán el usuario y sus 3 embeddings cifrados con AES-256. ¿Desea continuar?")
                .setPositiveButton("Eliminar y salir", (dialog, which) -> {
                    try {
                        repositorioUsuario.eliminarUsuario();
                        GestorSesion gestor = GestorSesion.obtenerInstancia();
                        gestor.reiniciarIntentos();
                        gestor.cerrarSesion();
                        irALogin();
                    } catch (BiometriaException e) {
                        mostrarMensaje("Error al eliminar perfil (" + e.getCodigoError() + ")", e.getMessage());
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void irALogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void mostrarMensaje(String titulo, String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton("Aceptar", null)
                .show();
    }
}
