package co.edu.udistrital.reconocimientofacial.modelo;

import java.util.Locale;

/**
 * Valida la cantidad de rostros detectados y si la orientacion facial
 * cumple con la pose esperada durante el registro o verificacion guiada.
 */
public class ValidadorPose {

    public static final float MAX_YAW_FRONTAL = 12.0f;
    public static final float MAX_ROLL_FRONTAL = 15.0f;
    public static final float MIN_YAW_GIRO = 15.0f;
    public static final float MAX_YAW_GIRO = 45.0f;

    /**
     * Verifica que exista exactamente un rostro en el fotograma.
     */
    public boolean validarCantidadRostros(int cantidadRostros) {
        return cantidadRostros == 1;
    }

    /**
     * Comprueba si los angulos de rotacion del rostro corresponden a la pose solicitada.
     */
    public boolean cumplePose(TipoPose poseEsperada, float yawY, float rollZ) {
        if (poseEsperada == null) {
            return false;
        }
        switch (poseEsperada) {
            case FRONTAL:
                return Math.abs(yawY) <= MAX_YAW_FRONTAL
                        && Math.abs(rollZ) <= MAX_ROLL_FRONTAL;
            case IZQUIERDA:
                return yawY >= MIN_YAW_GIRO && yawY <= MAX_YAW_GIRO;
            case DERECHA:
                return yawY <= -MIN_YAW_GIRO && yawY >= -MAX_YAW_GIRO;
            default:
                return false;
        }
    }

    /**
     * Genera un mensaje legible para orientar al usuario segun la deteccion actual.
     */
    public String obtenerGuia(int cantidadRostros, TipoPose poseEsperada, float yawY, float rollZ) {
        if (cantidadRostros <= 0) {
            return "No se detecta ningun rostro. Ubiquese frente a la camara.";
        }
        if (cantidadRostros > 1) {
            return "Se detectaron " + cantidadRostros + " rostros. Debe aparecer solo 1 persona.";
        }
        if (poseEsperada == null) {
            return "Pose no especificada.";
        }
        if (cumplePose(poseEsperada, yawY, rollZ)) {
            return "Pose " + poseEsperada.getEtiqueta() + " correcta. Mantenga quieto...";
        }
        return poseEsperada.getInstruccion()
                + String.format(Locale.US, " (Yaw: %.1f\u00B0)", yawY);
    }
}
