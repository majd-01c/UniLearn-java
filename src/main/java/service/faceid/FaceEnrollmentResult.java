package service.faceid;

import entities.User;

import java.sql.Timestamp;

public record FaceEnrollmentResult(boolean success, String reason, Timestamp enrolledAt, User user) {

    public static FaceEnrollmentResult failed(String reason) {
        return new FaceEnrollmentResult(false,
                reason == null || reason.isBlank() ? "Face enrollment failed" : reason,
                null,
                null);
    }
}
