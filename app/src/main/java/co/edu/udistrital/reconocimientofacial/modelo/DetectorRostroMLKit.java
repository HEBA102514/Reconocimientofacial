package co.edu.udistrital.reconocimientofacial.modelo;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import co.edu.udistrital.reconocimientofacial.excepciones.BiometriaException;
import co.edu.udistrital.reconocimientofacial.excepciones.MultiplesRostrosException;
import co.edu.udistrital.reconocimientofacial.excepciones.RostroNoDetectadoException;

/**
 * Implementacion de IDetectorRostro mediante Google ML Kit Face Detection en el dispositivo.
 * Recorta el rostro a 112x112 pixeles y captura los angulos Euler (Yaw, Roll) y la
 * probabilidad de ojos abiertos para construir el objeto Fotograma.
 */
public class DetectorRostroMLKit implements IDetectorRostro {

    public static final int TAMANO_RECORTE_MODELO = 112;
    private final FaceDetector detector;

    public DetectorRostroMLKit() {
        FaceDetectorOptions opciones = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build();
        this.detector = FaceDetection.getClient(opciones);
    }

    @Override
    public void detectarYRecortar(Bitmap imagenOriginal, String etiquetaImagen, CallbackDeteccion callback) {
        if (imagenOriginal == null) {
            callback.onErrorDeteccion(new BiometriaException(
                    "BIO-000",
                    "La imagen de " + etiquetaImagen + " es nula o invalida."
            ));
            return;
        }

        InputImage inputImage = InputImage.fromBitmap(imagenOriginal, 0);
        detector.process(inputImage)
                .addOnSuccessListener(rostros -> {
                    if (rostros == null || rostros.isEmpty()) {
                        callback.onErrorDeteccion(new RostroNoDetectadoException(etiquetaImagen));
                        return;
                    }
                    if (rostros.size() > 1) {
                        callback.onErrorDeteccion(new MultiplesRostrosException(etiquetaImagen, rostros.size()));
                        return;
                    }

                    Face rostro = rostros.get(0);
                    Rect bounds = rostro.getBoundingBox();

                    int left = Math.max(0, bounds.left);
                    int top = Math.max(0, bounds.top);
                    int right = Math.min(imagenOriginal.getWidth(), bounds.right);
                    int bottom = Math.min(imagenOriginal.getHeight(), bounds.bottom);

                    int width = Math.max(1, right - left);
                    int height = Math.max(1, bottom - top);

                    Bitmap recorteBruto = Bitmap.createBitmap(imagenOriginal, left, top, width, height);
                    Bitmap rostro112 = Bitmap.createScaledBitmap(
                            recorteBruto,
                            TAMANO_RECORTE_MODELO,
                            TAMANO_RECORTE_MODELO,
                            true
                    );

                    float yawY = rostro.getHeadEulerAngleY();
                    float rollZ = rostro.getHeadEulerAngleZ();
                    float ojoIzq = rostro.getLeftEyeOpenProbability() != null
                            ? rostro.getLeftEyeOpenProbability() : -1.0f;
                    float ojoDer = rostro.getRightEyeOpenProbability() != null
                            ? rostro.getRightEyeOpenProbability() : -1.0f;

                    Fotograma fotograma = new Fotograma(
                            imagenOriginal,
                            rostro112,
                            new Rect(left, top, right, bottom),
                            yawY,
                            rollZ,
                            ojoIzq,
                            ojoDer
                    );
                    callback.onRostroDetectado(fotograma);
                })
                .addOnFailureListener(e -> callback.onErrorDeteccion(new BiometriaException(
                        "BIO-MLKIT",
                        "Fallo al procesar la deteccion facial con ML Kit: " + e.getMessage(),
                        e
                )));
    }

    @Override
    public void cerrar() {
        detector.close();
    }
}
