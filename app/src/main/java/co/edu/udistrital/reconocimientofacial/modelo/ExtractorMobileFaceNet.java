package co.edu.udistrital.reconocimientofacial.modelo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.excepciones.ModeloNoDisponibleException;

/**
 * Implementacion de IExtractorCaracteristicas con MobileFaceNet en TensorFlow Lite.
 * Fundamentado en Ma et al. (2026): inferencia ligera en el dispositivo sobre el rostro
 * recortado, normalizacion de pixeles en [-1, 1] y normalizacion L2 del vector resultante.
 */
public class ExtractorMobileFaceNet implements IExtractorCaracteristicas {

    private static final String NOMBRE_ARCHIVO_MODELO = "mobilefacenet.tflite";
    private static final float MEDIA_IMAGEN = 127.5f;
    private static final float DESVIACION_IMAGEN = 128.0f;

    private final Interpreter interpreter;
    private final int batchSizeEntrada;
    private final int altoEntrada;
    private final int anchoEntrada;
    private final int dimensionSalida;

    public ExtractorMobileFaceNet(Context context) throws ModeloNoDisponibleException {
        try {
            MappedByteBuffer bufferModelo = cargarModeloDesdeAssets(context);
            Interpreter.Options opciones = new Interpreter.Options();
            opciones.setNumThreads(4);
            this.interpreter = new Interpreter(bufferModelo, opciones);

            int[] inputShape = this.interpreter.getInputTensor(0).shape();
            this.batchSizeEntrada = inputShape.length >= 1 ? inputShape[0] : 1;
            this.altoEntrada = inputShape.length >= 3 ? inputShape[1] : 112;
            this.anchoEntrada = inputShape.length >= 3 ? inputShape[2] : 112;

            int[] outputShape = this.interpreter.getOutputTensor(0).shape();
            this.dimensionSalida = outputShape.length >= 2 ? outputShape[1] : 192;
        } catch (Exception e) {
            throw new ModeloNoDisponibleException(
                    "No se pudo inicializar el modelo MobileFaceNet (" + NOMBRE_ARCHIVO_MODELO + "): "
                            + e.getMessage(),
                    e
            );
        }
    }

    private MappedByteBuffer cargarModeloDesdeAssets(Context context) throws IOException {
        try (AssetFileDescriptor fileDescriptor = context.getAssets().openFd(NOMBRE_ARCHIVO_MODELO);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    @Override
    public float[] extraerEmbedding(Fotograma fotograma) throws BiometriaException {
        if (fotograma == null || fotograma.getRostroRecortado() == null) {
            throw new BiometriaException("BIO-EXT-01", "El fotograma no contiene un rostro recortado.");
        }
        if (interpreter == null) {
            throw new ModeloNoDisponibleException("El interprete de TensorFlow Lite no esta activo.");
        }

        long inicioNs = System.nanoTime();
        try {
            Bitmap rostroAjustado = fotograma.getRostroRecortado();
            if (rostroAjustado.getWidth() != anchoEntrada || rostroAjustado.getHeight() != altoEntrada) {
                rostroAjustado = Bitmap.createScaledBitmap(rostroAjustado, anchoEntrada, altoEntrada, true);
            }

            ByteBuffer inputBuffer = preprocesarBitmap(rostroAjustado);
            float[][] salidaTensor = new float[batchSizeEntrada][dimensionSalida];

            interpreter.run(inputBuffer, salidaTensor);

            long duracionMs = Math.max(1L, (System.nanoTime() - inicioNs) / 1_000_000L);
            fotograma.setTiempoInferenciaMs(duracionMs);

            float[] vectorNormalizado = normalizarL2(salidaTensor[0]);
            fotograma.setEmbedding(vectorNormalizado);
            return vectorNormalizado;
        } catch (Exception e) {
            throw new ModeloNoDisponibleException(
                    "Error durante la extraccion de caracteristicas en TFLite: " + e.getMessage(),
                    e
            );
        }
    }

    private ByteBuffer preprocesarBitmap(Bitmap bitmap) {
        int bytesPorImagen = altoEntrada * anchoEntrada * 3 * 4;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(batchSizeEntrada * bytesPorImagen);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.rewind();

        int[] pixeles = new int[anchoEntrada * altoEntrada];
        bitmap.getPixels(pixeles, 0, anchoEntrada, 0, 0, anchoEntrada, altoEntrada);

        for (int b = 0; b < batchSizeEntrada; b++) {
            for (int pixelValor : pixeles) {
                int r = (pixelValor >> 16) & 0xFF;
                int g = (pixelValor >> 8) & 0xFF;
                int blue = pixelValor & 0xFF;

                byteBuffer.putFloat((r - MEDIA_IMAGEN) / DESVIACION_IMAGEN);
                byteBuffer.putFloat((g - MEDIA_IMAGEN) / DESVIACION_IMAGEN);
                byteBuffer.putFloat((blue - MEDIA_IMAGEN) / DESVIACION_IMAGEN);
            }
        }
        byteBuffer.rewind();
        return byteBuffer;
    }

    /**
     * Normaliza un vector en norma L2 (longitud unitaria = 1.0) para que el producto punto
     * corresponda exactamente a la similitud coseno.
     */
    public static float[] normalizarL2(float[] vector) {
        double sumaCuadrados = 0.0;
        for (float v : vector) {
            sumaCuadrados += (double) v * v;
        }
        double norma = Math.sqrt(sumaCuadrados + 1e-10);
        float[] resultado = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            resultado[i] = (float) (vector[i] / norma);
        }
        return resultado;
    }

    @Override
    public int getDimensionEmbedding() {
        return dimensionSalida;
    }

    @Override
    public void cerrar() {
        if (interpreter != null) {
            interpreter.close();
        }
    }
}
