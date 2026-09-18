package com.hospital.service;

import com.hospital.dao.IDoctorDAO;
import com.hospital.exception.DatabaseException;
import com.hospital.exception.DoctorNotFoundException;
import com.hospital.exception.ValidationException;
import com.hospital.model.Doctor;
import com.hospital.model.Specialization;
import com.hospital.util.AppLogger;
import java.util.List;

/**
 * Service managing doctor profiles, fees, and specializations.
 */
public class DoctorService {
    private final IDoctorDAO doctorDAO;

    public DoctorService(IDoctorDAO doctorDAO) {
        this.doctorDAO = doctorDAO;
    }

    public Doctor registerDoctor(String name, Specialization specialization, String phone, String email, double consultationFee)
            throws ValidationException, DatabaseException {

        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Doctor name cannot be empty.");
        }
        if (specialization == null) {
            throw new ValidationException("Doctor specialization must be specified.");
        }
        if (consultationFee < 0) {
            throw new ValidationException("Consultation fee cannot be negative.");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new ValidationException("Doctor contact phone cannot be empty.");
        }

        Doctor doctor = new Doctor(name.trim(), specialization, phone.trim(),
                email != null ? email.trim() : "", consultationFee);

        Doctor saved = doctorDAO.save(doctor);
        AppLogger.info("Registered new doctor: " + saved.getName() + " [" + saved.getSpecialization().getDisplayName() + "]");
        return saved;
    }

    public Doctor getDoctorById(int doctorId) throws DoctorNotFoundException, DatabaseException {
        return doctorDAO.findById(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException("Doctor with ID " + doctorId + " was not found."));
    }

    public List<Doctor> getAllDoctors() throws DatabaseException {
        return doctorDAO.findAll();
    }

    public List<Doctor> getDoctorsBySpecialization(Specialization specialization) throws DatabaseException {
        return doctorDAO.findBySpecialization(specialization);
    }
}
