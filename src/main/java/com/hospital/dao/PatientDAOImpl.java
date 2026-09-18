package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.Patient;
import com.hospital.util.AppLogger;
import com.hospital.util.DatabaseManager;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC SQLite implementation for IPatientDAO.
 */
public class PatientDAOImpl implements IPatientDAO {

    @Override
    public Patient save(Patient patient) throws DatabaseException {
        String sql = "INSERT INTO patients (name, age, gender, phone, email, medical_history) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, patient.getName());
            stmt.setInt(2, patient.getAge());
            stmt.setString(3, patient.getGender());
            stmt.setString(4, patient.getPhone());
            stmt.setString(5, patient.getEmail());
            stmt.setString(6, patient.getMedicalHistory());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DatabaseException("Creating patient failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    patient.setPatientId(generatedKeys.getInt(1));
                }
            }
            AppLogger.info("Patient saved successfully: " + patient);
            return patient;
        } catch (SQLException e) {
            AppLogger.severe("Database error saving patient", e);
            throw new DatabaseException("Failed to save patient: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Patient patient) throws DatabaseException {
        String sql = "UPDATE patients SET name = ?, age = ?, gender = ?, phone = ?, email = ?, medical_history = ? WHERE patient_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, patient.getName());
            stmt.setInt(2, patient.getAge());
            stmt.setString(3, patient.getGender());
            stmt.setString(4, patient.getPhone());
            stmt.setString(5, patient.getEmail());
            stmt.setString(6, patient.getMedicalHistory());
            stmt.setInt(7, patient.getPatientId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.severe("Database error updating patient ID " + patient.getPatientId(), e);
            throw new DatabaseException("Failed to update patient: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(int patientId) throws DatabaseException {
        String sql = "DELETE FROM patients WHERE patient_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, patientId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.severe("Database error deleting patient ID " + patientId, e);
            throw new DatabaseException("Failed to delete patient: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Patient> findById(int patientId) throws DatabaseException {
        String sql = "SELECT * FROM patients WHERE patient_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, patientId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPatient(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding patient by ID " + patientId, e);
            throw new DatabaseException("Failed to query patient: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Patient> findByPhone(String phone) throws DatabaseException {
        String sql = "SELECT * FROM patients WHERE phone = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phone);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPatient(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding patient by phone " + phone, e);
            throw new DatabaseException("Failed to query patient by phone: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Patient> searchByName(String keyword) throws DatabaseException {
        String sql = "SELECT * FROM patients WHERE name LIKE ? ORDER BY name ASC";
        List<Patient> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + keyword + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToPatient(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error searching patients by name: " + keyword, e);
            throw new DatabaseException("Failed to search patients: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Patient> findAll() throws DatabaseException {
        String sql = "SELECT * FROM patients ORDER BY patient_id DESC";
        List<Patient> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToPatient(rs));
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching all patients", e);
            throw new DatabaseException("Failed to fetch patients list: " + e.getMessage(), e);
        }
        return list;
    }

    private Patient mapResultSetToPatient(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setPatientId(rs.getInt("patient_id"));
        p.setName(rs.getString("name"));
        p.setAge(rs.getInt("age"));
        p.setGender(rs.getString("gender"));
        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        p.setMedicalHistory(rs.getString("medical_history"));
        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            try {
                p.setCreatedAt(LocalDateTime.parse(createdAtStr.replace(" ", "T")));
            } catch (Exception ignored) {}
        }
        return p;
    }
}
