package co.edu.udistrital.reconocimientofacial.modelo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Objeto de valor inmutable que encapsula el resultado de una verificacion facial 1:1
 * (Etapa 4: Comparacion y decision contra el umbral).
 */
public class ResultadoVerificacion {

    private final double similitudCoseno;
    private final double umbralAplicado;
    private final boolean coincide;
    private final String nombreValidador;
    private final long latenciaTotalMs;
    private final String fechaHoraFormateada;
    private final String detalleDiagnostico;

    public ResultadoVerificacion(
            double similitudCoseno,
            double umbralAplicado,
            boolean coincide,
            String nombreValidador,
            long latenciaTotalMs,
            String detalleDiagnostico
    ) {
        this.similitudCoseno = similitudCoseno;
        this.umbralAplicado = umbralAplicado;
        this.coincide = coincide;
        this.nombreValidador = nombreValidador;
        this.latenciaTotalMs = latenciaTotalMs;
        this.fechaHoraFormateada = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date());
        this.detalleDiagnostico = detalleDiagnostico;
    }

    public double getSimilitudCoseno() {
        return similitudCoseno;
    }

    public double getPorcentajeSimilitud() {
        double acotado = Math.max(0.0, Math.min(1.0, similitudCoseno));
        return acotado * 100.0;
    }

    public double getUmbralAplicado() {
        return umbralAplicado;
    }

    public boolean isCoincide() {
        return coincide;
    }

    public String getNombreValidador() {
        return nombreValidador;
    }

    public long getLatenciaTotalMs() {
        return latenciaTotalMs;
    }

    public String getFechaHoraFormateada() {
        return fechaHoraFormateada;
    }

    public String getDetalleDiagnostico() {
        return detalleDiagnostico;
    }

    public String toLineaCsv() {
        String veredicto = coincide ? "COINCIDE" : "NO_COINCIDE";
        return String.format(
                Locale.US,
                "%s;%s;%.4f;%.2f;%d;%s",
                fechaHoraFormateada,
                nombreValidador,
                similitudCoseno,
                umbralAplicado,
                latenciaTotalMs,
                veredicto
        );
    }
}
