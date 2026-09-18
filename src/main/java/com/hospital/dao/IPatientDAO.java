package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.Patient;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object Interface for Patient entity.
 */
public interface IPatientDAO {
    Patient save(Patient patient) throws DatabaseException;
    boolean update(Patient patient) throws DatabaseException;
    boolean delete(int patientId) throws DatabaseException;
    Optional<Patient> findById(int patientId) throws DatabaseException;
    Optional<Patient> findByPhone(String phone) throws DatabaseException;
    List<Patient> searchByName(String keyword) throws DatabaseException;
    List<Patient> findAll() throws DatabaseException;
}
