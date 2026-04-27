package service.faceid;

import com.github.sarxos.webcam.Webcam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CameraService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CameraService.class);

    public boolean isCameraAvailable() {
        try {
            List<Webcam> webcams = Webcam.getWebcams();
            return webcams != null && !webcams.isEmpty();
        } catch (Exception exception) {
            LOGGER.warn("Unable to probe camera availability", exception);
            return false;
        }
    }

    public CameraCaptureResult captureToTempImage() {
        Webcam webcam = null;
        try {
            List<Webcam> webcams = Webcam.getWebcams();
            if (webcams == null || webcams.isEmpty()) {
                return CameraCaptureResult.failed("Camera unavailable. Please use upload image instead");
            }

            webcam = webcams.get(0);
            configurePreferredResolution(webcam);

            webcam.open(true);
            BufferedImage frame = webcam.getImage();
            if (frame == null) {
                return CameraCaptureResult.failed("Camera frame capture failed. Please use upload image instead");
            }

            Path tempFile = Files.createTempFile("unilearn-face-capture-", ".png");
            ImageIO.write(frame, "png", tempFile.toFile());
            return new CameraCaptureResult(true, tempFile, "Camera capture successful");
        } catch (Exception exception) {
            LOGGER.warn("Camera capture failed", exception);
            return CameraCaptureResult.failed("Camera unavailable. Please use upload image instead");
        } finally {
            if (webcam != null) {
                try {
                    if (webcam.isOpen()) {
                        webcam.close();
                    }
                } catch (Exception closeException) {
                    LOGGER.debug("Error while closing camera", closeException);
                }
            }
        }
    }

    public void cleanupCapturedFile(Path imagePath) {
        if (imagePath == null) {
            return;
        }

        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException exception) {
            LOGGER.debug("Unable to delete temporary camera capture file {}", imagePath, exception);
        }
    }

    private void configurePreferredResolution(Webcam webcam) {
        if (webcam == null) {
            return;
        }

        Dimension[] supportedSizes = webcam.getViewSizes();
        if (supportedSizes == null || supportedSizes.length == 0) {
            return;
        }

        Dimension selected = supportedSizes[0];
        int targetArea = 640 * 480;
        int smallestGap = Integer.MAX_VALUE;

        for (Dimension size : supportedSizes) {
            int area = size.width * size.height;
            int gap = Math.abs(area - targetArea);
            if (gap < smallestGap) {
                smallestGap = gap;
                selected = size;
            }
        }

        webcam.setViewSize(selected);
    }
}
