I have everything I need. Here is the full README for the Java project:

text
# UniLearn – University Learning Management System (Java Desktop)

<div align="center">

[![Last Commit](https://img.shields.io/github/last-commit/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/commits/main)
[![Commit Activity](https://img.shields.io/github/commit-activity/t/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/commits/main)
[![Contributors](https://img.shields.io/github/contributors/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/graphs/contributors)
[![Forks](https://img.shields.io/github/forks/majd-01c/UniLearn-java?style=social)](https://github.com/majd-01c/UniLearn-java/network/members)
[![Stars](https://img.shields.io/github/stars/majd-01c/UniLearn-java?style=social)](https://github.com/majd-01c/UniLearn-java/stargazers)
[![Repo Size](https://img.shields.io/github/repo-size/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java)
[![Issues](https://img.shields.io/github/issues/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/issues)

</div>

## Overview

This project was developed as part of the **PIDEV – 3rd Year Engineering Program** at **Esprit School of Engineering** (Academic Year 2025–2026).

UniLearn Desktop is a **JavaFX desktop application** that mirrors the UniLearn web platform. It connects to the same MySQL database as the Symfony web application, enabling administrators, teachers, and students to manage academic programs, evaluations, grades, schedules, complaints, document requests, forums, and more — all through a native desktop interface.

## Features

- **Authentication** – Secure login with role-based access (Admin, Teacher, Student), password reset via email, and Face ID biometric login
- **User Management** – Admin panel for creating, editing, and managing users with profile pictures
- **Evaluation System** – Role-aware evaluation module:
  - 🎓 **Student** – View grades, schedules, submit complaints, request documents
  - 👨‍🏫 **Teacher** – Create assessments, enter grades, view class schedules
  - 🛡 **Admin** – Manage complaints, process document requests (PDF upload), create schedules
- **Forum** – Browse and participate in forum topics and comments
- **LMS** – Learning Management System module for course and program management
- **Face ID Login** – Biometric face verification for secure authentication
- **Email Notifications** – Password reset and verification via Google SMTP
- **Role-Based Navigation** – Dynamic UI that adapts automatically based on logged-in user role

## Tech Stack

### Desktop Application
- **Java 17** – Core language
- **JavaFX 17** – UI framework (FXML + CSS)
- **Hibernate ORM 6** – Database abstraction and entity management
- **MySQL 8.0** – Shared relational database (with the Symfony web project)
- **Maven** – Dependency management and build tool

### External Services
- **Google SMTP** – Email notifications (password reset, verification)
- **Face API** – Biometric face recognition login

### DevOps & Tools
- **Docker** – Containerized database environment
- **IntelliJ IDEA / NetBeans** – Recommended IDEs
- **Scene Builder** – JavaFX FXML visual designer

## Architecture
UniLearn-java/
├── src/main/java/
│ ├── controller/ # JavaFX controllers
│ │ ├── AppShellController # Main app shell & navigation
│ │ ├── LoginController # Authentication
│ │ ├── evaluation/ # Evaluation module (Admin/Teacher/Student)
│ │ ├── forum/ # Forum module
│ │ └── lms/ # LMS module
│ ├── entities/ # Hibernate ORM entities (shared with Symfony DB)
│ ├── service/ # Business logic services
│ ├── repository/ # Database access layer (Hibernate)
│ ├── security/ # UserSession, role management
│ └── util/ # AppNavigator, helpers
├── src/main/resources/
│ └── view/
│ ├── evaluation/
│ │ ├── student/ # Student evaluation FXML
│ │ ├── teacher/ # Teacher evaluation FXML
│ │ └── admin/ # Admin evaluation FXML
│ ├── forum/ # Forum FXML views
│ ├── lms/ # LMS FXML views
│ └── styles/ # evaluation.css, app.css
└── pom.xml # Maven build config

text

## Role-Based View Routing

The app automatically loads the correct UI based on the logged-in user's role:

| Role | Evaluation View | Access |
|------|----------------|--------|
| `STUDENT` | Grades, Schedule, Complaints, Documents | Read + Submit |
| `TEACHER` | Assessments, Grades Entry, Schedule | Create + Manage |
| `ADMIN` | Complaints Management, Document Requests, Schedules | Full Control |

## Contributors

| Name | GitHub | Module | Role |
|------|--------|--------|------|
| Alaa Salem | [@alaasalem-blip](https://github.com/alaasalem-blip) | Gestion User | Full-Stack Developer |
| Majd Labidi | [@majd-01c](https://github.com/majd-01c) | Gestion Communication | Full-Stack Developer |
| Khalil Fekih | [@khalil-feki](https://github.com/khalil-feki) | Gestion Evaluation | Full-Stack Developer |
| Haroun Chaabane | [@harounchaabane](https://github.com/harounchaabane) | Gestion Program | Full-Stack Developer |
| Dhia Amri | [@dhia573](https://github.com/dhia573) | Gestion Job Offre | Full-Stack Developer |

## Academic Context

Developed at **Esprit School of Engineering – Tunisia**
PIDEV – 3A | 2025–2026

- **Degree:** Engineering in Computer Science
- **Course:** PIDEV (Projet Intégré de Développement)
- **Year:** 3rd Year (3A)
- **Academic Year:** 2025–2026

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Docker (optional, for DB container)
- JavaFX 17 SDK

### Installation

```bash
# 1. Clone the repository
git clone https://github.com/majd-01c/UniLearn-java.git
cd UniLearn-java

# 2. Start the database (Docker)
docker compose up -d

# 3. Configure database connection
# Edit src/main/resources/hibernate.cfg.xml
# Set your MySQL host, username, and password

# 4. Build the project
mvn clean package

# 5. Run the application
mvn javafx:run
```

### Default Accounts

| Email | Password | Role |
|-------|----------|------|
| admin@unilearn.com | admin123 | ADMIN |
| student1@unilearn.com | student123 | STUDENT |
| teacher@unilearn.com | teacher123 | TEACHER |

> ℹ️ The same database is shared with the Symfony web project. Run the web project setup first to populate the database.

## Related Project

This desktop app shares its database with the UniLearn Symfony web application:
👉 [Esprit-PIDEV-3A57--2026-UniLearn](https://github.com/majd-01c/Esprit-PIDEV-3A57--2026-UniLearn)

## Acknowledgments

- **Esprit School of Engineering** for the academic framework and guidance
- **JavaFX & OpenJFX** open-source community
- **Hibernate ORM** for seamless database integration
- All open-source libraries that made this project possible
