# Job Offer Repositories - Quick Reference

## File Overview

| File | Lines | Purpose |
|------|-------|---------|
| `JobOfferRepository.java` | 340 | Job offer CRUD, search (public/partner/admin), pagination |
| `JobApplicationRepository.java` | 380 | Application CRUD, duplicate prevention, review queue |
| `PERSISTENCE_LAYER_DESIGN.md` | 350 | Schema, constraints, integration patterns, testing checklist |
| `package-info.java` | 50 | Module documentation, architecture overview |

---

## Quick Method Reference

### JobOfferRepository

```java
// CRUD
Optional<JobOffer> findById(Integer offerId)
JobOffer save(JobOffer offer)
void delete(Integer offerId)

// Public Search (All Users > Students/Partners)
List<JobOffer> findActiveOffers(int offset, int limit, String type, String location, String search)
long countActiveOffers(String type, String location, String search)

// Partner's Own Offers
List<JobOffer> findByPartnerId(Integer partnerId, int offset, int limit)
long countByPartnerId(Integer partnerId)

// Admin Oversight (All Statuses)
List<JobOffer> findAllForAdmin(int offset, int limit, String status, String type, Integer partnerId, String search)
long countAllForAdmin(String status, String type, Integer partnerId, String search)

// DTO Projections
List<JobOfferRowDto> findActiveOfferRows(int offset, int limit, String type, String location, String search)
List<JobOfferOptionDto> findPartnerOfferOptions(Integer partnerId)
Optional<JobOfferDto> findOfferDto(Integer offerId)
```

### JobApplicationRepository

```java
// CRUD
Optional<JobApplication> findById(Integer appId)
JobApplication save(JobApplication app)
void delete(Integer appId)

// Duplicate Prevention (✓ CRITICAL)
boolean hasStudentApplied(Integer studentId, Integer offerId)
Set<Integer> getStudentAppliedOfferIds(Integer studentId)

// Student's Applications
List<JobApplication> findByStudentId(Integer studentId, int offset, int limit)
long countByStudentId(Integer studentId)

// Partner's Review Queue
List<JobApplication> findApplicationsForPartnerReview(Integer partnerId, int offset, int limit)
long countApplicationsForPartnerReview(Integer partnerId)

// Admin Queries
List<JobApplication> findByStatus(String status, int offset, int limit)
List<JobApplication> findByOfferId(Integer offerId, int offset, int limit)

// DTO Projections
List<JobApplicationRowDto> findStudentApplicationRows(Integer studentId, int offset, int limit)
List<ApplicationReviewRowDto> findPartnerReviewRows(Integer partnerId, int offset, int limit)
Optional<JobApplicationDto> findApplicationDto(Integer appId)
```

---

## Controller Integration Pattern

### JobOfferListController
```java
public void loadOffers(String type, String location, int page) {
    JobOfferRepository repo = new JobOfferRepository();
    
    // Get pagination info
    long total = repo.countActiveOffers(type, location, null);
    
    // Load page data
    List<JobOfferRowDto> data = repo.findActiveOfferRows(
        (page - 1) * 20, 20, type, location, null
    );
    
    table.setItems(FXCollections.observableArrayList(data));
    updatePaginationUI(page, total / 20 + 1);
}
```

### JobOfferDetailController
```java
public void loadDetail(Integer offerId) {
    JobOfferRepository repo = new JobOfferRepository();
    Optional<JobOfferDto> offer = repo.findOfferDto(offerId);
    offer.ifPresent(this::displayOffer);
}

public void applyForOffer(Integer offerId) {
    JobApplicationRepository appRepo = new JobApplicationRepository();
    
    // Prevent duplicates
    if (appRepo.hasStudentApplied(studentId, offerId)) {
        showError("Already applied to this offer");
        return;
    }
    
    // Show application form...
}
```

### MyApplicationsController
```java
public void loadMyApplications() {
    JobApplicationRepository repo = new JobApplicationRepository();
    
    List<JobApplicationRowDto> apps = repo.findStudentApplicationRows(
        studentId, 0, 20
    );
    
    table.setItems(FXCollections.observableArrayList(apps));
}
```

### ApplicationReviewController
```java
public void loadReviewQueue() {
    JobApplicationRepository repo = new JobApplicationRepository();
    
    List<ApplicationReviewRowDto> queue = repo.findPartnerReviewRows(
        partnerId, 0, 20
    );
    
    table.setItems(FXCollections.observableArrayList(queue));
}
```

---

## Database Indexes for Performance

