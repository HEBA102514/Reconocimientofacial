package co.edu.udistrital.reconocimientofacial.modelo;

import android.graphics.Bitmap;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;

/**
 * Interfaz para la Etapa 2 del flujo biometrico (El Fadel, 2025):
 * Deteccion de rostro, extraccion de metadatos de pose/apertura ocular y recorte facial.
 */
public interface IDetectorRostro {

    interface CallbackDeteccion {
        void onRostroDetectado(Fotograma fotograma);
        void onErrorDeteccion(BiometriaException excepcion);
    }

    void detectarYRecortar(Bitmap imagenOriginal, String etiquetaImagen, CallbackDeteccion callback);

    void cerrar();
}
