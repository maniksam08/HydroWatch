package service.data_anomaly_detector.service;


import Pratham.model.RedisStreams;
import Pratham.model.SensorData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.springframework.data.redis.connection.stream.*;

@Service
public class SensorDataListener {
    private static final Logger LOG = LoggerFactory.getLogger(SensorDataListener.class);
    private static final String CONSUMER_NAME = "sensor-data-stream-consumer";
    private static final String CONSUMER_GROUP = "sensor-data-stream-group";

    private static final int MAX_RECORDS = 30;
    private static final Duration BLOCK_DURATION = Duration.ofSeconds(3);

    // Queue for passing data between threads
    private final BlockingQueue<SensorData> sensorDataQueue;
    private final ThreadPoolTaskExecutor asyncExecutor;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public SensorDataListener(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                              ThreadPoolTaskExecutor asyncExecutor,
                              BlockingQueue<SensorData> sensorDataQueue,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.asyncExecutor = asyncExecutor;
        this.sensorDataQueue = sensorDataQueue;
        this.objectMapper = objectMapper;

        createConsumerGroup();
    }

    @PostConstruct
    public void startListener() {
        asyncExecutor.execute(this::listenForSensorData);
    }

    public void listenForSensorData() {
        StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, String, String>> records = streamOps.read(
                        Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                        // Read up to max records, blocking for specified time if none are available
                        StreamReadOptions.empty().count(MAX_RECORDS).block(BLOCK_DURATION),
                        // Read from the last acknowledged record
                        StreamOffset.create(RedisStreams.WATER_QUALITY_STREAM, ReadOffset.lastConsumed())
                );

                for (MapRecord<String, String, String> record : records) {
                    String json = record.getValue().get(RedisStreams.SENSOR_DATA_KEY);
                    try {
                        SensorData sensorData = objectMapper.readValue(json, SensorData.class);
                        LOG.info("Received sensor data: {}", sensorData);

                        // Put the data into the queue
                        sensorDataQueue.put(sensorData);
                    } catch (JsonProcessingException e) {
                        LOG.error("Failed to parse sensor data: {}", json, e);
                    }
                }
                if (!records.isEmpty()) {
                    streamOps.acknowledge(RedisStreams.WATER_QUALITY_STREAM, CONSUMER_GROUP,
                            records.stream().map(Record::getId).toArray(RecordId[]::new));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void createConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(RedisStreams.WATER_QUALITY_STREAM, ReadOffset.from("0"), CONSUMER_GROUP);
            LOG.info("Consumer group '{}' created.", CONSUMER_GROUP);
        } catch (Exception e) {
            LOG.warn("Consumer group '{}' already exists.", CONSUMER_GROUP);
        }
    }
}
