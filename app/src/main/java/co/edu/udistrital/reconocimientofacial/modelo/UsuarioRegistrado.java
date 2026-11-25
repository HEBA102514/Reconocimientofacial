package co.edu.udistrital.reconocimientofacial.modelo;

import java.util.Arrays;

/**
 * Representa un usuario registrado en el sistema biometrico con sus tres
 * vectores de caracteristicas (frontal, izquierda y derecha).
 */
public class UsuarioRegistrado {

    private final String nombreUsuario;
    private final String hashClave;
    private final boolean consentimientoAceptado;
    private final String fechaRegistro;
    private float[] embeddingFrontal;
    private float[] embeddingIzquierda;
    private float[] embeddingDerecha;

    public UsuarioRegistrado(
            String nombreUsuario,
            String hashClave,
            boolean consentimientoAceptado,
            String fechaRegistro
    ) {
        this.nombreUsuario = nombreUsuario != null ? nombreUsuario.trim() : "";
        this.hashClave = hashClave != null ? hashClave.trim() : "";
        this.consentimientoAceptado = consentimientoAceptado;
        this.fechaRegistro = fechaRegistro != null ? fechaRegistro.trim() : "";
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getHashClave() {
        return hashClave;
    }

    public boolean isConsentimientoAceptado() {
        return consentimientoAceptado;
    }

    public String getFechaRegistro() {
        return fechaRegistro;
    }

    public float[] getEmbeddingFrontal() {
        return copiarVector(embeddingFrontal);
    }

    public void setEmbeddingFrontal(float[] vector) {
        this.embeddingFrontal = copiarVector(vector);
    }

    public float[] getEmbeddingIzquierda() {
        return copiarVector(embeddingIzquierda);
    }

    public void setEmbeddingIzquierda(float[] vector) {
        this.embeddingIzquierda = copiarVector(vector);
    }

    public float[] getEmbeddingDerecha() {
        return copiarVector(embeddingDerecha);
    }

    public void setEmbeddingDerecha(float[] vector) {
        this.embeddingDerecha = copiarVector(vector);
    }

    public void guardarEmbedding(TipoPose pose, float[] vector) {
        if (pose == null) {
            return;
        }
        switch (pose) {
            case FRONTAL:
                setEmbeddingFrontal(vector);
                break;
            case IZQUIERDA:
                setEmbeddingIzquierda(vector);
                break;
            case DERECHA:
                setEmbeddingDerecha(vector);
                break;
        }
    }

    public float[] obtenerEmbedding(TipoPose pose) {
        if (pose == null) {
            return null;
        }
        switch (pose) {
            case FRONTAL:
                return getEmbeddingFrontal();
            case IZQUIERDA:
                return getEmbeddingIzquierda();
            case DERECHA:
                return getEmbeddingDerecha();
            default:
                return null;
        }
    }

    public boolean tieneEmbeddingsCompletos() {
        return embeddingFrontal != null && embeddingFrontal.length > 0
                && embeddingIzquierda != null && embeddingIzquierda.length > 0
                && embeddingDerecha != null && embeddingDerecha.length > 0;
    }

    private float[] copiarVector(float[] origen) {
        if (origen == null) {
            return null;
        }
        return Arrays.copyOf(origen, origen.length);
    }
}
