package com.hospital.model;

import com.hospital.util.DateTimeUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single scheduled appointment between a patient and a doctor.
 *
 * <p>An {@code Appointment} moves through a simple lifecycle:</p>
 * <pre>
 *   SCHEDULED  ──►  COMPLETED   (consultation finished, invoice generated)
 *      │
 *      └──────────►  CANCELLED  (patient or clinic cancelled before the visit)
 * </pre>
 *
 * <p><strong>Priority handling</strong>: Every appointment has a {@link PriorityLevel}.
 * EMERGENCY appointments skip ahead of NORMAL ones in the doctor's daily queue.
 * This is enforced through the {@link #compareTo} implementation — when a list
 * of appointments is sorted, emergencies automatically rise to the top.</p>
 *
 * <p><strong>Display-only fields</strong>: {@code patientName} and {@code doctorName}
 * are not stored in the appointments table. They are populated by the service layer
 * after a JOIN-style lookup so the UI can display names without extra round-trips.</p>
 *
 * <p>Implements {@link Comparable} to drive priority queue sorting.</p>
 */
public class Appointment implements Comparable<Appointment> {

    // ── Core stored fields ───────────────────────────────────────────────────
    private int               appointmentId;    // database primary key
    private int               patientId;        // FK → patients.patient_id
    private int               doctorId;         // FK → doctors.doctor_id
    private Integer           slotId;           // FK → availability_slots.slot_id (nullable — ad-hoc bookings have no slot)
    private LocalDate         appointmentDate;  // date portion, kept separately for fast date-range queries
    private LocalDateTime     startTime;        // full timestamp of consultation start
    private LocalDateTime     endTime;          // full timestamp of consultation end
    private AppointmentStatus status;           // SCHEDULED | COMPLETED | CANCELLED
    private PriorityLevel     priority;         // NORMAL | EMERGENCY
    private String            reason;           // brief description of the visit
    private LocalDateTime     createdAt;        // when the booking record was first saved

    // ── Display-only fields (not persisted) ─────────────────────────────────
    // Populated at query time to avoid redundant DB calls in the UI layer.
    private String patientName;
    private String doctorName;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Default constructor — sets sensible defaults.
     * A new appointment is SCHEDULED and NORMAL priority until specified otherwise.
     */
    public Appointment() {
        this.status   = AppointmentStatus.SCHEDULED;
        this.priority = PriorityLevel.NORMAL;
    }

    /**
     * Full constructor used when reading an existing appointment from the database.
     *
     * @param appointmentId   the database-assigned primary key
     * @param patientId       the patient being seen
     * @param doctorId        the attending doctor
     * @param slotId          optional FK to the pre-defined availability slot (may be null)
     * @param appointmentDate the calendar date of the visit
     * @param startTime       the consultation's start timestamp
     * @param endTime         the consultation's expected end timestamp
     * @param status          current lifecycle state (SCHEDULED / COMPLETED / CANCELLED)
     * @param priority        NORMAL or EMERGENCY
     * @param reason          brief description of why the patient is coming in
     */
    public Appointment(int appointmentId, int patientId, int doctorId, Integer slotId,
                       LocalDate appointmentDate, LocalDateTime startTime, LocalDateTime endTime,
                       AppointmentStatus status, PriorityLevel priority, String reason) {
        this.appointmentId   = appointmentId;
        this.patientId       = patientId;
        this.doctorId        = doctorId;
        this.slotId          = slotId;
        this.appointmentDate = appointmentDate;
        this.startTime       = startTime;
        this.endTime         = endTime;
        // Defensive defaults: never leave status or priority as null.
        this.status          = status   != null ? status   : AppointmentStatus.SCHEDULED;
        this.priority        = priority != null ? priority : PriorityLevel.NORMAL;
        this.reason          = reason;
    }

    /**
     * Shorthand constructor for creating a brand-new SCHEDULED appointment.
     * The ID will be assigned by the database after saving.
     *
     * @param patientId       the patient being booked
     * @param doctorId        the attending doctor
     * @param slotId          optional link to a pre-defined slot
     * @param appointmentDate calendar date of the visit
     * @param startTime       consultation start
     * @param endTime         consultation end
     * @param priority        urgency level
     * @param reason          visit description
     */
    public Appointment(int patientId, int doctorId, Integer slotId, LocalDate appointmentDate,
                       LocalDateTime startTime, LocalDateTime endTime,
                       PriorityLevel priority, String reason) {
        this(/* id= */ 0, patientId, doctorId, slotId, appointmentDate,
             startTime, endTime, AppointmentStatus.SCHEDULED, priority, reason);
    }

    // =========================================================================
    // PRIORITY QUEUE SORT ORDER
    // =========================================================================

    /**
     * Defines the natural ordering for appointments in a doctor's daily queue.
     *
     * <p>Rule: EMERGENCY appointments always appear before NORMAL ones.
     * Within the same priority tier, appointments are ordered by their start time
     * (earliest first).</p>
     *
     * <p>This is used by {@link java.util.Collections#sort} when building the
     * doctor's daily queue in {@link com.hospital.service.SchedulingService}.</p>
     *
     * @param other the other appointment to compare against
     * @return negative if this appointment should appear first in the queue
     */
    @Override
    public int compareTo(Appointment other) {
        // Different priorities: EMERGENCY always wins and appears first.
        if (this.priority != other.priority) {
            return this.priority == PriorityLevel.EMERGENCY ? -1 : 1;
        }
        // Same priority: sort by who arrives earlier.
        return this.startTime.compareTo(other.startTime);
    }

    // =========================================================================
    // GETTERS AND SETTERS
    // =========================================================================

    public int getAppointmentId()                          { return appointmentId; }
    public void setAppointmentId(int appointmentId)        { this.appointmentId = appointmentId; }

    public int getPatientId()                              { return patientId; }
    public void setPatientId(int patientId)                { this.patientId = patientId; }

    public int getDoctorId()                               { return doctorId; }
    public void setDoctorId(int doctorId)                  { this.doctorId = doctorId; }

    /** Returns {@code null} for ad-hoc appointments that were not linked to a predefined slot. */
    public Integer getSlotId()                             { return slotId; }
    public void setSlotId(Integer slotId)                  { this.slotId = slotId; }

    public LocalDate getAppointmentDate()                  { return appointmentDate; }
    public void setAppointmentDate(LocalDate date)         { this.appointmentDate = date; }

    public LocalDateTime getStartTime()                    { return startTime; }
    public void setStartTime(LocalDateTime startTime)      { this.startTime = startTime; }

    public LocalDateTime getEndTime()                      { return endTime; }
    public void setEndTime(LocalDateTime endTime)          { this.endTime = endTime; }

    public AppointmentStatus getStatus()                   { return status; }
    public void setStatus(AppointmentStatus status)        { this.status = status; }

    public PriorityLevel getPriority()                     { return priority; }
    public void setPriority(PriorityLevel priority)        { this.priority = priority; }

    public String getReason()                              { return reason; }
    public void setReason(String reason)                   { this.reason = reason; }

    public LocalDateTime getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)      { this.createdAt = createdAt; }

    /** Display-only: patient's full name, populated by the service layer after a lookup. */
    public String getPatientName()                         { return patientName; }
    public void setPatientName(String patientName)         { this.patientName = patientName; }

    /** Display-only: doctor's full name, populated by the service layer after a lookup. */
    public String getDoctorName()                          { return doctorName; }
    public void setDoctorName(String doctorName)           { this.doctorName = doctorName; }

    // =========================================================================
    // TOSTRING — HUMAN-READABLE SUMMARY
    // =========================================================================

    /**
     * Returns a one-line summary suitable for logging and console output.
     * Example: {@code Appointment[ID=7, Patient=Alice (ID:3), Doctor=Dr. Smith (ID:1), Time=2026-10-01 09:00, Status=SCHEDULED, Priority=NORMAL]}
     */
    @Override
    public String toString() {
        return String.format(
                "Appointment[ID=%d, Patient=%s (ID:%d), Doctor=%s (ID:%d), Time=%s, Status=%s, Priority=%s]",
                appointmentId,
                patientName  != null ? patientName  : String.valueOf(patientId),  patientId,
                doctorName   != null ? doctorName   : String.valueOf(doctorId),   doctorId,
                DateTimeUtils.formatDateTime(startTime),
                status,
                priority);
    }
}
