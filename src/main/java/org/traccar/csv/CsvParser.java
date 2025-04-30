package org.traccar.csv;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CsvParser {
    @Data
    public static class GpsPoint {
        private double longitude;
        private double latitude;
        private double speed; // km/h
        private double course; // degrees
        
        public GpsPoint(double latitude, double longitude) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.speed = 0.0;
            this.course = 0.0;
        }
        
        public GpsPoint(double latitude, double longitude, double speed, double course) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.speed = speed;
            this.course = course;
        }
    }
    
    
    /**
     * Parses a CSV file containing GPS coordinates
     * Expected format: latitude,longitude,speed,course
     * 
     * @param filePath Path to the CSV file
     * @return List of GPS points
     */
    public List<CsvParser.GpsPoint> parseCsvFile(String filePath) {
        List<CsvParser.GpsPoint> points = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            
            line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                try {
                    String[] values = line.split(",");
                    if (values.length >= 2) {
                        double latitude = Double.parseDouble(values[7].trim());
                        double longitude = Double.parseDouble(values[8].trim());
                        double altitude = Double.parseDouble(values[9].trim());
                        double speed = Double.parseDouble(values[10].trim());
                        double course = Double.parseDouble(values[11].trim());
                        
                        points.add(new CsvParser.GpsPoint(latitude, longitude, speed, course));
                    }
                } catch (NumberFormatException e) {
                    log.warn("Skipping invalid line in CSV: {}", line);
                }
            }
            
            log.info("Successfully parsed {} GPS points from CSV file", points.size());
        } catch (IOException e) {
            log.error("Error reading CSV file: {}", e.getMessage());
        }
        
        return points;
    }
}
