package com.hospital.exception;

import com.hospital.model.AvailabilitySlot;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when a requested appointment booking or slot creation
 * conflicts with an existing doctor availability slot or appointment.
 */
public class SlotConflictException extends HospitalException {
    private final List<AvailabilitySlot> suggestedAlternatives;

    public SlotConflictException(String message) {
        super(message);
        this.suggestedAlternatives = Collections.emptyList();
    }

    public SlotConflictException(String message, List<AvailabilitySlot> suggestedAlternatives) {
        super(message);
        this.suggestedAlternatives = suggestedAlternatives != null ? suggestedAlternatives : Collections.emptyList();
    }

    public List<AvailabilitySlot> getSuggestedAlternatives() {
        return suggestedAlternatives;
    }
}
