package service.real_time_alert.service;

import Pratham.model.Anomaly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import service.real_time_alert.entity.AlertEntity;
import service.real_time_alert.repository.AlertRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {
    private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository repository;
    private final OutboundCallService outboundCallService;
    private final long cooldownSeconds;
    private final long maxCallAgeSeconds;
    private final long escalationSeconds;
    private final long maxCallsPerHour;

    public AlertService(AlertRepository repository,
                        OutboundCallService outboundCallService,
                        @Value("${alert.cooldown-seconds:600}") long cooldownSeconds,
                        @Value("${alert.max-call-age-seconds:300}") long maxCallAgeSeconds,
                        @Value("${alert.escalation-seconds:120}") long escalationSeconds,
                        @Value("${alert.max-calls-per-hour:2}") long maxCallsPerHour) {
        this.repository = repository;
        this.outboundCallService = outboundCallService;
        this.cooldownSeconds = cooldownSeconds;
        this.maxCallAgeSeconds = maxCallAgeSeconds;
        this.escalationSeconds = escalationSeconds;
        this.maxCallsPerHour = maxCallsPerHour;
    }

    public void process(Anomaly anomaly) {
        try {
            AlertEntity alert = repository.save(new AlertEntity(anomaly));
            LocalDateTime now = LocalDateTime.now();

            if (alert.getCreatedAt().isBefore(now.minusSeconds(maxCallAgeSeconds))) {
                LOG.info("Alert {} is too old for a call, skipping", alert.getId());
                return;
            }
            if (repository.existsByCallPlacedAtAfter(now.minusSeconds(cooldownSeconds))) {
                LOG.debug("A call was placed recently, no call for alert {}", alert.getId());
                return;
            }
            if (repository.countByCallPlacedAtAfter(now.minusHours(1)) >= maxCallsPerHour) {
                LOG.info("Hourly call limit ({}) reached, no call for alert {}", maxCallsPerHour, alert.getId());
                return;
            }
            if (outboundCallService.callOperator(alert, occurrencesInWindow(alert.getSensorId(), now), windowMinutes())) {
                alert.setCallPlacedAt(LocalDateTime.now());
                repository.save(alert);
            }
        } catch (Exception e) {
            LOG.error("Failed to process anomaly for sensor {}: {}", anomaly.sensorId(), e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${alert.escalation-check-ms:15000}")
    public void escalateUnacknowledged() {
        if (!outboundCallService.isEscalationConfigured()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            List<AlertEntity> due = repository.findByAcknowledgedFalseAndEscalatedAtIsNullAndCallPlacedAtBetween(
                    now.minusSeconds(cooldownSeconds), now.minusSeconds(escalationSeconds));
            for (AlertEntity alert : due) {
                if (outboundCallService.callEscalationContact(
                        alert, occurrencesInWindow(alert.getSensorId(), now), windowMinutes())) {
                    alert.setEscalatedAt(LocalDateTime.now());
                    repository.save(alert);
                }
            }
        } catch (Exception e) {
            LOG.error("Escalation check failed: {}", e.getMessage(), e);
        }
    }

    public long windowMinutes() {
        return Math.max(1, (cooldownSeconds + 59) / 60);
    }

    public List<AlertEntity> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 20))));
    }

    public List<AlertRepository.SensorAlertCount> countsInWindow() {
        return repository.countPerSensorSince(LocalDateTime.now().minusSeconds(cooldownSeconds));
    }

    @Transactional
    public int acknowledgeAll() {
        return repository.acknowledgeAllOpen();
    }

    private long occurrencesInWindow(String sensorId, LocalDateTime now) {
        return repository.countBySensorIdAndCreatedAtAfter(sensorId, now.minusSeconds(cooldownSeconds));
    }
}
