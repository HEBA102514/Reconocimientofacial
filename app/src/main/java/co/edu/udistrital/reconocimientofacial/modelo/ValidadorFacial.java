package co.edu.udistrital.reconocimientofacial.modelo;

import java.util.Locale;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;

/**
 * Clase abstracta que representa la jerarquia ValidadorFacial (Etapa 4 de la propuesta y El Fadel, 2025).
 * Implementa el patron Template Method en verificar() y delega polimorficamente a sus subclases
 * la validacion de calidad del Fotograma y la definicion del umbral de similitud coseno.
 */
public abstract class ValidadorFacial {

    /**
     * Metodo plantilla (Template Method) que ejecuta la verificacion biometrica 1:1.
     */
    public final ResultadoVerificacion verificar(Fotograma referencia, Fotograma validacion)
            throws BiometriaException {
        if (referencia == null || !referencia.tieneEmbedding()) {
            throw new BiometriaException("BIO-VAL-01", "Falta el fotograma o embedding de Referencia.");
        }
        if (validacion == null || !validacion.tieneEmbedding()) {
            throw new BiometriaException("BIO-VAL-02", "Falta el fotograma o embedding de Validacion.");
        }

        // Gancho polimorfico: cada subclase aplica sus propias reglas de calidad (pose y ojos)
        validarCalidadFotograma(referencia, "Referencia");
        validarCalidadFotograma(validacion, "Validacion");

        double similitud = calcularSimilitudCoseno(referencia.getEmbedding(), validacion.getEmbedding());
        double umbral = getUmbral();
        boolean coincide = similitud >= umbral;
        long latenciaTotal = referencia.getTiempoInferenciaMs() + validacion.getTiempoInferenciaMs();

        String diagnostico = String.format(
                Locale.US,
                "Modo: %s | Similitud Coseno: %.4f (%.1f%%) vs Umbral: %.2f (%.0f%%) | Latencia TFLite: %d ms",
                getNombreModo(),
                similitud,
                Math.max(0.0, similitud) * 100.0,
                umbral,
                umbral * 100.0,
                latenciaTotal
        );

        return new ResultadoVerificacion(
                similitud,
                umbral,
                coincide,
                getNombreModo(),
                latenciaTotal,
                diagnostico
        );
    }

    /**
     * Ejecuta la verificacion biometrica 1:1 comparando el fotograma de validacion contra las
     * tres poses registradas del usuario (frontal, izquierda y derecha) y tomando la similitud maxima.
     */
    public final ResultadoVerificacion verificarContraPerfil(UsuarioRegistrado usuario, Fotograma validacion)
            throws BiometriaException {
        if (usuario == null || !usuario.tieneEmbeddingsCompletos()) {
            throw new BiometriaException(
                    "BIO-VAL-04",
                    "El usuario registrado no tiene las 3 poses biometricas completas."
            );
        }
        if (validacion == null || !validacion.tieneEmbedding()) {
            throw new BiometriaException("BIO-VAL-02", "Falta el fotograma o embedding de Validacion.");
        }

        // Gancho polimorfico: cada subclase aplica sus propias reglas de calidad al fotograma de validacion
        validarCalidadFotograma(validacion, "Validacion");

        float[] embeddingValidacion = validacion.getEmbedding();
        double s1 = calcularSimilitudCoseno(usuario.getEmbeddingFrontal(), embeddingValidacion);
        double s2 = calcularSimilitudCoseno(usuario.getEmbeddingIzquierda(), embeddingValidacion);
        double s3 = calcularSimilitudCoseno(usuario.getEmbeddingDerecha(), embeddingValidacion);
        double similitudMax = Math.max(s1, Math.max(s2, s3));

        double umbral = getUmbral();
        boolean coincide = similitudMax >= umbral;

        String diagnostico = String.format(
                Locale.US,
                "Modo: %s | max(F=%.3f, I=%.3f, D=%.3f) = %.4f (%.1f%%) vs Umbral: %.2f | TFLite: %d ms",
                getNombreModo(),
                s1,
                s2,
                s3,
                similitudMax,
                Math.max(0.0, similitudMax) * 100.0,
                umbral,
                validacion.getTiempoInferenciaMs()
        );

        return new ResultadoVerificacion(
                similitudMax,
                umbral,
                coincide,
                getNombreModo(),
                validacion.getTiempoInferenciaMs(),
                diagnostico
        );
    }

    /**
     * Calcula la similitud coseno entre dos vectores de caracteristicas:
     * cos(theta) = (A . B) / (||A|| * ||B||).
     */
    public double calcularSimilitudCoseno(float[] v1, float[] v2) throws BiometriaException {
        if (v1 == null || v2 == null || v1.length == 0 || v1.length != v2.length) {
            throw new BiometriaException(
                    "BIO-VAL-03",
                    "Los vectores de caracteristicas tienen dimensiones incompatibles o estan vacios."
            );
        }

        double productoPunto = 0.0;
        double norma1 = 0.0;
        double norma2 = 0.0;

        for (int i = 0; i < v1.length; i++) {
            productoPunto += (double) v1[i] * v2[i];
            norma1 += (double) v1[i] * v1[i];
            norma2 += (double) v2[i] * v2[i];
        }

        double denominador = Math.sqrt(norma1) * Math.sqrt(norma2);
        if (denominador < 1e-10) {
            return 0.0;
        }
        return productoPunto / denominador;
    }

    /**
     * Valida las condiciones de calidad del fotograma segun la politica de la subclase.
     */
    protected abstract void validarCalidadFotograma(Fotograma fotograma, String etiqueta)
            throws BiometriaException;

    /**
     * Devuelve el umbral de decision (tau) entre [-1.0, 1.0].
     */
    public abstract double getUmbral();

    /**
     * Devuelve el nombre descriptivo del modo de validacion.
     */
    public abstract String getNombreModo();
}
