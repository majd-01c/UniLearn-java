package service.faceid;

public record FaceDetectionResult(boolean singleFaceDetected, int faceCount, String reason) {

    public static FaceDetectionResult success() {
        return new FaceDetectionResult(true, 1, "Single face detected");
    }

    public static FaceDetectionResult noFace(String reason) {
        return new FaceDetectionResult(false, 0, reason == null || reason.isBlank() ? "No face detected" : reason);
    }

    public static FaceDetectionResult multipleFaces(int faceCount, String reason) {
        int safeCount = Math.max(faceCount, 2);
        return new FaceDetectionResult(false, safeCount,
                reason == null || reason.isBlank() ? "Multiple faces detected" : reason);
    }
}
