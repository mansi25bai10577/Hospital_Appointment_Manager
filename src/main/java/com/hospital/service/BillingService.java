package com.hospital.service;

import com.hospital.dao.IAppointmentDAO;
import com.hospital.dao.IDoctorDAO;
import com.hospital.dao.IInvoiceDAO;
import com.hospital.exception.*;
import com.hospital.model.*;
import com.hospital.util.AppLogger;

import java.util.List;
import java.util.Optional;

/**
 * BillingService converts a completed consultation into a financial record.
 *
 * <p>The billing workflow is deliberately simple by design:
 * when a receptionist marks an appointment COMPLETED, this service:
 * <ol>
 *   <li>Looks up the doctor's base consultation fee.</li>
 *   <li>Adds any additional charges entered by the receptionist (e.g. lab tests, medication).</li>
 *   <li>Persists an {@link Invoice} in the database and returns a printable receipt.</li>
 * </ol>
 * </p>
 *
 * <p><strong>Idempotency</strong>: If an invoice already exists for an appointment
 * (e.g. the receptionist clicks "Complete" twice by mistake), the service returns
 * the existing invoice rather than creating a duplicate charge.</p>
 */
public class BillingService {

    // ── DAO dependencies ────────────────────────────────────────────────────
    private final IInvoiceDAO     invoiceDAO;
    private final IAppointmentDAO appointmentDAO;
    private final IDoctorDAO      doctorDAO;

    /**
     * Creates a BillingService with the three DAOs it depends on.
     *
     * @param invoiceDAO     saves and retrieves invoice records
     * @param appointmentDAO reads appointment status and links to doctors/patients
     * @param doctorDAO      fetches the doctor's consultation fee for billing
     */
    public BillingService(IInvoiceDAO invoiceDAO, IAppointmentDAO appointmentDAO, IDoctorDAO doctorDAO) {
        this.invoiceDAO     = invoiceDAO;
        this.appointmentDAO = appointmentDAO;
        this.doctorDAO      = doctorDAO;
    }

    // =========================================================================
    // PRIMARY OPERATION — COMPLETE CONSULTATION & GENERATE INVOICE
    // =========================================================================

    /**
     * Marks a consultation as COMPLETED and generates a billing invoice for it.
     *
     * <p><strong>Fee calculation:</strong>
     * <pre>Total Amount = Doctor's Consultation Fee + Additional Charges</pre>
     * For example, if Dr. Smith charges $150 and a blood test costs $75,
     * the invoice total will be $225.</p>
     *
     * <p><strong>Idempotent:</strong> Calling this method twice for the same
     * appointment will return the same invoice rather than creating a second one.
     * This prevents accidental double-billing.</p>
     *
     * @param appointmentId     the ID of the completed appointment
     * @param additionalCharges extra costs beyond the consultation fee (must be ≥ 0)
     * @param notes             free-text description of the additional charges
     *                          (e.g. "ECG + Blood Panel")
     * @return the saved or existing {@link Invoice} for this appointment
     * @throws AppointmentNotFoundException if the appointment ID is not found
     * @throws ValidationException          if the appointment is CANCELLED or charges are negative
     */
    public Invoice completeConsultationAndGenerateInvoice(int appointmentId,
                                                          double additionalCharges,
                                                          String notes)
            throws AppointmentNotFoundException, ValidationException, DatabaseException {

        // ── Guard: Charges can't be negative ────────────────────────────────
        if (additionalCharges < 0) {
            throw new ValidationException("Additional charges cannot be negative. " +
                    "Enter 0 if there are no extra costs.");
        }

        // ── Load the appointment we're billing ──────────────────────────────
        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(
                        "Appointment ID " + appointmentId + " not found."));

        // We cannot bill a cancelled appointment — nothing was delivered to the patient.
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ValidationException(
                    "Cannot generate an invoice for a CANCELLED appointment (ID: " + appointmentId + ").");
        }

        // ── Look up the doctor's consultation fee ────────────────────────────
        Doctor doctor = doctorDAO.findById(appointment.getDoctorId())
                .orElseThrow(() -> new DatabaseException(
                        "Associated Doctor ID " + appointment.getDoctorId() + " not found in database."));

        // ── Idempotency check: if an invoice already exists, return it ───────
        // This prevents charging the patient twice if the action is triggered more than once.
        Optional<Invoice> existingInvoice = invoiceDAO.findByAppointmentId(appointmentId);
        if (existingInvoice.isPresent()) {
            AppLogger.info("Invoice already exists for appointment #" + appointmentId + " — returning existing record.");
            return existingInvoice.get();
        }

        // ── Mark the appointment as COMPLETED in the database ────────────────
        appointmentDAO.updateStatus(appointmentId, AppointmentStatus.COMPLETED);

        // ── Calculate the total and create the invoice ───────────────────────
        double consultationFee = doctor.getConsultationFee();
        // Total = base fee + any extras (lab, medication, procedures, etc.)
        Invoice invoice = new Invoice(appointmentId, consultationFee, additionalCharges, notes);

        // Attach display names so the receipt can be printed without extra DB calls.
        invoice.setPatientName(appointment.getPatientName());
        invoice.setDoctorName(doctor.getName());
        invoice.setAppointmentTime(appointment.getStartTime());

        Invoice saved = invoiceDAO.save(invoice);

        AppLogger.info(String.format(
                "Generated Invoice INV-%05d for Appointment APT-%05d | " +
                "Consultation: $%.2f | Extra: $%.2f | Total: $%.2f",
                saved.getInvoiceId(), appointmentId,
                consultationFee, additionalCharges, saved.getTotalAmount()));

        return saved;
    }

    // =========================================================================
    // RETRIEVAL OPERATIONS
    // =========================================================================

    /**
     * Retrieves the invoice for a given appointment.
     * Useful when a patient needs a copy of their receipt after the fact.
     *
     * @param appointmentId the appointment whose invoice is requested
     * @throws ValidationException if no invoice exists yet for this appointment
     */
    public Invoice getInvoiceByAppointmentId(int appointmentId) throws DatabaseException, ValidationException {
        return invoiceDAO.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ValidationException(
                        "No invoice found for Appointment ID " + appointmentId +
                        ". Has the consultation been marked COMPLETED?"));
    }

    /**
     * Retrieves an invoice by its own ID (the INV-##### number printed on receipts).
     *
     * @param invoiceId the invoice's primary key
     * @throws ValidationException if no invoice with this ID exists
     */
    public Invoice getInvoiceById(int invoiceId) throws DatabaseException, ValidationException {
        return invoiceDAO.findById(invoiceId)
                .orElseThrow(() -> new ValidationException(
                        "No invoice found with Invoice ID " + invoiceId));
    }

    /**
     * Returns all invoices associated with a specific patient.
     * Can be used to show a patient's complete billing history.
     *
     * @param patientId the patient whose invoices to retrieve
     * @return a list of invoices, most recent first
     */
    public List<Invoice> getInvoicesByPatient(int patientId) throws DatabaseException {
        return invoiceDAO.findByPatientId(patientId);
    }

    /**
     * Returns every invoice in the system, ordered by generation date descending.
     * Used by the diagnostics menu to count total invoices.
     *
     * @return all invoices stored in the database
     */
    public List<Invoice> getAllInvoices() throws DatabaseException {
        return invoiceDAO.findAll();
    }
}
