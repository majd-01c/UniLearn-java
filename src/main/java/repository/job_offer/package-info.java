/**
 * Job Offer Module - Repository Layer (Persistence)
 * 
 * Architecture: Contract-First Pattern
 * ─────────────────────────────────────────────────────────────
 * 
 * Interface (Contract) → Implementation (Hibernate)
 * 
 * - {@link repository.job_offer.IJobOfferRepository} → {@link repository.job_offer.JobOfferRepository}
 * - {@link repository.job_offer.IJobApplicationRepository} → {@link repository.job_offer.JobApplicationRepository}  
 * - {@link repository.job_offer.ICustomSkillRepository} → {@link repository.job_offer.CustomSkillRepository}
 * 
 * Technology Stack
 * ─────────────────────────────────────────────────────────────
 * - Hibernate 6.4.10 (Jakarta Persistence 3.1.0)
 * - Criteria API (type-safe queries, no raw SQL/HQL)
 * - MySQL 8 database backend
 * - ThreadLocal session management (HibernateSessionFactory)
 * - Atomic transactions (begin/commit/rollback)
 * 
 * Core Features
 * ─────────────────────────────────────────────────────────────
 * ✓ CRUD operations (Create, Read, Update, Delete)
 * ✓ Pagination with offset/limit on all list queries
 * ✓ Filtering by status, type, location, search text
 * ✓ Role-based access patterns:
 *   - Public: findActiveOffers() — Students/Partners see active offers only
 *   - Partner: findByPartnerId() — Partner's own offers
 *   - Admin: findAllForAdmin() — All offers regardless of status
 * 
 * ✓ Duplicate application prevention:
 *   - hasStudentApplied(studentId, offerId) — Check before creating
 *   - UNIQUE constraint at DB: (offer_id, student_id)
 * 
 * ✓ DTO Projections for efficient UI binding:
 *   - OptionDto: {id, title} for ComboBox/dropdown
 *   - RowDto: {id, title, type, status, location, partner, date, count} for ListView/TableView
 *   - FullDto: All 19 fields for detail views and editing
 * 
 * ✓ Custom Skills management:
 *   - Partners can create reusable skill definitions
 *   - Prevent duplicate skill names per partner
 *   - Search and filter by partner or globally
 * 
 * Database Schema
 * ─────────────────────────────────────────────────────────────
 * job_offer
 *   - id, partner_id (→ user.id), title, type, status, location, description
 *   - required_skills, preferred_skills, min_experience_years, min_education, required_languages
 *   - created_at, updated_at, published_at, expires_at
 *   - Indexes: status, type, location, created_at, partner_id, published_at, expires_at
 * 
 * job_application
 *   - id, offer_id (→ job_offer.id), student_id (→ user.id), message, cv_file_name, status
 *   - score, score_breakdown, scored_at (Phase 2: AI scoring)
 *   - extracted_data, status_notified, status_notified_at, status_message (Phase 2)
 *   - created_at, updated_at
 *   - UNIQUE constraint: (offer_id, student_id) ← Prevents duplicates
 *   - Indexes: status, created_at, student_id, offer_id, score, status_notified
 * 
 * custom_skill
 *   - id, partner_id (→ user.id), name, description, category, created_at, updated_at
 *   - UNIQUE constraint: (partner_id, name) ← Prevents duplicate names per partner
 *   - Indexes: partner_id, name
 * 
 * Transaction Model
 * ─────────────────────────────────────────────────────────────
 * Each repository method manages its own transaction:
 * 
 * - Read-only queries: No transaction needed
 * - Write operations (save/delete): Atomic begin/commit/rollback
 * - Exception safety: Finally block always closes session
 * - No nested transactions: Single-threaded per request
 * 
 * Exception Strategy
 * ─────────────────────────────────────────────────────────────
 * All methods follow Try-Catch-Finally pattern:
 * 
 * Try:
 *   - Get session from HibernateSessionFactory (ThreadLocal)
 *   - Execute query or operation
 *   - Return result
 * 
 * Catch (Exception):
 *   - Log with LOGGER.error() for debugging
 *   - Throw IllegalStateException to caller
 *   - Caller decides how to handle (show UI error, retry, etc.)
 * 
 * Finally:
 *   - ALWAYS close session (prevents connection leaks)
 *   - Run regardless of success/failure
 * 
 * Session Management
 * ─────────────────────────────────────────────────────────────
 * Uses HibernateSessionFactory singleton with ThreadLocal storage:
 * 
 *   Session session = HibernateSessionFactory.getSession();
 *   try {
 *       // Execute query/operation
 *   } finally {
 *       HibernateSessionFactory.closeSession();  // Always cleanup
 *   }
 * 
 * Why ThreadLocal?
 * - One session per thread prevents cross-thread pollution
 * - Desktop app (JavaFX) runs on single UI thread
 * - Lazy initialization: Session created on first call
 * - Automatic thread cleanup with isActive() checks
 * 
 * Query Performance
 * ─────────────────────────────────────────────────────────────
 * Best Practices Applied:
 * 
 * 1. Database-side pagination:
 *    query.setFirstResult( (pageNum-1) * 20 );
 *    query.setMaxResults( 20 );
 *    ← Database returns only 20 rows, not 10,000
 * 
 * 2. DTO projections for UI:
 *    Load only needed columns, not full entities with relationships
 *    Avoids N+1 lazy loading query explosion
 * 
 * 3. Efficient filtering:
 *    WHERE status='ACTIVE' AND type='INTERNSHIP' AND location LIKE '%Paris%'
 *    ← Uses indexes, stops at first match
 * 
 * 4. Indexed lookups:
 *    hasStudentApplied() uses indexes on (offer_id, student_id)
 *    getStudentAppliedOfferIds() uses index on student_id
 * 
 * Duplicate Application Prevention
 * ─────────────────────────────────────────────────────────────
 * CRITICAL Feature: Ensures each student applies only once per offer
 * 
 * Database Level:
 *   CREATE UNIQUE INDEX unique_student_offer ON job_application(offer_id, student_id)
 * 
 * Application Level:
 *   if (appRepo.hasStudentApplied(studentId, offerId)) {
 *       showError("Already applied!");
 *       return;
 *   }
 *   // Safe to proceed
 *   appRepo.save(newApplication);
 * 
 * UI Level:
 *   Set<Integer> appliedOfferIds = appRepo.getStudentAppliedOfferIds(studentId);
 *   // Mark "Applied" badges in offer list
 * 
 * Repository Method Signatures
 * ─────────────────────────────────────────────────────────────
 * 
 * JobOfferRepository:
 *   - findById, save, delete (CRUD)
 *   - findActiveOffers, countActiveOffers (public search)
 *   - findByPartnerId, countByPartnerId (partner's offers)
 *   - findAllForAdmin, countAllForAdmin (admin search)
 *   - findActiveOfferRows, findPartnerOfferOptions, findOfferDto (DTO projections)
 * 
 * JobApplicationRepository:
 *   - findById, save, delete (CRUD)
 *   - hasStudentApplied, getStudentAppliedOfferIds (duplicate prevention)
 *   - findByStudentId, countByStudentId (student history)
 *   - findApplicationsForPartnerReview, countApplicationsForPartnerReview (review queue)
 *   - findByStatus, findByOfferId (admin queries)
 *   - findStudentApplicationRows, findPartnerReviewRows, findApplicationDto (DTO projections)
 * 
 * CustomSkillRepository:
 *   - findById, save, delete (CRUD)
 *   - findByPartnerId, searchPartnerSkills (partner's skills)
 *   - findAll, search (admin skills)
 *   - existsByNameAndPartnerId, countByPartnerId (utilities)
 * 
 * Usage Pattern
 * ─────────────────────────────────────────────────────────────
 * In Controllers:
 * 
 *   IJobOfferRepository repo = new JobOfferRepository();
 *   
 *   // Get data
 *   List<JobOfferRowDto> offers = repo.findActiveOfferRows(0, 20, type, location, null);
 *   long total = repo.countActiveOffers(type, location, null);
 *   
 *   // Bind to UI
 *   table.setItems(FXCollections.observableArrayList(offers));
 *   
 *   // Update pagination
 *   int pages = (int) Math.ceil((double) total / 20);
 *   updatePageControls(pages);
 * 
 * Testing Strategy
 * ─────────────────────────────────────────────────────────────
 * For unit testing, mock the interface:
 * 
 *   IJobOfferRepository mockRepo = mock(IJobOfferRepository.class);
 *   when(mockRepo.findById(1)).thenReturn(Optional.of(offerStub));
 *   
 *   MyController controller = new MyController(mockRepo);
 *   controller.loadOffer(1);
 *   
 *   verify(mockRepo).findById(1);
 *   assertTrue(controller.offerTitle.getText().contains("Java"));
 * 
 * Phase 1 vs Phase 2
 * ─────────────────────────────────────────────────────────────
 * Phase 1 (CURRENT):
 *   ✓ CRUD, search, pagination, filtering
 *   ✓ Public/partner/admin role-based queries
 *   ✓ Duplicate application prevention
 *   ✓ Custom skills CRUD
 * 
 * Phase 2 (NOT YET):
 * ⏳ ATS scoring integration (score, score_breakdown fields exist but unused)
 * ⏳ CV parsing (extracted_data field exists but unused)
 * ⏳ AI features (status_notified field exists but unused)
 * ⏳ Notification tracking (status_notified_at field exists but unused)
 * 
 * No new tables or columns needed in Phase 1.
 * 
 * @author Job Offer Module Team
 * @since PROMPT 04 (Phase 1 - Persistence Layer)
 * @version 1.0
 */
package repository.job_offer;
