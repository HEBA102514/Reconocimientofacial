package co.edu.udistrital.reconocimientofacial.excepciones;

/**
 * Se lanza cuando aparecen dos o mas rostros en una captura destinada a verificacion 1:1.
 */
public class MultiplesRostrosException extends BiometriaException {

    private final int cantidadDetectada;

    public MultiplesRostrosException(String etiquetaImagen, int cantidadDetectada) {
        super(
                "BIO-002",
                "Se detectaron " + cantidadDetectada + " rostros en la imagen de " + etiquetaImagen
                        + ". La verificacion 1:1 exige un unico rostro por captura."
        );
        this.cantidadDetectada = cantidadDetectada;
    }

    public int getCantidadDetectada() {
        return cantidadDetectada;
    }
}
