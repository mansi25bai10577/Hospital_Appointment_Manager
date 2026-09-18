package com.hospital.service;

import com.hospital.dao.*;
import com.hospital.exception.SlotConflictException;
import com.hospital.model.*;
import com.hospital.util.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite verifying SchedulingService slot conflict detection algorithm,
 * alternative slot suggestions, and emergency priority queueing.
 */
public class SchedulingServiceTest {
    private PatientService patientService;
    private DoctorService doctorService;
    private SchedulingService schedulingService;

    private Doctor testDoctor;
    private Patient testPatient1;
    private Patient testPatient2;

    @BeforeEach
    public void setUp() throws Exception {
        // Clean up test DB if exists
        File dbFile = new File("hospital.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }

        DatabaseManager.initializeDatabase();

        IPatientDAO patientDAO = new PatientDAOImpl();
        IDoctorDAO doctorDAO = new DoctorDAOImpl();
        IAppointmentDAO appointmentDAO = new AppointmentDAOImpl();

        patientService = new PatientService(patientDAO);
        doctorService = new DoctorService(doctorDAO);
        schedulingService = new SchedulingService(doctorDAO, patientDAO, appointmentDAO);

        testDoctor = doctorService.registerDoctor("Dr. Test Smith", Specialization.CARDIOLOGY, "555-0001", "test@doc.com", 150.0);
        testPatient1 = patientService.registerPatient("Patient One", 30, "Male", "555-0002", "p1@test.com", "None");
        testPatient2 = patientService.registerPatient("Patient Two", 45, "Female", "555-0003", "p2@test.com", "None");
    }

    @Test
    @DisplayName("Should successfully book an appointment when slot is completely free")
    public void testBookSlotSuccess() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 9, 0);
        LocalDateTime end = start.plusMinutes(30);

        Appointment appt = schedulingService.bookAppointment(
                testPatient1.getPatientId(), testDoctor.getDoctorId(), start, end, PriorityLevel.NORMAL, "Checkup"
        );

        assertNotNull(appt);
        assertTrue(appt.getAppointmentId() > 0);
        assertEquals(AppointmentStatus.SCHEDULED, appt.getStatus());
    }

    @Test
    @DisplayName("Should reject exact overlapping slot booking with SlotConflictException")
    public void testDetectExactSlotOverlap() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 9, 0);
        LocalDateTime end = start.plusMinutes(30);

        // First booking succeeds
        schedulingService.bookAppointment(
                testPatient1.getPatientId(), testDoctor.getDoctorId(), start, end, PriorityLevel.NORMAL, "First Appt"
        );

        // Exact same slot booking attempt must throw SlotConflictException
        SlotConflictException ex = assertThrows(SlotConflictException.class, () -> {
            schedulingService.bookAppointment(
                    testPatient2.getPatientId(), testDoctor.getDoctorId(), start, end, PriorityLevel.NORMAL, "Second Appt"
            );
        });

        assertTrue(ex.getMessage().contains("Slot Conflict"));
        assertNotNull(ex.getSuggestedAlternatives());
        assertFalse(ex.getSuggestedAlternatives().isEmpty(), "Should suggest alternative free slots when conflict occurs");
    }

    @Test
    @DisplayName("Should reject partial left overlap (starts before, ends inside existing slot)")
    public void testDetectPartialOverlapLeft() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 1, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 1, 10, 30);
        schedulingService.bookAppointment(testPatient1.getPatientId(), testDoctor.getDoctorId(), start1, end1, PriorityLevel.NORMAL, "Existing");

        // Proposed slot 09:45 to 10:15 (overlaps with 10:00-10:30)
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 1, 9, 45);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 1, 10, 15);

        assertThrows(SlotConflictException.class, () -> {
            schedulingService.bookAppointment(testPatient2.getPatientId(), testDoctor.getDoctorId(), start2, end2, PriorityLevel.NORMAL, "Overlapping Left");
        });
    }

    @Test
    @DisplayName("Should reject partial right overlap (starts inside, ends after existing slot)")
    public void testDetectPartialOverlapRight() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 1, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 1, 10, 30);
        schedulingService.bookAppointment(testPatient1.getPatientId(), testDoctor.getDoctorId(), start1, end1, PriorityLevel.NORMAL, "Existing");

        // Proposed slot 10:15 to 10:45 (overlaps with 10:00-10:30)
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 1, 10, 15);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 1, 10, 45);

        assertThrows(SlotConflictException.class, () -> {
            schedulingService.bookAppointment(testPatient2.getPatientId(), testDoctor.getDoctorId(), start2, end2, PriorityLevel.NORMAL, "Overlapping Right");
        });
    }

    @Test
    @DisplayName("Should reject enclosing overlap (proposed slot completely surrounds existing slot)")
    public void testDetectEnclosingOverlap() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 1, 10, 15);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 1, 10, 30);
        schedulingService.bookAppointment(testPatient1.getPatientId(), testDoctor.getDoctorId(), start1, end1, PriorityLevel.NORMAL, "Existing Inner");

        // Proposed slot 10:00 to 11:00 (encloses 10:15-10:30)
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 1, 10, 0);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 1, 11, 0);

        assertThrows(SlotConflictException.class, () -> {
            schedulingService.bookAppointment(testPatient2.getPatientId(), testDoctor.getDoctorId(), start2, end2, PriorityLevel.NORMAL, "Enclosing");
        });
    }

    @Test
    @DisplayName("Should sort daily queue placing EMERGENCY appointments first")
    public void testEmergencyPriorityQueueSorting() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 2);

        LocalDateTime t1 = LocalDateTime.of(date, LocalTime.of(9, 0));
        LocalDateTime t2 = LocalDateTime.of(date, LocalTime.of(10, 0));

        // Book NORMAL appointment at 09:00
        schedulingService.bookAppointment(testPatient1.getPatientId(), testDoctor.getDoctorId(), t1, t1.plusMinutes(30), PriorityLevel.NORMAL, "Routine");

        // Book EMERGENCY appointment at 10:00
        schedulingService.bookAppointment(testPatient2.getPatientId(), testDoctor.getDoctorId(), t2, t2.plusMinutes(30), PriorityLevel.EMERGENCY, "Chest Pain");

        List<Appointment> queue = schedulingService.getDoctorDailyQueue(testDoctor.getDoctorId(), date);

        assertEquals(2, queue.size());
        // EMERGENCY appointment should be first in queue despite starting later
        assertEquals(PriorityLevel.EMERGENCY, queue.get(0).getPriority());
        assertEquals("Chest Pain", queue.get(0).getReason());
    }
}
