package service;

import entities.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int BCRYPT_ROUNDS = 12;

    public List<User> getAllUsers() {
        return getAllUsers(1, DEFAULT_PAGE_SIZE);
    }

    public List<dto.lms.UserOptionDto> getAllUsersOptionsDto() {
        return getAllUsers(1, 1000).stream()
                .map(u -> new dto.lms.UserOptionDto(u.getId(), u.getEmail()))
                .collect(java.util.stream.Collectors.toList());
    }


    public List<User> getPartnerUsers() {
        Session session = HibernateSessionFactory.getSession();
        try {
            Query<User> query = session.createQuery(
                    "from User u where (lower(u.role) = 'business_partner' or lower(u.role) = 'partner' or lower(u.role) = 'role_partner') order by u.name asc, u.email asc",
                    User.class
            );
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to fetch partner users", exception);
            throw new IllegalStateException("Unable to fetch partner users", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public List<User> getAllUsers(int page, int pageSize) {
        Session session = HibernateSessionFactory.getSession();
        try {
            int safePage = Math.max(page, 1);
            int safePageSize = Math.max(pageSize, 1);

            Query<User> query = session.createQuery(
                    "from User u order by u.createdAt desc, u.id desc",
                    User.class
            );
            query.setFirstResult((safePage - 1) * safePageSize);
            query.setMaxResults(safePageSize);
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to fetch users with pagination", exception);
            throw new IllegalStateException("Unable to fetch users", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public Optional<User> getUserById(Long id) {
        Integer userId = toIntId(id);
        Session session = HibernateSessionFactory.getSession();
        try {
            return Optional.ofNullable(session.get(User.class, userId));
        } catch (Exception exception) {
            LOGGER.error("Failed to fetch user by id: {}", id, exception);
            throw new IllegalStateException("Unable to fetch user by id", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public Optional<User> getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<User> query = session.createQuery(
                    "from User u where lower(u.email) = :email",
                    User.class
            );
            query.setParameter("email", email.trim().toLowerCase());
            return query.uniqueResultOptional();
        } catch (Exception exception) {
            LOGGER.error("Failed to fetch user by email", exception);
            throw new IllegalStateException("Unable to fetch user by email", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public User createUser(User user, String tempPassword) {
        if (user == null) {
            throw new IllegalArgumentException("User payload is required");
        }
        if (tempPassword == null || tempPassword.isBlank()) {
            throw new IllegalArgumentException("Temporary password is required");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();

            Timestamp now = Timestamp.from(Instant.now());
            user.setPassword(BCrypt.hashpw(tempPassword, BCrypt.gensalt(BCRYPT_ROUNDS)));
            user.setMustChangePassword((byte) 1);
            user.setIsVerified((byte) 0);
            user.setNeedsVerification((byte) 1);
            user.setIsActive((byte) 1);
            if (user.getFaceEnabled() != (byte) 1) {
                user.setFaceEnabled((byte) 0);
            }
            if (user.getFaceEnabled() != (byte) 1) {
                user.setFaceDescriptors(null);
                user.setFaceEnrolledAt(null);
            }
            user.setTempPasswordGeneratedAt(now);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);

            session.persist(user);
            transaction.commit();
            return user;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to create user", exception);
            throw new IllegalStateException("Unable to create user", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public User updateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User id is required for update");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();

            user.setUpdatedAt(Timestamp.from(Instant.now()));
            User merged = (User) session.merge(user);

            transaction.commit();
            return merged;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to update user with id {}", user.getId(), exception);
            throw new IllegalStateException("Unable to update user", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public void deleteUser(Long userId) {
        Integer id = toIntId(userId);
        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();

            User user = session.get(User.class, id);
            if (user == null) {
                rollback(transaction);
                return;
            }

            session.remove(user);
            transaction.commit();
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to delete user with id {}", userId, exception);
            throw new IllegalStateException("Unable to delete user. Verify cascade relationships and foreign keys.", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public boolean toggleUserStatus(Long userId) {
        Integer id = toIntId(userId);
        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();

            User user = session.get(User.class, id);
            if (user == null) {
                rollback(transaction);
                throw new IllegalArgumentException("User not found for id: " + userId);
            }

            byte newStatus = user.getIsActive() == (byte) 1 ? (byte) 0 : (byte) 1;
            user.setIsActive(newStatus);
            user.setUpdatedAt(Timestamp.from(Instant.now()));
            session.merge(user);

            transaction.commit();
            return newStatus == (byte) 1;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to toggle user status for id {}", userId, exception);
            throw new IllegalStateException("Unable to toggle user status", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public List<User> searchUsers(String searchTerm, String role, Boolean isActive) {
        Session session = HibernateSessionFactory.getSession();
        try {
            StringBuilder hql = new StringBuilder("from User u where 1=1");

            if (searchTerm != null && !searchTerm.isBlank()) {
                hql.append(" and (")
                        .append("lower(u.email) like :search ")
                        .append("or lower(u.name) like :search ")
                        .append("or lower(u.phone) like :search ")
                        .append("or exists (")
                        .append("select p.id from Profile p ")
                        .append("where p.user.id = u.id ")
                        .append("and (lower(p.firstName) like :search or lower(p.lastName) like :search)")
                        .append(")")
                        .append(")");
            }

            if (role != null && !role.isBlank()) {
                hql.append(" and lower(u.role) = :role");
            }

            if (isActive != null) {
                hql.append(" and u.isActive = :isActive");
            }

            hql.append(" order by u.createdAt desc, u.id desc");

            Query<User> query = session.createQuery(hql.toString(), User.class);

            if (searchTerm != null && !searchTerm.isBlank()) {
                query.setParameter("search", "%" + searchTerm.trim().toLowerCase() + "%");
            }

            if (role != null && !role.isBlank()) {
                query.setParameter("role", role.trim().toLowerCase());
            }

            if (isActive != null) {
                query.setParameter("isActive", isActive ? (byte) 1 : (byte) 0);
            }

            return new ArrayList<>(query.getResultList());
        } catch (Exception exception) {
            LOGGER.error("Failed to search users", exception);
            throw new IllegalStateException("Unable to search users", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    private void rollback(Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
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
}
