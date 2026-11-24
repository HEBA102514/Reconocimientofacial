package co.edu.udistrital.reconocimientofacial.vista;

import java.util.List;

import co.edu.udistrital.reconocimientofacial.modelo.Fotograma;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;

/**
 * Interfaz que define el contrato de la Vista en la arquitectura MVC.
 * Permite que ControladorBiometrico actualice la pantalla sin acoplarse a widgets de Android.
 */
public interface IVistaBiometrica {

    void mostrarFotogramaReferencia(Fotograma fotograma);

    void mostrarFotogramaValidacion(Fotograma fotograma);

    void mostrarResultado(ResultadoVerificacion resultado);

    void mostrarError(String titulo, String mensaje);

    void mostrarProgreso(boolean visible, String mensajeEstado);

    void mostrarHistorial(List<String> registros);
}
