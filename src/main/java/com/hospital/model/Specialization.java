package com.hospital.model;

/**
 * Medical specializations supported by doctors in the clinic.
 */
public enum Specialization {
    GENERAL_PRACTICE("General Practice"),
    CARDIOLOGY("Cardiology"),
    DERMATOLOGY("Dermatology"),
    PEDIATRICS("Pediatrics"),
    NEUROLOGY("Neurology"),
    ORTHOPEDICS("Orthopedics"),
    GYNECOLOGY("Gynecology"),
    OPHTHALMOLOGY("Ophthalmology");

    private final String displayName;

    Specialization(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Specialization fromString(String text) {
        if (text == null) return GENERAL_PRACTICE;
        for (Specialization s : Specialization.values()) {
            if (s.name().equalsIgnoreCase(text.trim()) || s.displayName.equalsIgnoreCase(text.trim())) {
                return s;
            }
        }
        return GENERAL_PRACTICE;
    }
}
