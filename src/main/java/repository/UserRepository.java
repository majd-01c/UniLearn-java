package repository;

import entities.Profile;
import entities.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class UserRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRepository.class);
    private static final String SORT_FIELD_CREATED_AT = "createdAt";
    private static final String SORT_FIELD_ID = "id";
    private static final int DEFAULT_LIMIT = 20;

    public List<User> findAll() {
        return findAll(0, DEFAULT_LIMIT, SORT_FIELD_CREATED_AT, false);
    }

    public List<User> findAll(int offset, int limit, String sortBy, boolean ascending) {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<User> criteria = builder.createQuery(User.class);
            Root<User> root = criteria.from(User.class);

            String safeSortBy = resolveSortField(sortBy);
            if (ascending) {
                criteria.orderBy(builder.asc(root.get(safeSortBy)), builder.asc(root.get(SORT_FIELD_ID)));
            } else {
                criteria.orderBy(builder.desc(root.get(safeSortBy)), builder.desc(root.get(SORT_FIELD_ID)));
            }

            Query<User> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find all users", exception);
            throw new IllegalStateException("Unable to query users", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public Optional<User> findById(Long id) {
        Integer userId = toIntId(id);
        Session session = HibernateSessionFactory.getSession();
        try {
            return Optional.ofNullable(session.get(User.class, userId));
        } catch (Exception exception) {
            LOGGER.error("Failed to find user by id {}", id, exception);
            throw new IllegalStateException("Unable to query user by id", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<User> criteria = builder.createQuery(User.class);
            Root<User> root = criteria.from(User.class);
            criteria.select(root)
                    .where(builder.equal(builder.lower(root.get("email")), email.trim().toLowerCase()));

            return session.createQuery(criteria).uniqueResultOptional();
        } catch (Exception exception) {
            LOGGER.error("Failed to find user by email", exception);
            throw new IllegalStateException("Unable to query user by email", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public List<User> findByRole(String role) {
        if (role == null || role.isBlank()) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<User> criteria = builder.createQuery(User.class);
            Root<User> root = criteria.from(User.class);

            criteria.select(root)
                    .where(builder.equal(builder.lower(root.get("role")), role.trim().toLowerCase()))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)), builder.desc(root.get(SORT_FIELD_ID)));

            return session.createQuery(criteria).getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find users by role {}", role, exception);
            throw new IllegalStateException("Unable to query users by role", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public List<User> findActiveUsers() {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<User> criteria = builder.createQuery(User.class);
            Root<User> root = criteria.from(User.class);

            criteria.select(root)
                    .where(builder.equal(root.get("isActive"), (byte) 1))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)), builder.desc(root.get(SORT_FIELD_ID)));

            return session.createQuery(criteria).getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find active users", exception);
            throw new IllegalStateException("Unable to query active users", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public List<User> findBySearchTerm(String searchTerm) {
        return findBySearchTerm(searchTerm, 0, DEFAULT_LIMIT, SORT_FIELD_CREATED_AT, false);
    }

    public List<User> findBySearchTerm(String searchTerm, int offset, int limit, String sortBy, boolean ascending) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findAll(offset, limit, sortBy, ascending);
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            String likeTerm = "%" + searchTerm.trim().toLowerCase() + "%";

            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<User> criteria = builder.createQuery(User.class);
            Root<User> root = criteria.from(User.class);

            Subquery<Integer> profileExists = criteria.subquery(Integer.class);
            Root<Profile> profileRoot = profileExists.from(Profile.class);
            Predicate sameUser = builder.equal(profileRoot.get("user").get("id"), root.get("id"));
            Predicate firstNameLike = builder.like(builder.lower(profileRoot.get("firstName")), likeTerm);
            Predicate lastNameLike = builder.like(builder.lower(profileRoot.get("lastName")), likeTerm);

            profileExists.select(builder.literal(1))
                    .where(builder.and(sameUser, builder.or(firstNameLike, lastNameLike)));

            Predicate emailLike = builder.like(builder.lower(root.get("email")), likeTerm);
            Predicate nameLike = builder.like(builder.lower(root.get("name")), likeTerm);
            Predicate profileLike = builder.exists(profileExists);

            criteria.select(root)
                    .where(builder.or(emailLike, nameLike, profileLike));

            String safeSortBy = resolveSortField(sortBy);
            if (ascending) {
                criteria.orderBy(builder.asc(root.get(safeSortBy)), builder.asc(root.get(SORT_FIELD_ID)));
            } else {
                criteria.orderBy(builder.desc(root.get(safeSortBy)), builder.desc(root.get(SORT_FIELD_ID)));
            }

            Query<User> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return new ArrayList<>(query.getResultList());
        } catch (Exception exception) {
            LOGGER.error("Failed to search users with term {}", searchTerm, exception);
            throw new IllegalStateException("Unable to search users", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            User merged = (User) session.merge(user);
            transaction.commit();
            return merged;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to save user", exception);
            throw new IllegalStateException("Unable to save user", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public void delete(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User with id is required for delete");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            User managed = session.get(User.class, user.getId());
            if (managed != null) {
                session.remove(managed);
            }
            transaction.commit();
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to delete user id {}", user.getId(), exception);
            throw new IllegalStateException("Unable to delete user", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public void deleteById(Long id) {
        Integer userId = toIntId(id);

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();

            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaDelete<User> criteriaDelete = builder.createCriteriaDelete(User.class);
            Root<User> root = criteriaDelete.from(User.class);
            criteriaDelete.where(builder.equal(root.get("id"), userId));

            session.createMutationQuery(criteriaDelete).executeUpdate();
            transaction.commit();
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to delete user by id {}", id, exception);
            throw new IllegalStateException("Unable to delete user by id", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public long count() {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<User> root = criteria.from(User.class);
            criteria.select(builder.count(root));

            Long total = session.createQuery(criteria).getSingleResult();
            return total == null ? 0L : total;
        } catch (Exception exception) {
            LOGGER.error("Failed to count users", exception);
            throw new IllegalStateException("Unable to count users", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return SORT_FIELD_CREATED_AT;
        }

        return switch (sortBy) {
            case "id", "email", "role", "createdAt", "updatedAt", "isActive", "name" -> sortBy;
            default -> SORT_FIELD_CREATED_AT;
        };
    }

    private Integer toIntId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User id is required");
        }
        if (id > Integer.MAX_VALUE || id < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("User id is out of Integer range: " + id);
        }
        return id.intValue();
    }

    private void rollback(Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
    }
}
