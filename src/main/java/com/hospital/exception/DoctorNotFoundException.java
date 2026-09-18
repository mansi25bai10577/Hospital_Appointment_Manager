package com.hospital.exception;

public class DoctorNotFoundException extends HospitalException {
    public DoctorNotFoundException(String message) {
        super(message);
    }
}
