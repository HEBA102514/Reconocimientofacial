package co.edu.udistrital.reconocimientofacial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import co.edu.udistrital.reconocimientofacial.modelo.GestorSesion;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;

/**
 * Pruebas unitarias deterministas para GestorSesion: autenticacion por contrasena,
 * inicio de sesion biometrico 1:1, bloqueo de 30 segundos tras 3 fallos mixtos,
 * liberacion por tiempo, cierre de sesion y reinicio de intentos.
 */
public class GestorSesionTest {

    private GestorSesion gestor;
    private UsuarioRegistrado usuario;

    @Before
    public void setUp() {
        gestor = new GestorSesion();
        usuario = new UsuarioRegistrado("samuel", "hash_clave_segura", true, "2026-11-24");
    }

    @Test
    public void autenticarContrasenaCorrecta_activaSesionYRegistraMetodo() {
        boolean acceso = gestor.autenticarEnTiempo(usuario, "samuel", "hash_clave_segura", 1_000L);

        assertTrue(acceso);
        assertTrue(gestor.isSesionActiva());
        assertEquals("samuel", gestor.getUsuarioActivo());
        assertEquals("Contraseña", gestor.getMetodoAcceso());
    }

    @Test
    public void iniciarSesionBiometricaExitosa_activaSesionYReiniciaIntentos() {
        assertFalse(gestor.autenticarEnTiempo(usuario, "samuel", "clave_incorrecta", 500L));
        assertFalse(gestor.iniciarSesionBiometricaEnTiempo(usuario, false, "Similitud insuficiente", 800L));
        assertEquals(2, gestor.getIntentosFallidos());

        boolean acceso = gestor.iniciarSesionBiometricaEnTiempo(usuario, true, "OK", 1_000L);

        assertTrue(acceso);
        assertTrue(gestor.isSesionActiva());
        assertEquals(0, gestor.getIntentosFallidos());
        assertEquals("Biometría Facial 1:1", gestor.getMetodoAcceso());
        assertEquals("OK", gestor.getUltimoResumenBiometrico());
    }

    @Test
    public void tresFallosMixtos_activanBloqueoDeTreintaSegundos() {
        assertFalse(gestor.autenticarEnTiempo(usuario, "samuel", "clave_incorrecta", 5_000L));
        gestor.registrarFalloAccesoEnTiempo(7_000L);
        assertFalse(gestor.iniciarSesionBiometricaEnTiempo(usuario, false, "Similitud baja", 10_000L));

        assertTrue(gestor.estaBloqueadoEnTiempo(10_000L));
        assertEquals(30L, gestor.obtenerSegundosRestantesBloqueoEnTiempo(10_000L));
        assertFalse(gestor.autenticarEnTiempo(usuario, "samuel", "hash_clave_segura", 25_000L));
        assertFalse(gestor.iniciarSesionBiometricaEnTiempo(usuario, true, "OK", 25_000L));
        assertFalse(gestor.isSesionActiva());
    }

    @Test
    public void expiracionDeTreintaSegundos_liberaBloqueoYPermiteAcceso() {
        gestor.autenticarEnTiempo(usuario, "samuel", "clave_incorrecta", 5_000L);
        gestor.registrarFalloAccesoEnTiempo(7_000L);
        gestor.iniciarSesionBiometricaEnTiempo(usuario, false, "Similitud baja", 10_000L);
        assertTrue(gestor.estaBloqueadoEnTiempo(10_000L));

        assertFalse(gestor.estaBloqueadoEnTiempo(40_001L));
        assertEquals(0, gestor.getIntentosFallidos());
        assertTrue(gestor.autenticarEnTiempo(usuario, "samuel", "hash_clave_segura", 40_001L));
        assertTrue(gestor.isSesionActiva());
    }

    @Test
    public void cerrarSesion_limpiaEstadoDeSesionActiva() {
        assertTrue(gestor.autenticarEnTiempo(usuario, "samuel", "hash_clave_segura", 1_000L));
        assertTrue(gestor.isSesionActiva());

        gestor.cerrarSesion();

        assertFalse(gestor.isSesionActiva());
        assertNull(gestor.getUsuarioActivo());
        assertNull(gestor.getMetodoAcceso());
    }

    @Test
    public void reiniciarIntentos_limpiaContadorYBloqueo() {
        gestor.autenticarEnTiempo(usuario, "samuel", "clave_incorrecta", 5_000L);
        gestor.registrarFalloAccesoEnTiempo(7_000L);
        gestor.iniciarSesionBiometricaEnTiempo(usuario, false, "Similitud baja", 10_000L);
        assertTrue(gestor.estaBloqueadoEnTiempo(10_000L));

        gestor.reiniciarIntentos();

        assertEquals(0, gestor.getIntentosFallidos());
        assertFalse(gestor.estaBloqueadoEnTiempo(10_000L));
        assertFalse(gestor.estaBloqueado());
    }
}
