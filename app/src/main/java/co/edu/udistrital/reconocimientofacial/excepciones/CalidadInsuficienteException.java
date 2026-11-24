package co.edu.udistrital.reconocimientofacial.excepciones;

/**
 * Se lanza cuando las condiciones de la captura (angulos de pose Euler o apertura de ojos)
 * no superan el control de calidad biometrico definido por El Fadel (2025).
 */
public class CalidadInsuficienteException extends BiometriaException {

    public CalidadInsuficienteException(String detalleCalidad) {
        super("BIO-003", detalleCalidad);
    }
}
