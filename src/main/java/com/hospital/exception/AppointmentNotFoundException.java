package com.hospital.exception;

public class AppointmentNotFoundException extends HospitalException {
    public AppointmentNotFoundException(String message) {
        super(message);
    }
}
