package entities.job_offer;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * Audit log entry for ATS-related changes.
 * Tracks stage changes, score recalculations, note additions, and offer edits.
 */
@Entity
@Table(name = "ats_audit_log", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_actor",  columnList = "actor_id"),
    @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AtsAuditLog implements java.io.Serializable {

    // ── Action constants ──────────────────────────────────────────────────────
    public static final String ACTION_STAGE_CHANGED      = "STAGE_CHANGED";
    public static final String ACTION_STATUS_CHANGED     = "STATUS_CHANGED";
    public static final String ACTION_SCORE_CALCULATED   = "SCORE_CALCULATED";
    public static final String ACTION_NOTE_ADDED         = "NOTE_ADDED";
    public static final String ACTION_OFFER_CREATED      = "OFFER_CREATED";
    public static final String ACTION_OFFER_UPDATED      = "OFFER_UPDATED";
    public static final String ACTION_OFFER_STATUS       = "OFFER_STATUS_CHANGED";
    public static final String ACTION_CV_EXTRACTED       = "CV_EXTRACTED";

    public static final String ENTITY_JOB_APPLICATION   = "JOB_APPLICATION";
    public static final String ENTITY_JOB_OFFER         = "JOB_OFFER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private int entityId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "actor_id")
    private Integer actorId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, length = 19)
    private Timestamp createdAt;

    public AtsAuditLog() {}

    public AtsAuditLog(String entityType, int entityId, String action,
                       Integer actorId, String oldValue, String newValue) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actorId = actorId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static AtsAuditLog stageChange(int applicationId, Integer actorId, String from, String to) {
        return new AtsAuditLog(ENTITY_JOB_APPLICATION, applicationId, ACTION_STAGE_CHANGED, actorId, from, to);
    }

    public static AtsAuditLog scoreCalculated(int applicationId, Integer actorId, String score) {
        return new AtsAuditLog(ENTITY_JOB_APPLICATION, applicationId, ACTION_SCORE_CALCULATED, actorId, null, score);
    }

    public static AtsAuditLog noteAdded(int applicationId, Integer actorId, String note) {
        return new AtsAuditLog(ENTITY_JOB_APPLICATION, applicationId, ACTION_NOTE_ADDED, actorId, null, note);
    }

    public static AtsAuditLog offerStatusChange(int offerId, Integer actorId, String from, String to) {
        return new AtsAuditLog(ENTITY_JOB_OFFER, offerId, ACTION_OFFER_STATUS, actorId, from, to);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public int getEntityId() { return entityId; }
    public void setEntityId(int entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getActorId() { return actorId; }
    public void setActorId(Integer actorId) { this.actorId = actorId; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
