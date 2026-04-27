package service.faceid;

import java.awt.image.BufferedImage;

public interface FaceProvider {

    FaceDetectionResult detectSingleFace(BufferedImage image);

    String extractEmbedding(BufferedImage image);

    FaceComparisonResult compareEmbeddings(String enrolledEmbedding, String probeEmbedding);
}
