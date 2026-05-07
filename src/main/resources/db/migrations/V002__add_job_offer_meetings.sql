CREATE TABLE IF NOT EXISTS job_offer_meeting (
    id INT NOT NULL AUTO_INCREMENT,
    application_id INT NOT NULL,
    offer_id INT NOT NULL,
    student_id INT NOT NULL,
    partner_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    room_code VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'scheduled',
    scheduled_at DATETIME NOT NULL,
    scheduled_end_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_offer_meeting_application (application_id),
    KEY idx_job_offer_meeting_offer (offer_id),
    KEY idx_job_offer_meeting_student (student_id),
    KEY idx_job_offer_meeting_partner (partner_id),
    KEY idx_job_offer_meeting_status (status),
    KEY idx_job_offer_meeting_scheduled (scheduled_at),
    KEY idx_job_offer_meeting_window (scheduled_at, scheduled_end_at),
    CONSTRAINT fk_job_offer_meeting_application
        FOREIGN KEY (application_id) REFERENCES job_application (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_job_offer_meeting_offer
        FOREIGN KEY (offer_id) REFERENCES job_offer (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_job_offer_meeting_student
        FOREIGN KEY (student_id) REFERENCES user (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_job_offer_meeting_partner
        FOREIGN KEY (partner_id) REFERENCES user (id)
        ON DELETE CASCADE
);
