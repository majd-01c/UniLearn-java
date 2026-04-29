# Job Offer Module - Repository Pattern Documentation (PROMPT 04)

## Overview

The persistence layer implements data access for Job Offers and Job Applications using **Hibernate ORM** with **Criteria API** for type-safe queries. This document outlines the database schema, repository contracts, and integration patterns.

---

## Database Schema & Constraints

### Existing Tables (Pre-Phase 1)

#### `job_offer` Table
```sql
CREATE TABLE job_offer (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,              -- INTERNSHIP, APPRENTICESHIP, JOB
    status VARCHAR(50) NOT NULL,            -- PENDING, ACTIVE, CLOSED, REJECTED
    location VARCHAR(255),
    description TEXT,
    requirements TEXT,
    required_skills TEXT,                   -- JSON or comma-separated
    preferred_skills TEXT,                  -- JSON or comma-separated
    min_experience_years INT DEFAULT 0,
    min_education VARCHAR(100),
    required_languages TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    
    INDEX idx_status (status),
    INDEX idx_type (type),
    INDEX idx_location (location),
    INDEX idx_created_at (created_at),
    INDEX idx_user_id (user_id),
    INDEX idx_published_at (published_at)
);
```

#### `job_application` Table
```sql
CREATE TABLE job_application (
    id INT PRIMARY KEY AUTO_INCREMENT,
    job_offer_id INT NOT NULL,
    user_id INT NOT NULL,                   -- student_id (applicant)
    message TEXT,
    cv_file_name VARCHAR(255),
    status VARCHAR(50) NOT NULL,            -- SUBMITTED, REVIEWED, ACCEPTED, REJECTED
    score DECIMAL(5,2) DEFAULT NULL,
    score_breakdown TEXT,                   -- JSON: {writing:80, experience:75}
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    scored_at TIMESTAMP NULL,
    extracted_data TEXT,                    -- JSON: parsed CV data (Phase 2: AI)
    status_notified BOOLEAN DEFAULT FALSE,
    status_notified_at TIMESTAMP NULL,
    status_message VARCHAR(255),            -- Last notification message sent
    
    FOREIGN KEY (job_offer_id) REFERENCES job_offer(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    
    UNIQUE KEY unique_student_offer (job_offer_id, user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_user_id (user_id),
    INDEX idx_job_offer_id (job_offer_id),
    INDEX idx_score (score)
);
```

### Key Constraints Explained

| Constraint | Purpose | Enforcement |
|-----------|---------|-------------|
| `UNIQUE(job_offer_id, user_id)` | Prevent duplicate applications | DB-level + repository check |
| `INDEX(status)` | Filter by application/offer status | Used in all status queries |
| `INDEX(created_at)` | Sort by newest applications | Used for timeline views |
| `INDEX(user_id)` | Find student's applications | Used by StudentId queries |
| `INDEX(job_offer_id)` | Find offer's applications | Used by OfferId queries |
| `FOREIGN KEY` | Referential integrity | DB-level cascade on delete |

---

## Repository Class Hierarchy

### JobOfferRepository

**Location:** `repository/job_offer/JobOfferRepository.java`  
**Responsibilities:**
- Job offer CRUD operations
- Public search (all active offers)
- Partner-owned offers search
- Admin oversight search
- DTO projections

#### Core Methods

| Method | Purpose | SQL Pattern |
|--------|---------|------------|
| `findById(id)` | Get single offer | SELECT * WHERE id = ? |
| `save(offer)` | Create/update offer | INSERT or UPDATE |
| `delete(id)` | Remove offer | DELETE WHERE id = ? |
| `findActiveOffers(offset, limit, type, location, search)` | Public search | WHERE status='ACTIVE' AND (type/location filters) |
| `countActiveOffers(...)` | Pagination count | COUNT(*) WHERE status='ACTIVE' |
| `findByPartnerId(partnerId, offset, limit)` | Partner's offers | WHERE user_id = partnerId |
| `countByPartnerId(partnerId)` | Partner offer count | COUNT(*) WHERE user_id = ? |
| `findAllForAdmin(offset, limit, status, type, partnerId, search)` | Admin search (all statuses) | WHERE (status/type/user_id/search) |
| `countAllForAdmin(...)` | Admin count | COUNT(*) WHERE (filters) |
| `findActiveOfferRows(...)` | UI table data | Returns JobOfferRowDto list |
| `findPartnerOfferOptions(partnerId)` | ComboBox options | Returns JobOfferOptionDto list |
| `findOfferDto(id)` | Detailed view | Returns JobOfferDto |

#### Usage Example

```java
// Student browsing active internships in Paris
JobOfferRepository repo = new JobOfferRepository();
List<JobOffer> offers = repo.findActiveOffers(0, 20, "INTERNSHIP", "Paris", null);
long total = repo.countActiveOffers("INTERNSHIP", "Paris", null);
int pageNum = 1; // Pages are 1-indexed for UI

// Partner viewing own offers
List<JobOffer> myOffers = repo.findByPartnerId(partnerId, 0, 20);

// Admin viewing all offers
List<JobOffer> allOffers = repo.findAllForAdmin(0, 50, "PENDING", null, null, "senior");
```

