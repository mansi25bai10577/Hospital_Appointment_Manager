-- ============================================================
-- Hospital & Clinic Appointment Management System Schema
-- Database: SQLite
-- ============================================================

PRAGMA foreign_keys = ON;

-- 1. Patients Table
CREATE TABLE IF NOT EXISTS patients (
    patient_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    age INTEGER NOT NULL CHECK(age >= 0 AND age <= 120),
    gender TEXT NOT NULL,
    phone TEXT NOT NULL UNIQUE,
    email TEXT,
    medical_history TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. Doctors Table
CREATE TABLE IF NOT EXISTS doctors (
    doctor_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    specialization TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT,
    consultation_fee REAL NOT NULL CHECK(consultation_fee >= 0),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. Doctor Availability Slots Table
CREATE TABLE IF NOT EXISTS doctor_slots (
    slot_id INTEGER PRIMARY KEY AUTOINCREMENT,
    doctor_id INTEGER NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    is_booked INTEGER DEFAULT 0 CHECK(is_booked IN (0, 1)),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id) ON DELETE CASCADE
);

-- 4. Appointments Table
CREATE TABLE IF NOT EXISTS appointments (
    appointment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    patient_id INTEGER NOT NULL,
    doctor_id INTEGER NOT NULL,
    slot_id INTEGER,
    appointment_date TEXT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    priority TEXT NOT NULL DEFAULT 'NORMAL' CHECK(priority IN ('NORMAL', 'EMERGENCY')),
    reason TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE,
    FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id) ON DELETE CASCADE,
    FOREIGN KEY (slot_id) REFERENCES doctor_slots(slot_id) ON DELETE SET NULL
);

-- 5. Invoices Table
CREATE TABLE IF NOT EXISTS invoices (
    invoice_id INTEGER PRIMARY KEY AUTOINCREMENT,
    appointment_id INTEGER NOT NULL UNIQUE,
    consultation_fee REAL NOT NULL,
    additional_charges REAL DEFAULT 0.0,
    total_amount REAL NOT NULL,
    payment_status TEXT NOT NULL DEFAULT 'PAID',
    notes TEXT,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id) ON DELETE CASCADE
);

-- Performance Indexes for fast slot lookups & appointment queries
CREATE INDEX IF NOT EXISTS idx_doctor_slots ON doctor_slots(doctor_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_appointments_doctor_date ON appointments(doctor_id, appointment_date, status);
CREATE INDEX IF NOT EXISTS idx_appointments_patient ON appointments(patient_id);
