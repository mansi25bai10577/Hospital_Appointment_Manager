# Project Statement & Scope Document

## Hospital & Clinic Appointment Management System

---

## 1. Problem Statement

Small-to-medium healthcare clinics and hospital outpatient departments frequently face operational bottlenecks due to inefficient appointment scheduling and billing processes. Common challenges include:

- **Double-Booking & Schedule Conflicts**: Overlapping appointments due to manual schedule tracking or linear search methods, leading to extended patient wait times and doctor burnout.
- **Inflexible Emergency Handling**: Inability to prioritize urgent or critical emergency patients in a doctor's daily routine without disrupting scheduled workflows.
- **Manual Billing Errors**: Time-consuming manual fee calculations (consultation fees plus itemized lab/medication charges) prone to human errors and unrecorded transactions.
- **Lack of Clean Architectural Modularization**: Legacy administrative software often tightly couples UI logic with data access code, making it difficult to maintain, test, or upgrade to modern graphical interfaces (JavaFX/Swing) or REST services.

To solve these challenges, the **Hospital & Clinic Appointment Management System** provides a lightweight, modular, and high-performance Java-based console solution. It automates slot conflict detection in $O(\log N)$ time, provides smart alternative slot suggestions, prioritizes emergency appointments, and auto-generates itemized invoices upon consultation completion.

---

## 2. Scope of the Project

### In-Scope Functionality
- **Patient Record Management**:
  - Full CRUD operations for patient profiles (demographics, contact info, medical history).
  - Data validation preventing negative ages, invalid phone numbers, or duplicate registrations.
- **Doctor & Availability Slot Management**:
  - Profile management for doctors across multiple specializations (Cardiology, Pediatrics, Dermatology, Neurology, General Practice, etc.).
  - Custom availability slot definitions and schedule viewings.
- **Intelligent Appointment Scheduling**:
  - $O(\log N)$ slot conflict detection algorithm utilizing `TreeMap` interval overlap logic.
  - Automatic generation of the top 3 alternative free time slots when a requested time window is occupied.
  - Support for `NORMAL` and `EMERGENCY` priority levels, dynamically sorting doctor daily queues to place emergency cases first.
  - Complete lifecycle tracking: `SCHEDULED`, `COMPLETED`, and `CANCELLED`.
- **Automated Billing & Invoice Generation**:
  - Automated invoice creation upon marking an appointment `COMPLETED`.
  - Fee calculation: $\text{Total Amount} = \text{Doctor Consultation Fee} + \text{Itemized Additional Charges}$.
  - Formatting and printing of standardized text receipts.
- **Persistence & Security**:
  - Embedded SQLite database (`hospital.db`) with foreign key enforcement and relational indexes.
  - 100% prepared statements (`PreparedStatement`) to prevent SQL injection vulnerabilities.
  - Action logging via `java.util.logging` to `hospital.log` and console.

### Out-of-Scope (Future Enhancements)
- **Direct Payment Gateway Integration**: Payments are marked as settled (`PAID`) automatically upon invoice generation without live credit card API processing.
- **Graphical User Interface (GUI)**: The current delivery is a robust Console CLI; however, the clean layered architecture (Model-DAO-Service-UI) is designed to easily bind to a JavaFX or Swing frontend without modifying backend logic.
- **Multi-Hospital Cloud Synchronization**: The system operates as a single-facility or clinic desktop/server instance.

---

## 3. Target Users

1. **Clinic Receptionists & Administrators**:
   - Primary operational users responsible for registering new patients, booking/rescheduling appointments, managing emergency walk-ins, and issuing consultation billing receipts.
2. **Attending Doctors & Medical Staff**:
   - Users who consult daily appointment queues prioritized by emergency status and time slots, and mark consultations as completed.
3. **Academic Evaluators & Software Engineers**:
   - Reviewers assessing clean architecture, adherence to SOLID design principles, Java 17 features, custom exception hierarchies, unit test coverage, and algorithmic time complexity.

---

## 4. High-Level Features

| Feature Module | Key Capabilities |
| :--- | :--- |
| **Patient Management** | Register patients, search by name/ID, view medical history, update patient contact records. |
| **Doctor Management** | Create doctor profiles with specializations and consultation fees; define availability slots. |
| **Conflict-Free Scheduling Engine** | $O(\log N)$ `TreeMap` overlap check algorithm; prevents double-booking; suggests next available free slots. |
| **Emergency Queueing System** | Priority-based queueing sorting (`EMERGENCY` > `NORMAL`), ensuring urgent care cases are prioritized. |
| **Automated Billing Engine** | Auto-generates itemized invoices upon consultation completion; calculates fees and formats printable receipts. |
| **System Diagnostics & Audit Logging** | Database diagnostics monitor; structured action logging via `java.util.logging`. |
| **Automated Test Suite** | Full JUnit 5 unit test coverage for scheduling algorithm edge cases and billing calculations. |
