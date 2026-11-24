package co.edu.udistrital.reconocimientofacial.modelo;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;

/**
 * Subclase polimorfica de ValidadorFacial que aplica un umbral equilibrado (tau = 0.70)
 * para reducir la Tasa de Falso Rechazo (FRR) en capturas cotidianas a mano alzada
 * (El Fadel, 2025).
 */
public class ValidadorEstandar extends ValidadorFacial {

    public static final double UMBRAL_ESTANDAR = 0.70;

    @Override
    protected void validarCalidadFotograma(Fotograma fotograma, String etiqueta) throws BiometriaException {
        if (fotograma == null || !fotograma.tieneEmbedding()) {
            throw new BiometriaException(
                    "BIO-EST-01",
                    "El fotograma de " + etiqueta + " no dispone de vector de caracteristicas valido."
            );
        }
    }

    @Override
    public double getUmbral() {
        return UMBRAL_ESTANDAR;
    }

    @Override
    public String getNombreModo() {
        return "Estandar (Equilibrio FAR/FRR)";
    }
}
