package com.hospital.ui;

import com.hospital.exception.HospitalException;
import com.hospital.exception.SlotConflictException;
import com.hospital.model.*;
import com.hospital.service.*;
import com.hospital.util.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ConsoleMenu is the heart of the user experience — it's the "face" of the application.
 *
 * <p>This class acts as the top-level controller that wires together all four service
 * modules (Patient, Doctor, Scheduling, Billing) and presents them through an interactive
 * text-based menu. Every user action flows through here.</p>
 *
 * <p>Think of this class as the receptionist's desktop screen at the front desk:
 * it never stores data itself — it simply asks the right service to do the work,
 * then presents the result in a friendly, readable format.</p>
 *
 * <p>Design note: Errors from the service/DAO layer bubble up as typed exceptions.
 * The menu catches them here so the user sees a helpful message instead of
 * a raw stack trace.</p>
 */
public class ConsoleMenu {

    // ── Service dependencies ────────────────────────────────────────────────────
    // Each service handles one area of the clinic: patients, doctors, appointments,
    // and billing. They are injected at construction time (Dependency Injection),
    // making it easy to swap in mock implementations for testing.
    private final PatientService     patientService;
    private final DoctorService      doctorService;
    private final SchedulingService  schedulingService;
    private final BillingService     billingService;

    /** Handles all keyboard input from the user in a safe, validated way. */
    private final InputHandler input;

    /**
     * Creates a new ConsoleMenu wired up to all required services.
     *
     * @param patientService     manages patient records
     * @param doctorService      manages doctor profiles
     * @param schedulingService  handles slot conflict checking and appointment lifecycle
     * @param billingService     generates invoices and tracks payments
     */
    public ConsoleMenu(PatientService patientService, DoctorService doctorService,
                       SchedulingService schedulingService, BillingService billingService) {
        this.patientService    = patientService;
        this.doctorService     = doctorService;
        this.schedulingService = schedulingService;
        this.billingService    = billingService;
        this.input             = new InputHandler();
    }

    // =========================================================================
    // APPLICATION ENTRY — THE MAIN LOOP
    // =========================================================================

