package service.faceid;

import java.nio.file.Path;

public record CameraCaptureResult(boolean success, Path imagePath, String reason) {

    public static CameraCaptureResult failed(String reason) {
        return new CameraCaptureResult(false, null,
                reason == null || reason.isBlank() ? "Unable to capture image from camera" : reason);
    }
}
