package co.edu.udistrital.reconocimientofacial.vista;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

import co.edu.udistrital.reconocimientofacial.MainActivity;
import co.edu.udistrital.reconocimientofacial.R;
import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.GestorSesion;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.persistencia.CifradorAES;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioUsuarioArchivo;

/**
 * Pantalla de inicio de sesion del sistema biometrico.
 * Valida credenciales contra el archivo cifrado local y aplica bloqueo temporal
 * tras tres intentos fallidos.
 */
public class LoginActivity extends AppCompatActivity {

    private TextView txtEstadoRegistroLogin;
    private EditText edtUsuarioLogin;
    private EditText edtClaveLogin;
    private TextView txtMensajeLogin;
    private Button btnIniciarSesion;

    private GestorSesion gestorSesion;
    private RepositorioUsuarioArchivo repositorioUsuario;
    private CountDownTimer temporizadorBloqueo;

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
            iniciarContadorBloqueo();
        } else {
            cancelarTemporizador();
            btnIniciarSesion.setEnabled(true);
            txtMensajeLogin.setVisibility(View.GONE);
        }
    }

    private void inicializarVistas() {
        txtEstadoRegistroLogin = findViewById(R.id.txtEstadoRegistroLogin);
        edtUsuarioLogin = findViewById(R.id.edtUsuarioLogin);
        edtClaveLogin = findViewById(R.id.edtClaveLogin);
        txtMensajeLogin = findViewById(R.id.txtMensajeLogin);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        Button btnIrARegistro = findViewById(R.id.btnIrARegistro);

        btnIniciarSesion.setOnClickListener(v -> intentarIniciarSesion());
        btnIrARegistro.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegistroActivity.class);
            startActivity(intent);
        });
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
            } else {
                txtEstadoRegistroLogin.setText("Sin usuario registrado. Cree una cuenta primero.");
            }
        } catch (BiometriaException e) {
            txtEstadoRegistroLogin.setText("Error al leer perfil cifrado: " + e.getMessage());
        }
    }

    private void intentarIniciarSesion() {
        if (gestorSesion.estaBloqueado()) {
            iniciarContadorBloqueo();
            return;
        }

        if (!repositorioUsuario.existeUsuarioRegistrado()) {
            mostrarMensaje("Debe registrar un usuario antes de iniciar sesion.");
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
                mostrarMensaje("No se encontro un perfil valido. Registre una cuenta primero.");
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

    private void iniciarContadorBloqueo() {
        cancelarTemporizador();

        long segundos = gestorSesion.obtenerSegundosRestantesBloqueo();
        if (segundos <= 0L) {
            btnIniciarSesion.setEnabled(true);
            return;
        }

        btnIniciarSesion.setEnabled(false);
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
                btnIniciarSesion.setEnabled(true);
                mostrarMensaje("Bloqueo temporal finalizado. Puede intentar de nuevo.");
                temporizadorBloqueo = null;
            }
        }.start();
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
