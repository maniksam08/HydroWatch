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
            reader = new BufferedReader(new InputStreamReader(new ClassPathResource("sensor-data.csv").getInputStream()));
            reader.readLine();
            reader.readLine();
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

            String[] columns = line.split(",", -1);

            double temperature = parseOrDefault(columns[9], lastTemp);
            double oxygen = parseOrDefault(columns[11], lastOxygen);
            double ph = parseOrDefault(columns[15], lastPh);

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