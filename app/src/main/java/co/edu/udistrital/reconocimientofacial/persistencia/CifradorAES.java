package co.edu.udistrital.reconocimientofacial.persistencia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;

public class CifradorAES {
    private static final String ALGORITMO = "AES/CBC/PKCS5Padding";
    private static final int TAMANO_IV = 16;

    public String cifrar(String textoPlano, String claveTexto) throws BiometriaException {
        if (textoPlano == null || claveTexto == null) throw new BiometriaException("BIO-CIF-01", "Datos nulos para cifrar.");
        try {
            byte[] iv = new byte[TAMANO_IV];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, generarClaveAes(claveTexto), new IvParameterSpec(iv));
            return aHex(iv) + ":" + aHex(cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BiometriaException("BIO-CIF-01", "Error al cifrar datos: " + e.getMessage(), e);
        }
    }

    public String descifrar(String textoCifrado, String claveTexto) throws BiometriaException {
        if (textoCifrado == null || claveTexto == null || !textoCifrado.contains(":")) {
            throw new BiometriaException("BIO-CIF-02", "Datos invalidos para descifrar.");
        }
        try {
            String[] partes = textoCifrado.split(":", 2);
            byte[] iv = deHex(partes[0]);
            byte[] cifrado = deHex(partes[1]);
            if (iv.length != TAMANO_IV || cifrado.length == 0) throw new IllegalArgumentException("IV o bloque invalido.");
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, generarClaveAes(claveTexto), new IvParameterSpec(iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BiometriaException("BIO-CIF-02", "Error al descifrar datos: " + e.getMessage(), e);
        }
    }

    public static String sha256Hex(String texto) throws BiometriaException {
        if (texto == null) throw new BiometriaException("BIO-CIF-03", "Texto nulo para hash.");
        try {
            return aHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BiometriaException("BIO-CIF-03", "Error al calcular hash: " + e.getMessage(), e);
        }
    }

    private SecretKeySpec generarClaveAes(String claveTexto) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(claveTexto.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    private static String aHex(byte[] datos) {
        StringBuilder sb = new StringBuilder(datos.length * 2);
        for (byte b : datos) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private static byte[] deHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) throw new IllegalArgumentException("Hex invalido.");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return out;
    }
}
