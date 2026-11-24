package co.edu.udistrital.reconocimientofacial.excepciones;

/**
 * Se lanza cuando el archivo del modelo ligero MobileFaceNet (.tflite) no puede cargarse
 * o falla durante la inferencia en el dispositivo.
 */
public class ModeloNoDisponibleException extends BiometriaException {

    public ModeloNoDisponibleException(String mensaje) {
        super("BIO-004", mensaje);
    }

    public ModeloNoDisponibleException(String mensaje, Throwable causa) {
        super("BIO-004", mensaje, causa);
    }
}
