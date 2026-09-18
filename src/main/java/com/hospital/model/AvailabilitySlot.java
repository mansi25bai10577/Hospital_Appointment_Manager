package com.hospital.model;

import com.hospital.util.DateTimeUtils;
import java.time.LocalDateTime;

/**
 * Represents a doctor's availability slot — a specific window of time during which
 * a doctor is open to seeing patients.
 *
 * <p>Think of an {@code AvailabilitySlot} like a single empty box on a paper calendar:
 * it has a start time, an end time, and a flag indicating whether it has been claimed
 * by a patient yet ({@link #isBooked}).</p>
 *
 * <p>Slots are created by an administrator when setting up a doctor's schedule.
 * When a patient is booked into a matching slot, {@code isBooked} is flipped to
 * {@code true} so the slot cannot be double-booked.</p>
 *
 * <p>This class also contains the core interval-overlap helper ({@link #overlapsWith}),
 * which is used by the scheduling engine during conflict detection.</p>
 *
 * <p>Implements {@link Comparable} so lists of slots can be sorted by start time
 * without extra boilerplate.</p>
 */
public class AvailabilitySlot implements Comparable<AvailabilitySlot> {

    // ── Fields ──────────────────────────────────────────────────────────────
    private int           slotId;     // primary key assigned by the database
    private int           doctorId;   // which doctor owns this time window
    private LocalDateTime startTime;  // when the window opens (inclusive)
    private LocalDateTime endTime;    // when the window closes (exclusive)
    private boolean       isBooked;   // true once a patient appointment occupies this slot

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Default constructor required by the DAO mapping layer. */
    public AvailabilitySlot() {}

    /**
     * Full constructor used when reading an existing slot from the database.
     *
     * @param slotId    the database-assigned primary key
     * @param doctorId  the owning doctor's ID
     * @param startTime slot opening time
     * @param endTime   slot closing time
     * @param isBooked  whether a patient has already claimed this slot
     */
    public AvailabilitySlot(int slotId, int doctorId, LocalDateTime startTime,
                             LocalDateTime endTime, boolean isBooked) {
        this.slotId    = slotId;
        this.doctorId  = doctorId;
        this.startTime = startTime;
        this.endTime   = endTime;
        this.isBooked  = isBooked;
    }

    /**
     * Convenience constructor for creating a brand-new slot that hasn't been saved yet.
     * The ID will be assigned by the database after saving.
     *
     * @param doctorId  the owning doctor's ID
     * @param startTime slot opening time
     * @param endTime   slot closing time
     */
    public AvailabilitySlot(int doctorId, LocalDateTime startTime, LocalDateTime endTime) {
        this(/* slotId = */ 0, doctorId, startTime, endTime, /* isBooked = */ false);
    }

    // =========================================================================
    // CORE BUSINESS LOGIC — OVERLAP DETECTION
    // =========================================================================

    /**
     * Tests whether this slot's time window overlaps with the given interval.
     *
     * <p>Two time intervals [A_start, A_end) and [B_start, B_end) overlap
     * <em>if and only if</em>:</p>
     * <pre>
     *   A_start &lt; B_end   AND   B_start &lt; A_end
     * </pre>
     *
     * <p>In plain language: they overlap if each one starts before the other one ends.
     * This handles all overlap shapes:</p>
     * <ul>
     *   <li>Complete overlap (one contains the other)</li>
     *   <li>Left overlap (other starts before this and ends inside this)</li>
     *   <li>Right overlap (other starts inside this and ends after this)</li>
     *   <li>Exact match (same start and end)</li>
     * </ul>
     *
     * <p>Back-to-back slots do NOT overlap: a slot from 09:00–09:30 and
     * another from 09:30–10:00 are considered adjacent, not overlapping.</p>
     *
     * @param otherStart start of the interval to test against this slot
     * @param otherEnd   end of the interval to test against this slot
     * @return {@code true} if the intervals share any time in common
     */
    public boolean overlapsWith(LocalDateTime otherStart, LocalDateTime otherEnd) {
        // This slot starts before the other ends, AND the other starts before this ends.
        return this.startTime.isBefore(otherEnd) && otherStart.isBefore(this.endTime);
    }

    // =========================================================================
    // GETTERS AND SETTERS
    // =========================================================================

    public int getSlotId()                  { return slotId; }
    public void setSlotId(int slotId)       { this.slotId = slotId; }

    public int getDoctorId()                { return doctorId; }
    public void setDoctorId(int doctorId)   { this.doctorId = doctorId; }

    public LocalDateTime getStartTime()                  { return startTime; }
    public void setStartTime(LocalDateTime startTime)    { this.startTime = startTime; }

    public LocalDateTime getEndTime()                    { return endTime; }
    public void setEndTime(LocalDateTime endTime)        { this.endTime = endTime; }

    public boolean isBooked()               { return isBooked; }
    public void setBooked(boolean booked)   { isBooked = booked; }

    // =========================================================================
    // COMPARABLE — NATURAL SORT ORDER
    // =========================================================================

    /**
     * Compares slots by start time so lists can be sorted chronologically.
     * When two slots share the same start time, the one with the lower ID comes first.
     */
    @Override
    public int compareTo(AvailabilitySlot other) {
        int timeComparison = this.startTime.compareTo(other.startTime);
        if (timeComparison != 0) return timeComparison;
        return Integer.compare(this.slotId, other.slotId);
    }

    /**
     * Human-readable summary of this slot for logging and debug output.
     * Example: {@code Slot[ID=3, DoctorID=1, Time=2026-10-01 09:00 to 09:30, Booked=false]}
     */
    @Override
    public String toString() {
        return String.format("Slot[ID=%d, DoctorID=%d, Time=%s to %s, Booked=%b]",
                slotId,
                doctorId,
                DateTimeUtils.formatDateTime(startTime),
                DateTimeUtils.formatTime(endTime.toLocalTime()),
                isBooked);
    }
}
