package co.edu.udistrital.reconocimientofacial.persistencia;

import java.util.List;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;

/**
 * Interfaz para la persistencia de resultados de verificacion biometrica.
 */
public interface IRepositorioHistorial {

    void guardarResultado(ResultadoVerificacion resultado) throws BiometriaException;

    List<String> leerHistorial() throws BiometriaException;

    void limpiarHistorial() throws BiometriaException;
}
