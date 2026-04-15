# Job Offer Module - Domain Models & Validation Guide

## Overview

The Job Offer module implements domain-driven design with clear entity models, enums for status management, and comprehensive validation rules. All domain invariants are centralized to ensure consistency across the application.

---

## 1. Entity Models

### JobOffer Entity
**Location:** `entities.job_offer.JobOffer` (Hibernate-generated)

**Core Fields:**
- `id` (Integer): Primary key, auto-generated
- `partnerId` (Integer): Foreign key to User (partner/employer)
- `title` (String): Job title (required, 3-255 chars)
- `type` (String): Job type - see JobOfferType enum
- `description` (String): Full job description (required)
- `requirements` (String): Required qualifications
- `location` (String): Work location
- `requiredSkills` (String): CSV of required skills
- `preferredSkills` (String): CSV of preferred skills
- `minExperienceYears` (Integer): Minimum experience requirement (0-100)
- `minEducation` (String): Minimum education level
- `requiredLanguages` (String): CSV of required languages
- `status` (String): Job offer status - see JobOfferStatus enum
- `createdAt` (Timestamp): Creation timestamp
- `updatedAt` (Timestamp): Last update timestamp
- `publishedAt` (Timestamp): When offer was published/activated
- `expiresAt` (Timestamp): Application deadline

**Relationships:**
- Has many JobApplications (one-to-many, lazy-loaded)
- Belongs to User (partner/employer) via partnerId

**Invariants:**
- Status must be valid enum value
- Title must be non-empty and < 255 chars
- Experience years must be 0-100 if set
- publishedAt must be ≤ current time when status is ACTIVE
- expiresAt must be > createdAt if set
- Status transitions follow state machine (see JobOfferStatus)

### JobApplication Entity
**Location:** `entities.job_offer.JobApplication` (Hibernate-generated)

**Core Fields:**
- `id` (Integer): Primary key, auto-generated
- `offerId` (Integer): Foreign key to JobOffer
- `studentId` (Integer): Foreign key to User (student)
- `status` (String): Application status - see JobApplicationStatus enum
- `message` (String): Motivation letter / cover message (optional)
- `cvFileName` (String): Uploaded CV filename (optional in phase 1)
- `createdAt` (Timestamp): Application submission time
- `updatedAt` (Timestamp): Last update time
- `score` (Integer): Evaluation score (0-100, null if not reviewed)
- `scoreBreakdown` (String): Score justification / detailed feedback
- `scoredAt` (Timestamp): When evaluation occurred
- `extractedData` (String): Reserved for ATS/CV parsing (phase 2)
- `statusNotified` (Byte): Flag if status change was notified to student (0/1)
- `statusNotifiedAt` (Timestamp): When notification was sent
- `statusMessage` (String): Message included in notification

**Relationships:**
- Belongs to JobOffer via offerId
- Belongs to User (student) via studentId
- Database constraint: UNIQUE(offerId, studentId) - one application per student per offer

**Invariants:**
- Status must be valid enum value
- Score must be 0-100 if set (null if not reviewed)
- Status transitions follow state machine (see JobApplicationStatus)
- Cannot create duplicate application (database constraint enforced)
- Score must be set when status transitions to ACCEPTED/REJECTED
- Cannot modify application if offer is not ACTIVE (except for admin review)

---

## 2. Enum Classes

### JobOfferType
**Location:** `entities.job_offer.JobOfferType`

**Values:**
```
INTERNSHIP     - Temporary position for students/graduates gaining experience
APPRENTICESHIP - Structured learning program combining work and training
JOB           - Full-time or part-time permanent position (default)
```

**Helper Methods:**
- `getDescription()`: Human-readable description
- `fromString(String)`: Parse from string (case-insensitive, defaults to JOB)

### JobOfferStatus
**Location:** `entities.job_offer.JobOfferStatus`

**Values & Transitions:**
```
PENDING      → ACTIVE (admin approval) OR REJECTED (admin rejection)
ACTIVE       → CLOSED (when expired or partner closes)
CLOSED       → PENDING (reopen for re-listing)
REJECTED     → (TERMINAL - no transitions out)
```

**Helper Methods:**
- `acceptsApplications()`: Returns true only if ACTIVE
- `isTerminal()`: Returns true only for REJECTED
- `getDescription()`: Status description
- `fromString(String)`: Parse from string (case-insensitive, defaults to PENDING)

**State Machine Diagram:**
```
┌─────────┐
│ PENDING │← Reopen
└────┬────┘
     │
     ├─→ ACTIVE ──→ CLOSED ──┐
     │    (Accept)  (Expire)  │
     │                        │
     └────────────────────────┘
     │
     └─→ REJECTED (Terminal)
```

### JobApplicationStatus
**Location:** `entities.job_offer.JobApplicationStatus`

**Values & Transitions:**
```
SUBMITTED  → REVIEWED (partner reviews)
           → REJECTED (direct rejection)
REVIEWED   → ACCEPTED (positive decision)
           → REJECTED (negative decision)
ACCEPTED   → (TERMINAL - no transitions)
REJECTED   → (TERMINAL - no transitions)
```

