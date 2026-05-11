# UniLearn – University Learning Management System (Java Desktop)

<div align="center">

[![Last Commit](https://img.shields.io/github/last-commit/majd-01c/UniLearn-java/communication)](https://github.com/majd-01c/UniLearn-java/commits/communication)
[![Commit Activity](https://img.shields.io/github/commit-activity/t/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/commits/communication)
[![Contributors](https://img.shields.io/github/contributors/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/graphs/contributors)
[![Forks](https://img.shields.io/github/forks/majd-01c/UniLearn-java?style=social)](https://github.com/majd-01c/UniLearn-java/network/members)
[![Stars](https://img.shields.io/github/stars/majd-01c/UniLearn-java?style=social)](https://github.com/majd-01c/UniLearn-java/stargazers)
[![Repo Size](https://img.shields.io/github/repo-size/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java)
[![Issues](https://img.shields.io/github/issues/majd-01c/UniLearn-java)](https://github.com/majd-01c/UniLearn-java/issues)
[![Java](https://img.shields.io/badge/Java-17-orange?logo=java)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-17-blue?logo=java)](https://openjfx.io/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?logo=apachemaven)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Hibernate](https://img.shields.io/badge/Hibernate-6-59666C?logo=hibernate)](https://hibernate.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Academic-green)](https://github.com/majd-01c/UniLearn-java)

<br/>

> 🌿 **Active branch:** `communication` — Communication & Community Module  
> 🎓 **PIDEV 3A** · Esprit School of Engineering · 2025–2026

</div>

---

## 📖 Table of Contents

- [Overview](#overview)
- [Features](#features)
  - [Authentication & Security](#-authentication--security)
  - [User Management](#-user-management)
  - [Evaluation System](#-evaluation-system)
  - [Communication Module](#-communication-module-this-branch)
  - [LMS](#-lms-learning-management-system)
  - [Job Offers](#-job-offers)
- [Tech Stack](#-tech-stack)
- [Project Architecture](#-project-architecture)
- [Role-Based View Routing](#-role-based-view-routing)
- [Getting Started](#-getting-started)
- [Environment Configuration](#-environment-configuration)
- [Default Accounts](#-default-accounts)
- [Contributors](#-contributors)
- [Academic Context](#-academic-context)
- [Related Project](#-related-project)
- [Acknowledgments](#-acknowledgments)

---

## Overview

UniLearn Desktop is a **JavaFX desktop application** built as part of the **PIDEV – 3rd Year Engineering Program** at **Esprit School of Engineering** (Academic Year 2025–2026).

It mirrors the UniLearn web platform and connects to the **same shared MySQL database** as the Symfony web application. This enables seamless data synchronization between the desktop and web clients — administrators, teachers, and students can operate from either interface without data inconsistency.

The desktop app provides full management of:
- Academic programs, modules, courses, and schedules
- Evaluations, grades, assessments, and quizzes
- Complaints, document requests, and administrative workflows
- **Forums, events, messaging, and community features** *(this branch)*
- AI-assisted room management (IArooms)
- Job offers and student career support

---

## Features

### 🔐 Authentication & Security

| Feature | Description | Implementation |
|---------|-------------|----------------|
| Role-Based Login | Secure login with Admin / Teacher / Student roles | `LoginController.java` |
| Password Reset | OTP code sent via Google SMTP email | `PasswordResetController.java`, `PasswordResetRequestController.java` |
| SMS Verification | Two-factor authentication via SMS OTP | `SmsVerificationController.java` |
| Face ID Login | Biometric face recognition for passwordless login | `LoginController.java` + Face API |
| Change Password | Authenticated users can update their password | `ChangePasswordController.java` |
| Session Management | Singleton `UserSession` tracks logged-in user across views | `security/UserSession.java` |

---

### 👥 User Management

| Feature | Description | Role |
|---------|-------------|------|
| User List | Paginated, searchable user table with filters | Admin |
| Create / Edit User | Full user form with profile picture upload | Admin |
| User Details | Read-only user profile viewer | Admin |
| User Profile | Self-service profile editing with photo update | All |
| Role Assignment | Assign STUDENT / TEACHER / ADMIN roles | Admin |
| User Status | Enable / Disable accounts (`UserStatus` enum) | Admin |

---

### 📋 Evaluation System

Fully role-aware evaluation module that loads different views based on the authenticated user's role:

| Role | Capabilities |
|------|-------------|
| 🎓 **Student** | View personal grades, class schedule, submit complaints (`Reclamation`), request official documents (`DocumentRequest`) |
| 👨‍🏫 **Teacher** | Create and manage assessments, enter and update student grades, view class schedules |
| 🛡️ **Admin** | Manage all complaints, process document requests (generate PDF responses), create and manage schedules for all classes |

- **Entities:** `Assessment`, `Grade`, `Schedule`, `Reclamation`, `DocumentRequest`
- **Controllers:** `controller/evaluation/` (split by role: `student/`, `teacher/`, `admin/`)

---

### 💬 Communication Module *(this branch)*

The `communication` branch introduces the full **Communication & Community** feature set — the module developed by **Majd Labidi** for the PIDEV project.

---

#### 🗣️ Forum

A fully functional academic discussion forum:

- **Topics** – Any user can create a forum topic with a title and content body
- **Comments** – Users can post, edit, and delete comments on any topic
- **Moderation** – Admins can delete any topic or comment
- **Role-Aware UI** – Interface adapts based on whether the user is the author or an admin
- **Full CRUD** – Create, Read, Update, Delete for both topics and comments
controller/forum/
entities/forum/ ← Topic, Comment, ForumPost entities
view/forum/ ← FXML views for topic list, topic detail, comment form

text

---

#### 📅 Events

University event management with participation tracking:

- **Event Creation** – Admins can create events with title, description, date, location, and capacity
- **Event Listing** – All users can browse upcoming and past events
- **Participation** – Students and teachers can register for events
- **Participation Management** – Admins can view and manage all registrations
- **Status Tracking** – Events track registration count vs. capacity

```java
// Key entities
Event.java              ← title, description, date, location, capacity, organizer
EventParticipation.java ← user ↔ event many-to-many registration
```

---

#### 💌 Messaging

Two messaging systems for internal communication:

**Messenger (Direct Messages)**
- User-to-user private messaging
- Persisted via `MessengerMessages` entity
- Accessible from user profile / contact views

**Program Chat (Group Chat)**
- Group chat scoped to an academic program
- All students and teachers enrolled in a program can post
- Persisted via `ProgramChatMessage` entity

```java
MessengerMessages.java   ← sender, recipient, content, timestamp
ProgramChatMessage.java  ← program, author, content, timestamp
```

---

#### 📱 SMS Verification

Two-factor authentication flow:

1. User logs in with email + password
2. System sends a **6-digit OTP** via SMS to the registered phone number
3. User enters the OTP in `SmsVerificationController`
4. On success → session is established; on failure → access denied

- SMS gateway configured via `.env` (`SMS_API_KEY`, `SMS_SENDER`)
- `SmsVerificationController.java` handles OTP generation, sending, and validation

---

#### 🤖 IArooms (AI-Powered Room Management)

Intelligent room/resource scheduling assistant:

- AI-assisted **room suggestion** based on class size, equipment needs, and availability
- **Room booking management** for classes and events
- Integrates with the `IArooms/` Python/AI service directory (separate process)
- JavaFX frontend communicates with the AI backend via REST or socket
IArooms/ ← AI service (Python/ML model)
controller/iarooms/ ← JavaFX controllers for room management UI
entities/iarooms/ ← Room, Booking, RoomRequest entities

text

---

### 📚 LMS (Learning Management System)

| Feature | Entities |
|---------|---------|
| Programs | `Program`, `ProgramModule`, `BuildProgram` |
| Modules | `Module`, `ModuleCourse` |
| Courses | `Course`, `CourseContenu`, `CourseDocument` |
| Classes | `Classe`, `ClasseModule`, `ClasseCourse`, `ClasseContenu` |
| Content | `Contenu` |
| Quizzes | `Quiz`, `Question`, `Choice`, `Answer`, `UserAnswer` |
| Meetings | `ClassMeeting` |

---

### 💼 Job Offers

- Students can browse job and internship offers
- Admins can create and manage offers
- Controllers: `controller/job_offer/`
- Entities: `entities/job_offer/`

---

## 🛠 Tech Stack

### Desktop Application

| Technology | Version | Role |
|---|---|---|
| **Java** | 17 | Core language |
| **JavaFX** | 17 | UI framework — FXML layouts + CSS styling |
| **Hibernate ORM** | 6 | Entity mapping, HQL queries, session management |
| **MySQL** | 8.0 | Shared relational database (also used by Symfony web app) |
| **Maven** | 3.8+ | Build tool, dependency management, JavaFX plugin |

### External Services & APIs

| Service | Purpose | Config Key |
|---|---|---|
| **Google SMTP** | Email OTP for password reset & verification | `SMTP_HOST`, `SMTP_USER`, `SMTP_PASS` |
| **Face API** | Biometric face recognition login | `FACE_API_KEY`, `FACE_API_URL` |
| **SMS Gateway** | Two-factor SMS OTP delivery | `SMS_API_KEY`, `SMS_SENDER` |
| **IArooms AI** | Room suggestion engine | Internal service (`IArooms/`) |

### DevOps & Tools

| Tool | Purpose |
|---|---|
| **Docker Compose** | MySQL container — `docker-compose.yml` |
| **IntelliJ IDEA** | Recommended IDE |
| **Scene Builder** | JavaFX FXML visual designer |
| **NetBeans** | Alternative IDE |

---
## 📁 Project Architecture

### Source Structure

```text
UniLearn-java/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── controller/
│   │   │   ├── entities/
│   │   │   ├── service/
│   │   │   ├── services/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   ├── util/
│   │   │   ├── dto/
│   │   │   ├── Utils/
│   │   │   └── validation/
│   │   └── resources/
│   │       ├── hibernate.cfg.xml
│   │       └── view/
│   └── test/
├── IArooms/
├── lib/
├── uploads/
├── docs/
├── backup/
├── docker-compose.yml
├── pom.xml
└── .env
```

### Main Packages

| Package | Purpose |
|---------|---------|
| `controller/` | JavaFX MVC controllers for authentication, dashboards, forum, LMS, evaluation, job offers, and IA rooms |
| `entities/` | Hibernate ORM entities mapped to the shared MySQL database |
| `service/` / `services/` | Business logic and module services |
| `repository/` | Data access layer using Hibernate |
| `security/` | Session handling and authentication helpers |
| `util/` / `Utils/` | Navigation, loaders, and utility helpers |
| `validation/` | Input validation and form rules |
| `resources/view/` | FXML pages and CSS styles |

### Controller Modules

- `AppShellController.java` — main shell, sidebar, and role-based navigation
- `LoginController.java` — authentication with email/password and Face ID
- `SmsVerificationController.java` — SMS OTP verification
- `UserListController.java` / `UserFormController.java` — admin user management
- `evaluation/` — complaints, grades, schedules, document requests
- `forum/` — topics and comments
- `iarooms/` — AI-assisted room management
- `job_offer/` — internship and job offers
- `lms/` — programs, courses, and quizzes

### Entity Domains

**User & Security**
- `User`, `Role`, `UserStatus`, `Profile`, `ResetToken`, `FaceVerificationLog`

**Communication**
- `Event`, `EventParticipation`, `MessengerMessages`, `ProgramChatMessage`, `forum/`

**Evaluation**
- `Reclamation`, `DocumentRequest`, `Schedule`, `Assessment`, `Grade`

**LMS**
- `Program`, `ProgramModule`, `BuildProgram`
- `Module`, `ModuleCourse`
- `Course`, `CourseContenu`, `CourseDocument`
- `Classe`, `ClasseModule`, `ClasseCourse`, `ClasseContenu`
- `Quiz`, `Question`, `Choice`, `Answer`, `UserAnswer`
- `ClassMeeting`, `StudentClasse`, `TeacherClasse`, `Contenu`

### Resources

- `hibernate.cfg.xml` — Hibernate + MySQL configuration
- `view/evaluation/` — FXML interfaces by role: student, teacher, admin
- `view/forum/` — forum interfaces
- `view/lms/` — LMS interfaces
- `view/styles/` — global CSS files

### Other Directories

- `IArooms/` — AI room management service
- `lib/` — local dependencies
- `uploads/` — uploaded profile pictures and documents
- `docs/` — project documentation
- `backup/` — backup scripts

## 🔀 Role-Based View Routing

The `AppShellController` automatically routes the user to the correct views upon login:

| Module | STUDENT | TEACHER | ADMIN |
|--------|---------|---------|-------|
| **Dashboard** | FrontOffice Home | FrontOffice Home | BackOffice Home |
| **Evaluation** | Grades, Schedule, Complaints, Doc Requests | Assessments, Grade Entry, Schedule | Complaints Mgmt, Doc Processing, Schedule Builder |
| **Forum** | Browse, Post, Comment | Browse, Post, Comment | Browse, Post, Comment, **Moderate** |
| **Events** | Browse, Register | Browse, Register | **Create, Edit, Delete**, View Registrations |
| **Messaging** | DMs, Program Chat | DMs, Program Chat | DMs, Program Chat, **Full Access** |
| **IArooms** | View available rooms | View available rooms | **Create, Book, Manage** rooms |
| **LMS** | View courses & content | Manage courses & content | Full LMS management |
| **Job Offers** | Browse offers | Browse offers | **Create & manage** offers |

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java JDK | 17+ | [Download](https://www.oracle.com/java/technologies/downloads/) |
| Maven | 3.8+ | [Download](https://maven.apache.org/download.cgi) |
| MySQL | 8.0+ | Or use Docker (recommended) |
| Docker + Compose | Latest | For containerized DB |
| JavaFX SDK | 17 | [Download OpenJFX](https://openjfx.io/) |
| Scene Builder | 17+ | Optional, for FXML editing |

### Installation

```bash
# 1. Clone the repository
git clone https://github.com/majd-01c/UniLearn-java.git
cd UniLearn-java

# 2. Switch to the communication branch
git checkout communication

# 3. Start MySQL via Docker
docker compose up -d
# Verify: docker ps → should show mysql:8.0 container running

# 4. Configure environment variables
cp .env.example .env   # if example exists, otherwise edit .env directly
# See "Environment Configuration" section below

# 5. Configure Hibernate
# Edit src/main/resources/hibernate.cfg.xml
# Set connection.url, connection.username, connection.password

# 6. Build the project
mvn clean package -DskipTests

# 7. Run the application
mvn javafx:run
```

> ⚠️ **Important:** The Symfony web project must be set up first to create and populate the shared MySQL database schema. See the [related project](#-related-project).

---

## ⚙️ Environment Configuration

The `.env` file at the project root holds all sensitive credentials. **Never commit this file.**

```env
# ── Database ─────────────────────────────────────
DB_HOST=localhost
DB_PORT=3306
DB_NAME=unilearn
DB_USER=root
DB_PASSWORD=your_password

# ── Google SMTP (Email) ───────────────────────────
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your_email@gmail.com
SMTP_PASS=your_app_password       # Use Gmail App Password, not your main password

# ── Face API ─────────────────────────────────────
FACE_API_KEY=your_face_api_key
FACE_API_URL=https://api.example.com/face

# ── SMS Gateway ──────────────────────────────────
SMS_API_KEY=your_sms_api_key
SMS_SENDER=UniLearn              # Sender name shown on SMS
```

---

## 🔑 Default Accounts

| Email | Password | Role | Access Level |
|-------|----------|------|--------------|
| `admin@unilearn.com` | `admin123` | `ADMIN` | Full system access |
| `teacher@unilearn.com` | `teacher123` | `TEACHER` | Teaching tools + forum |
| `student1@unilearn.com` | `student123` | `STUDENT` | Learning + community |

> ℹ️ These accounts are seeded by the Symfony web project's database fixtures. Run the web app setup first.

---
## 👥 Contributors

| Name | GitHub | LinkedIn | Module | Responsibilities |
|------|--------|----------|--------|------------------|
| **Alaa Salem** | [@alaasalem-blip](https://github.com/alaasalem-blip) | — | Gestion User | Authentication, user CRUD, Face ID, profile management |
| **Majd Labidi** | [@majd-01c](https://github.com/majd-01c) | [LABIDI Majdedine](https://www.linkedin.com/in/labidi-majdedine-9a49b632a) | Gestion Communication | Forum, Events, Messaging, SMS Verification, IArooms |
| **Khalil Fekih** | [@khalil-feki](https://github.com/khalil-feki) | [Feki Khalil](https://www.linkedin.com/in/feki-khalil/) | Gestion Evaluation | Assessments, grades, complaints, schedules, document requests |
| **Haroun Chaabane** | [@harounchaabane](https://github.com/harounchaabane) | [Haroun Chaabane](https://www.linkedin.com/in/haroun-chaabane-208716261/) | Gestion Program | LMS, programs, modules, courses, quizzes |
| **Dhia Amri** | [@dhia573](https://github.com/dhia573) | [Dhia Amri](https://www.linkedin.com/in/dhia-amri-167230243/) | Gestion Job Offre | Job offer listings, applications |
---

## 🎓 Academic Context

| Field | Value |
|-------|-------|
| **Institution** | Esprit School of Engineering – Tunisia |
| **Degree** | Engineering in Computer Science |
| **Program** | PIDEV (Projet Intégré de Développement) |
| **Year** | 3rd Year (3A) |
| **Academic Year** | 2025–2026 |
| **Branch** | `communication` – Gestion Communication module |

---

## 🔗 Related Project

This desktop app shares its database with the UniLearn Symfony web application:

👉 **[Esprit-PIDEV-3A57--2026-UniLearn](https://github.com/majd-01c/Esprit-PIDEV-3A57--2026-UniLearn)** — the web platform counterpart

> Both projects must use the **same MySQL database** for data to be consistent across desktop and web interfaces.

---

## 🙏 Acknowledgments

- **Esprit School of Engineering** — for the academic framework, guidance, and PIDEV program structure
- **OpenJFX / JavaFX Community** — for the open-source UI framework
- **Hibernate ORM Team** — for seamless Java-to-database entity mapping
- **Docker** — for simplifying local development database setup
- All open-source library authors whose tools made this project possible
