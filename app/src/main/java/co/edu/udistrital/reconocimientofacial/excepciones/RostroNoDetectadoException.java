package co.edu.udistrital.reconocimientofacial.excepciones;

/**
 * Se lanza cuando ML Kit Face Detection no encuentra ningun rostro en la imagen capturada.
 */
public class RostroNoDetectadoException extends BiometriaException {

    public RostroNoDetectadoException(String etiquetaImagen) {
        super(
                "BIO-001",
                "No se detecto ningun rostro en la imagen de " + etiquetaImagen
                        + ". Asegure buena iluminacion y encuadre frontal."
        );
    }
}
