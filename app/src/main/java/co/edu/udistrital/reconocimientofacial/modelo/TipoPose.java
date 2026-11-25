package co.edu.udistrital.reconocimientofacial.modelo;

/**
 * Representa las tres poses faciales requeridas para el registro biometrico.
 */
public enum TipoPose {
    FRONTAL("Frontal", "Mire de frente a la camara"),
    IZQUIERDA("Izquierda", "Gire levemente el rostro a la izquierda"),
    DERECHA("Derecha", "Gire levemente el rostro a la derecha");

    private final String etiqueta;
    private final String instruccion;

    private TipoPose(String etiqueta, String instruccion) {
        this.etiqueta = etiqueta;
        this.instruccion = instruccion;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getInstruccion() {
        return instruccion;
    }
}
