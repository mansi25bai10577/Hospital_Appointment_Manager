package com.hospital.ui;

import com.hospital.dao.*;
import com.hospital.model.*;
import com.hospital.service.*;
import com.hospital.util.AppLogger;
import com.hospital.util.DatabaseManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Application Entry Point.
 * Initializes SQLite database schema, instantiates DAOs and Services,
 * seeds initial demo data if empty, and launches the interactive Console UI.
 */
public class Main {
    public static void main(String[] args) {
        try {
            AppLogger.info("Starting Hospital & Clinic Appointment Management System...");

            // 1. Initialize SQLite Database Schema
            DatabaseManager.initializeDatabase();

            // 2. Instantiate Data Access Objects (DAOs)
            IPatientDAO patientDAO = new PatientDAOImpl();
            IDoctorDAO doctorDAO = new DoctorDAOImpl();
            IAppointmentDAO appointmentDAO = new AppointmentDAOImpl();
            IInvoiceDAO invoiceDAO = new InvoiceDAOImpl();

            // 3. Instantiate Service Layer Components
            PatientService patientService = new PatientService(patientDAO);
            DoctorService doctorService = new DoctorService(doctorDAO);
            SchedulingService schedulingService = new SchedulingService(doctorDAO, patientDAO, appointmentDAO);
            BillingService billingService = new BillingService(invoiceDAO, appointmentDAO, doctorDAO);

            // 4. Seed sample data if database is fresh/empty
            seedInitialData(patientService, doctorService, schedulingService);

            // 5. Launch Interactive Console UI
            ConsoleMenu menu = new ConsoleMenu(patientService, doctorService, schedulingService, billingService);
            menu.start();

        } catch (Exception e) {
            AppLogger.severe("Fatal application initialization error", e);
            System.err.println("\n[!] FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void seedInitialData(PatientService patientService, DoctorService doctorService, SchedulingService schedulingService) {
        try {
            List<Doctor> doctors = doctorService.getAllDoctors();
            if (doctors.isEmpty()) {
                AppLogger.info("Seeding initial demo doctors and patients...");

                Doctor d1 = doctorService.registerDoctor("Dr. Alice Smith", Specialization.CARDIOLOGY, "+1-555-0192", "alice.smith@hospital.com", 150.00);
                Doctor d2 = doctorService.registerDoctor("Dr. Robert Johnson", Specialization.PEDIATRICS, "+1-555-0144", "robert.j@hospital.com", 120.00);
                Doctor d3 = doctorService.registerDoctor("Dr. Elena Rostova", Specialization.DERMATOLOGY, "+1-555-0188", "elena.r@hospital.com", 140.00);

                Patient p1 = patientService.registerPatient("John Doe", 34, "Male", "9876543210", "john.doe@example.com", "Hypertension history");
                Patient p2 = patientService.registerPatient("Emma Watson", 29, "Female", "9876543211", "emma.w@example.com", "Asthma");

                // Seed availability slots for tomorrow
                LocalDateTime tomorrow09 = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0));
                LocalDateTime tomorrow10 = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(10, 0));
                LocalDateTime tomorrow11 = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(11, 0));

                schedulingService.createAvailabilitySlot(d1.getDoctorId(), tomorrow09, tomorrow09.plusMinutes(30));
                schedulingService.createAvailabilitySlot(d1.getDoctorId(), tomorrow09.plusMinutes(30), tomorrow10);
                schedulingService.createAvailabilitySlot(d2.getDoctorId(), tomorrow10, tomorrow10.plusMinutes(30));

                // Seed one appointment
                schedulingService.bookAppointment(p1.getPatientId(), d1.getDoctorId(), tomorrow09, tomorrow09.plusMinutes(30), PriorityLevel.NORMAL, "Routine Cardiac Checkup");

                AppLogger.info("Initial seed data populated successfully.");
            }
        } catch (Exception e) {
            AppLogger.warning("Sample data seeding skipped or partial: " + e.getMessage());
        }
    }
}
