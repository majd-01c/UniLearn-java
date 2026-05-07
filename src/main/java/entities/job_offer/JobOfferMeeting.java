package entities.job_offer;

import entities.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "job_offer_meeting",
        uniqueConstraints = @UniqueConstraint(columnNames = "application_id"),
        indexes = {
                @Index(name = "idx_job_offer_meeting_offer", columnList = "offer_id"),
                @Index(name = "idx_job_offer_meeting_student", columnList = "student_id"),
                @Index(name = "idx_job_offer_meeting_partner", columnList = "partner_id"),
                @Index(name = "idx_job_offer_meeting_status", columnList = "status"),
                @Index(name = "idx_job_offer_meeting_scheduled", columnList = "scheduled_at")
        })
public class JobOfferMeeting implements java.io.Serializable {

    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_LIVE = "live";
    public static final String STATUS_ENDED = "ended";
    public static final String STATUS_CANCELLED = "cancelled";

    private static final SecureRandom ROOM_RANDOM = new SecureRandom();

    private Integer id;
    private JobApplication application;
    private JobOffer jobOffer;
    private User student;
    private User partner;
    private String title;
    private String description;
    private String roomCode;
    private String status;
    private Timestamp scheduledAt;
    private Timestamp startedAt;
    private Timestamp endedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public JobOfferMeeting() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.createdAt = now;
        this.updatedAt = now;
        this.roomCode = generateRoomCode();
        this.status = STATUS_SCHEDULED;
    }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    public JobApplication getApplication() {
        return application;
    }

    public void setApplication(JobApplication application) {
        this.application = application;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", nullable = false)
    public JobOffer getJobOffer() {
        return jobOffer;
    }

    public void setJobOffer(JobOffer jobOffer) {
        this.jobOffer = jobOffer;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    public User getPartner() {
        return partner;
    }

    public void setPartner(User partner) {
        this.partner = partner;
    }

    @Column(name = "title", nullable = false, length = 255)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Column(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Column(name = "room_code", nullable = false, length = 100)
    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    @Column(name = "status", nullable = false, length = 20)
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_at", nullable = false, length = 19)
    public Timestamp getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Timestamp scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "started_at", length = 19)
    public Timestamp getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Timestamp startedAt) {
        this.startedAt = startedAt;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ended_at", length = 19)
    public Timestamp getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Timestamp endedAt) {
        this.endedAt = endedAt;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, length = 19)
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, length = 19)
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Transient
    public boolean isLive() {
        return STATUS_LIVE.equals(status);
    }

    @Transient
    public boolean isScheduled() {
        return STATUS_SCHEDULED.equals(status);
    }

    @Transient
    public boolean isEnded() {
        return STATUS_ENDED.equals(status);
    }

    @Transient
    public boolean canJoinNow() {
        return canJoinAt(LocalDateTime.now());
    }

    @Transient
    public boolean canJoinAt(LocalDateTime now) {
        if (now == null || scheduledAt == null || isEnded() || STATUS_CANCELLED.equals(status)) {
            return false;
        }

        LocalDateTime scheduled = scheduledAt.toLocalDateTime();
        return scheduled.toLocalDate().equals(now.toLocalDate()) && !now.isBefore(scheduled);
    }

    public void markLive() {
        if (!isLive()) {
            status = STATUS_LIVE;
            startedAt = new Timestamp(System.currentTimeMillis());
        }
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public void reschedule(Timestamp scheduledAt) {
        this.scheduledAt = scheduledAt;
        this.status = STATUS_SCHEDULED;
        this.startedAt = null;
        this.endedAt = null;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public void end() {
        this.status = STATUS_ENDED;
        this.endedAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = this.endedAt;
    }

    private String generateRoomCode() {
        byte[] bytes = new byte[8];
        ROOM_RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return "unilearn-job-" + hex;
    }
}
