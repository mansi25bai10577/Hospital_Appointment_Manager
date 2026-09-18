package com.hospital.model;

import com.hospital.util.DateTimeUtils;
import java.time.LocalDateTime;

/**
 * Represents a billing invoice generated at the end of a patient consultation.
 *
 * <p>An invoice is created exactly once per completed appointment.
 * It records what the patient was charged and produces a printable receipt.</p>
 *
 * <p><strong>How the total is calculated:</strong></p>
 * <pre>
 *   Total Amount = Doctor's Consultation Fee + Additional Charges
 *
 *   Example:
 *     Consultation Fee  : $150.00  (Dr. Smith's base rate)
 *     Additional Charges: $75.00   (blood test + ECG)
 *     ─────────────────────────────
 *     TOTAL AMOUNT PAID : $225.00
 * </pre>
 *
 * <p><strong>Display-only fields</strong>: {@code patientName}, {@code doctorName},
 * and {@code appointmentTime} are not stored in the invoices table. They are attached
 * by the service layer so the receipt can be printed without extra database queries.</p>
 */
public class Invoice {

    // ── Core stored fields ───────────────────────────────────────────────────
    private int           invoiceId;          // database primary key (shown as INV-#####)
    private int           appointmentId;      // FK → appointments.appointment_id
    private double        consultationFee;    // base rate charged by the doctor
    private double        additionalCharges;  // extras: lab tests, medication, procedures, etc.
    private double        totalAmount;        // consultationFee + additionalCharges
    private String        paymentStatus;      // "PAID" (default for simplicity; future: PENDING, REFUNDED)
    private String        notes;              // free-text breakdown of additional charges
    private LocalDateTime generatedAt;        // when the invoice was created

    // ── Display-only fields (not persisted) ─────────────────────────────────
    // Attached after retrieval so the receipt prints without extra DB calls.
    private String        patientName;
    private String        doctorName;
    private LocalDateTime appointmentTime;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Default constructor — sets payment status to PAID and timestamps generation.
     * In this system, all invoices are immediately considered paid upon generation.
     * A future extension could add PENDING / REFUNDED states.
     */
    public Invoice() {
        this.paymentStatus = "PAID";
        this.generatedAt   = LocalDateTime.now();
    }

    /**
     * Full constructor used when reading an existing invoice from the database.
     *
     * @param invoiceId          the database primary key
     * @param appointmentId      the appointment this invoice belongs to
     * @param consultationFee    the doctor's base consultation charge
     * @param additionalCharges  any extras billed beyond the base fee
     * @param totalAmount        the pre-computed sum (should equal fee + extras)
     * @param paymentStatus      the payment state string (usually "PAID")
     * @param notes              optional breakdown of additional charges
     */
    public Invoice(int invoiceId, int appointmentId, double consultationFee,
                   double additionalCharges, double totalAmount,
                   String paymentStatus, String notes) {
        this.invoiceId         = invoiceId;
        this.appointmentId     = appointmentId;
        this.consultationFee   = consultationFee;
        this.additionalCharges = additionalCharges;
        this.totalAmount       = totalAmount;
        this.paymentStatus     = paymentStatus != null ? paymentStatus : "PAID";
        this.notes             = notes;
        this.generatedAt       = LocalDateTime.now();
    }

    /**
     * Convenience constructor for creating a brand-new invoice.
     * The total is computed automatically as {@code consultationFee + additionalCharges}.
     * The ID will be assigned by the database after saving.
     *
     * @param appointmentId     the appointment being billed
     * @param consultationFee   the doctor's base rate
     * @param additionalCharges extra charges (0 if no extras)
     * @param notes             description of additional charges (may be empty)
     */
    public Invoice(int appointmentId, double consultationFee, double additionalCharges, String notes) {
        // Delegate to the full constructor, computing the total here.
        this(/* id= */ 0,
             appointmentId,
             consultationFee,
             additionalCharges,
             /* total = */ consultationFee + additionalCharges,
             "PAID",
             notes);
    }

    // =========================================================================
    // FORMATTED RECEIPT
    // =========================================================================

