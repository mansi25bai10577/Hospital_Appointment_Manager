package com.hospital.service;

import com.hospital.dao.IPatientDAO;
import com.hospital.exception.DatabaseException;
import com.hospital.exception.PatientNotFoundException;
import com.hospital.exception.ValidationException;
import com.hospital.model.Patient;
import com.hospital.util.AppLogger;
import java.util.List;
import java.util.Optional;

/**
 * PatientService manages everything related to patient records.
 *
 * <p>This service acts as the business logic layer between the console UI
 * and the database. It enforces domain rules that should always hold true
 * regardless of how the system is accessed:</p>
 * <ul>
 *   <li>A patient cannot be registered with someone else's phone number.</li>
 *   <li>An age must be a sensible human age (0–120).</li>
 *   <li>A name cannot be blank — a record without a name is useless.</li>
 * </ul>
 *
 * <p>The {@link IPatientDAO} dependency is injected, which means
 * this service is completely independent of the database implementation.
 * You can swap SQLite for any other storage back-end without touching this class.</p>
 */
public class PatientService {

    /** The data access object that handles all SQL for patient records. */
    private final IPatientDAO patientDAO;

    /**
     * Creates a PatientService backed by the given DAO.
     *
     * @param patientDAO the storage layer for patient data
     */
    public PatientService(IPatientDAO patientDAO) {
        this.patientDAO = patientDAO;
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Registers a brand-new patient in the system.
     *
     * <p><strong>Rules enforced before saving:</strong></p>
     * <ul>
     *   <li>Name must not be blank.</li>
     *   <li>Age must be between 0 and 120.</li>
     *   <li>Phone must look like a valid number (digits, spaces, hyphens, ±country code).</li>
     *   <li>No other patient may already be registered with the same phone number —
     *       phone acts as a unique identifier for returning patients.</li>
     * </ul>
     *
     * @param name           patient's full name
     * @param age            patient's age in years
     * @param gender         patient's gender (free text: Male / Female / Other)
     * @param phone          primary contact phone number
     * @param email          optional email address (empty string is acceptable)
     * @param medicalHistory optional notes about allergies, chronic conditions, etc.
     * @return the saved patient with its newly assigned ID
     * @throws ValidationException if any input rule is violated
     * @throws DatabaseException   if saving fails at the storage level
     */
    public Patient registerPatient(String name, int age, String gender,
                                   String phone, String email, String medicalHistory)
            throws ValidationException, DatabaseException {

        // Validate all inputs before hitting the database.
        validatePatientInput(name, age, phone);

        // Phone uniqueness check — prevents creating a duplicate record for a
        // returning patient who may have been registered under a different name before.
        Optional<Patient> existingByPhone = patientDAO.findByPhone(phone.trim());
        if (existingByPhone.isPresent()) {
            throw new ValidationException(
                    "A patient with phone number '" + phone + "' is already registered. " +
                    "(Patient ID: " + existingByPhone.get().getPatientId() + "). " +
                    "Please look up the existing record instead.");
        }

        // Build the patient object with trimmed strings to avoid whitespace issues.
        Patient newPatient = new Patient(
                name.trim(),
                age,
                gender.trim(),
                phone.trim(),
                email != null ? email.trim() : "",
                medicalHistory != null ? medicalHistory.trim() : "");

        Patient saved = patientDAO.save(newPatient);
        AppLogger.info("Registered new patient: " + saved.getName() + " (ID: " + saved.getPatientId() + ")");
        return saved;
    }

    // =========================================================================
    // LOOKUPS
    // =========================================================================

    /**
     * Finds a patient by their numeric ID.
     *
     * <p>Used when displaying or booking for a patient whose ID is already known.</p>
     *
     * @param patientId the ID to search for
     * @return the matching patient
     * @throws PatientNotFoundException if no patient with this ID exists
     */
    public Patient getPatientById(int patientId) throws PatientNotFoundException, DatabaseException {
        return patientDAO.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(
                        "Patient with ID " + patientId + " was not found. " +
                        "Use 'View All Patients' to find the correct ID."));
    }

    /**
     * Searches for patients whose name contains the given keyword (case-insensitive).
     *
     * <p>Passing an empty or null keyword returns all patients — same as
     * calling {@link #getAllPatients()}.</p>
     *
     * @param keyword part of a patient's name to search by
     * @return matching patients, or all patients if keyword is blank
     */
    public List<Patient> searchPatientsByName(String keyword) throws DatabaseException {
        if (keyword == null || keyword.trim().isEmpty()) {
            // Empty search = "show me everyone"
            return patientDAO.findAll();
        }
        return patientDAO.searchByName(keyword.trim());
    }

    /**
     * Returns every registered patient, ordered by most recently added first.
     *
     * @return all patient records in the database
     */
    public List<Patient> getAllPatients() throws DatabaseException {
        return patientDAO.findAll();
    }

    // =========================================================================
    // UPDATES
    // =========================================================================

    /**
     * Updates the details of an existing patient record.
     *
     * <p>The caller is responsible for fetching the patient first (via
     * {@link #getPatientById}), modifying the fields they want to change,
     * and passing the modified object here. The same validation rules that
     * apply on registration apply here too.</p>
     *
     * @param patient a patient object with an existing ID and updated fields
     * @return {@code true} if the database row was changed
     * @throws ValidationException     if the updated data breaks a business rule
     * @throws PatientNotFoundException if the patient ID no longer exists
     */
    public boolean updatePatient(Patient patient) throws ValidationException, PatientNotFoundException, DatabaseException {
        // A patient object without an ID has never been saved — can't update what doesn't exist.
        if (patient == null || patient.getPatientId() <= 0) {
            throw new ValidationException("Cannot update: invalid or missing patient ID.");
        }

        validatePatientInput(patient.getName(), patient.getAge(), patient.getPhone());

        // Confirm the patient still exists before attempting the update.
        getPatientById(patient.getPatientId());

        return patientDAO.update(patient);
    }

    // =========================================================================
    // VALIDATION — PRIVATE HELPER
    // =========================================================================

    /**
     * Validates the core fields that must be correct for any patient record,
     * whether on initial registration or update.
     *
     * <p>Phone format: digits, spaces, hyphens, parentheses, and an optional
     * leading "+" for international codes. Length: 7–20 characters.</p>
     *
     * @throws ValidationException if any field fails its rule
     */
    private void validatePatientInput(String name, int age, String phone) throws ValidationException {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Patient name cannot be empty.");
        }
        if (age < 0 || age > 120) {
            throw new ValidationException(
                    "Age must be between 0 and 120. Entered value: " + age);
        }
        // Allow digits, spaces, hyphens, parentheses, and optional leading '+'
        // to cover formats like: 9876543210, +1-555-0192, (555) 0144
        if (phone == null || !phone.trim().matches("\\+?[0-9\\-\\s()]{7,20}")) {
            throw new ValidationException(
                    "Invalid phone number: '" + phone + "'. " +
                    "Use 7–20 characters with digits, spaces, hyphens, or parentheses.");
        }
    }
}
