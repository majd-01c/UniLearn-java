# UniLearn – University Learning Management System (Java Desktop)

<div align="center">

[![Last Commit](https://img.shields.io/github/last-commit/majd-01c/UniLearn-java/communication)](https://github.com/majd-01c/UniLearn-java/commits/communication)
[![Commit Activity](https://img.shields.io/github/commit-activity/t/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/commits/communication)
[![Contributors](https://img.shields.io/github/contributors/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/graphs/contributors)
[![Forks](https://img.shields.io/github/forks/majd-01c/UniLearn-java?style=social)](https://github.com/majd-01c/UniLearn-java/network/members)
[![Stars](https://img.shields.io/github/stars/majd-01c/UniLearn-java?style=social)](https://github.com/majd-01c/UniLearn-java/stargazers)
[![Repo Size](https://img.shields.io/github/repo-size/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java)
[![Issues](https://img.shields.io/github/issues/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/issues)

> 🌿 **Active branch:** `communication` — Communication & Community module

</div>

## Overview

This project was developed as part of the **PIDEV – 3rd Year Engineering Program** at **Esprit School of Engineering** (Academic Year 2025–2026).

UniLearn Desktop is a **JavaFX desktop application** that mirrors the UniLearn web platform. It connects to the same MySQL database as the Symfony web application, enabling administrators, teachers, and students to manage academic programs, evaluations, grades, schedules, complaints, document requests, forums, events, messaging, and more — all through a native desktop interface.

---

## Features

### 🔐 Authentication & Security
- Secure login with role-based access (Admin, Teacher, Student)
- Password reset via email (Google SMTP)
- **SMS Verification** – Two-factor authentication via SMS OTP (`SmsVerificationController`)
- **Face ID Login** – Biometric face recognition for secure login

### 👥 User Management
- Admin panel for creating, editing, and managing users with profile pictures
- Role-based navigation: UI dynamically adapts based on logged-in user role (`UserSession`)

### 📋 Evaluation System

| Role | Access |
|------|--------|
| 🎓 **Student** | View grades, schedules, submit complaints, request documents |
| 👨‍🏫 **Teacher** | Create assessments, enter grades, view class schedules |
| 🛡 **Admin** | Manage complaints, process document requests (PDF), create schedules |

### 💬 Communication Module *(this branch)*

The `communication` branch adds the full **Communication & Community** feature set:

#### 🗣️ Forum
- Browse and post **forum topics** with full CRUD support
- Post, edit, and delete **comments** on any topic
- Role-aware access: students and teachers can participate; admins can moderate
- Controllers: `controller/forum/`
- Entities: `entities/forum/` (Topic, Comment, etc.)

#### 📅 Events
- Create, manage, and list **university events** (`Event` entity)
- Students can **register / participate** in events (`EventParticipation` entity)
- Full lifecycle: creation → participation → management

#### 💌 Messaging
- **Messenger Messages** – Internal messaging system between users (`MessengerMessages` entity)
- **Program Chat** – Contextual group chat attached to academic programs (`ProgramChatMessage` entity)

#### 📱 SMS Verification
- Two-factor SMS OTP flow during authentication (`SmsVerificationController`)
- Integrates with an external SMS gateway via the `.env` configuration

#### 🤖 IArooms (AI-Powered Rooms)
- AI-assisted room/resource management module (`controller/iarooms/`, `entities/iarooms/`)
- Intelligent scheduling and room suggestion powered by the `IArooms/` AI service directory

### 📚 LMS (Learning Management System)
- Course and program management (classes, modules, courses, content)
- Quizzes with questions, choices, and user answers
- Course documents and content management

---

## Tech Stack

### Desktop Application

| Technology | Version | Purpose |
|---|---|---|
| **Java** | 17 | Core language |
| **JavaFX** | 17 | UI framework (FXML + CSS) |
| **Hibernate ORM** | 6 | Database abstraction & entity management |
| **MySQL** | 8.0 | Shared relational database (with Symfony web project) |
| **Maven** | 3.8+ | Dependency management & build tool |

### External Services

| Service | Purpose |
|---|---|
| **Google SMTP** | Email notifications (password reset, verification) |
| **Face API** | Biometric face recognition login |
| **SMS Gateway** | Two-factor SMS OTP verification |

### DevOps & Tools
- **Docker** – Containerized MySQL database (`docker-compose.yml`)
- **IntelliJ IDEA / NetBeans** – Recommended IDEs
- **Scene Builder** – JavaFX FXML visual designer

---

## Project Architecture

```
UniLearn-java/
├── src/main/java/
│   ├── controller/                     # JavaFX controllers
│   │   ├── AppShellController.java     # Main app shell & navigation
│   │   ├── LoginController.java        # Authentication (email + Face ID)
│   │   ├── SmsVerificationController.java  # SMS OTP two-factor auth
│   │   ├── UserListController.java     # Admin user management
│   │   ├── UserProfileController.java  # User profile management
│   │   ├── evaluation/                 # Evaluation module (Admin/Teacher/Student)
│   │   ├── forum/                      # Forum topics & comments
│   │   ├── iarooms/                    # AI-assisted room management
│   │   ├── job_offer/                  # Job offers module
│   │   └── lms/                        # LMS (courses, programs, quizzes)
│   ├── entities/                       # Hibernate ORM entities (shared DB)
│   │   ├── User.java                   # Core user entity
│   │   ├── Event.java                  # University events
│   │   ├── EventParticipation.java     # Event registration
│   │   ├── MessengerMessages.java      # Internal messaging
│   │   ├── ProgramChatMessage.java     # Program group chat
│   │   ├── Reclamation.java            # Student complaints
│   │   ├── DocumentRequest.java        # Document requests
│   │   ├── Schedule.java               # Class schedules
│   │   ├── forum/                      # Forum entities (Topic, Comment)
│   │   ├── iarooms/                    # IArooms entities
│   │   └── job_offer/                  # Job offer entities
│   ├── service/ & services/            # Business logic services
│   ├── repository/                     # Database access layer (Hibernate)
│   ├── security/                       # UserSession, role management
│   ├── util/                           # AppNavigator, helpers
│   ├── dto/                            # Data Transfer Objects
│   └── validation/                     # Input validation helpers
├── src/main/resources/
│   └── view/
│       ├── evaluation/
│       │   ├── student/                # Student evaluation FXML
│       │   ├── teacher/                # Teacher evaluation FXML
│       │   └── admin/                  # Admin evaluation FXML
│       ├── forum/                      # Forum FXML views
│       ├── lms/                        # LMS FXML views
│       └── styles/                     # evaluation.css, app.css
├── IArooms/                            # AI room management service
├── docker-compose.yml                  # MySQL container config
├── pom.xml                             # Maven build config
└── .env                                # Environment variables (DB, SMTP, SMS)
```

---

## Role-Based View Routing

The app automatically loads the correct UI based on the logged-in user's role:

| Role | Evaluation | Forum | Events | Messaging |
|------|------------|-------|--------|-----------|
| `STUDENT` | Grades, Schedule, Complaints, Docs | Browse & Post | Register | View |
| `TEACHER` | Assessments, Grades Entry, Schedule | Browse & Post | Register | View |
| `ADMIN` | Complaints Mgmt, Doc Requests, Schedules | Moderate | Create & Manage | Full |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Docker (optional, for DB container)
- JavaFX 17 SDK

### Installation

```bash
# 1. Clone the repository and switch to the communication branch
git clone https://github.com/majd-01c/UniLearn-java.git
cd UniLearn-java
git checkout communication

# 2. Start the database (Docker)
docker compose up -d

# 3. Configure environment variables
# Edit .env and set:
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
#   SMTP credentials (Gmail)
#   SMS gateway API key

# 4. Configure Hibernate
# Edit src/main/resources/hibernate.cfg.xml
# Set your MySQL connection details

# 5. Build the project
mvn clean package

# 6. Run the application
mvn javafx:run
```

### Default Accounts

| Email | Password | Role |
|-------|----------|------|
| admin@unilearn.com | admin123 | ADMIN |
| student1@unilearn.com | student123 | STUDENT |
| teacher@unilearn.com | teacher123 | TEACHER |

> ℹ️ The same database is shared with the Symfony web project. Run the web project setup first to populate the database.

---

## Contributors

| Name | GitHub | Module | Role |
|------|--------|--------|------|
| Alaa Salem | [@alaasalem-blip](https://github.com/alaasalem-blip) | Gestion User | Full-Stack Developer |
| Majd Labidi | [@majd-01c](https://github.com/majd-01c) | Gestion Communication | Full-Stack Developer |
| Khalil Fekih | [@khalil-feki](https://github.com/khalil-feki) | Gestion Evaluation | Full-Stack Developer |
| Haroun Chaabane | [@harounchaabane](https://github.com/harounchaabane) | Gestion Program | Full-Stack Developer |
| Dhia Amri | [@dhia573](https://github.com/dhia573) | Gestion Job Offre | Full-Stack Developer |

---

## Academic Context

Developed at **Esprit School of Engineering – Tunisia**
PIDEV – 3A | 2025–2026

- **Degree:** Engineering in Computer Science
- **Course:** PIDEV (Projet Intégré de Développement)
- **Year:** 3rd Year (3A)
- **Academic Year:** 2025–2026

---

## Related Project

This desktop app shares its database with the UniLearn Symfony web application:
👉 [Esprit-PIDEV-3A57--2026-UniLearn](https://github.com/majd-01c/Esprit-PIDEV-3A57--2026-UniLearn)

---

## Acknowledgments

- **Esprit School of Engineering** for the academic framework and guidance
- **JavaFX & OpenJFX** open-source community
- **Hibernate ORM** for seamless database integration
- All open-source libraries that made this project possible