    /**
     * Renders a human-readable, formatted receipt suitable for printing or display.
     *
     * <p>Example output:</p>
     * <pre>
     * =========================================================
     *                  HOSPITAL CLINIC INVOICE
     * =========================================================
     *  Invoice No        : INV-00007
     *  Appointment ID    : APT-00003
     *  Generated Date    : 2026-10-01 09:45
     *  ---------------------------------------------------------
     *  Patient Name      : Alice Johnson
     *  Attending Doctor  : Dr. Robert Smith
     *  Consultation Time : 2026-10-01 09:00
     *  ---------------------------------------------------------
     *  Consultation Fee  : $     150.00
     *  Additional Charges: $      75.00
     *  Description        : Blood Test + ECG
     *  ---------------------------------------------------------
     *  TOTAL AMOUNT PAID  : $     225.00  [PAID]
     * =========================================================
     * </pre>
     *
     * @return the formatted receipt as a multi-line string ready for console output
     */
    public String toFormattedReceipt() {
        StringBuilder receipt = new StringBuilder();

        receipt.append("\n=========================================================\n");
        receipt.append("                 HOSPITAL CLINIC INVOICE                 \n");
        receipt.append("=========================================================\n");
        receipt.append(String.format(" Invoice No        : INV-%05d\n", invoiceId));
        receipt.append(String.format(" Appointment ID    : APT-%05d\n", appointmentId));
        receipt.append(String.format(" Generated Date    : %s\n",
                DateTimeUtils.formatDateTime(generatedAt != null ? generatedAt : LocalDateTime.now())));
        receipt.append(" ---------------------------------------------------------\n");

        // These fields are only available if the service layer attached them.
        if (patientName    != null) receipt.append(String.format(" Patient Name      : %s\n", patientName));
        if (doctorName     != null) receipt.append(String.format(" Attending Doctor  : %s\n", doctorName));
        if (appointmentTime != null)receipt.append(String.format(" Consultation Time : %s\n",
                DateTimeUtils.formatDateTime(appointmentTime)));

        receipt.append(" ---------------------------------------------------------\n");
        receipt.append(String.format(" Consultation Fee   : $ %10.2f\n", consultationFee));
        receipt.append(String.format(" Additional Charges : $ %10.2f\n", additionalCharges));

        // Only print the notes line if there is actually something to describe.
        if (notes != null && !notes.trim().isEmpty()) {
            receipt.append(String.format(" Description        : %s\n", notes));
        }

        receipt.append(" ---------------------------------------------------------\n");
        receipt.append(String.format(" TOTAL AMOUNT PAID  : $ %10.2f  [%s]\n", totalAmount, paymentStatus));
        receipt.append("=========================================================\n");

        return receipt.toString();
    }

    // =========================================================================
    // GETTERS AND SETTERS
    // =========================================================================

    public int getInvoiceId()                              { return invoiceId; }
    public void setInvoiceId(int invoiceId)                { this.invoiceId = invoiceId; }

    public int getAppointmentId()                          { return appointmentId; }
    public void setAppointmentId(int appointmentId)        { this.appointmentId = appointmentId; }

    public double getConsultationFee()                     { return consultationFee; }
    public void setConsultationFee(double fee)             { this.consultationFee = fee; }

    public double getAdditionalCharges()                   { return additionalCharges; }
    public void setAdditionalCharges(double charges)       { this.additionalCharges = charges; }

    public double getTotalAmount()                         { return totalAmount; }
    public void setTotalAmount(double total)               { this.totalAmount = total; }

    public String getPaymentStatus()                       { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus)     { this.paymentStatus = paymentStatus; }

    public String getNotes()                               { return notes; }
    public void setNotes(String notes)                     { this.notes = notes; }

    public LocalDateTime getGeneratedAt()                  { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt)  { this.generatedAt = generatedAt; }

    /** Display-only: patient's name, populated by the service layer. */
    public String getPatientName()                         { return patientName; }
    public void setPatientName(String patientName)         { this.patientName = patientName; }

    /** Display-only: doctor's name, populated by the service layer. */
    public String getDoctorName()                          { return doctorName; }
    public void setDoctorName(String doctorName)           { this.doctorName = doctorName; }

    /** Display-only: the appointment's start time, used on the receipt. */
    public LocalDateTime getAppointmentTime()              { return appointmentTime; }
    public void setAppointmentTime(LocalDateTime time)     { this.appointmentTime = time; }

    // =========================================================================
    // TOSTRING — SHORT SUMMARY
    // =========================================================================

    /**
     * Returns a compact one-line summary for logging.
     * Use {@link #toFormattedReceipt()} when you need the full printable receipt.
     *
     * Example: {@code Invoice[ID=7, AppointmentID=3, Total=$225.00, Status=PAID]}
     */
    @Override
    public String toString() {
        return String.format("Invoice[ID=%d, AppointmentID=%d, Total=$%.2f, Status=%s]",
                invoiceId, appointmentId, totalAmount, paymentStatus);
    }
}
