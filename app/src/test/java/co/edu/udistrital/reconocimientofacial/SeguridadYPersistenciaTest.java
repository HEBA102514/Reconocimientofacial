package co.edu.udistrital.reconocimientofacial;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.RegistroTelemetria;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.persistencia.CifradorAES;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioTelemetriaCsv;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioUsuarioArchivo;

/**
 * Pruebas unitarias JUnit 4 para cifrado AES-CBC, persistencia cifrada de usuarios
 * con consentimiento informado y exportacion de telemetria experimental en CSV.
 */
public class SeguridadYPersistenciaTest {

    @Test
    public void cifradorAes_cifraYDescifraTextoConservandoContenido() throws BiometriaException {
        CifradorAES cifrador = new CifradorAES();
        String textoPlano = "perfil_biometrico_udistrital_2026";
        String clave = "clave_maestra_prueba";

        String cifrado = cifrador.cifrar(textoPlano, clave);

        assertTrue(cifrado.contains(":"));
        assertFalse(cifrado.contains(textoPlano));
        assertEquals(textoPlano, cifrador.descifrar(cifrado, clave));
    }

    @Test
    public void cifradorAes_generaCifradosDistintosParaMismoTextoPorIvAleatorio() throws BiometriaException {
        CifradorAES cifrador = new CifradorAES();
        String textoPlano = "mismo_mensaje_biometrico";
        String clave = "clave_iv_aleatorio";

        String c1 = cifrador.cifrar(textoPlano, clave);
        String c2 = cifrador.cifrar(textoPlano, clave);

        assertFalse(c1.equals(c2));
        assertEquals(textoPlano, cifrador.descifrar(c1, clave));
        assertEquals(textoPlano, cifrador.descifrar(c2, clave));
    }

    @Test(expected = BiometriaException.class)
    public void cifradorAes_rechazaClaveIncorrecta() throws BiometriaException {
        CifradorAES cifrador = new CifradorAES();
        String cifrado = cifrador.cifrar("dato_confidencial", "clave_correcta");
        cifrador.descifrar(cifrado, "clave_incorrecta");
    }

    @Test(expected = BiometriaException.class)
    public void repositorioUsuario_rechazaGuardarSinConsentimientoInformado() throws Exception {
        File archivoTemp = File.createTempFile("test_usuario_sin_consentimiento", ".dat");
        archivoTemp.deleteOnExit();

        RepositorioUsuarioArchivo repositorio = new RepositorioUsuarioArchivo(archivoTemp);
        UsuarioRegistrado usuario = new UsuarioRegistrado(
                "samuel.ramos",
                CifradorAES.sha256Hex("clave123"),
                false,
                "2026-11-24 23:00:00"
        );

        repositorio.guardarUsuario(usuario);
    }

    @Test
    public void repositorioUsuario_guardaCifradoYRecuperaTresEmbeddingsExactos() throws Exception {
        File archivoTemp = File.createTempFile("test_usuario_cifrado", ".dat");
        archivoTemp.deleteOnExit();

        RepositorioUsuarioArchivo repositorio = new RepositorioUsuarioArchivo(archivoTemp);
        String nombre = "samuel.ramos";
        String hash = CifradorAES.sha256Hex("ContrasenaSegura2026");

        UsuarioRegistrado original = new UsuarioRegistrado(
                nombre,
                hash,
                true,
                "2026-11-24 23:10:00"
        );
        float[] embFrontal = new float[]{0.125f, -0.5f, 0.875f, 0.25f};
        float[] embIzquierda = new float[]{-0.25f, 0.75f, 0.1f, -0.6f};
        float[] embDerecha = new float[]{0.4f, 0.2f, -0.8f, 0.15f};
        original.setEmbeddingFrontal(embFrontal);
        original.setEmbeddingIzquierda(embIzquierda);
        original.setEmbeddingDerecha(embDerecha);

        repositorio.guardarUsuario(original);

        // El archivo almacenado en disco debe estar cifrado y no exponer el nombre en texto plano
        String contenidoCrudo = new String(
                Files.readAllBytes(archivoTemp.toPath()),
                StandardCharsets.UTF_8
        );
        assertFalse(contenidoCrudo.contains(nombre));

        UsuarioRegistrado cargado = repositorio.cargarUsuario();
        assertNotNull(cargado);
        assertEquals(nombre, cargado.getNombreUsuario());
        assertEquals(hash, cargado.getHashClave());
        assertTrue(cargado.isConsentimientoAceptado());
        assertArrayEquals(embFrontal, cargado.getEmbeddingFrontal(), 0.0f);
        assertArrayEquals(embIzquierda, cargado.getEmbeddingIzquierda(), 0.0f);
        assertArrayEquals(embDerecha, cargado.getEmbeddingDerecha(), 0.0f);
    }

    @Test
    public void repositorioTelemetriaCsv_escribeEncabezadoYRegistros() throws Exception {
        File archivoTemp = File.createTempFile("test_telemetria", ".csv");
        archivoTemp.deleteOnExit();

        RepositorioTelemetriaCsv repositorio = new RepositorioTelemetriaCsv(archivoTemp);
        RegistroTelemetria r1 = new RegistroTelemetria(
                "samuel.ramos",
                "Genuino",
                "Buena",
                "Estandar",
                true,
                "PARPADEO",
                18L,
                34L,
                142.50,
                5.20,
                0.8912,
                0.70,
                "COINCIDE"
        );
        RegistroTelemetria r2 = new RegistroTelemetria(
                "samuel.ramos",
                "Impostor",
                "Baja",
                "Estricto",
                true,
                "GIRO_IZQUIERDA",
                24L,
                39L,
                148.10,
                5.20,
                0.4123,
                0.82,
                "RECHAZADO"
        );

        repositorio.guardarRegistro(r1);
        repositorio.guardarRegistro(r2);

        List<String> legibles = repositorio.leerRegistrosLegibles();
        assertEquals(2, legibles.size());

        String csvCompleto = repositorio.leerCsvCompleto();
        assertTrue(csvCompleto.startsWith(RegistroTelemetria.ENCABEZADO_CSV));
    }
}
