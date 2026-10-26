package service.real_time_alert.service;

import Pratham.model.Anomaly;
import Pratham.model.RedisStreams;
import Pratham.model.SensorData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RedisStreamListener {
    private static final Logger LOG = LoggerFactory.getLogger(RedisStreamListener.class);

    private static final String ANOMALY_CONSUMER_NAME = "realtime-anomalies-stream-consumer";
    private static final String ANOMALY_CONSUMER_GROUP = "realtime-anomalies-stream-group";

    private static final String SENSOR_CONSUMER_NAME = "realtime-sensor-stream-consumer";
    private static final String SENSOR_CONSUMER_GROUP = "realtime-sensor-stream-group";

    private static final Duration BLOCK_DURATION = Duration.ofSeconds(5);
    private static final int MAX_RECORDS = 30;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisStreamListener(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                               SimpMessagingTemplate messagingTemplate,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;

        createConsumerGroups();
    }

    @PostConstruct
    public void startListening() {
        new Thread(this::listenToAnomaliesStream).start();
        new Thread(this::listenToSensorDataStream).start();
    }

    private void listenToAnomaliesStream() {
        StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, String, String>> records = streamOps.read(
                        Consumer.from(ANOMALY_CONSUMER_GROUP, ANOMALY_CONSUMER_NAME),
                        StreamReadOptions.empty().count(MAX_RECORDS).block(BLOCK_DURATION),
                        StreamOffset.create(RedisStreams.ANOMALIES_STREAM, ReadOffset.lastConsumed())
                );

                if (records != null) {
                    for (MapRecord<String, String, String> record : records) {
                        String json = record.getValue().get(RedisStreams.ANOMALY_DATA_KEY);
                        try {
                            Anomaly anomaly = objectMapper.readValue(json, Anomaly.class);
                            LOG.info("Received anomaly data: {}", anomaly);

                            messagingTemplate.convertAndSend("/topic/anomalies", formatAnomalyMessage(anomaly));
                        } catch (JsonProcessingException e) {
                            LOG.error("Failed to parse anomaly data: {}", json, e);
                        }
                    }
                    if (!records.isEmpty()) {
                        streamOps.acknowledge(RedisStreams.ANOMALIES_STREAM, ANOMALY_CONSUMER_GROUP,
                                records.stream().map(Record::getId).toArray(RecordId[]::new));
                    }
                }
            } catch (Exception e) {
                LOG.error("Error reading from anomalies stream", e);
            }
        }
    }

    private void listenToSensorDataStream() {
        StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, String, String>> records = streamOps.read(
                        Consumer.from(SENSOR_CONSUMER_GROUP, SENSOR_CONSUMER_NAME),
                        StreamReadOptions.empty().count(MAX_RECORDS).block(BLOCK_DURATION),
                        StreamOffset.create(RedisStreams.WATER_QUALITY_STREAM, ReadOffset.lastConsumed())
                );

                if (records != null) {
                    for (MapRecord<String, String, String> record : records) {
                        String json = record.getValue().get(RedisStreams.SENSOR_DATA_KEY);
                        try {
                            SensorData data = objectMapper.readValue(json, SensorData.class);
                            messagingTemplate.convertAndSend("/topic/sensor-data", data);
                        } catch (JsonProcessingException e) {
                            LOG.error("Failed to parse sensor data: {}", json, e);
                        }
                    }
                    if (!records.isEmpty()) {
                        streamOps.acknowledge(RedisStreams.WATER_QUALITY_STREAM, SENSOR_CONSUMER_GROUP,
                                records.stream().map(Record::getId).toArray(RecordId[]::new));
                    }
                }
            } catch (Exception e) {
                LOG.error("Error reading from sensor data stream", e);
            }
        }
    }

    private void createConsumerGroups() {
        try {
            redisTemplate.opsForStream().createGroup(RedisStreams.ANOMALIES_STREAM, ReadOffset.from("0"), ANOMALY_CONSUMER_GROUP);
            LOG.info("Consumer group '{}' created.", ANOMALY_CONSUMER_GROUP);
        } catch (Exception e) {
            LOG.warn("Consumer group '{}' already exists.", ANOMALY_CONSUMER_GROUP);
        }

        try {
            redisTemplate.opsForStream().createGroup(RedisStreams.WATER_QUALITY_STREAM, ReadOffset.from("0"), SENSOR_CONSUMER_GROUP);
            LOG.info("Consumer group '{}' created.", SENSOR_CONSUMER_GROUP);
        } catch (Exception e) {
            LOG.warn("Consumer group '{}' already exists.", SENSOR_CONSUMER_GROUP);
        }
    }

    private String formatAnomalyMessage(Anomaly anomaly) {
        return String.format("%s (Sensor: %s, location: %s) - %s.",
                anomaly.createdAt().format(formatter),
                anomaly.sensorId(),
                anomaly.location(),
                anomaly.reason());
    }
}