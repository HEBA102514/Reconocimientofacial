package co.edu.udistrital.reconocimientofacial.vista;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import co.edu.udistrital.reconocimientofacial.modelo.TipoPose;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.persistencia.CifradorAES;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioUsuarioArchivo;

/**
 * Pantalla de registro de usuario con aceptacion obligatoria del consentimiento informado
 * y captura facial guiada de 3 poses.
 * Guarda las credenciales y embeddings cifrados localmente usando RepositorioUsuarioArchivo.
 */
public class RegistroActivity extends AppCompatActivity {

    private EditText edtRegistroUsuario;
    private EditText edtRegistroClave;
    private EditText edtRegistroConfirmarClave;
    private CheckBox chkConsentimiento;
    private TextView txtEstadoPosesRegistro;
    private TextView txtMensajeRegistro;

    private RepositorioUsuarioArchivo repositorioUsuario;
    private float[] embeddingFrontal;
    private float[] embeddingIzquierda;
    private float[] embeddingDerecha;

    private final ActivityResultLauncher<Intent> lanzadorCamaraGuiada =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    float[] frontal = data.getFloatArrayExtra(CamaraGuiadaActivity.EXTRA_EMBEDDING_FRONTAL);
                    float[] izquierda = data.getFloatArrayExtra(CamaraGuiadaActivity.EXTRA_EMBEDDING_IZQUIERDA);
                    float[] derecha = data.getFloatArrayExtra(CamaraGuiadaActivity.EXTRA_EMBEDDING_DERECHA);
                    String desafio = data.getStringExtra(CamaraGuiadaActivity.EXTRA_DESAFIO_LIVENESS);
                    if (desafio == null || desafio.trim().isEmpty()) {
                        desafio = "ACTIVO";
                    }
                    if (frontal != null && izquierda != null && derecha != null) {
                        embeddingFrontal = frontal;
                        embeddingIzquierda = izquierda;
                        embeddingDerecha = derecha;
                        txtEstadoPosesRegistro.setText("Liveness (" + desafio + ") + 3 poses capturadas ✓");
                        txtEstadoPosesRegistro.setTextColor(Color.parseColor("#2E7D32"));
                        txtMensajeRegistro.setVisibility(View.GONE);
                    }
                }
            });

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
        txtEstadoPosesRegistro = findViewById(R.id.txtEstadoPosesRegistro);
        txtMensajeRegistro = findViewById(R.id.txtMensajeRegistro);
        Button btnCapturarTresPoses = findViewById(R.id.btnCapturarTresPoses);
        Button btnConfirmarRegistro = findViewById(R.id.btnConfirmarRegistro);
        Button btnVolverLogin = findViewById(R.id.btnVolverLogin);

        btnCapturarTresPoses.setOnClickListener(v -> abrirCamaraGuiadaRegistro());
        btnConfirmarRegistro.setOnClickListener(v -> registrarUsuario());
        btnVolverLogin.setOnClickListener(v -> finish());
    }

    private void abrirCamaraGuiadaRegistro() {
        Intent intent = new Intent(this, CamaraGuiadaActivity.class);
        intent.putExtra(CamaraGuiadaActivity.EXTRA_MODO, CamaraGuiadaActivity.MODO_REGISTRO);
        lanzadorCamaraGuiada.launch(intent);
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

        if (embeddingFrontal == null || embeddingIzquierda == null || embeddingDerecha == null) {
            mostrarError("Debe completar la prueba de vida y capturar las 3 poses faciales antes de registrarse.");
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
            nuevoUsuario.guardarEmbedding(TipoPose.FRONTAL, embeddingFrontal);
            nuevoUsuario.guardarEmbedding(TipoPose.IZQUIERDA, embeddingIzquierda);
            nuevoUsuario.guardarEmbedding(TipoPose.DERECHA, embeddingDerecha);

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
