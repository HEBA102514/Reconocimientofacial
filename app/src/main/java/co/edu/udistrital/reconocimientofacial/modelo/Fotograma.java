package co.edu.udistrital.reconocimientofacial.modelo;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.Arrays;
import java.util.Locale;

/**
 * Representa un fotograma biometrico capturado y procesado (Etapa 1 y 2 de El Fadel, 2025).
 * Demuestra encapsulamiento fuerte: atributos privados y copias defensivas del vector embedding.
 */
public class Fotograma {

    private final Bitmap imagenOriginal;
    private final Bitmap rostroRecortado;
    private final Rect cajaDelimitadora;
    private float[] embedding;
    private final float anguloYawY;
    private final float anguloRollZ;
    private final float probOjoIzquierdoAbierto;
    private final float probOjoDerechoAbierto;
    private long tiempoInferenciaMs;
    private final long timestamp;

    public Fotograma(
            Bitmap imagenOriginal,
            Bitmap rostroRecortado,
            Rect cajaDelimitadora,
            float anguloYawY,
            float anguloRollZ,
            float probOjoIzquierdoAbierto,
            float probOjoDerechoAbierto
    ) {
        this.imagenOriginal = imagenOriginal;
        this.rostroRecortado = rostroRecortado;
        this.cajaDelimitadora = cajaDelimitadora != null ? new Rect(cajaDelimitadora) : null;
        this.anguloYawY = anguloYawY;
        this.anguloRollZ = anguloRollZ;
        this.probOjoIzquierdoAbierto = probOjoIzquierdoAbierto;
        this.probOjoDerechoAbierto = probOjoDerechoAbierto;
        this.timestamp = System.currentTimeMillis();
    }

    public Bitmap getImagenOriginal() {
        return imagenOriginal;
    }

    public Bitmap getRostroRecortado() {
        return rostroRecortado;
    }

    public Rect getCajaDelimitadora() {
        return cajaDelimitadora != null ? new Rect(cajaDelimitadora) : null;
    }

    /**
     * Devuelve una copia defensiva del vector de caracteristicas (embedding)
     * para proteger el estado interno del objeto.
     */
    public float[] getEmbedding() {
        if (embedding == null) {
            return null;
        }
        return Arrays.copyOf(embedding, embedding.length);
    }

    /**
     * Almacena una copia defensiva del vector de caracteristicas extraido por MobileFaceNet.
     */
    public void setEmbedding(float[] vector) {
        if (vector == null) {
            this.embedding = null;
        } else {
            this.embedding = Arrays.copyOf(vector, vector.length);
        }
    }

    public boolean tieneEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    public float getAnguloYawY() {
        return anguloYawY;
    }

    public float getAnguloRollZ() {
        return anguloRollZ;
    }

    public float getProbOjoIzquierdoAbierto() {
        return probOjoIzquierdoAbierto;
    }

    public float getProbOjoDerechoAbierto() {
        return probOjoDerechoAbierto;
    }

    public long getTiempoInferenciaMs() {
        return tiempoInferenciaMs;
    }

    public void setTiempoInferenciaMs(long tiempoInferenciaMs) {
        this.tiempoInferenciaMs = tiempoInferenciaMs;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Genera un resumen legible de los metadatos de pose, ojos y latencia.
     */
    public String obtenerResumenMetadatos() {
        String ojosTxt;
        if (probOjoIzquierdoAbierto >= 0f && probOjoDerechoAbierto >= 0f) {
            float promedioOjos = ((probOjoIzquierdoAbierto + probOjoDerechoAbierto) / 2.0f) * 100.0f;
            ojosTxt = String.format(Locale.US, "Ojos abiertos: %.0f%%", promedioOjos);
        } else {
            ojosTxt = "Ojos: N/D";
        }
        int dim = embedding != null ? embedding.length : 0;
        return String.format(
                Locale.US,
                "Pose (Yaw: %.1f°, Roll: %.1f°) | %s\nEmbedding: %dD | Inferencia TFLite: %d ms",
                anguloYawY,
                anguloRollZ,
                ojosTxt,
                dim,
                tiempoInferenciaMs
        );
    }
}
