package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.Invoice;
import com.hospital.util.AppLogger;
import com.hospital.util.DatabaseManager;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC SQLite implementation for IInvoiceDAO.
 */
public class InvoiceDAOImpl implements IInvoiceDAO {

    @Override
    public Invoice save(Invoice invoice) throws DatabaseException {
        String sql = "INSERT INTO invoices (appointment_id, consultation_fee, additional_charges, total_amount, payment_status, notes) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, invoice.getAppointmentId());
            stmt.setDouble(2, invoice.getConsultationFee());
            stmt.setDouble(3, invoice.getAdditionalCharges());
            stmt.setDouble(4, invoice.getTotalAmount());
            stmt.setString(5, invoice.getPaymentStatus());
            stmt.setString(6, invoice.getNotes());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DatabaseException("Creating invoice failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    invoice.setInvoiceId(generatedKeys.getInt(1));
                }
            }
            AppLogger.info("Invoice saved successfully: " + invoice);
            return invoice;
        } catch (SQLException e) {
            AppLogger.severe("Database error saving invoice", e);
            throw new DatabaseException("Failed to save invoice: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Invoice> findById(int invoiceId) throws DatabaseException {
        String sql = "SELECT i.*, p.name AS patient_name, d.name AS doctor_name, a.start_time AS appointment_time " +
                     "FROM invoices i " +
                     "JOIN appointments a ON i.appointment_id = a.appointment_id " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE i.invoice_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, invoiceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToInvoice(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding invoice ID " + invoiceId, e);
            throw new DatabaseException("Failed to query invoice: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Invoice> findByAppointmentId(int appointmentId) throws DatabaseException {
        String sql = "SELECT i.*, p.name AS patient_name, d.name AS doctor_name, a.start_time AS appointment_time " +
                     "FROM invoices i " +
                     "JOIN appointments a ON i.appointment_id = a.appointment_id " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE i.appointment_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, appointmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToInvoice(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding invoice by appointment ID " + appointmentId, e);
            throw new DatabaseException("Failed to query invoice by appointment: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Invoice> findByPatientId(int patientId) throws DatabaseException {
        String sql = "SELECT i.*, p.name AS patient_name, d.name AS doctor_name, a.start_time AS appointment_time " +
                     "FROM invoices i " +
                     "JOIN appointments a ON i.appointment_id = a.appointment_id " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "WHERE a.patient_id = ? ORDER BY i.generated_at DESC";
        List<Invoice> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, patientId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToInvoice(rs));
                }
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error finding invoices for patient ID " + patientId, e);
            throw new DatabaseException("Failed to query patient invoices: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Invoice> findAll() throws DatabaseException {
        String sql = "SELECT i.*, p.name AS patient_name, d.name AS doctor_name, a.start_time AS appointment_time " +
                     "FROM invoices i " +
                     "JOIN appointments a ON i.appointment_id = a.appointment_id " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "ORDER BY i.generated_at DESC";
        List<Invoice> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToInvoice(rs));
            }
        } catch (SQLException e) {
            AppLogger.severe("Database error fetching all invoices", e);
            throw new DatabaseException("Failed to fetch invoices list: " + e.getMessage(), e);
        }
        return list;
    }

    private Invoice mapResultSetToInvoice(ResultSet rs) throws SQLException {
        Invoice inv = new Invoice();
        inv.setInvoiceId(rs.getInt("invoice_id"));
        inv.setAppointmentId(rs.getInt("appointment_id"));
        inv.setConsultationFee(rs.getDouble("consultation_fee"));
        inv.setAdditionalCharges(rs.getDouble("additional_charges"));
        inv.setTotalAmount(rs.getDouble("total_amount"));
        inv.setPaymentStatus(rs.getString("payment_status"));
        inv.setNotes(rs.getString("notes"));

        String genAtStr = rs.getString("generated_at");
        if (genAtStr != null) {
            try {
                inv.setGeneratedAt(LocalDateTime.parse(genAtStr.replace(" ", "T")));
            } catch (Exception ignored) {}
        }

        try {
            inv.setPatientName(rs.getString("patient_name"));
            inv.setDoctorName(rs.getString("doctor_name"));
            String apptTimeStr = rs.getString("appointment_time");
            if (apptTimeStr != null) {
                inv.setAppointmentTime(LocalDateTime.parse(apptTimeStr.replace(" ", "T")));
            }
        } catch (SQLException ignored) {}

        return inv;
    }
}
