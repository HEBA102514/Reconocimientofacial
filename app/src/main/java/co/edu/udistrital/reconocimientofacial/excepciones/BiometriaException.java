package co.edu.udistrital.reconocimientofacial.excepciones;

/**
 * Excepcion base comprobada (checked exception) para errores del flujo de
 * verificacion biometrica facial 1:1.
 * Demuestra herencia de Exception y encapsulamiento del codigo de error.
 */
public class BiometriaException extends Exception {

    private final String codigoError;

    public BiometriaException(String codigoError, String mensaje) {
        super(mensaje);
        this.codigoError = codigoError;
    }

    public BiometriaException(String codigoError, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigoError = codigoError;
    }

    public String getCodigoError() {
        return codigoError;
    }
}
