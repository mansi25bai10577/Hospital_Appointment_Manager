package com.hospital.service;

import com.hospital.dao.*;
import com.hospital.model.*;
import com.hospital.util.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for BillingService invoice generation and consultation completion logic.
 */
public class BillingServiceTest {
    private PatientService patientService;
    private DoctorService doctorService;
    private SchedulingService schedulingService;
    private BillingService billingService;

    private Doctor testDoctor;
    private Patient testPatient;

    @BeforeEach
    public void setUp() throws Exception {
        File dbFile = new File("hospital.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }

        DatabaseManager.initializeDatabase();

        IPatientDAO patientDAO = new PatientDAOImpl();
        IDoctorDAO doctorDAO = new DoctorDAOImpl();
        IAppointmentDAO appointmentDAO = new AppointmentDAOImpl();
        IInvoiceDAO invoiceDAO = new InvoiceDAOImpl();

        patientService = new PatientService(patientDAO);
        doctorService = new DoctorService(doctorDAO);
        schedulingService = new SchedulingService(doctorDAO, patientDAO, appointmentDAO);
        billingService = new BillingService(invoiceDAO, appointmentDAO, doctorDAO);

        testDoctor = doctorService.registerDoctor("Dr. Billing Specialist", Specialization.GENERAL_PRACTICE, "555-9999", "doc@billing.com", 200.0);
        testPatient = patientService.registerPatient("Billing Patient", 40, "Female", "555-8888", "patient@billing.com", "Checkup");
    }

    @Test
    @DisplayName("Should auto-generate invoice with correct sum when consultation is completed")
    public void testGenerateInvoiceOnCompletion() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 14, 0);
        LocalDateTime end = start.plusMinutes(30);

        Appointment appt = schedulingService.bookAppointment(
                testPatient.getPatientId(), testDoctor.getDoctorId(), start, end, PriorityLevel.NORMAL, "Routine Consultation"
        );

        double extraCharges = 75.50; // e.g. blood work
        String notes = "Includes ECG & Blood Test";

        Invoice invoice = billingService.completeConsultationAndGenerateInvoice(appt.getAppointmentId(), extraCharges, notes);

        assertNotNull(invoice);
        assertTrue(invoice.getInvoiceId() > 0);
        assertEquals(200.0, invoice.getConsultationFee());
        assertEquals(75.50, invoice.getAdditionalCharges());
        assertEquals(275.50, invoice.getTotalAmount(), 0.001);
        assertEquals("PAID", invoice.getPaymentStatus());

        // Verify appointment status updated to COMPLETED
        Appointment updatedAppt = schedulingService.getDoctorDailyQueue(testDoctor.getDoctorId(), start.toLocalDate()).get(0);
        assertEquals(AppointmentStatus.COMPLETED, updatedAppt.getStatus());
    }
}
