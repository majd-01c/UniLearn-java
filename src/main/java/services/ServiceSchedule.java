package services;

import entities.Schedule;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceSchedule extends ServiceSupport implements IService<Schedule> {

    @Override
    public void add(Schedule schedule) {
        String sql = "INSERT INTO schedule (teacher_id, course_id, classe_id, day_of_week, start_time, end_time, room, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, schedule.getUser() == null ? null : schedule.getUser().getId());
            statement.setInt(2, schedule.getCourse().getId());
            statement.setInt(3, schedule.getClasse().getId());
            statement.setString(4, schedule.getDayOfWeek());
            statement.setTime(5, schedule.getStartTime());
            statement.setTime(6, schedule.getEndTime());
            setNullableString(statement, 7, schedule.getRoom());
            statement.setDate(8, schedule.getStartDate());
            statement.setDate(9, schedule.getEndDate());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Schedule schedule) {
        String sql = "UPDATE schedule SET teacher_id = ?, course_id = ?, classe_id = ?, day_of_week = ?, start_time = ?, end_time = ?, room = ?, start_date = ?, end_date = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, schedule.getUser() == null ? null : schedule.getUser().getId());
            statement.setInt(2, schedule.getCourse().getId());
            statement.setInt(3, schedule.getClasse().getId());
            statement.setString(4, schedule.getDayOfWeek());
            statement.setTime(5, schedule.getStartTime());
            statement.setTime(6, schedule.getEndTime());
            setNullableString(statement, 7, schedule.getRoom());
            statement.setDate(8, schedule.getStartDate());
            statement.setDate(9, schedule.getEndDate());
            statement.setInt(10, schedule.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Schedule schedule) {
        String sql = "DELETE FROM schedule WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, schedule.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Schedule> getALL() {
        String sql = "SELECT id, teacher_id, course_id, classe_id, day_of_week, start_time, end_time, room, start_date, end_date FROM schedule";
        List<Schedule> schedules = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Schedule schedule = new Schedule();
                schedule.setId(resultSet.getInt("id"));
                schedule.setUser(mapUserReference(getNullableInteger(resultSet, "teacher_id")));
                schedule.setCourse(mapCourseReference(resultSet.getInt("course_id")));
                schedule.setClasse(mapClasseReference(resultSet.getInt("classe_id")));
                schedule.setDayOfWeek(resultSet.getString("day_of_week"));
                schedule.setStartTime(resultSet.getTime("start_time"));
                schedule.setEndTime(resultSet.getTime("end_time"));
                schedule.setRoom(resultSet.getString("room"));
                schedule.setStartDate(resultSet.getDate("start_date"));
                schedule.setEndDate(resultSet.getDate("end_date"));
                schedules.add(schedule);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return schedules;
    }
}
