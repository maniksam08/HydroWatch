package service.sensor_data_collector.service;

import Pratham.model.RedisStreams;
import Pratham.model.SensorData;
import Pratham.model.SensorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Map;

@Service
public class SensorDataGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(SensorDataGenerator.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private BufferedReader reader;

    // Fallback values in case a row is missing data
    private double lastTemp = 20.0;
    private double lastPh = 7.0;
    private double lastOxygen = 6.0;

    public SensorDataGenerator(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            // Rename your downloaded file to sensor-data.csv and place in src/main/resources/
            reader = new BufferedReader(new InputStreamReader(new ClassPathResource("sensor-data.csv").getInputStream()));
            reader.readLine(); // Skip Row 1 (Headers)
            reader.readLine(); // Skip Row 2 (Units)
            LOG.info("CSV Reader successfully initialized. Skipped header and units rows.");
        } catch (Exception e) {
            LOG.error("Could not load CSV file. Ensure it is named 'sensor-data.csv' and is in the resources folder.", e);
        }
    }

    @Scheduled(fixedRate = 2000)
    public void readAndSendCsvRow() {
        if (reader == null) return;

        try {
            String line = reader.readLine();
            if (line == null) {
                LOG.info("End of CSV file reached.");
                return;
            }

            // Split by comma, preserving empty strings between commas
            String[] columns = line.split(",", -1);

            // Extract and clean data using the specific column indexes of your downloaded dataset
            // Index 9: Temperature, Index 11: Dissolved Oxygen, Index 15: pH
            double temperature = parseOrDefault(columns[9], lastTemp);
            double oxygen = parseOrDefault(columns[11], lastOxygen);
            double ph = parseOrDefault(columns[15], lastPh);

            // Update fallbacks for the next missing row
            lastTemp = temperature;
            lastOxygen = oxygen;
            lastPh = ph;

            sendData("sensor-1", SensorType.TEMPERATURE, temperature, "Brisbane-River");
            sendData("sensor-2", SensorType.PH, ph, "Brisbane-River");
            sendData("sensor-3", SensorType.OXYGEN, oxygen, "Brisbane-River");

        } catch (Exception e) {
            LOG.error("Error processing CSV row", e);
        }
    }

    // Helper method to safely parse numbers and handle empty/blank CSV columns
    private double parseOrDefault(String value, double defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void sendData(String sensorId, SensorType type, double value, String location) {
        StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();
        SensorData sensorData = new SensorData(sensorId, type, value, location);

        try {
            String dataJson = objectMapper.writeValueAsString(sensorData);
            streamOps.add(RedisStreams.WATER_QUALITY_STREAM, Map.of(RedisStreams.SENSOR_DATA_KEY, dataJson));
            LOG.info("Sent simulated CSV data: {}", dataJson);
        } catch (IOException e) {
            LOG.error("Failed to serialize sensor data: {}", e.getMessage());
        }
    }
}