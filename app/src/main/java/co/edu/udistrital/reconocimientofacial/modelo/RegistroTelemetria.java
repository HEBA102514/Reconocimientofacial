package co.edu.udistrital.reconocimientofacial.modelo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Objeto de valor inmutable que encapsula una fila de telemetria experimental
 * para exportacion y analisis estadistico en formato CSV.
 */
public class RegistroTelemetria {

    public static final String ENCABEZADO_CSV =
            "fecha;usuario;tipo_intento;iluminacion;modo_validador;liveness_superado;desafio_liveness;tiempo_deteccion_ms;tiempo_inferencia_ms;ram_usada_mb;tamano_modelo_mb;similitud_max;umbral;veredicto";

    private final String fecha;
    private final String usuario;
    private final String tipoIntento;
    private final String iluminacion;
    private final String modoValidador;
    private final boolean livenessSuperado;
    private final String desafioLiveness;
    private final long tiempoDeteccionMs;
    private final long tiempoInferenciaMs;
    private final double ramUsadaMb;
    private final double tamanoModeloMb;
    private final double similitudMax;
    private final double umbral;
    private final String veredicto;

    public RegistroTelemetria(
            String usuario,
            String tipoIntento,
            String iluminacion,
            String modoValidador,
            boolean livenessSuperado,
            String desafioLiveness,
            long tiempoDeteccionMs,
            long tiempoInferenciaMs,
            double ramUsadaMb,
            double tamanoModeloMb,
            double similitudMax,
            double umbral,
            String veredicto
    ) {
        this.fecha = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        this.usuario = limpiarTextoCsv(usuario);
        this.tipoIntento = limpiarTextoCsv(tipoIntento);
        this.iluminacion = limpiarTextoCsv(iluminacion);
        this.modoValidador = limpiarTextoCsv(modoValidador);
        this.livenessSuperado = livenessSuperado;
        this.desafioLiveness = limpiarTextoCsv(desafioLiveness);
        this.tiempoDeteccionMs = tiempoDeteccionMs;
        this.tiempoInferenciaMs = tiempoInferenciaMs;
        this.ramUsadaMb = ramUsadaMb;
        this.tamanoModeloMb = tamanoModeloMb;
        this.similitudMax = similitudMax;
        this.umbral = umbral;
        this.veredicto = limpiarTextoCsv(veredicto);
    }

    public String getFecha() {
        return fecha;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getTipoIntento() {
        return tipoIntento;
    }

    public String getIluminacion() {
        return iluminacion;
    }

    public String getModoValidador() {
        return modoValidador;
    }

    public boolean isLivenessSuperado() {
        return livenessSuperado;
    }

    public String getDesafioLiveness() {
        return desafioLiveness;
    }

    public long getTiempoDeteccionMs() {
        return tiempoDeteccionMs;
    }

    public long getTiempoInferenciaMs() {
        return tiempoInferenciaMs;
    }

    public double getRamUsadaMb() {
        return ramUsadaMb;
    }

    public double getTamanoModeloMb() {
        return tamanoModeloMb;
    }

    public double getSimilitudMax() {
        return similitudMax;
    }

    public double getUmbral() {
        return umbral;
    }

    public String getVeredicto() {
        return veredicto;
    }

    /**
     * Serializa el registro en una linea CSV separada por punto y coma (;).
     */
    public String toLineaCsv() {
        return String.format(
                Locale.US,
                "%s;%s;%s;%s;%s;%b;%s;%d;%d;%.2f;%.2f;%.4f;%.2f;%s",
                limpiarTextoCsv(fecha),
                limpiarTextoCsv(usuario),
                limpiarTextoCsv(tipoIntento),
                limpiarTextoCsv(iluminacion),
                limpiarTextoCsv(modoValidador),
                livenessSuperado,
                limpiarTextoCsv(desafioLiveness),
                tiempoDeteccionMs,
                tiempoInferenciaMs,
                ramUsadaMb,
                tamanoModeloMb,
                similitudMax,
                umbral,
                limpiarTextoCsv(veredicto)
        );
    }

    private static String limpiarTextoCsv(String valor) {
        if (valor == null) {
            return "";
        }
        return valor
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace(';', ',')
                .trim();
    }
}
