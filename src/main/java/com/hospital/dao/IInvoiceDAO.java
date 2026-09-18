package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.Invoice;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object Interface for Invoice entity.
 */
public interface IInvoiceDAO {
    Invoice save(Invoice invoice) throws DatabaseException;
    Optional<Invoice> findById(int invoiceId) throws DatabaseException;
    Optional<Invoice> findByAppointmentId(int appointmentId) throws DatabaseException;
    List<Invoice> findByPatientId(int patientId) throws DatabaseException;
    List<Invoice> findAll() throws DatabaseException;
}
