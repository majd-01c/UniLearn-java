package service.job_offer;

import entities.job_offer.AtsAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.job_offer.AtsAuditLogRepository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Service for writing and reading ATS audit log entries.
 *
 * All write operations are fire-and-forget — audit failures never interrupt
 * the main business flow. Use the {@code logXxx} methods from service/controller code.
 */
public class AtsAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtsAuditService.class);

    private final AtsAuditLogRepository repository;

    public AtsAuditService() {
        this(new AtsAuditLogRepository());
    }

    public AtsAuditService(AtsAuditLogRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    public void logStageChange(int applicationId, Integer actorId, String from, String to) {
        persist(AtsAuditLog.stageChange(applicationId, actorId, from, to));
    }

    public void logScoreCalculated(int applicationId, Integer actorId, int score) {
        persist(AtsAuditLog.scoreCalculated(applicationId, actorId, String.valueOf(score)));
    }

    public void logNoteAdded(int applicationId, Integer actorId, String note) {
        persist(AtsAuditLog.noteAdded(applicationId, actorId, note));
    }

    public void logOfferStatusChange(int offerId, Integer actorId, String from, String to) {
        persist(AtsAuditLog.offerStatusChange(offerId, actorId, from, to));
    }

    public void logCvExtracted(int applicationId, Integer actorId) {
        AtsAuditLog entry = new AtsAuditLog(
            AtsAuditLog.ENTITY_JOB_APPLICATION, applicationId,
            AtsAuditLog.ACTION_CV_EXTRACTED, actorId, null, "CV extracted via Gemini API");
        persist(entry);
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Get full audit history for a job application, ordered oldest→newest.
     */
    public List<AtsAuditLog> getApplicationHistory(int applicationId) {
        try {
            return repository.findByEntity(AtsAuditLog.ENTITY_JOB_APPLICATION, applicationId);
        } catch (Exception e) {
            LOGGER.warn("Could not load audit history for application {}", applicationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get full audit history for a job offer, ordered oldest→newest.
     */
    public List<AtsAuditLog> getOfferHistory(int offerId) {
        try {
            return repository.findByEntity(AtsAuditLog.ENTITY_JOB_OFFER, offerId);
        } catch (Exception e) {
            LOGGER.warn("Could not load audit history for offer {}", offerId, e);
            return Collections.emptyList();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Persist and swallow any exception so audit never breaks the main flow. */
    private void persist(AtsAuditLog entry) {
        try {
            repository.save(entry);
        } catch (Exception e) {
            LOGGER.error("Failed to write audit log entry [{}] entity={} id={}",
                    entry.getAction(), entry.getEntityType(), entry.getEntityId(), e);
        }
    }
}