```sql
-- job_offer table
CREATE INDEX idx_status ON job_offer(status);
CREATE INDEX idx_type ON job_offer(type);
CREATE INDEX idx_location ON job_offer(location);
CREATE INDEX idx_created_at ON job_offer(created_at);
CREATE INDEX idx_user_id ON job_offer(user_id);
CREATE INDEX idx_published_at ON job_offer(published_at);

-- job_application table
CREATE INDEX idx_status ON job_application(status);
CREATE INDEX idx_created_at ON job_application(created_at);
CREATE INDEX idx_user_id ON job_application(user_id);
CREATE INDEX idx_job_offer_id ON job_application(job_offer_id);
CREATE INDEX idx_score ON job_application(score);

-- Unique constraint (prevent duplicates)
ALTER TABLE job_application 
ADD CONSTRAINT unique_student_offer 
UNIQUE KEY (job_offer_id, user_id);
```

---

## Exception Handling

All methods follow this pattern:

```
Try:
├─ Get session from HibernateSessionFactory
├─ Build criteria/execute query
└─ Return result

Catch (Exception):
├─ Log error with context
├─ Throw IllegalStateException (with original cause)

Finally:
└─ Close session (prevents leaks)
```

**Common exceptions caught:**
- `PersistenceException` - Hibernate/JPA error
- `ConstraintViolationException` - DB constraint violation
- `EntityNotFoundException` - Entity not found
- `TransactionException` - Transaction issues

**All** converted to `IllegalStateException` for consistent caller handling.

---

## Transaction Safety

```java
// Example: save() method
Transaction transaction = null;
try {
    transaction = session.beginTransaction();
    entity.merge(...);
    session.flush();           // ← Validate before commit
    transaction.commit();      // ← Atomic
    return entity;
} catch (Exception e) {
    if (transaction != null && transaction.isActive()) {
        transaction.rollback();  // ← Undo on error
    }
    throw new IllegalStateException(...);
} finally {
    HibernateSessionFactory.closeSession();  // ← Always cleanup
}
```

---

## Key Features Implemented

✓ **Type-safe queries**: Criteria API (no raw SQL/HQL)  
✓ **CRUD operations**: Standard findById, save, delete  
✓ **Pagination**: offset + limit on all list queries  
✓ **Filtering**: status, type, location, search text  
✓ **Duplicate prevention**: hasStudentApplied() check  
✓ **Role-based access**: public/partner-owned/admin queries  
✓ **DTO projections**: OptionDto, RowDto, FullDto  
✓ **Session management**: ThreadLocal + finally cleanup  
✓ **Transaction atomicity**: begin/commit/rollback  
✓ **Error logging**: Descriptive LOGGER.error() calls  

---

## Phase 1 Scope Completed

✓ Repositories created  
✓ CRUD operations implemented  
✓ Search + pagination implemented  
✓ Duplicate prevention implemented  
✓ Role-based queries implemented  
✓ DTO projections implemented  
✓ Database constraints verified  
✓ Documentation completed  

**Note:** Phase 2 (ATS/AI features) uses existing fields:  
- `score` → AI scoring result  
- `score_breakdown` → JSON breakdown  
- `extracted_data` → Parsed CV data  
- `status_notified` → Notification tracking  

No new tables needed in Phase 1.

---

## Next Steps (PROMPT 05+)

1. **Service Layer** - Integrate repositories + business rules
2. **Application Form** - CV upload + message collection dialog
3. **Permission Checks** - Enforce JobOfferBusinessRules at service layer
4. **Manual Testing** - Verify all query filters and duplicates
5. **Phase 2** - ATS scoring, AI integrations

---

## File Locations Summary

```
repository/job_offer/
├── JobOfferRepository.java          (340 lines)
├── JobApplicationRepository.java    (380 lines)
├── package-info.java                (50 lines)
└── PERSISTENCE_LAYER_DESIGN.md      (350 lines)
```

Total: **1,120 lines of code + documentation**

Related supporting files (already created in PROMPT 03):
```
entities/job_offer/
├── JobOffer.java
├── JobApplication.java
├── JobOfferStatus.java
├── JobApplicationStatus.java
└── JobOfferType.java

dto/job_offer/
├── JobOfferDto.java
├── JobApplicationDto.java
├── JobOfferRowDto.java
├── JobApplicationRowDto.java
├── ApplicationReviewRowDto.java
└── JobOfferOptionDto.java

util/job_offer/
├── JobOfferBusinessRules.java
└── JobOfferValidators.java
```