---

### JobApplicationRepository

**Location:** `repository/job_offer/JobApplicationRepository.java`  
**Responsibilities:**
- Application CRUD
- Duplicate prevention (✓ hasStudentApplied)
- Student application history
- Partner review queue
- Admin bulk queries
- DTO projections

#### Core Methods

| Method | Purpose | SQL Pattern |
|--------|---------|------------|
| `findById(id)` | Get single application | SELECT * WHERE id = ? |
| `save(app)` | Create/update application | INSERT or UPDATE |
| `delete(id)` | Remove application | DELETE WHERE id = ? |
| **`hasStudentApplied(studentId, offerId)`** | **Duplicate prevention** | **COUNT(*) WHERE user_id=? AND job_offer_id=?** |
| **`getStudentAppliedOfferIds(studentId)`** | **Mark "Already Applied"** | **SELECT job_offer_id WHERE user_id=?** |
| `findByStudentId(studentId, offset, limit)` | Student's applications | WHERE user_id = studentId |
| `countByStudentId(studentId)` | Student application count | COUNT(*) WHERE user_id = ? |
| `findApplicationsForPartnerReview(partnerId, offset, limit)` | Partner's review queue | WHERE offer.user_id = partnerId AND status='SUBMITTED' |
| `countApplicationsForPartnerReview(partnerId)` | Pending review count | COUNT(*) WHERE (review queue) |
| `findByStatus(status, offset, limit)` | Admin filter by status | WHERE status = ? |
| `findByOfferId(offerId, offset, limit)` | All applicants for offer | WHERE job_offer_id = ? |
| `findStudentApplicationRows(...)` | UI table data | Returns JobApplicationRowDto list |
| `findPartnerReviewRows(...)` | Review queue UI | Returns ApplicationReviewRowDto list |
| `findApplicationDto(id)` | Detailed review view | Returns JobApplicationDto |

#### Usage Example

```java
// Duplicate prevention in application form
JobApplicationRepository appRepo = new JobApplicationRepository();
if (appRepo.hasStudentApplied(studentId, offerId)) {
    showError("You've already applied to this offer");
    return;
}

// Create new application
JobApplication app = new JobApplication(...);
appRepo.save(app);

// Student views their applications
List<JobApplicationRowDto> myApps = appRepo.findStudentApplicationRows(studentId, 0, 20);

// Partner reviews applications
List<ApplicationReviewRowDto> queue = appRepo.findPartnerReviewRows(partnerId, 0, 20);

// UI: Mark which offers student has applied to
Set<Integer> appliedOfferIds = appRepo.getStudentAppliedOfferIds(studentId);
// Then in table: if (appliedOfferIds.contains(offerId)) showBadge("Applied");
```

---

## Query Performance Patterns

### Cached Frequently-Used Data

For patterns that might be called multiple times in a view load, consider layer-level caching:

```java
// In MyApplicationsController
JobApplicationRepository appRepo = new JobApplicationRepository();
Set<Integer> cachedAppliedOfferIds = appRepo.getStudentAppliedOfferIds(studentId);

// Then in table rendering:
for (JobOffer offer : offers) {
    if (cachedAppliedOfferIds.contains(offer.getId())) {
        badgeLabel.setText("Applied");
    }
}
```

### Pagination Best Practices

```java
// Correct: Load count once, use for UI pagination controls
long totalOffers = repo.countActiveOffers(type, location, search);
int pageSize = 20;
int totalPages = (int) Math.ceil((double) totalOffers / pageSize);

// Then load data for current page
List<JobOffer> pageData = repo.findActiveOffers((pageNum-1) * pageSize, pageSize, type, location, search);
```

---

## Integration Points with Other Layers

### Controllers → Repositories

```java
// JobOfferListController.java
public void loadOffers(String type, String location, int pageNum) {
    JobOfferRepository repo = new JobOfferRepository();
    long total = repo.countActiveOffers(type, location, null);
    List<JobOfferRowDto> page = repo.findActiveOfferRows((pageNum-1)*20, 20, type, location, null);
    
    offerTable.setItems(FXCollections.observableArrayList(page));
    updatePaginationControls(pageNum, total);
}
```

### Services → Repositories (Phase 2)

Will migrate JDBC ServiceJobOffer/ServiceJobApplication to use repositories:

```java
// ServiceJobOffer.java (future Hibernate version)
public class ServiceJobOffer {
    private JobOfferRepository repo = new JobOfferRepository();
    
    public void publishOffer(JobOffer offer) {
        // Business logic validation
        JobOfferBusinessRules.requiresPublishPermission(offer, currentUser);
        
        // Then persist
        repo.save(offer);
    }
}
```

---

