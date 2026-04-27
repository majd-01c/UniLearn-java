package service.faceid;

import entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.FaceVerificationLogRepository;
import service.UserService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaceRecognitionServiceTest {

    @Mock
    private FaceProvider faceProvider;

    @Mock
    private UserService userService;

    @Mock
    private FaceVerificationLogRepository faceLogRepository;

    private FaceRecognitionService faceRecognitionService;
    private Path tempImagePath;

    @BeforeEach
    void setUp() throws Exception {
        faceRecognitionService = new FaceRecognitionService(faceProvider, userService, faceLogRepository);

        BufferedImage image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_RGB);
        tempImagePath = Files.createTempFile("face-test-", ".png");
        ImageIO.write(image, "png", tempImagePath.toFile());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempImagePath != null) {
            Files.deleteIfExists(tempImagePath);
        }
    }

    @Test
    void verifyFace_whenDisabled_returnsFailureWithoutProviderCall() {
        User user = user(11, false, "abc123");

        FaceVerificationResult result = faceRecognitionService.verifyFace(user, tempImagePath.toFile());

        assertFalse(result.verified());
        assertTrue(result.reason().toLowerCase().contains("disabled"));
        verifyNoInteractions(faceProvider);
    }

    @Test
    void verifyFace_afterTooManyFailures_becomesRateLimited() {
        User user = user(12, true, "beef");

        when(faceProvider.detectSingleFace(any())).thenReturn(FaceDetectionResult.success());
        when(faceProvider.extractEmbedding(any())).thenReturn("feed");
        when(faceProvider.compareEmbeddings(any(), any()))
                .thenReturn(new FaceComparisonResult(false, 0.42, "Mismatch"));

        for (int i = 0; i < 5; i++) {
            FaceVerificationResult result = faceRecognitionService.verifyFace(user, tempImagePath.toFile());
            assertFalse(result.verified());
            assertFalse(result.rateLimited());
        }

        FaceVerificationResult blockedResult = faceRecognitionService.verifyFace(user, tempImagePath.toFile());
        assertFalse(blockedResult.verified());
        assertTrue(blockedResult.rateLimited());
        assertTrue(blockedResult.reason().toLowerCase().contains("too many failed attempts"));
    }

    @Test
    void enrollFace_success_persistsEmbeddingAndTimestamp() {
        User user = user(13, false, null);

        when(faceProvider.detectSingleFace(any())).thenReturn(FaceDetectionResult.success());
        when(faceProvider.extractEmbedding(any())).thenReturn("deadbeef");
        when(userService.updateUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaceEnrollmentResult result = faceRecognitionService.enrollFace(user, tempImagePath.toFile());

        assertTrue(result.success());
        assertNotNull(result.user());
        assertEquals("deadbeef", result.user().getFaceEmbedding());
        assertNotNull(result.user().getFaceEnrolledAt());
        verify(userService, times(1)).updateUser(any(User.class));
    }

    private User user(int id, boolean faceEnabled, String embedding) {
        User user = new User();
        user.setId(id);
        user.setFaceIdEnabled(faceEnabled);
        user.setFaceEmbedding(embedding);
        return user;
    }
}
