package co.edu.udistrital.reconocimientofacial.controlador;

import android.graphics.Bitmap;

import java.util.List;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.Fotograma;
import co.edu.udistrital.reconocimientofacial.modelo.IDetectorRostro;
import co.edu.udistrital.reconocimientofacial.modelo.IExtractorCaracteristicas;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;
import co.edu.udistrital.reconocimientofacial.modelo.UsuarioRegistrado;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorFacial;
import co.edu.udistrital.reconocimientofacial.persistencia.IRepositorioHistorial;
import co.edu.udistrital.reconocimientofacial.vista.IVistaBiometrica;

/**
 * Controlador principal bajo el patron MVC.
 * Coordina las 4 etapas del flujo biometrico (El Fadel, 2025; Ma et al., 2026):
 * 1. Recibe la imagen capturada desde la Vista.
 * 2. Invoca IDetectorRostro (ML Kit) para obtener el Fotograma recortado.
 * 3. Invoca IExtractorCaracteristicas (MobileFaceNet TFLite) para calcular el embedding.
 * 4. Invoca polimorficamente ValidadorFacial para decidir la verificacion 1:1 y persiste el resultado.
 */
public class ControladorBiometrico {

    private final IDetectorRostro detectorRostro;
    private final IExtractorCaracteristicas extractorCaracteristicas;
    private ValidadorFacial validadorActual;
    private final IRepositorioHistorial repositorioHistorial;
    private final IVistaBiometrica vista;

    private Fotograma fotogramaReferencia;
    private Fotograma fotogramaValidacion;

    public ControladorBiometrico(
            IDetectorRostro detectorRostro,
            IExtractorCaracteristicas extractorCaracteristicas,
            ValidadorFacial validadorInicial,
            IRepositorioHistorial repositorioHistorial,
            IVistaBiometrica vista
    ) {
        this.detectorRostro = detectorRostro;
        this.extractorCaracteristicas = extractorCaracteristicas;
        this.validadorActual = validadorInicial;
        this.repositorioHistorial = repositorioHistorial;
        this.vista = vista;
    }

    /**
     * Cambia en tiempo de ejecucion la estrategia polimorfica de validacion
     * (por ejemplo, entre ValidadorEstandar y ValidadorEstricto).
     */
    public void setValidadorActual(ValidadorFacial nuevoValidador) {
        if (nuevoValidador != null) {
            this.validadorActual = nuevoValidador;
        }
    }

    public ValidadorFacial getValidadorActual() {
        return validadorActual;
    }

    public void procesarImagenReferencia(Bitmap imagen) {
        vista.mostrarProgreso(true, "Detectando rostro en imagen de Referencia...");
        detectorRostro.detectarYRecortar(imagen, "Referencia", new IDetectorRostro.CallbackDeteccion() {
            @Override
            public void onRostroDetectado(Fotograma fotograma) {
                try {
                    extractorCaracteristicas.extraerEmbedding(fotograma);
                    fotogramaReferencia = fotograma;
                    vista.mostrarProgreso(false, "Referencia lista.");
                    vista.mostrarFotogramaReferencia(fotograma);
                } catch (BiometriaException e) {
                    vista.mostrarProgreso(false, "");
                    vista.mostrarError("Error en Extraccion (" + e.getCodigoError() + ")", e.getMessage());
                }
            }

            @Override
            public void onErrorDeteccion(BiometriaException excepcion) {
                vista.mostrarProgreso(false, "");
                vista.mostrarError("Error en Referencia (" + excepcion.getCodigoError() + ")", excepcion.getMessage());
            }
        });
    }

    public void procesarImagenValidacion(Bitmap imagen) {
        vista.mostrarProgreso(true, "Detectando rostro en imagen de Validacion...");
        detectorRostro.detectarYRecortar(imagen, "Validacion", new IDetectorRostro.CallbackDeteccion() {
            @Override
            public void onRostroDetectado(Fotograma fotograma) {
                try {
                    extractorCaracteristicas.extraerEmbedding(fotograma);
                    fotogramaValidacion = fotograma;
                    vista.mostrarProgreso(false, "Validacion lista.");
                    vista.mostrarFotogramaValidacion(fotograma);
                } catch (BiometriaException e) {
                    vista.mostrarProgreso(false, "");
                    vista.mostrarError("Error en Extraccion (" + e.getCodigoError() + ")", e.getMessage());
                }
            }

            @Override
            public void onErrorDeteccion(BiometriaException excepcion) {
                vista.mostrarProgreso(false, "");
                vista.mostrarError("Error en Validacion (" + excepcion.getCodigoError() + ")", excepcion.getMessage());
            }
        });
    }

    public void ejecutarVerificacion() {
        if (fotogramaReferencia == null) {
            vista.mostrarError("Datos incompletos", "Primero capture o cargue la imagen de Referencia.");
            return;
        }
        if (fotogramaValidacion == null) {
            vista.mostrarError("Datos incompletos", "Primero capture o cargue la imagen de Validacion.");
            return;
        }

        try {
            ResultadoVerificacion resultado = validadorActual.verificar(fotogramaReferencia, fotogramaValidacion);
            repositorioHistorial.guardarResultado(resultado);
            vista.mostrarResultado(resultado);
        } catch (BiometriaException e) {
            vista.mostrarError("Verificacion Rechazada (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    /**
     * Ejecuta la verificacion 1:1 comparando el fotograma de validacion contra los tres
     * embeddings almacenados en el perfil del usuario registrado.
     */
    public ResultadoVerificacion ejecutarVerificacionConPerfil(
            UsuarioRegistrado usuario,
            Fotograma fotogramaValidacion
    ) {
        if (usuario == null || fotogramaValidacion == null) {
            vista.mostrarError(
                    "Datos incompletos",
                    "Se requiere un usuario registrado y un fotograma de validacion."
            );
            return null;
        }

        try {
            ResultadoVerificacion resultado = validadorActual.verificarContraPerfil(usuario, fotogramaValidacion);
            repositorioHistorial.guardarResultado(resultado);
            vista.mostrarResultado(resultado);
            return resultado;
        } catch (BiometriaException e) {
            vista.mostrarError("Verificacion Rechazada (" + e.getCodigoError() + ")", e.getMessage());
            return null;
        }
    }

    public void consultarHistorial() {
        try {
            List<String> registros = repositorioHistorial.leerHistorial();
            vista.mostrarHistorial(registros);
        } catch (BiometriaException e) {
            vista.mostrarError("Error de Archivo (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    public void limpiarHistorial() {
        try {
            repositorioHistorial.limpiarHistorial();
            vista.mostrarHistorial(repositorioHistorial.leerHistorial());
        } catch (BiometriaException e) {
            vista.mostrarError("Error de Archivo (" + e.getCodigoError() + ")", e.getMessage());
        }
    }

    public void liberarRecursos() {
        if (detectorRostro != null) {
            detectorRostro.cerrar();
        }
        if (extractorCaracteristicas != null) {
            extractorCaracteristicas.cerrar();
        }
    }
}
