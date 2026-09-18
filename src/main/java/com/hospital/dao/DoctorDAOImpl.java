package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.AvailabilitySlot;
import com.hospital.model.Doctor;
import com.hospital.model.Specialization;
import com.hospital.util.AppLogger;
import com.hospital.util.DatabaseManager;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC SQLite implementation for IDoctorDAO.
 */
public class DoctorDAOImpl implements IDoctorDAO {

    @Override
    public Doctor save(Doctor doctor) throws DatabaseException {
        String sql = "INSERT INTO doctors (name, specialization, phone, email, consultation_fee) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, doctor.getName());
            stmt.setString(2, doctor.getSpecialization().name());
            stmt.setString(3, doctor.getPhone());
            stmt.setString(4, doctor.getEmail());
            stmt.setDouble(5, doctor.getConsultationFee());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DatabaseException("Creating doctor failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    doctor.setDoctorId(generatedKeys.getInt(1));
                }
            }
            AppLogger.info("Doctor saved successfully: " + doctor);
            return doctor;
        } catch (SQLException e) {
            AppLogger.severe("Database error saving doctor", e);
            throw new DatabaseException("Failed to save doctor: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Doctor doctor) throws DatabaseException {
        String sql = "UPDATE doctors SET name = ?, specialization = ?, phone = ?, email = ?, consultation_fee = ? WHERE doctor_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, doctor.getName());
            stmt.setString(2, doctor.getSpecialization().name());
            stmt.setString(3, doctor.getPhone());
            stmt.setString(4, doctor.getEmail());
            stmt.setDouble(5, doctor.getConsultationFee());
            stmt.setInt(6, doctor.getDoctorId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.severe("Database error updating doctor ID " + doctor.getDoctorId(), e);
            throw new DatabaseException("Failed to update doctor: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Doctor> findById(int doctorId) throws DatabaseException {
        String sql = "SELECT * FROM doctors WHERE doctor_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, doctorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDoctor(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding doctor by ID " + doctorId, e);
            throw new DatabaseException("Failed to query doctor: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Doctor> findAll() throws DatabaseException {
        String sql = "SELECT * FROM doctors ORDER BY name ASC";
        List<Doctor> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToDoctor(rs));
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching doctors", e);
            throw new DatabaseException("Failed to fetch doctors list: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Doctor> findBySpecialization(Specialization specialization) throws DatabaseException {
        String sql = "SELECT * FROM doctors WHERE specialization = ? ORDER BY name ASC";
        List<Doctor> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, specialization.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToDoctor(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching doctors by specialization " + specialization, e);
            throw new DatabaseException("Failed to query doctors by specialization: " + e.getMessage(), e);
        }
        return list;
    }

    // -------------------------------------------------------------
    // Doctor Availability Slots Management
    // -------------------------------------------------------------

    @Override
    public AvailabilitySlot addSlot(AvailabilitySlot slot) throws DatabaseException {
        String sql = "INSERT INTO doctor_slots (doctor_id, start_time, end_time, is_booked) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, slot.getDoctorId());
            stmt.setString(2, slot.getStartTime().toString());
            stmt.setString(3, slot.getEndTime().toString());
            stmt.setInt(4, slot.isBooked() ? 1 : 0);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DatabaseException("Creating slot failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    slot.setSlotId(generatedKeys.getInt(1));
                }
            }
            AppLogger.info("Doctor slot added: " + slot);
            return slot;
        } catch (SQLException e) {
            AppLogger.severe("Database error adding slot", e);
            throw new DatabaseException("Failed to add availability slot: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateSlotStatus(int slotId, boolean isBooked) throws DatabaseException {
        String sql = "UPDATE doctor_slots SET is_booked = ? WHERE slot_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, isBooked ? 1 : 0);
            stmt.setInt(2, slotId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.severe("Database error updating slot status ID " + slotId, e);
            throw new DatabaseException("Failed to update slot status: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AvailabilitySlot> getDoctorSlots(int doctorId) throws DatabaseException {
        String sql = "SELECT * FROM doctor_slots WHERE doctor_id = ? ORDER BY start_time ASC";
        List<AvailabilitySlot> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, doctorId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToSlot(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching slots for doctor ID " + doctorId, e);
            throw new DatabaseException("Failed to fetch doctor slots: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<AvailabilitySlot> getDoctorSlotsInRange(int doctorId, LocalDateTime start, LocalDateTime end) throws DatabaseException {
        String sql = "SELECT * FROM doctor_slots WHERE doctor_id = ? AND start_time < ? AND end_time > ? ORDER BY start_time ASC";
        List<AvailabilitySlot> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, doctorId);
            stmt.setString(2, end.toString());
            stmt.setString(3, start.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToSlot(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching slots in range for doctor ID " + doctorId, e);
            throw new DatabaseException("Failed to query slots in range: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public Optional<AvailabilitySlot> findSlotById(int slotId) throws DatabaseException {
        String sql = "SELECT * FROM doctor_slots WHERE slot_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, slotId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSlot(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding slot by ID " + slotId, e);
            throw new DatabaseException("Failed to query slot: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    private Doctor mapResultSetToDoctor(ResultSet rs) throws SQLException {
        Doctor d = new Doctor();
        d.setDoctorId(rs.getInt("doctor_id"));
        d.setName(rs.getString("name"));
        d.setSpecialization(Specialization.fromString(rs.getString("specialization")));
        d.setPhone(rs.getString("phone"));
        d.setEmail(rs.getString("email"));
        d.setConsultationFee(rs.getDouble("consultation_fee"));
        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            try {
                d.setCreatedAt(LocalDateTime.parse(createdAtStr.replace(" ", "T")));
            } catch (Exception ignored) {}
        }
        return d;
    }

    private AvailabilitySlot mapResultSetToSlot(ResultSet rs) throws SQLException {
        AvailabilitySlot s = new AvailabilitySlot();
        s.setSlotId(rs.getInt("slot_id"));
        s.setDoctorId(rs.getInt("doctor_id"));
        s.setStartTime(LocalDateTime.parse(rs.getString("start_time").replace(" ", "T")));
        s.setEndTime(LocalDateTime.parse(rs.getString("end_time").replace(" ", "T")));
        s.setBooked(rs.getInt("is_booked") == 1);
        return s;
    }
}
