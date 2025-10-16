package org.ft.services;

import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LogWriterService {

    // Method to write a log entry into a date-specific file
    public void writeLog(String logMessage, String filename, String path) {
        // Get today's date
        LocalDate today = LocalDate.now();
        String formattedDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // Create file name using date (e.g., log-YYYY-MM-DD.txt)
        String fileName = filename + "_" + formattedDate + ".txt";

        // Check path is existing, if not create it
        File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdirs(); // Create the directory if it does not exist
        }

        // Write to the file
        try {
            // Open the file in append mode
            File file = new File(path + fileName);
            // Check if file exists, create a new one if it doesn't
            if (!file.exists()) {
                file.createNewFile();
            }

            // Use BufferedWriter to write to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                // Append the log message to the file
                writer.write(convertToTimeStamp() + ": " + logMessage);
                writer.newLine(); // Add a new line after the message
            }
        } catch (IOException e) {
            // Handle any IO exceptions that may occur
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    private String convertToTimeStamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
