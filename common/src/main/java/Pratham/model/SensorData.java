package Pratham.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record SensorData (
        String sensorId,
        SensorType type,
        double value,
        String location,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp
) {
    public SensorData(String sensorId, SensorType type, double value, String location) {
        this(sensorId, type, value, location, LocalDateTime.now());
    }
}