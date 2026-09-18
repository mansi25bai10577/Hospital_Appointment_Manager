package com.hospital.model;

import java.time.LocalDateTime;

/**
 * Represents a doctor registered in the clinic system.
 *
 * <p>A {@code Doctor} profile captures the professional details needed to schedule
 * appointments and generate billing invoices. The {@link #consultationFee} in
 * particular is used directly by the billing engine — it becomes the base charge
 * on every invoice generated for this doctor's appointments.</p>
 *
 * <p>Each doctor has a {@link Specialization} (e.g. CARDIOLOGY, GENERAL_PRACTICE)
 * which is displayed in the booking menu to help receptionists direct patients
 * to the right professional.</p>
 *
 * <p>Doctors have availability slots defined separately ({@link AvailabilitySlot}).
 * This separation keeps the Doctor model simple and focused on identity and pricing,
 * while scheduling concerns live in their own table.</p>
 */
public class Doctor {

    // ── Fields ──────────────────────────────────────────────────────────────
    private int            doctorId;          // database primary key (auto-assigned)
    private String         name;              // full name including title, e.g. "Dr. Sarah Patel"
    private Specialization specialization;    // medical specialty (enum for type safety)
    private String         phone;             // clinic contact phone
    private String         email;             // optional professional email
    private double         consultationFee;   // base charge per consultation in USD
    private LocalDateTime  createdAt;         // when the profile was registered in the system

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Default no-args constructor required by the DAO mapping layer. */
    public Doctor() {}

    /**
     * Full constructor used when reading an existing doctor from the database.
     *
     * @param doctorId         the database-assigned primary key
     * @param name             doctor's full professional name
     * @param specialization   their area of medicine (see {@link Specialization})
     * @param phone            clinic contact number
     * @param email            optional email (empty string if not provided)
     * @param consultationFee  base fee charged per patient visit
     */
    public Doctor(int doctorId, String name, Specialization specialization,
                  String phone, String email, double consultationFee) {
        this.doctorId         = doctorId;
        this.name             = name;
        this.specialization   = specialization;
        this.phone            = phone;
        this.email            = email;
        this.consultationFee  = consultationFee;
    }

    /**
     * Convenience constructor for registering a brand-new doctor.
     * The ID is set to 0 and will be assigned by the database after saving.
     *
     * @param name             doctor's full name
     * @param specialization   their specialty
     * @param phone            contact phone
     * @param email            optional email
     * @param consultationFee  fee per visit
     */
    public Doctor(String name, Specialization specialization,
                  String phone, String email, double consultationFee) {
        this(/* id= */ 0, name, specialization, phone, email, consultationFee);
    }

    // =========================================================================
    // GETTERS AND SETTERS
    // =========================================================================

    public int getDoctorId()                               { return doctorId; }
    public void setDoctorId(int doctorId)                  { this.doctorId = doctorId; }

    public String getName()                                { return name; }
    public void setName(String name)                       { this.name = name; }

    public Specialization getSpecialization()              { return specialization; }
    public void setSpecialization(Specialization spec)     { this.specialization = spec; }

    public String getPhone()                               { return phone; }
    public void setPhone(String phone)                     { this.phone = phone; }

    public String getEmail()                               { return email; }
    public void setEmail(String email)                     { this.email = email; }

    /**
     * The base fee used directly by the billing engine as the "Consultation Fee"
     * line item on every invoice this doctor's appointments generate.
     */
    public double getConsultationFee()                     { return consultationFee; }
    public void setConsultationFee(double fee)             { this.consultationFee = fee; }

    public LocalDateTime getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)      { this.createdAt = createdAt; }

    // =========================================================================
    // TOSTRING — SHORT SUMMARY
    // =========================================================================

    /**
     * Returns a one-line summary for logging and console display.
     * Example: {@code Doctor[ID=1, Name='Dr. Sarah Patel', Specialization='Cardiology', Fee=$200.00]}
     */
    @Override
    public String toString() {
        return String.format("Doctor[ID=%d, Name='%s', Specialization='%s', Fee=$%.2f]",
                doctorId, name, specialization.getDisplayName(), consultationFee);
    }
}
