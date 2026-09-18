package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object Interface for Appointment entity.
 */
public interface IAppointmentDAO {
    Appointment save(Appointment appointment) throws DatabaseException;
    boolean updateStatus(int appointmentId, AppointmentStatus newStatus) throws DatabaseException;
    boolean reschedule(int appointmentId, Integer newSlotId, LocalDateTime newStart, LocalDateTime newEnd) throws DatabaseException;
    Optional<Appointment> findById(int appointmentId) throws DatabaseException;
    List<Appointment> findByPatientId(int patientId) throws DatabaseException;
    List<Appointment> findByDoctorId(int doctorId) throws DatabaseException;
    List<Appointment> findByDoctorAndDate(int doctorId, LocalDate date) throws DatabaseException;
    List<Appointment> findActiveAppointmentsForDoctor(int doctorId) throws DatabaseException;
    List<Appointment> findAll() throws DatabaseException;
}
