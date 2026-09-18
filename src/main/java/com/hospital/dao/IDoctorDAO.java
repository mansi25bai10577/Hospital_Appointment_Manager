package com.hospital.dao;

import com.hospital.exception.DatabaseException;
import com.hospital.model.AvailabilitySlot;
import com.hospital.model.Doctor;
import com.hospital.model.Specialization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object Interface for Doctor entity and availability slots.
 */
public interface IDoctorDAO {
    Doctor save(Doctor doctor) throws DatabaseException;
    boolean update(Doctor doctor) throws DatabaseException;
    Optional<Doctor> findById(int doctorId) throws DatabaseException;
    List<Doctor> findAll() throws DatabaseException;
    List<Doctor> findBySpecialization(Specialization specialization) throws DatabaseException;

    // Slot Management
    AvailabilitySlot addSlot(AvailabilitySlot slot) throws DatabaseException;
    boolean updateSlotStatus(int slotId, boolean isBooked) throws DatabaseException;
    List<AvailabilitySlot> getDoctorSlots(int doctorId) throws DatabaseException;
    List<AvailabilitySlot> getDoctorSlotsInRange(int doctorId, LocalDateTime start, LocalDateTime end) throws DatabaseException;
    Optional<AvailabilitySlot> findSlotById(int slotId) throws DatabaseException;
}
