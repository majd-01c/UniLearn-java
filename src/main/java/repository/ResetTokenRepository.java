package repository;

import entities.ResetToken;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ResetTokenRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResetTokenRepository.class);

    public Optional<ResetToken> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<ResetToken> query = session.createQuery(
                    "from ResetToken rt where rt.token = :token",
                    ResetToken.class
            );
            query.setParameter("token", token.trim());
            return query.uniqueResultOptional();
        } catch (Exception exception) {
            LOGGER.error("Failed to find reset token", exception);
            throw new IllegalStateException("Unable to find reset token", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public List<ResetToken> findActiveTokensForUser(Long userId) {
        Integer id = toIntId(userId);
        Timestamp now = Timestamp.from(Instant.now());

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<ResetToken> query = session.createQuery(
                    "from ResetToken rt where rt.user.id = :userId and rt.used = :used and rt.expiryDate > :now order by rt.createdAt desc",
                    ResetToken.class
            );
            query.setParameter("userId", id);
            query.setParameter("used", (byte) 0);
            query.setParameter("now", now);
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find active tokens for user id {}", userId, exception);
            throw new IllegalStateException("Unable to find active reset tokens", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public ResetToken save(ResetToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Reset token is required");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            ResetToken merged = (ResetToken) session.merge(token);
            transaction.commit();
            return merged;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to save reset token", exception);
            throw new IllegalStateException("Unable to save reset token", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public boolean markTokenAsUsed(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();

            Query<ResetToken> query = session.createQuery(
                    "from ResetToken rt where rt.token = :token",
                    ResetToken.class
            );
            query.setParameter("token", tokenValue.trim());
            ResetToken token = query.uniqueResult();

            if (token == null) {
                rollback(transaction);
                return false;
            }

            token.setUsed((byte) 1);
            session.merge(token);
            transaction.commit();
            return true;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to mark token as used", exception);
            throw new IllegalStateException("Unable to mark reset token as used", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public int deleteExpiredTokens() {
        Timestamp now = Timestamp.from(Instant.now());

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();

                MutationQuery deleteQuery = session.createMutationQuery(
                    "delete from ResetToken rt where rt.expiryDate < :now"
            );
            deleteQuery.setParameter("now", now);
            int deletedRows = deleteQuery.executeUpdate();

            transaction.commit();
            return deletedRows;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to delete expired reset tokens", exception);
            throw new IllegalStateException("Unable to delete expired reset tokens", exception);
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