**Helper Methods:**
- `hasDecision()`: Returns true if ACCEPTED or REJECTED
- `requiresNotification()`: Returns true if ACCEPTED or REJECTED (student should be notified)
- `isTerminal()`: Returns true if ACCEPTED or REJECTED
- `getDescription()`: Status description
- `fromString(String)`: Parse from string (case-insensitive, defaults to SUBMITTED)

**State Machine Diagram:**
```
┌──────────┐
│ SUBMITTED│
└────┬─────┘
     │
     ├──→ REVIEWED ──┬──→ ACCEPTED (Terminal)
     │               │
     │               └──→ REJECTED (Terminal)
     │
     └──→ REJECTED (Terminal)
```

---

## 3. DTO Classes

### JobOfferDto
**Purpose:** Full data transfer object for job offer display and editing
**Immutability:** All fields final, no setters
**Use Cases:** Detailed view, form pre-population, edit mode

**Key Fields:** offerId, partnerId, title, type, status, location, description, applicationCount

### JobOfferOptionDto
**Purpose:** Minimal DTO for ComboBox/select dropdown
**Use Cases:** Offer selection in forms, partner's offer list
**Fields:** offerId, title
**toString():** Returns title for display

### JobOfferRowDto
**Purpose:** Table row display format for job listing
**Use Cases:** ListView or TableView in JobOfferListController
**Fields:** offerId, title, type, status, location, partnerEmail, applicationCount, createdAt

### JobApplicationDto
**Purpose:** Full application data for detailed views
**Use Cases:** Application detail display, partner review
**Fields:** All fields including score, message, CV filename, etc.

### JobApplicationRowDto
**Purpose:** Table row format for student's application list
**Use Cases:** MyApplicationsController ListView
**Fields:** applicationId, offerId, offerTitle, offerType, status, score, createdAt, updatedAt

### ApplicationReviewRowDto
**Purpose:** Partner review queue format
**Use Cases:** ApplicationReviewController - applications awaiting partner action
**Fields:** applicationId, offerId, offerTitle, studentEmail, status, score, createdAt

---

## 4. Validation Rules

### JobOfferBusinessRules Class

**State Transition Validation:**
```java
boolean canTransitionOfferStatus(JobOfferStatus from, JobOfferStatus to)
// Enforces state machine rules above
// Returns false if transition is invalid

String offerTransitionErrorMessage(JobOfferStatus from, JobOfferStatus to)
// Error description for invalid transition
```

**Application Transition Validation:**
```java
boolean canTransitionApplicationStatus(JobApplicationStatus from, JobApplicationStatus to)
// Enforces state machine rules above

String applicationTransitionErrorMessage(JobApplicationStatus from, JobApplicationStatus to)
// Error description
```

**Permission Rules:**
```java
boolean canStudentApplyToOffer(JobOfferStatus offerStatus)
// true only if status == ACTIVE

boolean canPartnerManageOffer(Integer partnerId, Integer offerOwnerId)
// true if partnerId == offerOwnerId (Owner check)

boolean canPartnerReviewOffer(Integer partnerId, Integer offerOwnerId)
// true if partnerId == offerOwnerId
```

**Notification Rules:**
```java
boolean needsStatusNotification(JobApplicationStatus status)
// true if status is ACCEPTED or REJECTED

boolean hasApplicationDecision(JobApplicationStatus status)
// true if status is ACCEPTED or REJECTED
```

**Input Validation:**
```java
String validateJobOfferInput(String title, String description, String type)
// Returns error message if invalid, null if valid
// Checks: title non-empty (3-255 chars), description non-empty, type valid

String validateJobApplicationInput(String message, String cvFileName)
// Returns error message if invalid, null if valid
// Current phase: permissive (both optional)

String validateExperienceYears(Integer years)
// Returns error if not null and not in 0-100 range

String validateApplicationScore(Integer score)
// Returns error if not null and not in 0-100 range
```

### JobOfferValidators Class

**Email Validation:**
```java
boolean isValidEmail(String email)
// Basic regex pattern: word@domain.extension
```

**Date Validation:**
```java
boolean isOfferExpired(Timestamp expiresAt)
// true if expiresAt is before current time

boolean isPastDate(Timestamp date)
boolean isFutureDate(Timestamp date)
```

**Field Validation:**
```java
boolean isValidLocation(String location)
// Non-empty, at least 2 chars

boolean isValidCvFileName(String cvFileName)
// Checks for .pdf, .doc, .docx, .txt, .rtf extensions
// Returns false if empty

boolean isValidApplicationMessage(String message)
// Max 5000 characters

boolean isValidSkillsField(String skills)
// Max 5000 characters

boolean isValidEducationLevel(String education)
// Must be: HIGH_SCHOOL, BACHELOR, MASTER, PHD, NOT_REQUIRED

String normalizeEducationLevel(String education)
// Returns normalized version or NOT_REQUIRED as default
```

