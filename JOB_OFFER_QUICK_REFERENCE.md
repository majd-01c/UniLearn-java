# Quick Reference: Job Offer Domain Model

## Files Created in PROMPT 03

### Location Map
```
entities/job_offer/
├── JobOfferType.java              ← ENUM
├── JobOfferStatus.java            ← ENUM (state machine)
├── JobApplicationStatus.java      ← ENUM (state machine)
└── package-info.java

dto/job_offer/
├── JobOfferDto.java               ← Full offer data
├── JobOfferOptionDto.java         ← ComboBox selection
├── JobOfferRowDto.java            ← Table display
├── JobApplicationDto.java         ← Full application data
├── JobApplicationRowDto.java      ← Student app list table
├── ApplicationReviewRowDto.java    ← Partner review queue
└── package-info.java

util/job_offer/
├── JobOfferBusinessRules.java     ← State machines + permissions
├── JobOfferValidators.java        ← Input validation helpers
└── package-info.java

Documentation:
├── JOB_OFFER_DOMAIN_MODELS.md     ← Complete reference (400+ lines)
└── PROMPT_03_COMPLETION_SUMMARY.md ← This phase summary
```

---

## Enum Cheat Sheet

### JobOfferType
```java
JobOfferType type = JobOfferType.JOB;
String desc = type.getDescription();
JobOfferType parsed = JobOfferType.fromString("job");  // Case-insensitive
```

### JobOfferStatus
```java
JobOfferStatus status = JobOfferStatus.PENDING;
boolean acceptsApps = status.acceptsApplications();    // true only if ACTIVE
boolean isTerminal = status.isTerminal();              // true only if REJECTED
```

### JobApplicationStatus
```java
JobApplicationStatus status = JobApplicationStatus.SUBMITTED;
boolean hasDecision = status.hasDecision();            // true if ACCEPTED|REJECTED
boolean needsNotif = status.requiresNotification();    // true if ACCEPTED|REJECTED
boolean isTerminal = status.isTerminal();              // true if ACCEPTED|REJECTED
```

---

## DTO Cheat Sheet

### For List Display
```java
// Use JobOfferRowDto for table/ListView
JobOfferRowDto row = new JobOfferRowDto(offerId, title, type, status, 
                                        location, email, appCount, date);
listView.setItems(FXCollections.observableArrayList(rows));
```

### For ComboBox Selection
```java
// Use JobOfferOptionDto for dropdown
List<JobOfferOptionDto> options = Service.getAllOfferOptions();
ComboBox<JobOfferOptionDto> combo = new ComboBox<>(
    FXCollections.observableArrayList(options)
);
// toString() automatically returns title for display
```

### For Detailed View
```java
// Use JobOfferDto for detail view or form pre-population
JobOfferDto fullData = Service.getOfferFullData(offerId);
titleField.setText(fullData.getTitle());
descArea.setText(fullData.getDescription());
```

---

## Validation Cheat Sheet

### Check if Student Can Apply
```java
if (!JobOfferBusinessRules.canStudentApplyToOffer(offer.getStatus())) {
    showError("Offer not accepting applications");
    return;
}
```

### Validate Form Input
```java
String error = JobOfferBusinessRules.validateJobOfferInput(title, desc, type);
if (error != null) {
    showError(error);
    return;
}
```

### Check Status Transition
```java
JobOfferStatus newStatus = JobOfferStatus.ACTIVE;
if (!JobOfferBusinessRules.canTransitionOfferStatus(currentStatus, newStatus)) {
    String msg = JobOfferBusinessRules.offerTransitionErrorMessage(currentStatus, newStatus);
    throw new IllegalStateException(msg);
}
```

### Check Permissions
```java
if (!JobOfferBusinessRules.canPartnerManageOffer(partnerId, offerOwnerId)) {
    throw new SecurityException("Not authorized");
}
```

### Validate Fields
```java
String email = emailField.getText();
if (!JobOfferValidators.isValidEmail(email)) {
    showError("Invalid email format");
    return;
}

if (!JobOfferValidators.isValidCvFileName(cvFile)) {
    showError("Invalid CV file - use .pdf, .doc, or .docx");
    return;
}
```

---

## State Machine Reference

### JobOfferStatus Transitions
```
PENDING         → ACTIVE (approve), REJECTED (reject)
ACTIVE          → CLOSED (expire/close)
CLOSED          → PENDING (reopen)
REJECTED        → (Terminal, no exit)

Only ACTIVE accepts applications
```

### JobApplicationStatus Transitions
```
SUBMITTED       → REVIEWED (review), REJECTED (reject)
REVIEWED        → ACCEPTED (approve), REJECTED (reject)
ACCEPTED        → (Terminal, no exit)
REJECTED        → (Terminal, no exit)

ACCEPTED/REJECTED require notification
```

---

## Integration Checklist

- [ ] Services layer enums for database conversions
- [ ] DTOs in service method signatures
- [ ] Business rules validation in service methods
- [ ] Permission checks using canPartnerManageOffer()
- [ ] Status transition validation before updates
- [ ] Optional: Unit tests for state machines
- [ ] Optional: Unit tests for validation rules

---

## Phase 1 vs Phase 2

**Phase 1 (Now):**
- ✅ Basic CRUD with enum status
- ✅ State machine enforcement
- ✅ Permission checks (partner owns)
- ✅ Application status workflow (SUBMITTED → REVIEWED → ACCEPTED/REJECTED)

**Phase 2 (Future):**
- [ ] ATS scoring (score_breakdown, extracted_data)
- [ ] CV parsing (cvFileName, extractedData)
- [ ] Email notifications (status_notified, status_message)
- [ ] AI features (motivation letter assist)

---

## Common Pitfalls to Avoid

❌ **Don't:** Use String for status directly  
✅ **Do:** Use JobOfferStatus enum (type-safe)

❌ **Don't:** Modify DTO fields after creation  
✅ **Do:** Create new DTO with updated values

❌ **Don't:** Skip validation in service layer  
✅ **Do:** Use JobOfferBusinessRules validators first

❌ **Don't:** Transition status without checking canTransition()  
✅ **Do:** Always validate transition first

❌ **Don't:** Allow application when offer.status != ACTIVE  
✅ **Do:** Check canStudentApplyToOffer() first

---

## Useful Database Queries

```sql
-- Get all ACTIVE offers
SELECT * FROM job_offer WHERE status = 'ACTIVE';

-- Get applications for an offer  
SELECT * FROM job_application WHERE offer_id = ? AND status = 'REVIEWED';

-- Get student's applications
SELECT * FROM job_application WHERE student_id = ? ORDER BY created_at DESC;

-- Count applications per offer
SELECT offer_id, COUNT(*) as count FROM job_application GROUP BY offer_id;

-- Get pending offers awaiting approval
SELECT * FROM job_offer WHERE status = 'PENDING' ORDER BY created_at;

-- Get applications needing review (SUBMITTED status)
SELECT * FROM job_application WHERE status = 'SUBMITTED' ORDER BY created_at;
```

---

**Last Updated:** April 13, 2026  
**Version:** 1.0 (Phase 1)