    /**
     * Launches the interactive console session.
     *
     * <p>This method runs a simple loop: show banner → show menu → read choice → act.
     * It keeps running until the user types 0 (Exit).</p>
     */
    public void start() {
        printBanner();

        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = input.readIntRange("Select an option [0-4]: ", 0, 4);

            switch (choice) {
                case 1 -> patientManagementMenu();       // Go to Patient module
                case 2 -> doctorManagementMenu();         // Go to Doctor/Schedule module
                case 3 -> appointmentAndBillingMenu();    // Go to Appointment & Billing module
                case 4 -> systemDiagnosticsMenu();        // Show database stats
                case 0 -> {
                    System.out.println("\n  Thank you for using Hospital Appointment System. Goodbye!");
                    running = false;
                }
            }
        }
    }

    /** Prints the ASCII art clinic header shown once at startup. */
    private void printBanner() {
        System.out.println("""
            ====================================================================
               _  _               _ _         _      _ _ _ _
              | || |___ ____ _ __(_| |_ __ _| |    / / / / /
              | __ / _ (_-< '_ \\ | |  _/ _` | |   / / / / / 
              |_||_\\___/__/ .__/_|_|\\__\\__,_|_|  /_/_/_/_/  
                          |_| CLINIC APPOINTMENT & BILLING SYSTEM
            ====================================================================
            """);
    }

    /** Prints the four-option main navigation menu. */
    private void printMainMenu() {
        System.out.println("""
            
            ┌───────────────────── MAIN MENU ─────────────────────┐
            │  1. Patient Management                              │
            │  2. Doctor & Scheduling Slot Management             │
            │  3. Appointment Booking, Rescheduling & Billing     │
            │  4. System Diagnostics & Database Status            │
            │  0. Exit System                                     │
            └─────────────────────────────────────────────────────┘
            """);
    }

    // =========================================================================
    // MODULE 1 — PATIENT MANAGEMENT
    // =========================================================================
    //
    // Everything a receptionist needs to manage patient records:
    // registering a new arrival, looking up someone by name, and updating
    // contact or medical details.
    // =========================================================================

    /** Displays and drives the Patient Management sub-menu loop. */
    private void patientManagementMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("""
                
                --- PATIENT MANAGEMENT ---
                1. Register New Patient
                2. Search Patients by Name
                3. View All Patients
                4. Update Patient Record
                0. Back to Main Menu
                """);

            int choice = input.readIntRange("Choice: ", 0, 4);
            try {
                switch (choice) {
                    case 1 -> registerPatient();
                    case 2 -> searchPatients();
                    case 3 -> viewAllPatients();
                    case 4 -> updatePatient();
                    case 0 -> back = true;
                }
            } catch (HospitalException e) {
                // Surface a human-readable error message; never crash the menu loop.
                System.out.println("  [!] Error: " + e.getMessage());
            }
        }
    }

    /**
     * Walks the user through entering all the information needed to create
     * a new patient record. Phone uniqueness is enforced in the service layer —
     * if someone tries to register twice with the same number, they'll get
     * a clear error rather than a duplicate row.
     */
    private void registerPatient() throws HospitalException {
        System.out.println("\n[ Register New Patient ]");

        String name    = input.readNonEmptyString("Enter Patient Full Name: ");
        int    age     = input.readIntRange("Enter Age: ", 0, 120);
        String gender  = input.readNonEmptyString("Enter Gender (Male/Female/Other): ");
        String phone   = input.readNonEmptyString("Enter Phone Number: ");
        String email   = input.readString("Enter Email (Optional, press Enter to skip): ");
        String history = input.readString("Enter Medical History / Allergies (Optional, press Enter to skip): ");

        Patient saved = patientService.registerPatient(name, age, gender, phone, email, history);

        System.out.println("\n  [✓] SUCCESS: Patient registered successfully!");
        System.out.println("      " + saved);
    }

    /**
     * Lets the user search by any part of a patient's name.
     * Entering nothing will return all patients (same as "View All").
     */
    private void searchPatients() throws HospitalException {
        String keyword = input.readString("Enter name keyword to search (or press Enter to list all): ");
        List<Patient> results = patientService.searchPatientsByName(keyword);
        printPatientTable(results);
    }

    /** Displays every registered patient in a formatted table. */
    private void viewAllPatients() throws HospitalException {
        List<Patient> allPatients = patientService.getAllPatients();
        printPatientTable(allPatients);
    }

    /**
     * Allows updating an existing patient's name, age, phone, or medical history.
     * The user can leave any field blank to keep the current value —
     * only the fields they fill in will be changed.
     */
    private void updatePatient() throws HospitalException {
        int     patientId = input.readInt("Enter Patient ID to update: ");
        Patient patient   = patientService.getPatientById(patientId);

        System.out.println("  Current details: " + patient);
        System.out.println("  (Press Enter on any field to leave it unchanged)");

        // Only overwrite each field if the user actually typed something.
        String newName = input.readString("New Name [" + patient.getName() + "]: ");
        if (!newName.isEmpty()) patient.setName(newName);

        String newAgeStr = input.readString("New Age [" + patient.getAge() + "]: ");
        if (!newAgeStr.isEmpty()) patient.setAge(Integer.parseInt(newAgeStr));

        String newPhone = input.readString("New Phone [" + patient.getPhone() + "]: ");
        if (!newPhone.isEmpty()) patient.setPhone(newPhone);

        String newHistory = input.readString("New Medical History (leave blank to keep): ");
        if (!newHistory.isEmpty()) patient.setMedicalHistory(newHistory);

        boolean updated = patientService.updatePatient(patient);
        if (updated) {
            System.out.println("  [✓] Patient record updated successfully.");
        }
    }

    /**
     * Renders a list of patients in a neat fixed-width table.
     * Long names or emails are truncated with "..." so the columns stay aligned.
     */
    private void printPatientTable(List<Patient> patients) {
        if (patients.isEmpty()) {
            System.out.println("  No patient records found.");
            return;
        }

        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("%-6s | %-20s | %-4s | %-8s | %-15s | %-18s\n",
                "ID", "Name", "Age", "Gender", "Phone", "Email");
        System.out.println("--------------------------------------------------------------------------------");

        for (Patient p : patients) {
            System.out.printf("%-6d | %-20s | %-4d | %-8s | %-15s | %-18s\n",
                    p.getPatientId(),
                    truncate(p.getName(), 20),
                    p.getAge(),
                    p.getGender(),
                    p.getPhone(),
                    truncate(p.getEmail(), 18));
        }

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  Total: %d patient(s) found.\n", patients.size());
    }

    // =========================================================================
    // MODULE 2 — DOCTOR & SCHEDULING SLOT MANAGEMENT
    // =========================================================================
    //
    // Used by an administrator to set up the clinic roster: add doctors,
    // assign their working hours as discrete time slots, and review the
    // priority-sorted daily queue for any doctor.
    // =========================================================================

    /** Displays and drives the Doctor Management sub-menu loop. */
    private void doctorManagementMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("""
                
                --- DOCTOR & SCHEDULING MANAGEMENT ---
                1. Register Doctor Profile
                2. View All Doctors & Specializations
                3. Define Doctor Availability Time Slot
                4. View Doctor Schedule / Emergency Queue
                0. Back to Main Menu
                """);

            int choice = input.readIntRange("Choice: ", 0, 4);
            try {
                switch (choice) {
                    case 1 -> registerDoctor();
                    case 2 -> viewAllDoctors();
                    case 3 -> defineDoctorSlot();
                    case 4 -> viewDoctorQueue();
                    case 0 -> back = true;
                }
            } catch (HospitalException e) {
                System.out.println("  [!] Error: " + e.getMessage());
            }
        }
    }

    /** Collects the details needed to create a new doctor profile in the system. */
    private void registerDoctor() throws HospitalException {
        System.out.println("\n[ Register New Doctor ]");

        String         name = input.readNonEmptyString("Doctor Full Name: ");
        Specialization spec = input.readSpecialization();   // shows a numbered list for easy selection
        String        phone = input.readNonEmptyString("Contact Phone: ");
        String        email = input.readString("Email (Optional, press Enter to skip): ");
        double          fee = input.readDouble("Consultation Fee ($): ");

        Doctor saved = doctorService.registerDoctor(name, spec, phone, email, fee);

        System.out.println("\n  [✓] SUCCESS: Doctor profile created!");
        System.out.println("      " + saved);
    }

    /** Prints all registered doctors in a formatted table sorted by name. */
    private void viewAllDoctors() throws HospitalException {
        List<Doctor> doctors = doctorService.getAllDoctors();

        if (doctors.isEmpty()) {
            System.out.println("  No doctor profiles found. Use option 1 to add a doctor first.");
            return;
        }

        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("%-6s | %-20s | %-18s | %-12s | %-8s\n",
                "ID", "Name", "Specialization", "Phone", "Fee");
        System.out.println("--------------------------------------------------------------------------------");

        for (Doctor d : doctors) {
            System.out.printf("%-6d | %-20s | %-18s | %-12s | $%-7.2f\n",
                    d.getDoctorId(),
                    truncate(d.getName(), 20),
                    d.getSpecialization().getDisplayName(),
                    d.getPhone(),
                    d.getConsultationFee());
        }

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  Total: %d doctor(s) on record.\n", doctors.size());
    }

    /**
     * Lets an admin define a specific time window during which a doctor is
     * available to see patients. The scheduling engine checks these windows
     * when booking to prevent double-bookings.
     *
     * <p>For example: "Dr. Smith is available on 2026-10-01 from 09:00 to 09:30"</p>
     */
    private void defineDoctorSlot() throws HospitalException {
        int    doctorId = input.readInt("Enter Doctor ID: ");
        Doctor doctor   = doctorService.getDoctorById(doctorId);

        System.out.println("  Selected Doctor: " + doctor.getName()
                + " [" + doctor.getSpecialization().getDisplayName() + "]");

        LocalDateTime slotStart = input.readDateTime("Enter Slot Start Time");
        LocalDateTime slotEnd   = input.readDateTime("Enter Slot End Time");

        // The service checks for overlaps with already-defined slots before saving.
        AvailabilitySlot created = schedulingService.createAvailabilitySlot(doctorId, slotStart, slotEnd);
        System.out.println("  [✓] Availability slot created: " + created);
    }

    /**
     * Shows a doctor's appointment schedule for a chosen date, sorted so that
     * EMERGENCY cases appear at the top regardless of their booked time.
     * This gives staff an instant overview of who needs to be seen urgently.
     */
    private void viewDoctorQueue() throws HospitalException {
        int       doctorId = input.readInt("Enter Doctor ID: ");
        Doctor    doctor   = doctorService.getDoctorById(doctorId);
        LocalDate date     = input.readDate("Enter Date to View Queue");

        List<Appointment> queue = schedulingService.getDoctorDailyQueue(doctorId, date);

        System.out.printf("\n--- DAILY SCHEDULE FOR DR. %s ON %s ---\n",
                doctor.getName().toUpperCase(), DateTimeUtils.formatDate(date));

        if (queue.isEmpty()) {
            System.out.println("  No appointments scheduled for this date.");
            return;
        }

        // Column headers
        System.out.println("---------------------------------------------------------------------------------------");
        System.out.printf("%-8s | %-12s | %-15s | %-20s | %-10s | %-10s\n",
                "Appt ID", "Time", "Priority", "Patient Name", "Status", "Reason");
        System.out.println("---------------------------------------------------------------------------------------");

        for (Appointment appt : queue) {
            // Mark emergency appointments with a visual warning tag
            String priorityTag = appt.getPriority() == PriorityLevel.EMERGENCY
                    ? "🚨 EMERGENCY"
                    : "NORMAL";

            System.out.printf("%-8d | %-12s | %-15s | %-20s | %-10s | %-10s\n",
                    appt.getAppointmentId(),
                    DateTimeUtils.formatTime(appt.getStartTime().toLocalTime()),
                    priorityTag,
                    truncate(appt.getPatientName(), 20),
                    appt.getStatus(),
                    truncate(appt.getReason(), 15));
        }

        System.out.println("---------------------------------------------------------------------------------------");
        System.out.printf("  %d appointment(s) for the day | EMERGENCY cases listed first.\n", queue.size());
    }

    // =========================================================================
    // MODULE 3 — APPOINTMENT BOOKING & BILLING
    // =========================================================================
    //
    // The most-used part of the system. Receptionists book new appointments,
    // handle changes, and complete consultations. The billing engine auto-triggers
    // the moment a consultation is marked done.
    // =========================================================================

    /** Displays and drives the Appointment & Billing sub-menu loop. */
    private void appointmentAndBillingMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("""
                
                --- APPOINTMENT BOOKING & BILLING ---
                1. Book New Appointment (With Conflict Detection & Priority)
                2. Reschedule Appointment
                3. Cancel Appointment
                4. Complete Consultation & Generate Invoice
                5. View / Print Invoice Details
                0. Back to Main Menu
                """);

            int choice = input.readIntRange("Choice: ", 0, 5);
            try {
                switch (choice) {
                    case 1 -> bookAppointment();
                    case 2 -> rescheduleAppointment();
                    case 3 -> cancelAppointment();
                    case 4 -> completeConsultation();
                    case 5 -> printInvoice();
                    case 0 -> back = true;
                }
            } catch (HospitalException e) {
                System.out.println("\n  [!] Error: " + e.getMessage());
            }
        }
    }

    /**
     * Guides the receptionist through booking an appointment.
     *
     * <p>This method is the most important user-facing workflow in the system.
     * After collecting the patient, doctor, time window, priority, and reason,
     * it calls the scheduling engine which:</p>
     * <ol>
     *   <li>Verifies the patient and doctor exist.</li>
     *   <li>Runs the O(log N) overlap-detection algorithm against existing appointments.</li>
     *   <li>If a conflict is found, throws SlotConflictException with 3 alternative free slots.</li>
     *   <li>Otherwise persists the booking and marks the matched availability slot as booked.</li>
     * </ol>
     *
     * <p>EMERGENCY appointments are flagged visually and sorted to the top of the
     * doctor's daily queue automatically.</p>
     */
    private void bookAppointment() throws HospitalException {
        System.out.println("\n[ Book New Appointment ]");

        // Step 1: Confirm patient exists before asking for more information.
        int     patientId = input.readInt("Enter Patient ID: ");
        Patient patient   = patientService.getPatientById(patientId);

        // Step 2: Confirm doctor exists and show their consultation fee upfront.
        int    doctorId = input.readInt("Enter Doctor ID: ");
        Doctor doctor   = doctorService.getDoctorById(doctorId);

        System.out.printf("  Booking for Patient: %s  |  Doctor: %s  |  Base Fee: $%.2f\n",
                patient.getName(), doctor.getName(), doctor.getConsultationFee());

        // Step 3: Collect time window — end time is derived from duration so the
        //         user doesn't have to do the arithmetic themselves.
        LocalDateTime startTime = input.readDateTime("Enter Appointment Start Time");
        int durationMinutes     = input.readIntRange("Enter Duration in Minutes (e.g. 30): ", 15, 240);
        LocalDateTime endTime   = startTime.plusMinutes(durationMinutes);

        // Step 4: Ask about urgency. EMERGENCY slots jump to the front of the queue.
        PriorityLevel priority = input.readPriorityLevel();
        String        reason   = input.readString("Reason for Visit: ");

        // Step 5: Try to book. If the slot is taken, show alternatives instead of failing silently.
        try {
            Appointment booked = schedulingService.bookAppointment(
                    patientId, doctorId, startTime, endTime, priority, reason);

            System.out.println("\n  [✓] SUCCESS: Appointment booked!");
            System.out.println("      " + booked);

        } catch (SlotConflictException conflict) {
            // The requested time overlaps with an existing appointment.
            // Rather than just saying "no", we show the next available free windows.
            System.out.println("\n  [✘] BOOKING REJECTED: " + conflict.getMessage());

            if (!conflict.getSuggestedAlternatives().isEmpty()) {
                System.out.println("\n  💡 SUGGESTED ALTERNATIVE FREE SLOTS FOR DR. "
                        + doctor.getName().toUpperCase() + ":");
                for (AvailabilitySlot suggestion : conflict.getSuggestedAlternatives()) {
                    System.out.printf("      ➜  %s  to  %s\n",
                            DateTimeUtils.formatDateTime(suggestion.getStartTime()),
                            DateTimeUtils.formatTime(suggestion.getEndTime().toLocalTime()));
                }
            }
        }
    }

    /**
     * Reschedules an existing SCHEDULED appointment to a new date and time.
     * The conflict-check algorithm runs again against the new window, so
     * double-bookings are impossible even during rescheduling.
     */
    private void rescheduleAppointment() throws HospitalException {
        int           apptId    = input.readInt("Enter Appointment ID to Reschedule: ");
        LocalDateTime newStart  = input.readDateTime("Enter New Start Time");
        int           duration  = input.readIntRange("Duration in Minutes: ", 15, 240);
        LocalDateTime newEnd    = newStart.plusMinutes(duration);

        try {
            boolean success = schedulingService.rescheduleAppointment(apptId, newStart, newEnd);
            if (success) {
                System.out.println("  [✓] Appointment rescheduled to "
                        + DateTimeUtils.formatDateTime(newStart) + " successfully.");
            }
        } catch (SlotConflictException conflict) {
            // New time slot is already taken — inform the user clearly.
            System.out.println("  [✘] RESCHEDULE CONFLICT: " + conflict.getMessage());
        }
    }

    /**
     * Cancels an appointment and frees up the associated time slot so
     * another patient can be booked into it.
     */
    private void cancelAppointment() throws HospitalException {
        int apptId = input.readInt("Enter Appointment ID to Cancel: ");

        boolean cancelled = schedulingService.cancelAppointment(apptId);
        if (cancelled) {
            System.out.println("  [✓] Appointment ID " + apptId
                    + " has been cancelled. The time slot is now free.");
        }
    }

    /**
     * Marks a consultation as COMPLETED and immediately generates a billing invoice.
     *
     * <p>The invoice total is calculated as:
     * <pre>Total = Doctor's Consultation Fee + Any Additional Charges (lab, meds, etc.)</pre>
     * The formatted receipt is printed to the console and persisted to the database.</p>
     */
    private void completeConsultation() throws HospitalException {
        int    apptId        = input.readInt("Enter Appointment ID to complete: ");
        double extraCharges  = input.readDouble("Additional Charges (lab tests, medication, etc.) [$]: ");
        String notes         = input.readString("Invoice Notes / Charge Breakdown (Optional): ");

        Invoice invoice = billingService.completeConsultationAndGenerateInvoice(apptId, extraCharges, notes);

        System.out.println("  [✓] Consultation marked COMPLETED. Invoice generated and saved.");
        System.out.println(invoice.toFormattedReceipt());
    }

    /**
     * Retrieves and prints a previously generated invoice for any appointment.
     * Useful when a patient asks for a copy of their receipt.
     */
    private void printInvoice() throws HospitalException {
        int apptId = input.readInt("Enter Appointment ID to view Invoice: ");

        Invoice invoice = billingService.getInvoiceByAppointmentId(apptId);
        System.out.println(invoice.toFormattedReceipt());
    }

    // =========================================================================
    // MODULE 4 — SYSTEM DIAGNOSTICS
    // =========================================================================

    /**
     * Shows a quick health snapshot of the system: how many patients and doctors
     * are registered, how many invoices have been generated, and whether all
     * services can reach the database.
     */
    private void systemDiagnosticsMenu() {
        System.out.println("\n─────────────────────────────────────────");
        System.out.println("         SYSTEM DIAGNOSTICS");
        System.out.println("─────────────────────────────────────────");

        try {
            int patientCount = patientService.getAllPatients().size();
            int doctorCount  = doctorService.getAllDoctors().size();
            int invoiceCount = billingService.getAllInvoices().size();

            System.out.println("  Database Engine     : SQLite 3 (hospital.db)");
            System.out.println("  Registered Patients : " + patientCount);
            System.out.println("  Registered Doctors  : " + doctorCount);
            System.out.println("  Invoices Generated  : " + invoiceCount);
            System.out.println("  System Status       : ✓ OPERATIONAL — ALL SERVICES OK");

        } catch (Exception e) {
            // If any database query fails, report the problem clearly.
            System.out.println("  [!] Diagnostics Error: " + e.getMessage());
            System.out.println("  System Status       : ✘ DEGRADED — CHECK DATABASE");
        }

        System.out.println("─────────────────────────────────────────");
    }

    // =========================================================================
    // HELPER UTILITIES
    // =========================================================================

    /**
     * Safely shortens a string to {@code maxLen} characters.
     * If the string exceeds the limit, it ends with "..." so the reader knows
     * there is more content that isn't shown (common in terminal table columns).
     *
     * @param str    the string to shorten (may be null)
     * @param maxLen the maximum allowed character count
     * @return the original string if short enough, otherwise a truncated version ending in "..."
     */
    private String truncate(String str, int maxLen) {
        if (str == null || str.isEmpty()) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen - 3) + "...";
    }
}
