package service.faceid;

public record FaceVerificationResult(boolean verified, double similarityScore, boolean rateLimited, String reason) {

    public static FaceVerificationResult failure(String reason) {
        return new FaceVerificationResult(false, 0.0, false,
                reason == null || reason.isBlank() ? "Face verification failed" : reason);
    }

    public static FaceVerificationResult blocked(String reason) {
        return new FaceVerificationResult(false, 0.0, true,
                reason == null || reason.isBlank() ? "Face verification is temporarily blocked" : reason);
    }
}