---

## 5. Database Constraints

### job_offer Table
```sql
CREATE TABLE job_offer (
    id INT PRIMARY KEY AUTO_INCREMENT,
    partner_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    location VARCHAR(255),
    description LONGTEXT NOT NULL,
    requirements LONGTEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    expires_at TIMESTAMP,
    required_skills LONGTEXT,
    preferred_skills LONGTEXT,
    min_experience_years INT,
    min_education VARCHAR(50),
    required_languages LONGTEXT,
    FOREIGN KEY (partner_id) REFERENCES user(id) ON DELETE RESTRICT,
    INDEX idx_status (status),
    INDEX idx_partner_id (partner_id),
    INDEX idx_created_at (created_at)
);
```

### job_application Table
```sql
CREATE TABLE job_application (
    id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    offer_id INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    message LONGTEXT,
    cv_file_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    score INT,
    score_breakdown LONGTEXT,
    scored_at TIMESTAMP,
    extracted_data LONGTEXT,
    status_notified TINYINT,
    status_notified_at TIMESTAMP,
    status_message VARCHAR(255),
    FOREIGN KEY (student_id) REFERENCES user(id) ON DELETE RESTRICT,
    FOREIGN KEY (offer_id) REFERENCES job_offer(id) ON DELETE CASCADE,
    UNIQUE KEY uk_student_offer (student_id, offer_id),
    INDEX idx_status (status),
    INDEX idx_student_id (student_id),
    INDEX idx_created_at (created_at)
);
```

---

## 6. Key Invariants Summary

✅ **Always Enforced:**
1. JobOfferStatus respects state machine (PENDING → ACTIVE/REJECTED → CLOSED)
2. JobApplicationStatus respects state machine (SUBMITTED → REVIEWED → ACCEPTED/REJECTED)
3. Unique application per student per offer (DB constraint)
4. Only ACTIVE offers accept applications
5. Score must be 0-100 if set
6. Partner owns their offers (or Admin overrides)
7. REJECTED offer status is terminal (no transitions out)
8. ACCEPTED/REJECTED application status is terminal

⚠️ **Should Be Enforced (Phase 1):**
1. Required fields on offer creation
2. Valid email format for partners
3. Valid score (0-100) before state transition
4. Notification sent flag accurate

---

## 7. Phase 1 vs Phase 2

**Phase 1 (Current):**
- Basic CRUD operations
- Status workflow enforcement (PENDING → ACTIVE/CLOSED → REJECTED)
- Application creation (SUBMITTED state)
- Application review (REVIEWED state)
- Decision recording (ACCEPTED/REJECTED state)
- Permission checks (Partner owns, Admin override)

**Phase 2 (Future):**
- ATS scoring algorithm (extractedData, scoreBreakdown)
- CV parsing (cvFileName handling)
- AI-generated motivation letter assistance
- Email notifications (statusNotified, statusNotifiedAt, statusMessage)
- Advanced matching (skill matching, experience validation)
- Bulk actions (admin approval/rejection of multiple offers)

---

## 8. Usage Examples

### Validating a Job Offer Creation
```java
String error = JobOfferBusinessRules.validateJobOfferInput(title, description, type);
if (error != null) {
    showError("Validation Failed", error);
    return;
}

// Additional field validation
if (!JobOfferValidators.isValidLocation(location)) {
    showError("Invalid location");
    return;
}
```

### Checking Application Status Transition
```java
boolean canTransition = JobOfferBusinessRules
    .canTransitionApplicationStatus(currentStatus, newStatus);
if (!canTransition) {
    String error = JobOfferBusinessRules
        .applicationTransitionErrorMessage(currentStatus, newStatus);
    throw new IllegalStateException(error);
}
```

### Checking Student Eligibility
```java
boolean canApply = JobOfferBusinessRules.canStudentApplyToOffer(offerStatus);
if (!canApply) {
    showError("Cannot Apply", "This offer is not accepting applications");
    return;
}
```

### Permission Check
```java
if (!JobOfferBusinessRules.canPartnerManageOffer(currentPartnerId, offerOwnerId)) {
    throw new SecurityException("Partner cannot manage this offer");
}
```

### Notification Decision
```java
if (JobOfferBusinessRules.needsStatusNotification(newStatus)) {
    sendNotificationToStudent(application);
}
```

---

## 9. Future Extensibility

**For Phase 2 ATS Features:**
- Add `JobApplicationScore` value object for weighted scoring
- Add `ScoringCriteria` enum for evaluation dimensions
- Add `CvData` entity for parsed CV information
- Add `NotificationTemplate` for email message customization

**For Phase 2 AI Features:**
- Add `MotivationLetterAssistant` service
- Add `CandidateRecommendation` DTO
- Add `SkillMatcher` utility class

---

## Contact
Job Offer Module Owner: UniLearn Development Team  
Last Updated: April 13, 2026
