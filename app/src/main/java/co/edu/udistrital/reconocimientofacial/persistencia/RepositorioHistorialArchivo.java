package co.edu.udistrital.reconocimientofacial.persistencia;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.modelo.ResultadoVerificacion;

/**
 * Implementacion de IRepositorioHistorial que almacena las verificaciones realizadas
 * en un archivo plano CSV local (historial_verificaciones.csv) usando java.io.
 */
public class RepositorioHistorialArchivo implements IRepositorioHistorial {

    private static final String NOMBRE_ARCHIVO = "historial_verificaciones.csv";
    private final File archivoCsv;

    public RepositorioHistorialArchivo(Context context) {
        this.archivoCsv = new File(context.getFilesDir(), NOMBRE_ARCHIVO);
    }

    public RepositorioHistorialArchivo(File archivoCsv) {
        this.archivoCsv = archivoCsv;
    }

    @Override
    public void guardarResultado(ResultadoVerificacion resultado) throws BiometriaException {
        if (resultado == null) {
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivoCsv, true))) {
            writer.write(resultado.toLineaCsv());
            writer.newLine();
        } catch (IOException e) {
            throw new BiometriaException(
                    "BIO-IO-01",
                    "No se pudo guardar el registro en el archivo de historial: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public List<String> leerHistorial() throws BiometriaException {
        if (!archivoCsv.exists()) {
            return Collections.emptyList();
        }
        List<String> registros = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(archivoCsv))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (!linea.trim().isEmpty()) {
                    registros.add(formatearLineaLegible(linea));
                }
            }
        } catch (IOException e) {
            throw new BiometriaException(
                    "BIO-IO-02",
                    "Error al leer el archivo de historial: " + e.getMessage(),
                    e
            );
        }
        Collections.reverse(registros);
        return registros;
    }

    @Override
    public void limpiarHistorial() throws BiometriaException {
        if (archivoCsv.exists() && !archivoCsv.delete()) {
            throw new BiometriaException("BIO-IO-03", "No fue posible eliminar el archivo de historial.");
        }
    }

    private String formatearLineaLegible(String lineaCsv) {
        String[] partes = lineaCsv.split(";");
        if (partes.length < 6) {
            return lineaCsv;
        }
        return String.format(
                "[%s] %s\nSimilitud: %s (Umbral: %s) | %s ms -> %s",
                partes[0],
                partes[1],
                partes[2],
                partes[3],
                partes[4],
                partes[5]
        );
    }
}
