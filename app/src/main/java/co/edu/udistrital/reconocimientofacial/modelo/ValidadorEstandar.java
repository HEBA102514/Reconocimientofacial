package co.edu.udistrital.reconocimientofacial.modelo;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;

/**
 * Subclase polimorfica de ValidadorFacial que aplica un umbral equilibrado (tau = 0.65)
 * calibrado en B6 sobre la telemetria experimental real: eleva la Exactitud de 77.78%
 * a 83.33% y reduce la Tasa de Falso Rechazo (FRR) de 21.43% a 14.29% en capturas
 * cotidianas a mano alzada (El Fadel, 2025).
 */
public class ValidadorEstandar extends ValidadorFacial {

    public static final double UMBRAL_ESTANDAR = 0.65;

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