## Transaction Boundaries

Each repository method is atomic and manages its own transaction:

```java
public JobApplication save(JobApplication application) {
    Session session = HibernateSessionFactory.getSession();
    Transaction transaction = null;
    try {
        transaction = session.beginTransaction();
        JobApplication merged = session.merge(application);
        session.flush();                    // ← Force validation before commit
        transaction.commit();               // ← Atomic commit
        return merged;
    } catch (Exception exception) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();         // ← Rollback on error
        }
        LOGGER.error("Failed to save job application", exception);
        throw new IllegalStateException("Unable to save job application", exception);
    } finally {
        HibernateSessionFactory.closeSession();  // ← Always close
    }
}
```

**Transaction Safety:**
- ✓ Flush before commit validates entity constraints
- ✓ Rollback cancels all changes if exception occurs
- ✓ Finally block ensures session cleanup even on error
- ✓ No nested transactions (single-threaded per request)

---

## Exception Handling Strategy

All repository methods follow this pattern:

```
Try: Get session → Execute query → Return result
Catch: Log with context → Throw IllegalStateException
Finally: Always close session (prevents leaks)
```

**Example:**
```java
try {
    Query<JobOffer> query = session.createQuery(criteria);
    return query.getResultList();
} catch (Exception exception) {
    LOGGER.error("Failed to find active offers", exception);  // ← Descriptive log
    throw new IllegalStateException("Unable to query active offers", exception);  // ← Caller decides handling
} finally {
    HibernateSessionFactory.closeSession();  // ← Always cleanup
}
```

---

## DTO Projection Strategy

Three DTO types minimize data transfer:

| DTO Type | Use Case | Fields | Source |
|----------|----------|--------|--------|
| **OptionDto** | ComboBox/dropdown | id, title | UI selection lists |
| **RowDto** | Table/list view | id, title, type, status, location, date | ListView/TableView binding |
| **FullDto** | Detail view | All 19 fields (JobOfferDto) | Detail pages, editing forms |

```java
// Usage in controller
List<JobOfferOptionDto> options = repo.findPartnerOfferOptions(partnerId);
comboBox.setItems(FXCollections.observableArrayList(options));

List<JobOfferRowDto> rows = repo.findActiveOfferRows(0, 20, type, location, null);
offerTable.setItems(FXCollections.observableArrayList(rows));
```

---

## Phase 1 vs Phase 2 Scope

### Phase 1 (CURRENT - PROMPT 04)
✓ CRUD operations  
✓ Search + pagination  
✓ Duplicate prevention (hasStudentApplied)  
✓ Role-based queries (Student/Partner/Admin)  
✓ Basic DTO projections  

### Phase 2 (Future)
⏳ ATS scoring integration  
⏳ CV parsing (extracted_data field)  
⏳ AI scoring rules (score_breakdown field)  
⏳ Notification tracking (status_notified field)  
⏳ Custom Skills CRUD repository  

**No ATS or AI tables added in Phase 1** — existing `job_application` fields (score, extracted_data, score_breakdown) reserved for Phase 2 features.

---

## Testing Checklist

Before deployment, verify:

- [ ] Duplicate application check works (hasStudentApplied returns true after save)
- [ ] Pagination offsets load correct results (offset=0,limit=20 vs offset=20,limit=20)
- [ ] Partner queries filter correctly (findByPartnerId only shows that partner's offers)
- [ ] Admin queries bypass status filters (findAllForAdmin shows PENDING offers too)
- [ ] DTO projections don't load relationships (check query count < 1+N)
- [ ] Session cleanup on exception (sessions don't accumulate/leak)
- [ ] Constraints enforced (UNIQUE on (offer_id, student_id))

---

## File Locations

- **Repositories:** `src/main/java/repository/job_offer/`
  - JobOfferRepository.java
  - JobApplicationRepository.java
  - package-info.java

- **Related Entities:** `src/main/java/entities/job_offer/`
  - JobOffer.java
  - JobApplication.java

- **Related DTOs:** `src/main/java/dto/job_offer/`
  - JobOfferDto.java
  - JobApplicationDto.java
  - JobOfferRowDto.java / JobApplicationRowDto.java / ApplicationReviewRowDto.java
  - JobOfferOptionDto.java

- **Related Validation:** `src/main/java/util/job_offer/`
  - JobOfferBusinessRules.java
  - JobOfferValidators.java

---

## Summary

The persistence layer provides:
1. **Type-safe queries** via Criteria API (no string-based HQL/SQL)
2. **Automatic session management** via ThreadLocal singleton
3. **Role-based data access** (public search, partner-owned, admin oversight)
4. **Duplicate prevention** via repository hasStudentApplied() check
5. **Efficient DTO projections** (RowDto/OptionDto/FullDto)
6. **Atomic transactions** with exception safety (try-catch-finally)
7. **Comprehensive logging** for debugging and monitoring

Next layer: Service implementations that combine repository queries + business rule validation.
