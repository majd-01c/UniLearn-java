package service;

import org.hibernate.Session;
import org.hibernate.query.Query;
import util.HibernateSessionFactory;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UserDetailsService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<String> findCreatedEventSummaries(Integer userId, int limit) {
        if (userId == null) {
            return List.of();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<Object[]> query = session.createQuery(
                    "select e.id, e.title, e.status, e.startAt, e.createdAt "
                            + "from Event e "
                            + "where e.user.id = :userId "
                            + "order by e.createdAt desc",
                    Object[].class
            );
            query.setParameter("userId", userId);
            query.setMaxResults(Math.max(limit, 1));

            List<String> summaries = new ArrayList<>();
            for (Object[] row : query.getResultList()) {
                Integer eventId = (Integer) row[0];
                String title = safeText((String) row[1]);
                String status = safeText((String) row[2]);
                Timestamp startAt = (Timestamp) row[3];
                Timestamp createdAt = (Timestamp) row[4];

                summaries.add("#" + safeId(eventId)
                        + " | " + title
                        + " | Status: " + blankAsDash(status)
                        + " | Start: " + formatTimestamp(startAt)
                        + " | Created: " + formatTimestamp(createdAt));
            }

            return summaries;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public List<String> findGradeSummaries(Integer userId, int limit) {
        if (userId == null) {
            return List.of();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<Object[]> query = session.createQuery(
                    "select g.id, a.title, g.score, g.createdAt, "
                            + "student.id, student.email, teacher.id, teacher.email "
                            + "from Grade g "
                            + "left join g.assessment a "
                            + "left join g.userByStudentId student "
                            + "left join g.userByTeacherId teacher "
                            + "where student.id = :userId or teacher.id = :userId "
                            + "order by g.createdAt desc",
                    Object[].class
            );
            query.setParameter("userId", userId);
            query.setMaxResults(Math.max(limit, 1));

            List<String> summaries = new ArrayList<>();
            for (Object[] row : query.getResultList()) {
                Integer gradeId = (Integer) row[0];
                String assessmentTitle = safeText((String) row[1]);
                Double score = (Double) row[2];
                Timestamp createdAt = (Timestamp) row[3];
                Integer studentId = (Integer) row[4];
                String studentEmail = (String) row[5];
                Integer teacherId = (Integer) row[6];
                String teacherEmail = (String) row[7];

                boolean isStudent = userId.equals(studentId);
                boolean isTeacher = userId.equals(teacherId);

                String role;
                if (isStudent && isTeacher) {
                    role = "Student/Teacher";
                } else if (isStudent) {
                    role = "Student";
                } else {
                    role = "Teacher";
                }

                summaries.add("#" + safeId(gradeId)
                        + " | " + role
                        + " | Assessment: " + blankAsDash(assessmentTitle)
                        + " | Score: " + formatScore(score)
                        + " | Student: " + blankAsDash(studentEmail)
                        + " | Teacher: " + blankAsDash(teacherEmail)
                        + " | Created: " + formatTimestamp(createdAt));
            }

            return summaries;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public long countEventsCreated(Integer userId) {
        if (userId == null) {
            return 0L;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<Long> query = session.createQuery(
                    "select count(e.id) from Event e where e.user.id = :userId",
                    Long.class
            );
            query.setParameter("userId", userId);

            Long result = query.uniqueResult();
            return result == null ? 0L : result;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public long countGradesAsStudent(Integer userId) {
        if (userId == null) {
            return 0L;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<Long> query = session.createQuery(
                    "select count(g.id) from Grade g where g.userByStudentId.id = :userId",
                    Long.class
            );
            query.setParameter("userId", userId);

            Long result = query.uniqueResult();
            return result == null ? 0L : result;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    public long countGradesAsTeacher(Integer userId) {
        if (userId == null) {
            return 0L;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            Query<Long> query = session.createQuery(
                    "select count(g.id) from Grade g where g.userByTeacherId.id = :userId",
                    Long.class
            );
            query.setParameter("userId", userId);

            Long result = query.uniqueResult();
            return result == null ? 0L : result;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "-";
        }
        return String.format("%.2f", score);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String blankAsDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private String safeId(Integer id) {
        return id == null ? "-" : String.valueOf(id);
    }
}
