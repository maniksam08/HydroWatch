package service.data_anomaly_detector.service;

import Pratham.model.RedisStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class RedisStreamCleanerService {
    private static final Logger LOG = LoggerFactory.getLogger(RedisStreamCleanerService.class);

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${stream.clean.water-quality.max-records}")
    private long waterQualityMaxRecords;

    @Value("${stream.clean.anomalies.max-records}")
    private long anomaliesMaxRecords;

    public RedisStreamCleanerService(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRateString = "${stream.clean.water-quality.interval}")
    public void cleanWaterQualityStream() {
        cleanOldRecords(RedisStreams.WATER_QUALITY_STREAM, waterQualityMaxRecords);
    }

    @Scheduled(fixedRateString = "${stream.clean.anomalies.interval}")
    public void cleanAnomaliesStream() {
        cleanOldRecords(RedisStreams.ANOMALIES_STREAM, anomaliesMaxRecords);
    }

    public void cleanOldRecords(String streamName, long maxRecords) {
        try {
            StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();
            Long removedCount = streamOps.trim(streamName, maxRecords);
            LOG.info("Removed {} old records from stream '{}'", removedCount, streamName);
        } catch (Exception e) {
            LOG.error("Failed to clean stream '{}': {}", streamName, e.getMessage(), e);
        }
    }
}
