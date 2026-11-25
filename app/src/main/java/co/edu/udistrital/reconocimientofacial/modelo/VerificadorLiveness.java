package co.edu.udistrital.reconocimientofacial.modelo;

import java.util.Random;

/**
 * Controla la prueba de vida (liveness) mediante un desafio aleatorio
 * con ventana maxima de tiempo y validacion temporal para evitar fotos estaticas.
 */
public class VerificadorLiveness {

    /**
     * Desafios soportados en la prueba de vida.
     */
    public enum Desafio {
        PARPADEO("Parpadee una vez mirando a la camara"),
        GIRO_IZQUIERDA("Gire suavemente la cabeza a la izquierda"),
        GIRO_DERECHA("Gire suavemente la cabeza a la derecha");

        private final String instruccion;

        private Desafio(String instruccion) {
            this.instruccion = instruccion;
        }

        public String getInstruccion() {
            return instruccion;
        }
    }

    /**
     * Estados posibles del verificador de prueba de vida.
     */
    public enum Estado {
        EN_PROGRESO,
        SUPERADO,
        EXPIRADO
    }

    public static final long TIEMPO_MAXIMO_MS = 8_000L;
    public static final float UMBRAL_OJO_CERRADO = 0.35f;
    public static final float UMBRAL_OJO_ABIERTO = 0.65f;
    public static final float UMBRAL_GIRO_YAW = 16.0f;

    private final Random random;
    private Desafio desafioActual;
    private Estado estado;
    private long inicioMs;
    private boolean vioOjosAbiertos;
    private boolean vioOjosCerrados;
    private boolean vioPosicionInicial;

    /**
     * Crea un verificador con un desafio aleatorio e inicio en el instante actual.
     */
    public VerificadorLiveness() {
        this.random = new Random();
        reiniciar(System.currentTimeMillis());
    }

    /**
     * Crea un verificador determinista para pruebas unitarias.
     */
    public VerificadorLiveness(Desafio desafioFijo, long inicioMs) {
        this.random = new Random();
        this.desafioActual = desafioFijo != null ? desafioFijo : Desafio.PARPADEO;
        this.inicioMs = inicioMs;
        this.estado = Estado.EN_PROGRESO;
        limpiarBanderas();
    }

    /**
     * Evalua un fotograma usando la hora actual del sistema.
     */
    public Estado evaluarFotograma(int cantidadRostros, float yawY, float probOjoIzq, float probOjoDer) {
        return evaluarFotograma(cantidadRostros, yawY, probOjoIzq, probOjoDer, System.currentTimeMillis());
    }

    /**
     * Evalua un fotograma en un instante dado y actualiza el estado del desafio.
     */
    public Estado evaluarFotograma(
            int cantidadRostros,
            float yawY,
            float probOjoIzq,
            float probOjoDer,
            long ahoraMs
    ) {
        if (estado != Estado.EN_PROGRESO) {
            return estado;
        }
        if (ahoraMs - inicioMs > TIEMPO_MAXIMO_MS) {
            estado = Estado.EXPIRADO;
            return estado;
        }
        if (cantidadRostros != 1) {
            return Estado.EN_PROGRESO;
        }

        switch (desafioActual) {
            case PARPADEO:
                evaluarParpadeo(probOjoIzq, probOjoDer);
                break;
            case GIRO_IZQUIERDA:
                evaluarGiro(yawY, true);
                break;
            case GIRO_DERECHA:
                evaluarGiro(yawY, false);
                break;
        }
        return estado;
    }

    /**
     * Reinicia el desafio escogiendo uno aleatorio nuevo en el instante actual.
     */
    public void reiniciar() {
        reiniciar(System.currentTimeMillis());
    }

    /**
     * Reinicia el desafio escogiendo uno aleatorio nuevo con marca de tiempo explicita.
     */
    public void reiniciar(long ahoraMs) {
        Desafio[] desafios = Desafio.values();
        this.desafioActual = desafios[random.nextInt(desafios.length)];
        this.inicioMs = ahoraMs;
        this.estado = Estado.EN_PROGRESO;
        limpiarBanderas();
    }

    public Desafio getDesafioActual() {
        return desafioActual;
    }

    public Estado getEstado() {
        return estado;
    }

    public boolean isSuperado() {
        return estado == Estado.SUPERADO;
    }

    public boolean isExpirado() {
        return estado == Estado.EXPIRADO;
    }

    /**
     * Devuelve los segundos restantes redondeados hacia arriba usando el reloj del sistema.
     */
    public long obtenerSegundosRestantes() {
        return obtenerSegundosRestantes(System.currentTimeMillis());
    }

    /**
     * Devuelve los segundos restantes redondeados hacia arriba para un instante dado.
     */
    public long obtenerSegundosRestantes(long ahoraMs) {
        if (estado != Estado.EN_PROGRESO) {
            return 0L;
        }
        return Math.max(0L, (long) Math.ceil((TIEMPO_MAXIMO_MS - (ahoraMs - inicioMs)) / 1000.0));
    }

    private void evaluarParpadeo(float probOjoIzq, float probOjoDer) {
        boolean ojosAbiertos = probOjoIzq >= UMBRAL_OJO_ABIERTO && probOjoDer >= UMBRAL_OJO_ABIERTO;
        boolean ojosCerrados = probOjoIzq >= 0f
                && probOjoDer >= 0f
                && probOjoIzq <= UMBRAL_OJO_CERRADO
                && probOjoDer <= UMBRAL_OJO_CERRADO;

        if (!vioOjosAbiertos) {
            if (ojosAbiertos) {
                vioOjosAbiertos = true;
            }
        } else if (!vioOjosCerrados) {
            if (ojosCerrados) {
                vioOjosCerrados = true;
            }
        } else if (ojosAbiertos) {
            estado = Estado.SUPERADO;
        }
    }

    private void evaluarGiro(float yawY, boolean haciaIzquierda) {
        if (!vioPosicionInicial) {
            if (Math.abs(yawY) <= 12.0f) {
                vioPosicionInicial = true;
            }
        } else if (haciaIzquierda && yawY >= UMBRAL_GIRO_YAW) {
            estado = Estado.SUPERADO;
        } else if (!haciaIzquierda && yawY <= -UMBRAL_GIRO_YAW) {
            estado = Estado.SUPERADO;
        }
    }

    private void limpiarBanderas() {
        this.vioOjosAbiertos = false;
        this.vioOjosCerrados = false;
        this.vioPosicionInicial = false;
    }
}
