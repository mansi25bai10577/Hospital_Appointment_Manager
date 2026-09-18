package com.hospital.model;

import java.time.LocalDateTime;

/**
 * Represents a patient registered in the clinic system.
 *
 * <p>A {@code Patient} is the central entity around which everything else revolves —
 * appointments are booked for patients, invoices are generated on their behalf,
 * and their medical history follows them from visit to visit.</p>
 *
 * <p><strong>Phone as a unique identifier:</strong><br>
 * Phone number serves as a natural duplicate-detection key. Before saving a new patient,
 * the service layer checks that no existing record shares the same phone number.
 * This prevents a returning patient from accidentally being registered twice.</p>
 *
 * <p><strong>Optional fields:</strong><br>
 * {@code email} and {@code medicalHistory} are optional. A patient with no email
 * is stored with an empty string rather than null, which simplifies display code
 * throughout the application.</p>
 */
public class Patient {

    // ── Fields ──────────────────────────────────────────────────────────────
    private int           patientId;       // database primary key (auto-assigned)
    private String        name;            // full name as registered at the clinic
    private int           age;             // age in years at time of registration
    private String        gender;          // Male / Female / Other
    private String        phone;           // primary contact number; must be unique
    private String        email;           // optional contact email
    private String        medicalHistory;  // allergies, chronic conditions, past surgeries, etc.
    private LocalDateTime createdAt;       // when the patient record was first created

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Default no-args constructor required by the DAO mapping layer. */
    public Patient() {}

    /**
     * Full constructor used when reading an existing patient from the database.
     *
     * @param patientId      the database-assigned primary key
     * @param name           patient's full name
     * @param age            age in years
     * @param gender         gender string (Male / Female / Other)
     * @param phone          unique contact phone number
     * @param email          optional email address (empty string if not provided)
     * @param medicalHistory optional notes on allergies, conditions, medications
     */
    public Patient(int patientId, String name, int age, String gender,
                   String phone, String email, String medicalHistory) {
        this.patientId      = patientId;
        this.name           = name;
        this.age            = age;
        this.gender         = gender;
        this.phone          = phone;
        this.email          = email;
        this.medicalHistory = medicalHistory;
    }

    /**
     * Convenience constructor for registering a brand-new patient.
     * The ID is set to 0 and will be assigned by the database after saving.
     *
     * @param name           patient's full name
     * @param age            age in years
     * @param gender         gender string
     * @param phone          primary phone number
     * @param email          optional email
     * @param medicalHistory optional medical notes
     */
    public Patient(String name, int age, String gender,
                   String phone, String email, String medicalHistory) {
        this(/* id= */ 0, name, age, gender, phone, email, medicalHistory);
    }

    // =========================================================================
    // GETTERS AND SETTERS
    // =========================================================================

    public int getPatientId()                              { return patientId; }
    public void setPatientId(int patientId)                { this.patientId = patientId; }

    public String getName()                                { return name; }
    public void setName(String name)                       { this.name = name; }

    public int getAge()                                    { return age; }
    public void setAge(int age)                            { this.age = age; }

    public String getGender()                              { return gender; }
    public void setGender(String gender)                   { this.gender = gender; }

    /** Unique contact phone. Changing this via the update flow also triggers uniqueness re-check. */
    public String getPhone()                               { return phone; }
    public void setPhone(String phone)                     { this.phone = phone; }

    public String getEmail()                               { return email; }
    public void setEmail(String email)                     { this.email = email; }

    public String getMedicalHistory()                      { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory)   { this.medicalHistory = medicalHistory; }

    /** Timestamp of when the patient was first registered — set by the database. */
    public LocalDateTime getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)      { this.createdAt = createdAt; }

    // =========================================================================
    // TOSTRING — SHORT SUMMARY
    // =========================================================================

    /**
     * Returns a one-line summary for logging and console display.
     * Example: {@code Patient[ID=3, Name='Alice Johnson', Age=34, Gender='Female', Phone='9876543210']}
     */
    @Override
    public String toString() {
        return String.format("Patient[ID=%d, Name='%s', Age=%d, Gender='%s', Phone='%s']",
                patientId, name, age, gender, phone);
    }
}
