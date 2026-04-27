package service.faceid;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class LocalHeuristicFaceProviderTest {

    private final LocalHeuristicFaceProvider provider = new LocalHeuristicFaceProvider();

    @Test
    void compareEmbeddings_identicalHash_matches() {
        FaceComparisonResult result = provider.compareEmbeddings("ffffffff", "ffffffff");

        assertTrue(result.matched());
        assertEquals(1.0, result.similarityScore(), 0.0001);
    }

    @Test
    void compareEmbeddings_differentHash_fails() {
        FaceComparisonResult result = provider.compareEmbeddings("ffffffff", "00000000");

        assertFalse(result.matched());
        assertTrue(result.similarityScore() < 0.82);
    }

    @Test
    void detectSingleFace_singleCluster_returnsSuccess() {
        BufferedImage image = new BufferedImage(220, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 220, 220);
        g.setColor(new Color(205, 162, 130));
        g.fillOval(60, 30, 100, 150);
        g.dispose();

        FaceDetectionResult result = provider.detectSingleFace(image);

        assertTrue(result.singleFaceDetected());
        assertEquals(1, result.faceCount());
    }

    @Test
    void detectSingleFace_multipleClusters_returnsFailure() {
        BufferedImage image = new BufferedImage(260, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 260, 220);
        g.setColor(new Color(205, 162, 130));
        g.fillOval(20, 30, 90, 140);
        g.fillOval(150, 40, 90, 140);
        g.dispose();

        FaceDetectionResult result = provider.detectSingleFace(image);

        assertFalse(result.singleFaceDetected());
        assertTrue(result.faceCount() >= 2);
    }
}
