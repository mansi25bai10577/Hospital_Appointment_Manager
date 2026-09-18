package com.hospital.service;

import com.hospital.dao.IAppointmentDAO;
import com.hospital.dao.IDoctorDAO;
import com.hospital.dao.IPatientDAO;
import com.hospital.exception.*;
import com.hospital.model.*;
import com.hospital.util.AppLogger;
import com.hospital.util.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SchedulingService is the most algorithmically rich class in the system.
 *
 * <p>It is responsible for three things:</p>
 * <ol>
 *   <li><strong>Availability slots</strong> — An admin defines windows of time when a
 *       doctor is available. This service validates those windows and saves them.</li>
 *
 *   <li><strong>Appointment booking with conflict detection</strong> — When a receptionist
 *       tries to book a patient into a time window, this service checks all existing
 *       appointments for that doctor and rejects the request if there is any overlap.
 *       The check runs in O(log N) time using a {@link TreeMap} rather than scanning
 *       every appointment one-by-one.</li>
 *
 *   <li><strong>Priority queueing</strong> — Doctors see EMERGENCY patients first,
 *       then NORMAL patients ordered by start time. This is handled by the natural
 *       ordering defined on the {@link Appointment} model.</li>
 * </ol>
 *
 * <p><strong>Why TreeMap for conflict detection?</strong><br>
 * A {@code TreeMap<LocalDateTime, Appointment>} keeps appointments sorted by start
 * time at zero extra cost. When we receive a proposed interval [newStart, newEnd),
 * we only need to check the appointment that starts just before {@code newStart}
 * (via {@code floorEntry}) and any appointments starting within the window
 * (via {@code subMap}). This avoids comparing against every booking.</p>
 */
public class SchedulingService {

    // ── DAO dependencies ────────────────────────────────────────────────────
    // The service never talks to the database directly.
    // It delegates all persistence work to the DAO layer.
    private final IDoctorDAO      doctorDAO;
    private final IPatientDAO     patientDAO;
    private final IAppointmentDAO appointmentDAO;

    /**
     * Creates a SchedulingService with the three DAOs it needs.
     *
     * @param doctorDAO      access to doctor profiles and their availability slots
     * @param patientDAO     access to patient records (used to verify patient exists before booking)
     * @param appointmentDAO access to saved appointments (used for conflict checking)
     */
    public SchedulingService(IDoctorDAO doctorDAO, IPatientDAO patientDAO, IAppointmentDAO appointmentDAO) {
        this.doctorDAO      = doctorDAO;
        this.patientDAO     = patientDAO;
        this.appointmentDAO = appointmentDAO;
    }

    // =========================================================================
    // PUBLIC API — AVAILABILITY SLOT MANAGEMENT
    // =========================================================================

