package co.edu.udistrital.reconocimientofacial.modelo;

import java.util.Locale;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.excepciones.CalidadInsuficienteException;

/**
 * Subclase polimorfica de ValidadorFacial que minimiza la Tasa de Falsa Aceptacion (FAR)
 * aplicando un umbral estricto (tau = 0.83) calibrado en B6 para lograr FAR = 0.00%
 * bloqueando el ataque de spoofing de 0.8208, y exigiendo control de pose frontal y
 * ojos abiertos sobre el Fotograma (El Fadel, 2025).
 */
public class ValidadorEstricto extends ValidadorFacial {

    public static final double UMBRAL_ESTRICTO = 0.83;
    public static final float MAX_ANGULO_GRADOS = 18.0f;
    public static final float MIN_PROB_OJOS_ABIERTOS = 0.50f;

    @Override
    protected void validarCalidadFotograma(Fotograma fotograma, String etiqueta) throws BiometriaException {
        float yaw = Math.abs(fotograma.getAnguloYawY());
        float roll = Math.abs(fotograma.getAnguloRollZ());

        if (yaw > MAX_ANGULO_GRADOS || roll > MAX_ANGULO_GRADOS) {
            throw new CalidadInsuficienteException(String.format(
                    Locale.US,
                    "Control de pose rechazado en %s: giro lateral (%.1f°) o inclinacion (%.1f°) supera el maximo permitido de %.0f°.",
                    etiqueta,
                    yaw,
                    roll,
                    MAX_ANGULO_GRADOS
            ));
        }

        float ojoIzq = fotograma.getProbOjoIzquierdoAbierto();
        float ojoDer = fotograma.getProbOjoDerechoAbierto();

        if (ojoIzq >= 0.0f && ojoDer >= 0.0f) {
            if (ojoIzq < MIN_PROB_OJOS_ABIERTOS || ojoDer < MIN_PROB_OJOS_ABIERTOS) {
                throw new CalidadInsuficienteException(String.format(
                        Locale.US,
                        "Control de presencia rechazado en %s: se requiere mantener ambos ojos abiertos (Izq: %.0f%%, Der: %.0f%%).",
                        etiqueta,
                        ojoIzq * 100.0f,
                        ojoDer * 100.0f
                ));
            }
        }
    }

    @Override
    public double getUmbral() {
        return UMBRAL_ESTRICTO;
    }

    @Override
    public String getNombreModo() {
        return "Estricto (Alta Seguridad + Pose/Ojos)";
    }
}
