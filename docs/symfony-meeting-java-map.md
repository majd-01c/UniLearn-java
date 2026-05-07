# Symfony Meeting Flow To Java Mapping

## Symfony Source

| Symfony file | Role in meeting flow |
| --- | --- |
| `src/Entity/Program/ClassMeeting.php` | Meeting entity, status constants, generated room code, `start()`, `end()`, `isLive()` helpers. |
| `src/Repository/ClassMeetingRepository.php` | Meeting queries by teacher assignment, live class meetings, upcoming class meetings, and room code. |
| `src/Controller/Teacher/TeacherMeetingController.php` | Teacher create/list/start/join/end/delete flow, ownership checks, Jitsi room render. |
| `src/Controller/Student/StudentLearningController.php` | Student class page, meeting list, active enrollment checks, live-only join flow. |
| `templates/Gestion_Program/student_learning/classe.html.twig` | Class page "Join Class Meeting" entry point. |
| `templates/Gestion_Program/student_learning/meetings.html.twig` | Student live/upcoming meeting page. |
| `templates/Gestion_Program/student_learning/meeting_room.html.twig` | Student Jitsi room. |
| `templates/Gestion_Program/teacher_meeting/*.html.twig` | Teacher create/list/room views. |
| `.env` | `JITSI_HOST=172.21.2.144:8443`. |

## Java Target

| Java file | Mirrors |
| --- | --- |
| `src/main/java/entities/ClassMeeting.java` | Symfony `ClassMeeting` entity, status constants, room generation, lifecycle helpers. |
| `src/main/java/repository/lms/ClassMeetingRepository.java` | Symfony `ClassMeetingRepository`. |
| `src/main/java/service/lms/MeetingService.java` | Symfony controller business rules and access checks. |
| `src/main/java/controller/lms/MeetingController.java` | Teacher/student meeting pages and Jitsi room controller. |
| `src/main/resources/view/lms/student/student-classe-view.fxml` | Symfony student class page entry button. |
| `src/main/resources/view/lms/student/student-meetings.fxml` | Symfony student meetings list. |
| `src/main/resources/view/lms/teacher/teacher-meetings.fxml` | Symfony teacher meetings list/create page. |
| `src/main/resources/view/lms/meeting-room.fxml` | Symfony Jitsi room templates. |
| `src/main/java/controller/lms/TeacherClasseWorkspaceController.java` | Symfony teacher class page meeting actions. |
| `src/main/java/util/AppNavigator.java` and `src/main/java/controller/AppShellController.java` | Symfony routes translated to JavaFX navigation. |
| `.env` | Java now reads the same `JITSI_HOST` through `ConfigurationProvider`. |

## Preserved Rules

- Meetings belong to `TeacherClasse`, not directly to `Classe`.
- Students reach meetings through an active `StudentClasse` enrollment.
- Students can see live and scheduled meetings, but can only join meetings with status `live`.
- Teachers can create scheduled meetings or start immediately.
- Teacher join starts a non-live meeting, matching Symfony's host behavior.
- Ending a meeting changes status to `ended` and sets `endedAt`.
- Jitsi room names use the Symfony format: `unilearn-` plus 16 random hex characters.
