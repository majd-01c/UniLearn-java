package services;

import entities.Classe;
import entities.Choice;
import entities.Contenu;
import entities.Course;
import entities.Event;
import entities.forum.ForumComment;
import entities.forum.ForumCategory;
import entities.forum.ForumTopic;
import entities.job_offer.JobOffer;
import entities.Module;
import entities.Program;
import entities.Quiz;
import entities.Assessment;
import entities.TeacherClasse;
import entities.User;
import entities.UserAnswer;
import Utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public abstract class ServiceSupport {
    protected final Connection connection;

    protected ServiceSupport() {
        connection = MyDatabase.getInstance().getConnection();
    }

    protected void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    protected void setNullableTimestamp(PreparedStatement statement, int index, Timestamp value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, value);
        }
    }

    protected void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    protected Integer getNullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    protected User mapUserReference(Integer id) {
        if (id == null) {
            return null;
        }
        User user = new User();
        user.setId(id);
        return user;
    }

    protected Course mapCourseReference(Integer id) {
        if (id == null) {
            return null;
        }
        Course course = new Course();
        course.setId(id);
        return course;
    }

    protected ForumCategory mapForumCategoryReference(Integer id) {
        if (id == null) {
            return null;
        }
        ForumCategory category = new ForumCategory();
        category.setId(id);
        return category;
    }

    protected Classe mapClasseReference(Integer id) {
        if (id == null) {
            return null;
        }
        Classe classe = new Classe();
        classe.setId(id);
        return classe;
    }

    protected Contenu mapContenuReference(Integer id) {
        if (id == null) {
            return null;
        }
        Contenu contenu = new Contenu();
        contenu.setId(id);
        return contenu;
    }

    protected Quiz mapQuizReference(Integer id) {
        if (id == null) {
            return null;
        }
        Quiz quiz = new Quiz();
        quiz.setId(id);
        return quiz;
    }

    protected Choice mapChoiceReference(Integer id) {
        if (id == null) {
            return null;
        }
        Choice choice = new Choice();
        choice.setId(id);
        return choice;
    }

    protected UserAnswer mapUserAnswerReference(Integer id) {
        if (id == null) {
            return null;
        }
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setId(id);
        return userAnswer;
    }

    protected Assessment mapAssessmentReference(Integer id) {
        if (id == null) {
            return null;
        }
        Assessment assessment = new Assessment();
        assessment.setId(id);
        return assessment;
    }

    protected JobOffer mapJobOfferReference(Integer id) {
        if (id == null) {
            return null;
        }
        JobOffer jobOffer = new JobOffer();
        jobOffer.setId(id);
        return jobOffer;
    }

    protected Program mapProgramReference(Integer id) {
        if (id == null) {
            return null;
        }
        Program program = new Program();
        program.setId(id);
        return program;
    }

    protected Module mapModuleReference(Integer id) {
        if (id == null) {
            return null;
        }
        Module module = new Module();
        module.setId(id);
        return module;
    }

    protected TeacherClasse mapTeacherClasseReference(Integer id) {
        if (id == null) {
            return null;
        }
        TeacherClasse teacherClasse = new TeacherClasse();
        teacherClasse.setId(id);
        return teacherClasse;
    }

    protected ForumTopic mapForumTopicReference(Integer id) {
        if (id == null) {
            return null;
        }
        ForumTopic forumTopic = new ForumTopic();
        forumTopic.setId(id);
        return forumTopic;
    }

    protected ForumComment mapForumCommentReference(Integer id) {
        if (id == null) {
            return null;
        }
        ForumComment forumComment = new ForumComment();
        forumComment.setId(id);
        return forumComment;
    }

    protected Event mapEventReference(Integer id) {
        if (id == null) {
            return null;
        }
        Event event = new Event();
        event.setId(id);
        return event;
    }
}
