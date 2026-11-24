package co.edu.udistrital.reconocimientofacial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.List;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.excepciones.CalidadInsuficienteException;
import co.edu.udistrital.reconocimientofacial.modelo.ExtractorMobileFaceNet;
import co.edu.udistrital.reconocimientofacial.modelo.Fotograma;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstandar;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorEstricto;
import co.edu.udistrital.reconocimientofacial.modelo.ValidadorFacial;
import co.edu.udistrital.reconocimientofacial.persistencia.IRepositorioHistorial;
import co.edu.udistrital.reconocimientofacial.persistencia.RepositorioHistorialArchivo;

/**
 * Pruebas unitarias que verifican el encapsulamiento de Fotograma, la normalizacion L2,
 * el polimorfismo de ValidadorFacial (ValidadorEstandar vs ValidadorEstricto), el manejo
 * de excepciones propias y la persistencia en archivos CSV.
 */
public class ValidadorFacialTest {

    @Test
    public void fotograma_protegeEmbeddingConCopiaDefensiva() {
        Fotograma fotograma = new Fotograma(null, null, null, 0f, 0f, 0.9f, 0.9f);
        float[] original = new float[]{0.6f, 0.8f};
        fotograma.setEmbedding(original);

        // Modificar el arreglo externo no debe alterar el estado interno de Fotograma
        original[0] = -999.0f;
        float[] recuperado = fotograma.getEmbedding();
        assertNotNull(recuperado);
        assertEquals(0.6f, recuperado[0], 1e-5f);

        // Modificar el arreglo devuelto tampoco debe alterar el estado interno
        recuperado[1] = 123.0f;
        assertEquals(0.8f, fotograma.getEmbedding()[1], 1e-5f);
    }

    @Test
    public void normalizarL2_produceVectorDeNormaUnitaria() {
        float[] bruto = new float[]{3.0f, 4.0f};
        float[] normalizado = ExtractorMobileFaceNet.normalizarL2(bruto);
        assertEquals(0.6f, normalizado[0], 1e-4f);
        assertEquals(0.8f, normalizado[1], 1e-4f);
    }

    @Test
    public void polimorfismoValidador_mismoParCoincideEnEstandarYRechazaEnEstricto() throws BiometriaException {
        // Dos vectores con similitud coseno ~ 0.768 (entre 0.70 y 0.82)
        Fotograma ref = new Fotograma(null, null, null, 2.0f, 1.0f, 0.95f, 0.95f);
        ref.setEmbedding(ExtractorMobileFaceNet.normalizarL2(new float[]{1.0f, 0.0f}));

        Fotograma val = new Fotograma(null, null, null, 3.0f, 2.0f, 0.90f, 0.90f);
        val.setEmbedding(ExtractorMobileFaceNet.normalizarL2(new float[]{0.768f, 0.640f}));

        ValidadorFacial validadorEstandar = new ValidadorEstandar();
        ValidadorFacial validadorEstricto = new ValidadorEstricto();

        ResultadoVerificacion resEstandar = validadorEstandar.verificar(ref, val);
        ResultadoVerificacion resEstricto = validadorEstricto.verificar(ref, val);

        assertTrue(resEstandar.isCoincide());
        assertFalse(resEstricto.isCoincide());
    }

    @Test(expected = CalidadInsuficienteException.class)
    public void validadorEstricto_lanzaExcepcionCuandoPoseExcedeLimite() throws BiometriaException {
        Fotograma ref = new Fotograma(null, null, null, 0.0f, 0.0f, 0.95f, 0.95f);
        ref.setEmbedding(new float[]{1.0f, 0.0f});

        // Rostro girado 32 grados (> 18 grados permitidos)
        Fotograma valGirado = new Fotograma(null, null, null, 32.0f, 0.0f, 0.95f, 0.95f);
        valGirado.setEmbedding(new float[]{1.0f, 0.0f});

        ValidadorFacial validador = new ValidadorEstricto();
        validador.verificar(ref, valGirado);
    }

    @Test
    public void repositorioArchivo_guardaYLeeRegistrosCsv() throws Exception {
        File archivoTemp = File.createTempFile("test_historial", ".csv");
        archivoTemp.deleteOnExit();

        IRepositorioHistorial repositorio = new RepositorioHistorialArchivo(archivoTemp);
        ResultadoVerificacion r = new ResultadoVerificacion(
                0.885,
                0.70,
                true,
                "Estandar",
                42L,
                "Prueba unitaria"
        );

        repositorio.guardarResultado(r);
        List<String> historial = repositorio.leerHistorial();
        assertEquals(1, historial.size());
        assertTrue(historial.get(0).contains("COINCIDE"));

        repositorio.limpiarHistorial();
        assertTrue(repositorio.leerHistorial().isEmpty());
    }
}
