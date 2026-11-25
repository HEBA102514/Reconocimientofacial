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
import co.edu.udistrital.reconocimientofacial.modelo.RegistroTelemetria;

/**
 * Repositorio de telemetria experimental en formato CSV usando java.io.
 * Almacena las metricas de deteccion, inferencia TFLite, memoria, iluminacion y veredicto.
 */
public class RepositorioTelemetriaCsv {

    private static final String NOMBRE_ARCHIVO = "telemetria_experimentos.csv";
    private final File archivoCsv;

    public RepositorioTelemetriaCsv(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        this.archivoCsv = new File(dir, NOMBRE_ARCHIVO);
    }

    public RepositorioTelemetriaCsv(File archivoCsv) {
        this.archivoCsv = archivoCsv;
    }

    public void guardarRegistro(RegistroTelemetria registro) throws BiometriaException {
        if (registro == null) {
            return;
        }
        File directorioPadre = archivoCsv.getParentFile();
        if (directorioPadre != null && !directorioPadre.exists()) {
            directorioPadre.mkdirs();
        }
        boolean escribirEncabezado = !archivoCsv.exists() || archivoCsv.length() == 0;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivoCsv, true))) {
            if (escribirEncabezado) {
                writer.write(RegistroTelemetria.ENCABEZADO_CSV);
                writer.newLine();
            }
            writer.write(registro.toLineaCsv());
            writer.newLine();
        } catch (IOException e) {
            throw new BiometriaException(
                    "BIO-IO-04",
                    "No se pudo guardar el registro de telemetria CSV: " + e.getMessage(),
                    e
            );
        }
    }

    public List<String> leerRegistrosLegibles() throws BiometriaException {
        if (!archivoCsv.exists()) {
            return Collections.emptyList();
        }
        List<String> registros = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(archivoCsv))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String limpia = linea.trim();
                if (limpia.isEmpty() || esEncabezado(limpia)) {
                    continue;
                }
                registros.add(formatearLineaLegible(limpia));
            }
        } catch (IOException e) {
            throw new BiometriaException(
                    "BIO-IO-05",
                    "Error al leer el archivo de telemetria CSV: " + e.getMessage(),
                    e
            );
        }
        Collections.reverse(registros);
        return registros;
    }

    public String leerCsvCompleto() throws BiometriaException {
        if (!archivoCsv.exists()) {
            return "";
        }
        StringBuilder contenido = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(archivoCsv))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                contenido.append(linea).append('\n');
            }
        } catch (IOException e) {
            throw new BiometriaException(
                    "BIO-IO-06",
                    "Error al exportar el archivo de telemetria CSV: " + e.getMessage(),
                    e
            );
        }
        return contenido.toString();
    }

    public void limpiarRegistros() throws BiometriaException {
        if (archivoCsv.exists() && !archivoCsv.delete()) {
            throw new BiometriaException(
                    "BIO-IO-07",
                    "No fue posible eliminar el archivo de telemetria CSV."
            );
        }
    }

    public String getRutaArchivo() {
        return archivoCsv != null ? archivoCsv.getAbsolutePath() : "";
    }

    private boolean esEncabezado(String linea) {
        return RegistroTelemetria.ENCABEZADO_CSV.equals(linea) || linea.startsWith("fecha;usuario;");
    }

    private String formatearLineaLegible(String lineaCsv) {
        String[] partes = lineaCsv.split(";");
        if (partes.length < 14) {
            return lineaCsv;
        }
        return String.format(
                "[%s] %s | %s (Luz: %s)\nModo: %s | Liveness: %s (%s)\nDet: %s ms | Inf: %s ms | RAM: %s MB | Modelo: %s MB\nSimilitud: %s (Umbral: %s) -> %s",
                partes[0],
                partes[1],
                partes[2],
                partes[3],
                partes[4],
                partes[5],
                partes[6],
                partes[7],
                partes[8],
                partes[9],
                partes[10],
                partes[11],
                partes[12],
                partes[13]
        );
    }
}
