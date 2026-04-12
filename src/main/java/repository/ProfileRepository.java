package repository;

import entities.Profile;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Optional;

public class ProfileRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileRepository.class);

    public Optional<Profile> findByUserId(Long userId) {
        Integer id = toIntId(userId);
        Session session = HibernateSessionFactory.getSession();
        try {
            Query<Profile> query = session.createQuery(
                    "from Profile p where p.user.id = :userId",
                    Profile.class
            );
            query.setParameter("userId", id);
            return query.uniqueResultOptional();
        } catch (Exception exception) {
            LOGGER.error("Failed to find profile by user id {}", userId, exception);
            throw new IllegalStateException("Unable to find profile by user id", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public Profile save(Profile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile is required");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            Profile merged = (Profile) session.merge(profile);
            transaction.commit();
            return merged;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to save profile", exception);
            throw new IllegalStateException("Unable to save profile", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public void delete(Profile profile) {
        if (profile == null || profile.getId() == null) {
            throw new IllegalArgumentException("Profile with id is required for delete");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            Profile managed = session.get(Profile.class, profile.getId());
            if (managed != null) {
                session.remove(managed);
            }
            transaction.commit();
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to delete profile id {}", profile.getId(), exception);
            throw new IllegalStateException("Unable to delete profile", exception);
        } finally {
            HibernateSessionFactory.closeSession();
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

    private void rollback(Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
    }
}