    /**
     * Creates a new availability window during which a doctor can see patients.
     *
     * <p>Before saving, two guards are applied:</p>
     * <ul>
     *   <li>The slot cannot be in the past — you can't retroactively open a window.</li>
     *   <li>The slot cannot overlap with an existing slot for the same doctor —
     *       a doctor cannot be in two places at the same time.</li>
     * </ul>
     *
     * @param doctorId  the ID of the doctor this slot belongs to
     * @param startTime when the window opens
     * @param endTime   when the window closes (must be strictly after startTime)
     * @return the saved slot with its database-assigned ID
     * @throws DoctorNotFoundException if the doctor ID does not match any record
     * @throws SlotConflictException   if this window overlaps with an existing slot
     * @throws ValidationException     if the time range is invalid (e.g. end before start)
     */
    public AvailabilitySlot createAvailabilitySlot(int doctorId, LocalDateTime startTime, LocalDateTime endTime)
            throws DoctorNotFoundException, SlotConflictException, ValidationException, DatabaseException {

        // ── Guard 1: Basic time range sanity ────────────────────────────────
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ValidationException("End time must be strictly after start time.");
        }
        // Slots in the past don't make practical sense in a scheduling context.
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new ValidationException("Cannot create an availability slot in the past.");
        }

        // ── Guard 2: Doctor must exist ───────────────────────────────────────
        Doctor doctor = doctorDAO.findById(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException("Doctor ID " + doctorId + " not found."));

        // ── Guard 3: Overlap check against existing slots for this doctor ────
        // Build a sorted map of this doctor's current slots, then run the interval check.
        NavigableMap<LocalDateTime, AvailabilitySlot> existingSlotMap = buildDoctorSlotTreeMap(doctorId);
        checkForSlotConflict(existingSlotMap, startTime, endTime, /* excludeSlotId = */ null);

        // All guards passed — persist the new slot.
        AvailabilitySlot newSlot = new AvailabilitySlot(doctorId, startTime, endTime);
        AvailabilitySlot saved   = doctorDAO.addSlot(newSlot);

        AppLogger.info(String.format(
                "Created availability slot ID %d for Dr. %s (%s → %s)",
                saved.getSlotId(), doctor.getName(),
                DateTimeUtils.formatDateTime(startTime),
                DateTimeUtils.formatTime(endTime.toLocalTime())));

        return saved;
    }

    // =========================================================================
    // PUBLIC API — APPOINTMENT LIFECYCLE
    // =========================================================================

    /**
     * Books an appointment for a patient with a specific doctor at the requested time.
     *
     * <p><strong>Step-by-step flow:</strong></p>
     * <ol>
     *   <li>Verify the patient and doctor both exist in the database.</li>
     *   <li>Load all active (SCHEDULED) appointments for the doctor into a {@link TreeMap}.</li>
     *   <li>Run the interval overlap check — if the requested window collides with any
     *       existing appointment, throw {@link SlotConflictException} and include a list
     *       of suggested free alternative windows so the receptionist isn't left guessing.</li>
     *   <li>Optionally link the new appointment to an existing {@link AvailabilitySlot}
     *       if an exact match is found, and mark that slot as booked.</li>
     *   <li>Persist and return the saved appointment.</li>
     * </ol>
     *
     * @param patientId ID of the patient being booked
     * @param doctorId  ID of the attending doctor
     * @param startTime requested appointment start
     * @param endTime   requested appointment end
     * @param priority  {@code NORMAL} or {@code EMERGENCY}; EMERGENCY bubbles to top of queue
     * @param reason    brief description of the visit (e.g. "chest pain", "routine checkup")
     * @return the saved {@link Appointment} with its generated ID
     * @throws SlotConflictException if the requested time overlaps with an existing booking
     */
    public Appointment bookAppointment(int patientId, int doctorId,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       PriorityLevel priority, String reason)
            throws PatientNotFoundException, DoctorNotFoundException,
                   SlotConflictException, ValidationException, DatabaseException {

        // ── Step 1: Verify both parties exist ───────────────────────────────
        Patient patient = patientDAO.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException("Patient ID " + patientId + " not found."));
        Doctor doctor   = doctorDAO.findById(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException("Doctor ID " + doctorId + " not found."));

        // ── Step 2: Validate the time range ─────────────────────────────────
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ValidationException("Invalid time range. End time must be after start time.");
        }
        // Default to NORMAL if caller omitted priority (defensive programming).
        if (priority == null) {
            priority = PriorityLevel.NORMAL;
        }

        // ── Step 3: Build the doctor's appointment index and run conflict check ──
        //
        // We only look at SCHEDULED appointments — completed or cancelled ones are
        // no longer occupying the calendar.
        List<Appointment> activeAppointments = appointmentDAO.findActiveAppointmentsForDoctor(doctorId);
        NavigableMap<LocalDateTime, Appointment> appointmentIndex = buildAppointmentTreeMap(activeAppointments);

        Optional<Appointment> collision = findConflictingAppointment(appointmentIndex, startTime, endTime);

        if (collision.isPresent()) {
            // There is an overlap. Generate alternative suggestions to help the receptionist
            // immediately offer the patient the next available times.
            Appointment blockingAppointment = collision.get();
            List<AvailabilitySlot> alternatives = generateAlternativeSlots(doctorId, startTime, 30, 3);

            String conflictMessage = String.format(
                    "Slot Conflict: Requested time (%s – %s) overlaps with an existing %s appointment " +
                    "(ID: %d) already booked for Dr. %s.",
                    DateTimeUtils.formatTime(startTime.toLocalTime()),
                    DateTimeUtils.formatTime(endTime.toLocalTime()),
                    blockingAppointment.getPriority(),
                    blockingAppointment.getAppointmentId(),
                    doctor.getName());

            AppLogger.warning("Booking rejected — conflict: " + conflictMessage);
            throw new SlotConflictException(conflictMessage, alternatives);
        }

        // ── Step 4: Link to an existing AvailabilitySlot if one matches exactly ──
        //
        // If the admin pre-defined slots, we mark the matched one as booked.
        // If no exact slot exists (ad-hoc booking), we simply leave slotId as null.
        List<AvailabilitySlot> matchingSlots = doctorDAO.getDoctorSlotsInRange(doctorId, startTime, endTime);
        Integer linkedSlotId = null;
        for (AvailabilitySlot slot : matchingSlots) {
            boolean isExactMatch = !slot.isBooked()
                    && slot.getStartTime().equals(startTime)
                    && slot.getEndTime().equals(endTime);
            if (isExactMatch) {
                linkedSlotId = slot.getSlotId();
                doctorDAO.updateSlotStatus(slot.getSlotId(), /* booked = */ true);
                break;
            }
        }

        // ── Step 5: Persist and return ──────────────────────────────────────
        LocalDate appointmentDate = startTime.toLocalDate();
        Appointment newAppointment = new Appointment(
                patientId, doctorId, linkedSlotId, appointmentDate,
                startTime, endTime, priority, reason);

        Appointment saved = appointmentDAO.save(newAppointment);
        // Attach display names so callers can render them without extra DB calls.
        saved.setPatientName(patient.getName());
        saved.setDoctorName(doctor.getName());

        AppLogger.info(String.format(
                "Booked %s appointment #%d — Patient: %s | Doctor: %s | Time: %s",
                priority,
                saved.getAppointmentId(),
                patient.getName(),
                doctor.getName(),
                DateTimeUtils.formatDateTime(startTime)));

        return saved;
    }

    /**
     * Moves an existing appointment to a new time slot.
     *
     * <p>Rescheduling has one extra subtlety: we must exclude the appointment
     * being rescheduled from the conflict check — otherwise it would always
     * collide with itself.</p>
     *
     * @param appointmentId the ID of the appointment to move
     * @param newStart      the desired new start time
     * @param newEnd        the desired new end time
     * @return {@code true} if the database row was updated
     * @throws SlotConflictException if the new window conflicts with a different appointment
     * @throws ValidationException   if the appointment is not in SCHEDULED state
     */
    public boolean rescheduleAppointment(int appointmentId, LocalDateTime newStart, LocalDateTime newEnd)
            throws AppointmentNotFoundException, SlotConflictException, ValidationException, DatabaseException {

        // Fetch the appointment being rescheduled — it must exist and be SCHEDULED.
        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(
                        "Appointment ID " + appointmentId + " not found."));

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ValidationException(
                    "Only SCHEDULED appointments can be rescheduled. " +
                    "Current status: " + appointment.getStatus());
        }

        // Build the doctor's conflict map, but remove this appointment from it.
        // Without this exclusion, the appointment would always "conflict" with its own old time.
        List<Appointment> otherAppointments = appointmentDAO
                .findActiveAppointmentsForDoctor(appointment.getDoctorId())
                .stream()
                .filter(a -> a.getAppointmentId() != appointmentId) // exclude self
                .collect(Collectors.toList());

        NavigableMap<LocalDateTime, Appointment> appointmentIndex = buildAppointmentTreeMap(otherAppointments);
        Optional<Appointment> collision = findConflictingAppointment(appointmentIndex, newStart, newEnd);

        if (collision.isPresent()) {
            List<AvailabilitySlot> alternatives =
                    generateAlternativeSlots(appointment.getDoctorId(), newStart, 30, 3);
            throw new SlotConflictException(
                    "Cannot reschedule: the desired slot conflicts with another appointment.", alternatives);
        }

        // Free up the old slot before committing to the new time.
        if (appointment.getSlotId() != null) {
            doctorDAO.updateSlotStatus(appointment.getSlotId(), /* booked = */ false);
        }

        boolean success = appointmentDAO.reschedule(appointmentId, /* newSlotId = */ null, newStart, newEnd);
        if (success) {
            AppLogger.info(String.format(
                    "Appointment #%d rescheduled to %s", appointmentId, DateTimeUtils.formatDateTime(newStart)));
        }
        return success;
    }

    /**
     * Cancels an appointment and frees its time slot so other patients can use it.
     *
     * @param appointmentId the appointment to cancel
     * @return {@code true} if the cancellation was persisted
     * @throws ValidationException if the appointment is already cancelled
     */
    public boolean cancelAppointment(int appointmentId)
            throws AppointmentNotFoundException, ValidationException, DatabaseException {

        Appointment appointment = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(
                        "Appointment ID " + appointmentId + " not found."));

        // Prevent double-cancellations — they are confusing and clutter the log.
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ValidationException(
                    "Appointment ID " + appointmentId + " is already cancelled.");
        }

        boolean updated = appointmentDAO.updateStatus(appointmentId, AppointmentStatus.CANCELLED);

        // Release the linked availability slot so another patient can use the window.
        if (updated && appointment.getSlotId() != null) {
            doctorDAO.updateSlotStatus(appointment.getSlotId(), /* booked = */ false);
        }

        AppLogger.info("Cancelled appointment #" + appointmentId);
        return updated;
    }

    /**
     * Returns a doctor's appointment list for a given day, sorted for clinical use:
     * EMERGENCY cases appear first (regardless of their booked time), followed by
     * NORMAL cases sorted by ascending start time.
     *
     * <p>The sort is driven by the {@link Appointment#compareTo} implementation,
     * which encodes the EMERGENCY-first, then time-ordered rule.</p>
     *
     * @param doctorId the doctor whose queue to retrieve
     * @param date     the calendar date (appointments outside this date are excluded)
     * @return sorted list of appointments for the day
     */
    public List<Appointment> getDoctorDailyQueue(int doctorId, LocalDate date) throws DatabaseException {
        List<Appointment> dayAppointments = appointmentDAO.findByDoctorAndDate(doctorId, date);
        // Natural ordering: EMERGENCY first, then by start time (defined on the model).
        Collections.sort(dayAppointments);
        return dayAppointments;
    }

    // =========================================================================
    // PRIVATE — TREEMAP INTERVAL OVERLAP-DETECTION ALGORITHM
    // =========================================================================
    //
    // The core insight: two intervals [S1, E1) and [S2, E2) overlap
    // if and only if   S1 < E2  AND  S2 < E1.
    //
    // By indexing intervals in a TreeMap keyed by start time, we can
    // reach any potentially overlapping interval in O(log N) steps
    // instead of scanning all N intervals linearly.
    // =========================================================================

    /**
     * Loads all availability slots for a doctor into a {@link TreeMap}.
     *
     * <p>The TreeMap keeps slots sorted by their start time automatically,
     * which enables the O(log N) floor/ceiling lookups used in conflict detection.</p>
     */
    private NavigableMap<LocalDateTime, AvailabilitySlot> buildDoctorSlotTreeMap(int doctorId)
            throws DatabaseException {

        NavigableMap<LocalDateTime, AvailabilitySlot> slotMap = new TreeMap<>();
        for (AvailabilitySlot slot : doctorDAO.getDoctorSlots(doctorId)) {
            slotMap.put(slot.getStartTime(), slot);
        }
        return slotMap;
    }

    /**
     * Loads a list of appointments into a {@link TreeMap} keyed by start time.
     * Used both for booking conflict checks and for rescheduling conflict checks.
     */
    private NavigableMap<LocalDateTime, Appointment> buildAppointmentTreeMap(List<Appointment> appointments) {
        NavigableMap<LocalDateTime, Appointment> appointmentMap = new TreeMap<>();
        for (Appointment appt : appointments) {
            appointmentMap.put(appt.getStartTime(), appt);
        }
        return appointmentMap;
    }

    /**
     * Checks whether a proposed time window [proposedStart, proposedEnd) overlaps
     * any existing slot in the provided map, and throws if it does.
     *
     * <p>Two intervals overlap when: {@code existingStart < proposedEnd AND proposedStart < existingEnd}</p>
     *
     * <p>We only need to check two entries from the TreeMap:</p>
     * <ul>
     *   <li><strong>floor entry</strong>: the slot that starts at or just before {@code proposedStart}
     *       — it might extend into our window from the left.</li>
     *   <li><strong>ceiling entry</strong>: the slot that starts at or just after {@code proposedStart}
     *       — it might start inside our window.</li>
     * </ul>
     *
     * @param slotMap        the existing slots indexed by start time
     * @param proposedStart  the start of the candidate window
     * @param proposedEnd    the end of the candidate window
     * @param excludeSlotId  if non-null, skip this slot ID (used when editing an existing slot)
     * @throws SlotConflictException if any overlap is detected
     */
    private void checkForSlotConflict(NavigableMap<LocalDateTime, AvailabilitySlot> slotMap,
                                      LocalDateTime proposedStart,
                                      LocalDateTime proposedEnd,
                                      Integer excludeSlotId) throws SlotConflictException {

        // Check the slot that starts just before (or at) our proposed window.
        // Example: existing slot 09:00–09:30, proposed 09:15–09:45 → overlap.
        Map.Entry<LocalDateTime, AvailabilitySlot> floorEntry = slotMap.floorEntry(proposedStart);
        if (floorEntry != null) {
            AvailabilitySlot candidate = floorEntry.getValue();
            boolean isSelf = excludeSlotId != null && candidate.getSlotId() == excludeSlotId;
            if (!isSelf && candidate.overlapsWith(proposedStart, proposedEnd)) {
                throw new SlotConflictException(
                        "Time slot overlaps with existing slot starting at "
                        + DateTimeUtils.formatDateTime(candidate.getStartTime()));
            }
        }

        // Check the slot that starts just after (or at) our proposed window.
        // Example: existing slot 09:30–10:00, proposed 09:15–09:45 → overlap.
        Map.Entry<LocalDateTime, AvailabilitySlot> ceilingEntry = slotMap.ceilingEntry(proposedStart);
        if (ceilingEntry != null) {
            AvailabilitySlot candidate = ceilingEntry.getValue();
            boolean isSelf = excludeSlotId != null && candidate.getSlotId() == excludeSlotId;
            if (!isSelf && candidate.overlapsWith(proposedStart, proposedEnd)) {
                throw new SlotConflictException(
                        "Time slot overlaps with existing slot starting at "
                        + DateTimeUtils.formatDateTime(candidate.getStartTime()));
            }
        }
    }

    /**
     * Finds the first appointment that overlaps with the requested window [start, end).
     *
     * <p>Algorithm walkthrough:</p>
     * <ol>
     *   <li>Use {@code floorEntry(start)} to get the appointment that begins at or before
     *       {@code start}. If it hasn't ended yet when {@code start} arrives, they overlap.</li>
     *   <li>Use {@code subMap(start, end)} to retrieve all appointments beginning
     *       strictly inside the window. Any of those will overlap by definition.</li>
     * </ol>
     *
     * @return the first conflicting appointment, or {@link Optional#empty()} if the window is free
     */
    private Optional<Appointment> findConflictingAppointment(
            NavigableMap<LocalDateTime, Appointment> appointmentMap,
            LocalDateTime start,
            LocalDateTime end) {

        // Case A: An appointment that started before us might still be running when we begin.
        Map.Entry<LocalDateTime, Appointment> preceding = appointmentMap.floorEntry(start);
        if (preceding != null) {
            Appointment a = preceding.getValue();
            // If the existing appointment ends after our start, the windows overlap.
            if (a.getStartTime().isBefore(end) && start.isBefore(a.getEndTime())) {
                return Optional.of(a);
            }
        }

        // Case B: Any appointment that starts inside our window will overlap.
        for (Appointment a : appointmentMap.subMap(start, end).values()) {
            if (a.getStartTime().isBefore(end) && start.isBefore(a.getEndTime())) {
                return Optional.of(a);
            }
        }

        return Optional.empty(); // The window is clear — no conflicts found.
    }

    // =========================================================================
    // PUBLIC API — ALTERNATIVE SLOT SUGGESTIONS
    // =========================================================================

    /**
     * Generates up to {@code count} free time windows near {@code fromTime},
     * suggesting them to a receptionist when a booking attempt is rejected.
     *
     * <p><strong>How it works:</strong></p>
     * <ol>
     *   <li>Start scanning 15 minutes after the requested time.</li>
     *   <li>Snap the candidate to the nearest clean half-hour boundary (e.g. 09:30, 10:00)
     *       so suggestions look natural rather than odd offsets like "09:47".</li>
     *   <li>Only consider hours between 08:00 and 18:00 — no after-hours suggestions.</li>
     *   <li>For each candidate window of length {@code durationMinutes}, run the same
     *       conflict check. If the window is free, add it to the suggestion list.</li>
     *   <li>Advance by 30-minute increments until enough free windows are found
     *       or 24 hours of scanning have been exhausted.</li>
     * </ol>
     *
     * @param doctorId        the doctor for whom alternatives are being searched
     * @param fromTime        the originally requested (conflicting) time
     * @param durationMinutes the length each suggested slot should have
     * @param count           the maximum number of alternatives to return
     * @return a list of free {@link AvailabilitySlot} objects (not yet persisted)
     */
    public List<AvailabilitySlot> generateAlternativeSlots(int doctorId,
                                                            LocalDateTime fromTime,
                                                            int durationMinutes,
                                                            int count)
            throws DatabaseException {

        List<AvailabilitySlot> suggestions = new ArrayList<>();

        // Load existing appointments to check candidates against.
        NavigableMap<LocalDateTime, Appointment> appointmentIndex =
                buildAppointmentTreeMap(appointmentDAO.findActiveAppointmentsForDoctor(doctorId));

        // Begin scanning from 15 minutes after the conflicting window.
        LocalDateTime candidate = fromTime.plusMinutes(15);

        // Snap to a clean half-hour boundary so suggestions look tidy.
        int minute = candidate.getMinute();
        if (minute > 0 && minute < 30) {
            candidate = candidate.withMinute(30).withSecond(0).withNano(0);
        } else if (minute > 30) {
            candidate = candidate.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }

        // Scan forward in 30-minute steps, stopping after 48 steps (24 hours) at most.
        int scannedWindows = 0;
        while (suggestions.size() < count && scannedWindows < 48) {
            int hour = candidate.getHour();

            // Only suggest during reasonable clinic operating hours.
            if (hour >= 8 && hour < 18) {
                LocalDateTime candidateEnd = candidate.plusMinutes(durationMinutes);
                boolean windowIsFree = findConflictingAppointment(
                        appointmentIndex, candidate, candidateEnd).isEmpty();

                if (windowIsFree) {
                    // Create a placeholder slot (not saved to DB) for display purposes.
                    suggestions.add(new AvailabilitySlot(doctorId, candidate, candidateEnd));
                }
            }

            candidate = candidate.plusMinutes(30);
            scannedWindows++;
        }

        return suggestions;
    }
}
