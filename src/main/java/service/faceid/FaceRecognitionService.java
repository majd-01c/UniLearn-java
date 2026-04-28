package service.faceid;

import entities.FaceVerificationLog;
import entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.FaceVerificationLogRepository;
import service.UserService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class FaceRecognitionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceRecognitionService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(2);

    private static final String LOG_ACTION_ENROLL_OK = "ENROLL_OK";
    private static final String LOG_ACTION_ENROLL_FAIL = "ENROLL_FAIL";
    private static final String LOG_ACTION_VERIFY_OK = "VERIFY_OK";
    private static final String LOG_ACTION_VERIFY_FAIL = "VERIFY_FAIL";
    private static final String LOG_ACTION_VERIFY_BLOCKED = "VERIFY_BLOCKED";
    private static final String LOG_ACTION_VERIFY_DISABLED = "VERIFY_DISABLED";

    private final FaceProvider faceProvider;
    private final UserService userService;
    private final FaceVerificationLogRepository faceLogRepository;
    private final FaceQualityGateClient qualityGateClient = new FaceQualityGateClient();

    private final Map<Integer, Deque<Instant>> failedAttemptsByUser = new ConcurrentHashMap<>();
    private final Map<Integer, Instant> blockedUntilByUser = new ConcurrentHashMap<>();

    public FaceRecognitionService() {
        this(new LocalHeuristicFaceProvider(), new UserService(), new FaceVerificationLogRepository());
    }

    FaceRecognitionService(FaceProvider faceProvider,
                           UserService userService,
                           FaceVerificationLogRepository faceLogRepository) {
        this.faceProvider = faceProvider;
        this.userService = userService;
        this.faceLogRepository = faceLogRepository;
    }

    public FaceEnrollmentResult enrollFace(User user, File imageFile) {
        if (user == null || user.getId() == null) {
            return FaceEnrollmentResult.failed("A valid user is required for enrollment");
        }

        try {
            BufferedImage image = loadImage(imageFile);
            // Run remote quality gate before any heavy processing
            QualityResult quality = qualityGateClient.checkImageQuality(imageFile, user.getId() == null ? null : user.getId().intValue(), "upload");
            if (quality != null && quality.hasError()) {
                String msg = "Face quality check error: " + quality.getErrorMessage();
                logAudit(user, LOG_ACTION_ENROLL_FAIL, null, msg);
                // If bypass is allowed by configuration, log and continue; otherwise block enrollment
                if (!qualityGateClient.isAllowBypass()) {
                    return FaceEnrollmentResult.failed(msg);
                } else {
                    LOGGER.warn("Quality gate error bypassed for userId={}: {}", safeUserId(user), quality.getErrorMessage());
                }
            } else if (quality != null && !quality.isPassed()) {
                // Build a readable failure message from tips
                String tipsMsg = "";
                if (quality.getTips() != null && !quality.getTips().isEmpty()) {
                    tipsMsg = String.join("; ", quality.getTips());
                }
                String reason = "Face quality check failed" + (tipsMsg.isBlank() ? "" : ": " + tipsMsg);
                logAudit(user, LOG_ACTION_ENROLL_FAIL, null, reason);
                return FaceEnrollmentResult.failed(reason);
            }
            FaceDetectionResult detection = faceProvider.detectSingleFace(image);
            if (!detection.singleFaceDetected()) {
                logAudit(user, LOG_ACTION_ENROLL_FAIL, null, detection.reason());
                return FaceEnrollmentResult.failed(detection.reason());
            }

            String embedding = faceProvider.extractEmbedding(image);
            if (embedding == null || embedding.isBlank()) {
                logAudit(user, LOG_ACTION_ENROLL_FAIL, null, "Face template extraction failed");
                return FaceEnrollmentResult.failed("Unable to extract a face template from this image");
            }

            user.setFaceEmbedding(embedding);
            user.setFaceEnrolledAt(Timestamp.from(Instant.now()));

            User updatedUser = userService.updateUser(user);
            logAudit(updatedUser, LOG_ACTION_ENROLL_OK, null, "Face enrollment successful");

            return new FaceEnrollmentResult(true, "Face enrollment successful", updatedUser.getFaceEnrolledAt(), updatedUser);
        } catch (IllegalArgumentException exception) {
            logAudit(user, LOG_ACTION_ENROLL_FAIL, null, exception.getMessage());
            return FaceEnrollmentResult.failed(exception.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("Face enrollment failed for userId={}", safeUserId(user), exception);
            logAudit(user, LOG_ACTION_ENROLL_FAIL, null, "Unexpected enrollment error");
            return FaceEnrollmentResult.failed("Unable to complete face enrollment. Please try another image");
        }
    }

    public FaceVerificationResult verifyFace(User user, File imageFile) {
        if (user == null || user.getId() == null) {
            return FaceVerificationResult.failure("A valid user is required for Face ID verification");
        }

        Instant now = Instant.now();
        FaceVerificationResult blockStatus = getBlockStatus(user, now);
        if (blockStatus != null) {
            logAudit(user, LOG_ACTION_VERIFY_BLOCKED, null, blockStatus.reason());
            return blockStatus;
        }

        if (!user.isFaceIdEnabled()) {
            String reason = "Face ID is disabled for this account";
            logAudit(user, LOG_ACTION_VERIFY_DISABLED, null, reason);
            return FaceVerificationResult.failure(reason);
        }

        if (!isFaceEnrolled(user)) {
            String reason = "Face ID is not enrolled for this account";
            logAudit(user, LOG_ACTION_VERIFY_FAIL, null, reason);
            return FaceVerificationResult.failure(reason);
        }

        try {
            BufferedImage image = loadImage(imageFile);
            FaceDetectionResult detection = faceProvider.detectSingleFace(image);
            if (!detection.singleFaceDetected()) {
                registerFailedAttempt(user.getId(), now);
                logAudit(user, LOG_ACTION_VERIFY_FAIL, null, detection.reason());
                return FaceVerificationResult.failure(detection.reason());
            }

            String probeEmbedding = faceProvider.extractEmbedding(image);
            FaceComparisonResult comparison = faceProvider.compareEmbeddings(user.getFaceEmbedding(), probeEmbedding);

            if (comparison.matched()) {
                clearFailedAttempts(user.getId());
                logAudit(user, LOG_ACTION_VERIFY_OK, 1.0 - comparison.similarityScore(), comparison.reason());
                return new FaceVerificationResult(true, comparison.similarityScore(), false, comparison.reason());
            }

            registerFailedAttempt(user.getId(), now);
            logAudit(user, LOG_ACTION_VERIFY_FAIL, 1.0 - comparison.similarityScore(), comparison.reason());
            return new FaceVerificationResult(false, comparison.similarityScore(), false, comparison.reason());
        } catch (IllegalArgumentException exception) {
            registerFailedAttempt(user.getId(), now);
            logAudit(user, LOG_ACTION_VERIFY_FAIL, null, exception.getMessage());
            return FaceVerificationResult.failure(exception.getMessage());
        } catch (Exception exception) {
            registerFailedAttempt(user.getId(), now);
            LOGGER.warn("Face verification failed for userId={}", user.getId(), exception);
            logAudit(user, LOG_ACTION_VERIFY_FAIL, null, "Unexpected verification error");
            return FaceVerificationResult.failure("Unable to process face verification. Please try again");
        }
    }

    public User setFaceIdEnabled(User user, boolean enabled) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A valid user is required");
        }

        user.setFaceIdEnabled(enabled);
        return userService.updateUser(user);
    }

    public User clearEnrollment(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A valid user is required");
        }

        user.setFaceEmbedding(null);
        user.setFaceEnrolledAt(null);
        user.setFaceIdEnabled(false);

        User updated = userService.updateUser(user);
        clearFailedAttempts(updated.getId());
        return updated;
    }

    public boolean isFaceEnrolled(User user) {
        return user != null
                && user.getFaceEmbedding() != null
                && !user.getFaceEmbedding().isBlank();
    }

    /**
     * Expose whether the configured quality gate allows bypassing failures.
     */
    public boolean isQualityGateBypassAllowed() {
        try {
            return qualityGateClient.isAllowBypass();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Force enrollment skipping the external quality gate.
     */
    public FaceEnrollmentResult enrollFaceSkippingQuality(User user, File imageFile) {
        if (user == null || user.getId() == null) {
            return FaceEnrollmentResult.failed("A valid user is required for enrollment");
        }

        try {
            BufferedImage image = loadImage(imageFile);

            FaceDetectionResult detection = faceProvider.detectSingleFace(image);
            if (!detection.singleFaceDetected()) {
                logAudit(user, LOG_ACTION_ENROLL_FAIL, null, detection.reason());
                return FaceEnrollmentResult.failed(detection.reason());
            }

            String embedding = faceProvider.extractEmbedding(image);
            if (embedding == null || embedding.isBlank()) {
                logAudit(user, LOG_ACTION_ENROLL_FAIL, null, "Face template extraction failed");
                return FaceEnrollmentResult.failed("Unable to extract a face template from this image");
            }

            user.setFaceEmbedding(embedding);
            user.setFaceEnrolledAt(Timestamp.from(Instant.now()));

            User updatedUser = userService.updateUser(user);
            logAudit(updatedUser, LOG_ACTION_ENROLL_OK, null, "Face enrollment successful");

            return new FaceEnrollmentResult(true, "Face enrollment successful", updatedUser.getFaceEnrolledAt(), updatedUser);
        } catch (IllegalArgumentException exception) {
            logAudit(user, LOG_ACTION_ENROLL_FAIL, null, exception.getMessage());
            return FaceEnrollmentResult.failed(exception.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("Face enrollment failed for userId={}", safeUserId(user), exception);
            logAudit(user, LOG_ACTION_ENROLL_FAIL, null, "Unexpected enrollment error");
            return FaceEnrollmentResult.failed("Unable to complete face enrollment. Please try another image");
        }
    }

    private BufferedImage loadImage(File imageFile) {
        if (imageFile == null) {
            throw new IllegalArgumentException("No image file selected");
        }
        if (!imageFile.exists() || !imageFile.isFile()) {
            throw new IllegalArgumentException("Selected image file does not exist");
        }

        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new IllegalArgumentException("Selected file is not a valid image");
            }
            return image;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to read image file", exception);
        }
    }

    private FaceVerificationResult getBlockStatus(User user, Instant now) {
        Instant blockedUntil = blockedUntilByUser.get(user.getId());
        if (blockedUntil == null) {
            return null;
        }

        if (now.isBefore(blockedUntil)) {
            long seconds = Math.max(1L, Duration.between(now, blockedUntil).toSeconds());
            return FaceVerificationResult.blocked("Too many failed attempts. Try again in " + seconds + " seconds");
        }

        blockedUntilByUser.remove(user.getId());
        return null;
    }

    private void registerFailedAttempt(Integer userId, Instant now) {
        if (userId == null) {
            return;
        }

        Deque<Instant> attempts = failedAttemptsByUser.computeIfAbsent(userId, ignored -> new ConcurrentLinkedDeque<>());
        attempts.addLast(now);
        pruneOldAttempts(attempts, now);

        if (attempts.size() >= MAX_FAILED_ATTEMPTS) {
            blockedUntilByUser.put(userId, now.plus(LOCK_DURATION));
            attempts.clear();
        }
    }

    private void clearFailedAttempts(Integer userId) {
        if (userId == null) {
            return;
        }

        failedAttemptsByUser.remove(userId);
        blockedUntilByUser.remove(userId);
    }

    private void pruneOldAttempts(Deque<Instant> attempts, Instant now) {
        Instant threshold = now.minus(ATTEMPT_WINDOW);
        while (!attempts.isEmpty()) {
            Instant first = attempts.peekFirst();
            if (first == null || !first.isBefore(threshold)) {
                break;
            }
            attempts.pollFirst();
        }
    }

    private void logAudit(User user, String action, Double distance, String reason) {
        if (user == null || user.getId() == null) {
            return;
        }

        String safeReason = sanitizeReason(reason);
        LOGGER.info("Face ID action={} userId={} detail={} scoreDistance={}",
                action,
                user.getId(),
                safeReason,
                distance == null ? "n/a" : String.format("%.4f", distance));

        try {
            FaceVerificationLog log = new FaceVerificationLog();
            log.setUser(user);
            log.setAction(action);
            log.setDistance(distance);
            log.setIpAddress("desktop-local");
            log.setCreatedAt(Timestamp.from(Instant.now()));
            faceLogRepository.save(log);
        } catch (Exception exception) {
            LOGGER.warn("Unable to persist face verification audit log for userId={}", user.getId(), exception);
        }
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "n/a";
        }

        String cleaned = reason.replaceAll("[\r\n\t]", " ").trim();
        if (cleaned.length() <= 120) {
            return cleaned;
        }
        return cleaned.substring(0, 117) + "...";
    }

    private Integer safeUserId(User user) {
        return user == null ? null : user.getId();
    }
}
