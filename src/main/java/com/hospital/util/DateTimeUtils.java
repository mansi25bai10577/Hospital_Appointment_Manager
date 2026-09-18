package com.hospital.util;

import com.hospital.exception.ValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility functions for date and time parsing, formatting, and validation.
 */
public class DateTimeUtils {
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public static LocalDate parseDate(String dateStr) throws ValidationException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new ValidationException("Date string cannot be empty.");
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid date format '" + dateStr + "'. Expected format: YYYY-MM-DD (e.g. 2026-09-20).");
        }
    }

    public static LocalTime parseTime(String timeStr) throws ValidationException {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new ValidationException("Time string cannot be empty.");
        }
        try {
            return LocalTime.parse(timeStr.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid time format '" + timeStr + "'. Expected format: HH:mm in 24h (e.g. 09:30, 14:00).");
        }
    }

    public static LocalDateTime parseDateTime(String dateTimeStr) throws ValidationException {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new ValidationException("Date-time string cannot be empty.");
        }
        String cleanStr = dateTimeStr.trim().replace("T", " ");
        try {
            return LocalDateTime.parse(cleanStr, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid date-time format '" + dateTimeStr + "'. Expected format: YYYY-MM-DD HH:mm.");
        }
    }

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    public static String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMATTER) : "";
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : "";
    }
}
