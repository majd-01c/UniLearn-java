package service;

import entities.Profile;
import entities.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.sql.Timestamp;
import java.time.Instant;

public class ProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileService.class);

    public Profile createProfile(User user, String firstName, String lastName, String phone, String description) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A valid user is required to create a profile");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();

            User managedUser = session.get(User.class, user.getId());
            if (managedUser == null) {
                throw new IllegalArgumentException("User not found for id: " + user.getId());
            }

            Profile profile = new Profile();
            profile.setUser(managedUser);
            profile.setFirstName(firstName.trim());
            profile.setLastName(lastName.trim());
            profile.setPhone(blankToNull(phone));
            profile.setDescription(blankToNull(description));
            profile.setUpdatedAt(Timestamp.from(Instant.now()));

            session.persist(profile);
            transaction.commit();
            return profile;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to create profile for user id {}", user.getId(), exception);
            throw new IllegalStateException("Unable to create profile", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public Profile updateProfile(Profile profile) {
        if (profile == null || profile.getId() == null) {
            throw new IllegalArgumentException("Profile id is required for update");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();

            profile.setUpdatedAt(Timestamp.from(Instant.now()));
            Profile merged = (Profile) session.merge(profile);

            transaction.commit();
            return merged;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to update profile id {}", profile.getId(), exception);
            throw new IllegalStateException("Unable to update profile", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public Profile getProfileByUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A valid user is required");
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<Profile> query = session.createQuery(
                    "from Profile p where p.user.id = :userId",
                    Profile.class
            );
            query.setParameter("userId", user.getId());
            return query.uniqueResult();
        } catch (Exception exception) {
            LOGGER.error("Failed to fetch profile for user id {}", user.getId(), exception);
            throw new IllegalStateException("Unable to fetch profile by user", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public void deleteProfile(Long profileId) {
        Integer id = toIntId(profileId);
        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();

            Profile profile = session.get(Profile.class, id);
            if (profile == null) {
                rollback(transaction);
                return;
            }

            session.remove(profile);
            transaction.commit();
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to delete profile id {}", profileId, exception);
            throw new IllegalStateException("Unable to delete profile", exception);
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
            throw new IllegalArgumentException("Profile id is required");
        }
        if (id > Integer.MAX_VALUE || id < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Profile id is out of Integer range: " + id);
        }
        return id.intValue();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
