package com.hospital.exception;

/**
 * Base custom exception for all business and domain-specific errors
 * in the Hospital Appointment System.
 */
public class HospitalException extends Exception {
    public HospitalException(String message) {
        super(message);
    }

    public HospitalException(String message, Throwable cause) {
        super(message, cause);
    }
}
