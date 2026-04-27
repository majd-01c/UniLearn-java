package repository;

import entities.FaceVerificationLog;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

public class FaceVerificationLogRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceVerificationLogRepository.class);

    public FaceVerificationLog save(FaceVerificationLog log) {
        if (log == null) {
            throw new IllegalArgumentException("Face verification log is required");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;

        try {
            transaction = session.beginTransaction();
            FaceVerificationLog merged = (FaceVerificationLog) session.merge(log);
            transaction.commit();
            return merged;
        } catch (Exception exception) {
            rollback(transaction);
            LOGGER.error("Failed to save face verification log", exception);
            throw new IllegalStateException("Unable to save face verification log", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    private void rollback(Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
    }
}
