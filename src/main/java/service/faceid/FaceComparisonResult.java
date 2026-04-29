package service.faceid;

public record FaceComparisonResult(boolean matched, double similarityScore, String reason) {

    public static FaceComparisonResult failed(String reason) {
        return new FaceComparisonResult(false, 0.0, reason == null || reason.isBlank() ? "Face comparison failed" : reason);
    }
}
