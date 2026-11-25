package co.edu.udistrital.reconocimientofacial.modelo;

/**
 * Gestiona el estado de la sesion activa, los intentos fallidos de ingreso
 * y el bloqueo temporal tras superar el maximo de errores permitidos.
 */
public class GestorSesion {

    public static final int MAX_INTENTOS_FALLIDOS = 3;
    public static final long TIEMPO_BLOQUEO_MS = 30_000L;

    private static final GestorSesion INSTANCIA = new GestorSesion();

    private int intentosFallidos;
    private long bloqueadoHastaMs;
    private boolean sesionActiva;
    private String usuarioActivo;
    private String metodoAcceso;
    private String ultimoResumenBiometrico;

    public GestorSesion() {
        this.intentosFallidos = 0;
        this.bloqueadoHastaMs = 0L;
        this.sesionActiva = false;
        this.usuarioActivo = null;
        this.metodoAcceso = null;
        this.ultimoResumenBiometrico = null;
    }

    public static GestorSesion obtenerInstancia() {
        return INSTANCIA;
    }

    public boolean autenticar(UsuarioRegistrado usuarioGuardado, String nombreIngresado, String hashClaveIngresada) {
        return autenticarEnTiempo(usuarioGuardado, nombreIngresado, hashClaveIngresada, System.currentTimeMillis());
    }

    public boolean autenticarEnTiempo(UsuarioRegistrado usuarioGuardado, String nombreIngresado, String hashClaveIngresada, long ahoraMs) {
        if (estaBloqueadoEnTiempo(ahoraMs)) {
            return false;
        }

        boolean credencialesValidas = usuarioGuardado != null
                && nombreIngresado != null
                && hashClaveIngresada != null
                && usuarioGuardado.getNombreUsuario().equals(nombreIngresado.trim())
                && usuarioGuardado.getHashClave().equals(hashClaveIngresada.trim());

        if (credencialesValidas) {
            intentosFallidos = 0;
            bloqueadoHastaMs = 0L;
            sesionActiva = true;
            usuarioActivo = usuarioGuardado.getNombreUsuario();
            metodoAcceso = "Contraseña";
            ultimoResumenBiometrico = null;
            return true;
        }

        registrarFalloAccesoEnTiempo(ahoraMs);
        return false;
    }

    public boolean iniciarSesionBiometrica(
            UsuarioRegistrado usuarioGuardado,
            boolean coincideBiometria,
            String resumenBiometrico
    ) {
        return iniciarSesionBiometricaEnTiempo(
                usuarioGuardado,
                coincideBiometria,
                resumenBiometrico,
                System.currentTimeMillis()
        );
    }

    public boolean iniciarSesionBiometricaEnTiempo(
            UsuarioRegistrado usuarioGuardado,
            boolean coincideBiometria,
            String resumenBiometrico,
            long ahoraMs
    ) {
        if (estaBloqueadoEnTiempo(ahoraMs)) {
            return false;
        }

        if (usuarioGuardado != null && coincideBiometria) {
            intentosFallidos = 0;
            bloqueadoHastaMs = 0L;
            sesionActiva = true;
            usuarioActivo = usuarioGuardado.getNombreUsuario();
            metodoAcceso = "Biometría Facial 1:1";
            ultimoResumenBiometrico = resumenBiometrico;
            return true;
        }

        registrarFalloAccesoEnTiempo(ahoraMs);
        return false;
    }

    public void registrarFalloAcceso() {
        registrarFalloAccesoEnTiempo(System.currentTimeMillis());
    }

    public void registrarFalloAccesoEnTiempo(long ahoraMs) {
        if (estaBloqueadoEnTiempo(ahoraMs)) {
            return;
        }
        sesionActiva = false;
        usuarioActivo = null;
        metodoAcceso = null;
        ultimoResumenBiometrico = null;
        intentosFallidos++;
        if (intentosFallidos >= MAX_INTENTOS_FALLIDOS) {
            bloqueadoHastaMs = ahoraMs + TIEMPO_BLOQUEO_MS;
        }
    }

    public boolean estaBloqueado() {
        return estaBloqueadoEnTiempo(System.currentTimeMillis());
    }

    public boolean estaBloqueadoEnTiempo(long ahoraMs) {
        if (bloqueadoHastaMs <= 0L) {
            return false;
        }
        if (ahoraMs < bloqueadoHastaMs) {
            return true;
        }
        bloqueadoHastaMs = 0L;
        intentosFallidos = 0;
        return false;
    }

    public long obtenerSegundosRestantesBloqueo() {
        return obtenerSegundosRestantesBloqueoEnTiempo(System.currentTimeMillis());
    }

    public long obtenerSegundosRestantesBloqueoEnTiempo(long ahoraMs) {
        if (!estaBloqueadoEnTiempo(ahoraMs)) {
            return 0L;
        }
        return (long) Math.ceil((bloqueadoHastaMs - ahoraMs) / 1000.0);
    }

    public void cerrarSesion() {
        sesionActiva = false;
        usuarioActivo = null;
        metodoAcceso = null;
        ultimoResumenBiometrico = null;
    }

    public void reiniciarIntentos() {
        intentosFallidos = 0;
        bloqueadoHastaMs = 0L;
    }

    public boolean isSesionActiva() {
        return sesionActiva;
    }

    public String getUsuarioActivo() {
        return usuarioActivo;
    }

    public String getMetodoAcceso() {
        return metodoAcceso;
    }

    public String getUltimoResumenBiometrico() {
        return ultimoResumenBiometrico;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public int getIntentosRestantes() {
        return Math.max(0, MAX_INTENTOS_FALLIDOS - intentosFallidos);
    }
}
