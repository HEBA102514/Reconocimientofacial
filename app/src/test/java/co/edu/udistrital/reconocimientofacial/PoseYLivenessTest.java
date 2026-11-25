package co.edu.udistrital.reconocimientofacial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import co.edu.udistrital.reconocimientofacial.modelo.TipoPose;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorPose;
import co.edu.udistrital.reconocimientofacial.modelo.VerificadorLiveness;

/**
 * Pruebas unitarias JUnit 4 para la validacion de poses faciales (ValidadorPose)
 * y la prueba de vida activa con control temporal (VerificadorLiveness).
 */
public class PoseYLivenessTest {

    @Test
    public void validadorPose_validaExactamenteUnRostro() {
        ValidadorPose validador = new ValidadorPose();

        assertFalse(validador.validarCantidadRostros(0));
        assertTrue(validador.validarCantidadRostros(1));
        assertFalse(validador.validarCantidadRostros(2));
    }

    @Test
    public void validadorPose_evaluaRangosFrontalIzquierdaYDerecha() {
        ValidadorPose validador = new ValidadorPose();

        // FRONTAL: |yaw| <= 12 y |roll| <= 15
        assertTrue(validador.cumplePose(TipoPose.FRONTAL, 0.0f, 5.0f));
        assertFalse(validador.cumplePose(TipoPose.FRONTAL, 20.0f, 0.0f));

        // IZQUIERDA: 15 <= yaw <= 45
        assertTrue(validador.cumplePose(TipoPose.IZQUIERDA, 22.0f, 0.0f));
        assertFalse(validador.cumplePose(TipoPose.IZQUIERDA, -22.0f, 0.0f));

        // DERECHA: -45 <= yaw <= -15
        assertTrue(validador.cumplePose(TipoPose.DERECHA, -22.0f, 0.0f));
        assertFalse(validador.cumplePose(TipoPose.DERECHA, 5.0f, 0.0f));
    }

    @Test
    public void livenessParpadeo_superaConSecuenciaAbiertoCerradoAbierto() {
        VerificadorLiveness verificador = new VerificadorLiveness(
                VerificadorLiveness.Desafio.PARPADEO,
                1_000L
        );

        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.90f, 0.90f, 1_500L)
        );
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.20f, 0.20f, 2_500L)
        );
        assertEquals(
                VerificadorLiveness.Estado.SUPERADO,
                verificador.evaluarFotograma(1, 0.0f, 0.90f, 0.90f, 3_500L)
        );
        assertTrue(verificador.isSuperado());
    }

    @Test
    public void livenessParpadeo_expiraAnteFotoEstaticaSinParpadeo() {
        VerificadorLiveness verificador = new VerificadorLiveness(
                VerificadorLiveness.Desafio.PARPADEO,
                1_000L
        );

        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.95f, 0.95f, 2_000L)
        );
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.95f, 0.95f, 6_000L)
        );
        assertEquals(
                VerificadorLiveness.Estado.EXPIRADO,
                verificador.evaluarFotograma(1, 0.0f, 0.95f, 0.95f, 9_500L)
        );
        assertTrue(verificador.isExpirado());
    }

    @Test
    public void livenessGiroIzquierda_exigeRostroFrontalPrevioAlGiro() {
        VerificadorLiveness verificador = new VerificadorLiveness(
                VerificadorLiveness.Desafio.GIRO_IZQUIERDA,
                1_000L
        );

        // Iniciar ya girado sin haber pasado por frontal no supera el desafio
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 25.0f, 0.90f, 0.90f, 1_500L)
        );
        // Pasa por posicion frontal
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.90f, 0.90f, 2_500L)
        );
        // Luego gira a la izquierda (>= 16 grados)
        assertEquals(
                VerificadorLiveness.Estado.SUPERADO,
                verificador.evaluarFotograma(1, 22.0f, 0.90f, 0.90f, 3_500L)
        );
        assertTrue(verificador.isSuperado());
    }

    @Test
    public void livenessGiroDerecha_superaConTransicionFrontalADerecha() {
        VerificadorLiveness verificador = new VerificadorLiveness(
                VerificadorLiveness.Desafio.GIRO_DERECHA,
                1_000L
        );

        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.90f, 0.90f, 1_500L)
        );
        assertEquals(
                VerificadorLiveness.Estado.SUPERADO,
                verificador.evaluarFotograma(1, -22.0f, 0.90f, 0.90f, 3_000L)
        );
        assertTrue(verificador.isSuperado());
    }

    @Test
    public void liveness_ignoraFotogramasConCeroOMultiplesRostros() {
        VerificadorLiveness verificador = new VerificadorLiveness(
                VerificadorLiveness.Desafio.PARPADEO,
                1_000L
        );

        // Fotogramas invalidos al inicio (0 y 2 rostros) no deben hacer avanzar el estado
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(0, 0.0f, 0.90f, 0.90f, 1_500L)
        );
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(2, 0.0f, 0.90f, 0.90f, 2_000L)
        );

        // Con 1 rostro avanza hasta ojos cerrados
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.90f, 0.90f, 2_500L)
        );
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(1, 0.0f, 0.20f, 0.20f, 3_000L)
        );

        // El cierre del parpadeo con 0 o 2 rostros debe ser ignorado
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(0, 0.0f, 0.90f, 0.90f, 3_500L)
        );
        assertEquals(
                VerificadorLiveness.Estado.EN_PROGRESO,
                verificador.evaluarFotograma(2, 0.0f, 0.90f, 0.90f, 4_000L)
        );
        assertFalse(verificador.isSuperado());
    }
}
