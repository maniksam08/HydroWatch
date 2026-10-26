package service.data_anomaly_detector.service;

import Pratham.model.Anomaly;
import Pratham.model.RedisStreams;
import Pratham.model.SensorData;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Service
public class AnomalyProcessorService {
    private static final Logger LOG = LoggerFactory.getLogger(AnomalyProcessorService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<SensorData> sensorDataQueue;
    private final ThreadPoolTaskExecutor asyncExecutor;

    public AnomalyProcessorService(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                                   ThreadPoolTaskExecutor asyncExecutor,
                                   ObjectMapper objectMapper,
                                   BlockingQueue<SensorData> sensorDataQueue) {
        this.redisTemplate = redisTemplate;
        this.asyncExecutor = asyncExecutor;
        this.objectMapper = objectMapper;
        this.sensorDataQueue = sensorDataQueue;
    }

    @PostConstruct
    public void startProcessor() {
        asyncExecutor.execute(this::process);
    }

    public void process() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Waiting for data to be added to the queue
                SensorData sensorData = sensorDataQueue.take();
                LOG.info("Processing sensor data: {}", sensorData);

                checkForAnomalies(sensorData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkForAnomalies(SensorData sensorData) {
        double value = sensorData.value();
        String message = null;

        switch (sensorData.type()) {
            case TEMPERATURE -> {
                if (value < 10.0) message = "Temperature is below normal by " + String.format("%.2f", 10.0 - value) + "°C";
                else if (value > 20.0) message = "Temperature is above normal by " + String.format("%.2f", value - 20.0) + "°C";
            }
            case PH -> {
                if (value < 6.0) message = "PH level is below normal by " + String.format("%.2f", 6.0 - value);
                else if (value > 8.0) message = "PH level is above normal by " + String.format("%.2f", value - 8.0);
            }
            case OXYGEN -> {
                if (value < 4.0) message = "Oxygen level is below normal by " + String.format("%.2f", 4.0 - value) + " units";
                else if (value > 10.0) message = "Oxygen level is above normal by " + String.format("%.2f", value - 10.0) + " units";
            }
        }

        if (message != null) {
            Anomaly anomaly = new Anomaly(sensorData.sensorId(), sensorData.location(), sensorData.timestamp(), message);
            LOG.info("Anomaly detected: {}", anomaly);
            sendAnomalyToStream(anomaly);
        }
    }

    private void sendAnomalyToStream(Anomaly anomaly){
        try {
            StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();
            // Convert sensor data to JSON format
            String dataJson = objectMapper.writeValueAsString(anomaly);

            // Send the data to Redis stream
            streamOps.add(RedisStreams.ANOMALIES_STREAM, Map.of(RedisStreams.ANOMALY_DATA_KEY, dataJson));
            LOG.info("Anomaly data was sent: {}", dataJson);
        } catch (Exception e) {
            LOG.error("Failed to serialize anomaly data: {}", e.getMessage(), e);
        }
    }
}
