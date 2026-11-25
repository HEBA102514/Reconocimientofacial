package co.edu.udistrital.reconocimientofacial.vista;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import co.edu.udistrital.reconocimientofacial.R;
import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.GestorSesion;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.persistencia.CifradorAES;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioUsuarioArchivo;

/**
 * Pantalla de registro de usuario con aceptacion obligatoria del consentimiento informado.
 * Guarda las credenciales cifradas localmente usando RepositorioUsuarioArchivo.
 */
public class RegistroActivity extends AppCompatActivity {

    private EditText edtRegistroUsuario;
    private EditText edtRegistroClave;
    private EditText edtRegistroConfirmarClave;
    private CheckBox chkConsentimiento;
    private TextView txtMensajeRegistro;

    private RepositorioUsuarioArchivo repositorioUsuario;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_registro);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootRegistro), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repositorioUsuario = new RepositorioUsuarioArchivo(this);
        inicializarVistas();
    }

    private void inicializarVistas() {
        edtRegistroUsuario = findViewById(R.id.edtRegistroUsuario);
        edtRegistroClave = findViewById(R.id.edtRegistroClave);
        edtRegistroConfirmarClave = findViewById(R.id.edtRegistroConfirmarClave);
        chkConsentimiento = findViewById(R.id.chkConsentimiento);
        txtMensajeRegistro = findViewById(R.id.txtMensajeRegistro);
        Button btnConfirmarRegistro = findViewById(R.id.btnConfirmarRegistro);
        Button btnVolverLogin = findViewById(R.id.btnVolverLogin);

        btnConfirmarRegistro.setOnClickListener(v -> registrarUsuario());
        btnVolverLogin.setOnClickListener(v -> finish());
    }

    private void registrarUsuario() {
        String usuario = edtRegistroUsuario.getText().toString().trim();
        String clave = edtRegistroClave.getText().toString();
        String confirmarClave = edtRegistroConfirmarClave.getText().toString();

        if (usuario.length() < 3) {
            mostrarError("El nombre de usuario debe tener al menos 3 caracteres.");
            return;
        }

        if (clave.length() < 4) {
            mostrarError("La contraseña debe tener al menos 4 caracteres.");
            return;
        }

        if (!clave.equals(confirmarClave)) {
            mostrarError("La contraseña y su confirmación no coinciden.");
            return;
        }

        if (!chkConsentimiento.isChecked()) {
            mostrarError("Debe aceptar el consentimiento informado para registrar sus datos.");
            return;
        }

        try {
            String hashClave = CifradorAES.sha256Hex(clave);
            String fechaRegistro = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date());

            UsuarioRegistrado nuevoUsuario = new UsuarioRegistrado(
                    usuario,
                    hashClave,
                    true,
                    fechaRegistro
            );

            repositorioUsuario.guardarUsuario(nuevoUsuario);
            GestorSesion.obtenerInstancia().reiniciarIntentos();

            Toast.makeText(this, "Usuario registrado correctamente.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (BiometriaException e) {
            mostrarError("No se pudo guardar el registro: " + e.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        txtMensajeRegistro.setText(mensaje);
        txtMensajeRegistro.setVisibility(View.VISIBLE);
    }
}
