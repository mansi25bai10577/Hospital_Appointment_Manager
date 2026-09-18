package com.hospital.ui;

import com.hospital.model.PriorityLevel;
import com.hospital.model.Specialization;
import com.hospital.util.DateTimeUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Scanner;

/**
 * Utility wrapper around System.in providing safe user input parsing,
 * prompt formatting, and validation error recovery.
 */
public class InputHandler {
    private final Scanner scanner;

    public InputHandler() {
        this.scanner = new Scanner(System.in);
    }

    public String readString(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public String readNonEmptyString(String prompt) {
        while (true) {
            String input = readString(prompt);
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("  [!] Input cannot be blank. Please try again.");
        }
    }

    public int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid integer format. Please enter a valid number.");
            }
        }
    }

    public int readIntRange(String prompt, int min, int max) {
        while (true) {
            int val = readInt(prompt);
            if (val >= min && val <= max) {
                return val;
            }
            System.out.printf("  [!] Value out of range. Please enter a number between %d and %d.\n", min, max);
        }
    }

    public double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid decimal format. Please enter a valid monetary amount.");
            }
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            String input = readString(prompt + " (YYYY-MM-DD): ");
            try {
                return DateTimeUtils.parseDate(input);
            } catch (Exception e) {
                System.out.println("  [!] " + e.getMessage());
            }
        }
    }

    public LocalTime readTime(String prompt) {
        while (true) {
            String input = readString(prompt + " (HH:mm in 24h, e.g. 09:30): ");
            try {
                return DateTimeUtils.parseTime(input);
            } catch (Exception e) {
                System.out.println("  [!] " + e.getMessage());
            }
        }
    }

    public LocalDateTime readDateTime(String prompt) {
        while (true) {
            String input = readString(prompt + " (YYYY-MM-DD HH:mm): ");
            try {
                return DateTimeUtils.parseDateTime(input);
            } catch (Exception e) {
                System.out.println("  [!] " + e.getMessage());
            }
        }
    }

    public Specialization readSpecialization() {
        System.out.println("\nSelect Specialization:");
        Specialization[] values = Specialization.values();
        for (int i = 0; i < values.length; i++) {
            System.out.printf("  %d. %s\n", i + 1, values[i].getDisplayName());
        }
        int choice = readIntRange("Choice (1-" + values.length + "): ", 1, values.length);
        return values[choice - 1];
    }

    public PriorityLevel readPriorityLevel() {
        System.out.println("\nSelect Priority Level:");
        System.out.println("  1. NORMAL (Standard booking)");
        System.out.println("  2. EMERGENCY (Priority queueing)");
        int choice = readIntRange("Choice (1-2): ", 1, 2);
        return choice == 2 ? PriorityLevel.EMERGENCY : PriorityLevel.NORMAL;
    }
}
