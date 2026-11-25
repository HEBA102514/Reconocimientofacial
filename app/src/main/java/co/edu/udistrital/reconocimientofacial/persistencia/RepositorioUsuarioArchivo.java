package co.edu.udistrital.reconocimientofacial.persistencia;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;

/**
 * Repositorio local que persiste el usuario registrado y sus embeddings faciales
 * en un archivo de texto cifrado con AES usando java.io.
 */
public class RepositorioUsuarioArchivo {

    private static final String NOMBRE_ARCHIVO = "usuario_cifrado.dat";
    private static final String CLAVE_ARCHIVO = "UD_POO_2026_ClaveLocal_AES";
    private static final String MARCADOR_VACIO = "VACIO";

    private final File archivoUsuario;
    private final CifradorAES cifrador;

    public RepositorioUsuarioArchivo(Context context) {
        this(new File(context.getFilesDir(), NOMBRE_ARCHIVO), new CifradorAES());
    }

    public RepositorioUsuarioArchivo(File archivoUsuario) {
        this(archivoUsuario, new CifradorAES());
    }

    public RepositorioUsuarioArchivo(File archivoUsuario, CifradorAES cifrador) {
        this.archivoUsuario = archivoUsuario;
        this.cifrador = cifrador != null ? cifrador : new CifradorAES();
    }

    public void guardarUsuario(UsuarioRegistrado usuario) throws BiometriaException {
        if (usuario == null) {
            throw new BiometriaException("BIO-USU-01", "Los datos del usuario no pueden ser nulos.");
        }
        if (!usuario.isConsentimientoAceptado()) {
            throw new BiometriaException(
                    "BIO-USU-01",
                    "Debe aceptar el consentimiento informado antes de registrar datos biometricos."
            );
        }
        if (archivoUsuario == null) {
            throw new BiometriaException("BIO-IO-04", "No se especifico un archivo de usuario valido.");
        }

        StringBuilder plano = new StringBuilder();
        plano.append(limpiarTexto(usuario.getNombreUsuario())).append("\n");
        plano.append(limpiarTexto(usuario.getHashClave())).append("\n");
        plano.append(Boolean.toString(usuario.isConsentimientoAceptado())).append("\n");
        plano.append(limpiarTexto(usuario.getFechaRegistro())).append("\n");
        plano.append(vectorATexto(usuario.getEmbeddingFrontal())).append("\n");
        plano.append(vectorATexto(usuario.getEmbeddingIzquierda())).append("\n");
        plano.append(vectorATexto(usuario.getEmbeddingDerecha()));

        String contenidoCifrado = cifrador.cifrar(plano.toString(), CLAVE_ARCHIVO);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivoUsuario, false))) {
            writer.write(contenidoCifrado);
        } catch (IOException e) {
            throw new BiometriaException(
                    "BIO-IO-04",
                    "No se pudo guardar el archivo de usuario: " + e.getMessage(),
                    e
            );
        }
    }

    public UsuarioRegistrado cargarUsuario() throws BiometriaException {
        if (!existeUsuarioRegistrado()) {
            return null;
        }

        StringBuilder contenido = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(archivoUsuario))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                contenido.append(linea);
            }
        } catch (IOException e) {
            throw new BiometriaException(
                    "BIO-IO-05",
                    "No se pudo leer el archivo de usuario: " + e.getMessage(),
                    e
            );
        }

        String textoCifrado = contenido.toString().trim();
        if (textoCifrado.isEmpty()) {
            return null;
        }

        String plano = cifrador.descifrar(textoCifrado, CLAVE_ARCHIVO);
        String[] lineas = plano.split("\n", -1);
        if (lineas.length < 7) {
            throw new BiometriaException("BIO-USU-02", "El archivo de usuario tiene un formato invalido.");
        }

        boolean consentimiento = Boolean.parseBoolean(lineas[2].trim());
        UsuarioRegistrado usuario = new UsuarioRegistrado(
                lineas[0],
                lineas[1],
                consentimiento,
                lineas[3]
        );
        usuario.setEmbeddingFrontal(textoAVector(lineas[4]));
        usuario.setEmbeddingIzquierda(textoAVector(lineas[5]));
        usuario.setEmbeddingDerecha(textoAVector(lineas[6]));
        return usuario;
    }

    public boolean existeUsuarioRegistrado() {
        return archivoUsuario != null && archivoUsuario.exists() && archivoUsuario.length() > 0;
    }

    public void eliminarUsuario() throws BiometriaException {
        if (archivoUsuario != null && archivoUsuario.exists() && !archivoUsuario.delete()) {
            throw new BiometriaException("BIO-IO-06", "No fue posible eliminar el archivo de usuario.");
        }
    }

    private String limpiarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\r", " ").replace("\n", " ").trim();
    }

    private String vectorATexto(float[] vector) {
        if (vector == null || vector.length == 0) {
            return MARCADOR_VACIO;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(Float.toString(vector[i]));
        }
        return sb.toString();
    }

    private float[] textoAVector(String texto) throws BiometriaException {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty() || MARCADOR_VACIO.equals(limpio)) {
            return null;
        }
        String[] partes = limpio.split(",");
        float[] vector = new float[partes.length];
        try {
            for (int i = 0; i < partes.length; i++) {
                vector[i] = Float.parseFloat(partes[i].trim());
            }
        } catch (NumberFormatException e) {
            throw new BiometriaException(
                    "BIO-USU-03",
                    "Formato invalido en el vector biometrico almacenado.",
                    e
            );
        }
        return vector;
    }
}
