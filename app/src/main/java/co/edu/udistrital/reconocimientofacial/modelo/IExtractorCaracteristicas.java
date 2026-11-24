package co.edu.udistrital.reconocimientofacial.modelo;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;

/**
 * Interfaz para la Etapa 3 del flujo biometrico (Ma et al., 2026; El Fadel, 2025):
 * Extraccion de vectores de caracteristicas (embeddings) mediante red neuronal ligera.
 */
public interface IExtractorCaracteristicas {

    float[] extraerEmbedding(Fotograma fotograma) throws BiometriaException;

    int getDimensionEmbedding();

    void cerrar();
}
