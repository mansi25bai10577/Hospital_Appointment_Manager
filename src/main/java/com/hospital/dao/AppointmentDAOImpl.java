package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.model.PriorityLevel;
import com.hospital.util.AppLogger;
import com.hospital.util.DatabaseManager;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC SQLite implementation for IAppointmentDAO.
 */
public class AppointmentDAOImpl implements IAppointmentDAO {

    @Override
    public Appointment save(Appointment appointment) throws DatabaseException {
        String sql = "INSERT INTO appointments (patient_id, doctor_id, slot_id, appointment_date, start_time, end_time, status, priority, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, appointment.getPatientId());
            stmt.setInt(2, appointment.getDoctorId());
            if (appointment.getSlotId() != null) {
                stmt.setInt(3, appointment.getSlotId());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, appointment.getAppointmentDate().toString());
            stmt.setString(5, appointment.getStartTime().toString());
            stmt.setString(6, appointment.getEndTime().toString());
            stmt.setString(7, appointment.getStatus().name());
            stmt.setString(8, appointment.getPriority().name());
            stmt.setString(9, appointment.getReason());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DatabaseException("Creating appointment failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    appointment.setAppointmentId(generatedKeys.getInt(1));
                }
            }
            AppLogger.info("Appointment saved successfully: " + appointment);
            return appointment;
        } catch (SQLException e) {
            AppLogger.severe("Database error saving appointment", e);
            throw new DatabaseException("Failed to save appointment: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateStatus(int appointmentId, AppointmentStatus newStatus) throws DatabaseException {
        String sql = "UPDATE appointments SET status = ? WHERE appointment_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newStatus.name());
            stmt.setInt(2, appointmentId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.severe("Database error updating status for appointment ID " + appointmentId, e);
            throw new DatabaseException("Failed to update appointment status: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean reschedule(int appointmentId, Integer newSlotId, LocalDateTime newStart, LocalDateTime newEnd) throws DatabaseException {
        String sql = "UPDATE appointments SET slot_id = ?, appointment_date = ?, start_time = ?, end_time = ?, status = 'SCHEDULED' WHERE appointment_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (newSlotId != null) {
                stmt.setInt(1, newSlotId);
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            stmt.setString(2, newStart.toLocalDate().toString());
            stmt.setString(3, newStart.toString());
            stmt.setString(4, newEnd.toString());
            stmt.setInt(5, appointmentId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.severe("Database error rescheduling appointment ID " + appointmentId, e);
            throw new DatabaseException("Failed to reschedule appointment: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Appointment> findById(int appointmentId) throws DatabaseException {
        String sql = "SELECT a.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM appointments a " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE a.appointment_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, appointmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAppointment(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding appointment ID " + appointmentId, e);
            throw new DatabaseException("Failed to query appointment: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Appointment> findByPatientId(int patientId) throws DatabaseException {
        String sql = "SELECT a.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM appointments a " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE a.patient_id = ? ORDER BY a.start_time DESC";
        return executeQueryList(sql, patientId);
    }

    @Override
    public List<Appointment> findByDoctorId(int doctorId) throws DatabaseException {
        String sql = "SELECT a.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM appointments a " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE a.doctor_id = ? ORDER BY a.start_time ASC";
        return executeQueryList(sql, doctorId);
    }

    @Override
    public List<Appointment> findByDoctorAndDate(int doctorId, LocalDate date) throws DatabaseException {
        String sql = "SELECT a.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM appointments a " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE a.doctor_id = ? AND a.appointment_date = ? ORDER BY a.start_time ASC";
        List<Appointment> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, doctorId);
            stmt.setString(2, date.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAppointment(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching appointments for doctor ID " + doctorId + " on date " + date, e);
            throw new DatabaseException("Failed to query appointments by date: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Appointment> findActiveAppointmentsForDoctor(int doctorId) throws DatabaseException {
        String sql = "SELECT a.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM appointments a " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE a.doctor_id = ? AND a.status = 'SCHEDULED' ORDER BY a.start_time ASC";
        return executeQueryList(sql, doctorId);
    }

    @Override
    public List<Appointment> findAll() throws DatabaseException {
        String sql = "SELECT a.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM appointments a " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "ORDER BY a.start_time DESC";
        List<Appointment> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToAppointment(rs));
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching all appointments", e);
            throw new DatabaseException("Failed to fetch appointments list: " + e.getMessage(), e);
        }
        return list;
    }

    private List<Appointment> executeQueryList(String sql, int parameterId) throws DatabaseException {
        List<Appointment> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, parameterId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAppointment(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error executing appointment list query", e);
            throw new DatabaseException("Failed to query appointments: " + e.getMessage(), e);
        }
        return list;
    }

    private Appointment mapResultSetToAppointment(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setAppointmentId(rs.getInt("appointment_id"));
        a.setPatientId(rs.getInt("patient_id"));
        a.setDoctorId(rs.getInt("doctor_id"));
        int slotIdVal = rs.getInt("slot_id");
        a.setSlotId(rs.wasNull() ? null : slotIdVal);
        a.setAppointmentDate(LocalDate.parse(rs.getString("appointment_date")));
        a.setStartTime(LocalDateTime.parse(rs.getString("start_time").replace(" ", "T")));
        a.setEndTime(LocalDateTime.parse(rs.getString("end_time").replace(" ", "T")));
        a.setStatus(AppointmentStatus.valueOf(rs.getString("status")));
        a.setPriority(PriorityLevel.valueOf(rs.getString("priority")));
        a.setReason(rs.getString("reason"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            try {
                a.setCreatedAt(LocalDateTime.parse(createdAtStr.replace(" ", "T")));
            } catch (Exception ignored) {}
        }

        try {
            a.setPatientName(rs.getString("patient_name"));
            a.setDoctorName(rs.getString("doctor_name"));
        } catch (SQLException ignored) {}

        return a;
    }
}
