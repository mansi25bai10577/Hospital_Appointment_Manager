# Hospital & Clinic Appointment Management System

A complete, production-ready Java console application built with **Clean Layered Architecture**, plain **JDBC with SQLite**, **$O(\log N)$ time-slot conflict detection algorithms**, **emergency priority queueing**, **automated billing/invoice generation**, and **JUnit 5 unit tests**.

Designed for academic submission, emphasizing object-oriented design principles (SOLID), proper abstraction through Java interfaces, comprehensive custom exception handling, and standard logging.

---

## Table of Contents
- [Architecture & Design](#architecture--design)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Database Schema](#database-schema)
- [Slot Conflict Detection Algorithm](#slot-conflict-detection-algorithm)
- [Setup & Running Instructions](#setup--running-instructions)
- [Running Unit Tests](#running-unit-tests)
- [Sample Usage Walkthrough](#sample-usage-walkthrough)
- [Project Directory Structure](#project-directory-structure)

---

## Architecture & Design

The application enforces strict **Separation of Concerns (SoC)** using a 4-Tier Layered Architecture:

```
+-------------------------------------------------------------+
|                  UI Layer (Console UI / CLI)               |
|            com.hospital.ui.Main & ConsoleMenu               |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                     Service Layer                           |
|  SchedulingService, BillingService, PatientService, Doctor  |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                  Data Access Layer (DAO)                    |
|   IPatientDAO, IDoctorDAO, IAppointmentDAO, IInvoiceDAO    |
|               (SQLite JDBC Implementations)                 |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                       Database                              |
|                 SQLite Database (hospital.db)               |
+-------------------------------------------------------------+
```

### OOP Principles Applied
1. **Abstraction**: All database interaction is defined via Java DAO interfaces (`IPatientDAO`, `IDoctorDAO`, `IAppointmentDAO`, `IInvoiceDAO`), decoupling the business services from the underlying database driver.
2. **Encapsulation**: Domain models (`Patient`, `Doctor`, `AvailabilitySlot`, `Appointment`, `Invoice`) maintain private fields, explicit constructors, and getters/setters.
3. **Polymorphism & Single Responsibility**:
   - `SchedulingService`: Owns time slot interval math, TreeMap binary search lookups, and queue sorting.
   - `BillingService`: Owns consultation fee calculation, optional itemized charge accumulation, and receipt rendering.
4. **Custom Exception Hierarchy**: Extends a base `HospitalException` class:
   - `SlotConflictException` (carries suggested free slots list)
   - `PatientNotFoundException`
   - `DoctorNotFoundException`
   - `AppointmentNotFoundException`
   - `ValidationException`
   - `DatabaseException`

---

## Key Features

### 1. Patient Management
- **Registration**: Capture full patient demographic records (name, age, gender, phone, email, medical history/allergies).
- **Validation**: Strict input validation preventing negative age, blank names, or duplicate phone numbers.
- **Search & View**: Fast patient lookups by ID, phone, or name keywords.

### 2. Doctor & Scheduling Slot Management
- **Doctor Profiles**: Support for specializations (`CARDIOLOGY`, `DERMATOLOGY`, `PEDIATRICS`, `NEUROLOGY`, etc.) and consultation fees.
- **Slot Definition**: Define custom availability windows for doctors.
- **Queue Viewing**: Inspect doctor daily queues organized with priority sorting.

### 3. Appointment Booking & Conflict Prevention
- **$O(\log N)$ Overlap Detection**: Rejects any booking attempt that overlaps with an existing appointment or slot.
- **Alternative Slot Auto-Suggestions**: When a booking fails due to a conflict, the system automatically scans future hours and presents the top 3 available non-conflicting slots for that doctor.
- **Emergency Priority Queueing**: Support for `EMERGENCY` priority appointments, automatically placing critical cases ahead in doctor daily queues.
- **Rescheduling & Cancellation**: Update or cancel scheduled appointments while updating availability slot statuses.

### 4. Automated Billing & Invoicing
- **Auto-Generation**: Marking an appointment as `COMPLETED` automatically triggers `BillingService.completeConsultationAndGenerateInvoice()`.
- **Fee Calculation**: `Total Amount = Doctor Consultation Fee + Itemized Additional Charges` (e.g. lab tests, medication).
- **Printable Receipts**: Generates clean formatted ASCII text receipts with invoice IDs and timestamps.

### 5. Logging & Security
- **SQL Injection Prevention**: 100% prepared statements (`PreparedStatement`) across all DAO implementations.
- **Logging**: Action logging via `java.util.logging` to both `hospital.log` and console.

---

## Tech Stack
- **Language**: Java 17+
- **Database**: SQLite 3 via `org.xerial:sqlite-jdbc` (Single-file database `hospital.db`)
- **Build Tool**: Apache Maven
- **Testing**: JUnit 5 (`junit-jupiter`)

---

## Database Schema

Database tables are defined in `schema.sql` with foreign keys enabled (`PRAGMA foreign_keys = ON;`):

- **`patients`**: Stores patient demographics and contact details.
- **`doctors`**: Stores doctor names, specializations, and base consultation fees.
- **`doctor_slots`**: Tracks discrete availability time windows.
- **`appointments`**: Stores scheduled, completed, and cancelled appointments with status and priority level (`NORMAL` / `EMERGENCY`).
- **`invoices`**: Tracks billed consultation fees, extra charges, and payment statuses.

---

## Slot Conflict Detection Algorithm

### Mathematical Overlap Condition
Two time intervals $[S_1, E_1)$ and $[S_2, E_2)$ overlap **if and only if**:
$$\max(S_1, S_2) < \min(E_1, E_2) \iff S_1 < E_2 \quad \text{AND} \quad S_2 < E_1$$

### $O(\log N)$ TreeMap Search
Instead of performing an $O(N)$ linear scan over all appointments:
1. Active appointments for a doctor are indexed in a `NavigableMap<LocalDateTime, Appointment>` (`TreeMap`).
2. Given a proposed time range $[S_{\text{new}}, E_{\text{new}})$:
   - `floorEntry(S_new)` retrieves the closest appointment starting at or before $S_{\text{new}}$ in $O(\log N)$ time.
   - `subMap(S_new, E_new)` scans candidate entries starting within the window.
3. If an overlap is detected, `SlotConflictException` is thrown along with suggested alternative slots generated in 30-minute intervals.

---

## Setup & Running Instructions

### Prerequisites
- Java JDK 17+ (or IntelliJ bundled JBR JDK)
- Apache Maven

---

### Step 1: Set JAVA_HOME & Build Project

**In PowerShell:**
```powershell
$env:JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\jbr"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" compile
```

**In Command Prompt (CMD):**
```cmd
set JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\jbr
"C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" compile
```

---

### Step 2: Run the Console Application

**In PowerShell:**
```powershell
$env:JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\jbr"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" exec:java
```

**In Command Prompt (CMD):**
```cmd
set JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\jbr
"C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" exec:java
```

---

## Running Unit Tests

Run the complete JUnit 5 unit test suite (`SchedulingServiceTest`, `BillingServiceTest`):

**In PowerShell:**
```powershell
$env:JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\jbr"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" test
```

Expected Output:
```
[INFO] Running com.hospital.service.BillingServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.150 s
[INFO] Running com.hospital.service.SchedulingServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.210 s
[INFO] BUILD SUCCESS
```

---

## Sample Usage Walkthrough

### 1. Booking a Normal Appointment
```
[ Book New Appointment ]
Enter Patient ID: 1
Enter Doctor ID: 1
Booking for Patient: John Doe | Doctor: Dr. Alice Smith ($150.00)
Enter Appointment Start Time (YYYY-MM-DD HH:mm): 2026-10-01 09:00
Enter Duration in Minutes (e.g. 30): 30
Select Priority Level:
  1. NORMAL (Standard booking)
  2. EMERGENCY (Priority queueing)
Choice (1-2): 1
Reason for Visit: Annual Cardiac Screening

  [✓] SUCCESS: Appointment booked!
      Appointment[ID=1, Patient=John Doe (ID:1), Doctor=Dr. Alice Smith (ID:1), Time=2026-10-01 09:00, Status=SCHEDULED, Priority=NORMAL]
```

### 2. Conflicting Slot Rejection & Auto-Suggestions
```
[ Book New Appointment ]
Enter Patient ID: 2
Enter Doctor ID: 1
Enter Appointment Start Time (YYYY-MM-DD HH:mm): 2026-10-01 09:15
Enter Duration in Minutes: 30

  [✘] BOOKING REJECTED: Slot Conflict: Requested time (09:15 - 09:45) overlaps with an existing NORMAL appointment (ID: 1) for Dr. Alice Smith.

  💡 SUGGESTED ALTERNATIVE FREE SLOTS FOR DR. ALICE SMITH:
      -> 2026-10-01 09:30 to 10:00
      -> 2026-10-01 10:00 to 10:30
      -> 2026-10-01 10:30 to 11:00
```

### 3. Generated Invoice Sample
```
=========================================================
                 HOSPITAL CLINIC INVOICE                 
=========================================================
 Invoice No        : INV-00001
 Appointment ID    : APT-00001
 Generated Date    : 2026-10-01 09:30
 ---------------------------------------------------------
 Patient Name      : John Doe
 Attending Doctor  : Dr. Alice Smith
 Consultation Time : 2026-10-01 09:00
 ---------------------------------------------------------
 Consultation Fee  : $     150.00
 Additional Charges : $      75.50
 Description        : Includes ECG & Blood Test
 ---------------------------------------------------------
 TOTAL AMOUNT PAID  : $     225.50  [PAID]
=========================================================
```

---

## Project Directory Structure

```
HospitalAppointment/
├── pom.xml
├── schema.sql
├── README.md
├── hospital.db (Generated on first run)
├── hospital.log (Generated during operation)
└── src/
    ├── main/
    │   ├── java/com/hospital/
    │   │   ├── dao/
    │   │   │   ├── IAppointmentDAO.java
    │   │   │   ├── IDoctorDAO.java
    │   │   │   ├── IInvoiceDAO.java
    │   │   │   ├── IPatientDAO.java
    │   │   │   ├── AppointmentDAOImpl.java
    │   │   │   ├── DoctorDAOImpl.java
    │   │   │   ├── InvoiceDAOImpl.java
    │   │   │   └── PatientDAOImpl.java
    │   │   ├── exception/
    │   │   │   ├── AppointmentNotFoundException.java
    │   │   │   ├── DatabaseException.java
    │   │   │   ├── DoctorNotFoundException.java
    │   │   │   ├── HospitalException.java
    │   │   │   ├── PatientNotFoundException.java
    │   │   │   ├── SlotConflictException.java
    │   │   │   └── ValidationException.java
    │   │   ├── model/
    │   │   │   ├── Appointment.java
    │   │   │   ├── AppointmentStatus.java
    │   │   │   ├── AvailabilitySlot.java
    │   │   │   ├── Doctor.java
    │   │   │   ├── Invoice.java
    │   │   │   ├── Patient.java
    │   │   │   ├── PriorityLevel.java
    │   │   │   └── Specialization.java
    │   │   ├── service/
    │   │   │   ├── BillingService.java
    │   │   │   ├── DoctorService.java
    │   │   │   ├── PatientService.java
    │   │   │   └── SchedulingService.java
    │   │   ├── ui/
    │   │   │   ├── ConsoleMenu.java
    │   │   │   ├── InputHandler.java
    │   │   │   └── Main.java
    │   │   └── util/
    │   │       ├── AppLogger.java
    │   │       ├── DatabaseConfig.java
    │   │       ├── DatabaseManager.java
    │   │       └── DateTimeUtils.java
    │   └── resources/
    │       └── config.properties
    └── test/
        └── java/com/hospital/service/
            ├── BillingServiceTest.java
            └── SchedulingServiceTest.java
```
## Author
Mansi Kashyap
25BAI10577
