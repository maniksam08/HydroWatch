package service.real_time_alert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import service.real_time_alert.entity.AlertEntity;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Service
public class OutboundCallService {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundCallService.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;
    private final String agentId;
    private final String operatorPhone;
    private final String escalationPhone;

    public OutboundCallService(@Value("${bolna.api-url:https://api.bolna.ai}") String apiUrl,
                               @Value("${bolna.api-key:}") String apiKey,
                               @Value("${bolna.agent-id:}") String agentId,
                               @Value("${bolna.operator-phone:}") String operatorPhone,
                               @Value("${bolna.escalation-phone:}") String escalationPhone,
                               @Value("${bolna.enabled:false}") boolean enabled) {
        this.apiKey = apiKey;
        this.agentId = agentId;
        this.operatorPhone = operatorPhone;
        this.escalationPhone = escalationPhone;
        this.enabled = enabled;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }public boolean callOperator(AlertEntity alert, long occurrences, long windowMinutes) {
        return place(operatorPhone, alert, occurrences, windowMinutes,
                "You are calling the primary on-duty operator.");
    }

    public boolean isEscalationConfigured() {
        return enabled && !escalationPhone.isBlank();
    }

    public boolean callEscalationContact(AlertEntity alert, long occurrences, long windowMinutes) {
        return place(escalationPhone, alert, occurrences, windowMinutes,
                "The primary operator did not acknowledge this alert, so you are calling the escalation contact.");
    }

    private boolean place(String phone, AlertEntity alert, long occurrences, long windowMinutes, String callNote) {
        if (!enabled) {
            LOG.info("Voice calls disabled, skipping call for sensor {}", alert.getSensorId());
            return false;
        }
        if (apiKey.isBlank() || agentId.isBlank() || phone.isBlank()) {
            LOG.warn("Bolna is not fully configured (api key, agent id or phone missing)");
            return false;
        }

        Map<String, Object> body = Map.of(
                "agent_id", agentId,
                "recipient_phone_number", phone,
                "user_data", Map.of(
                        "sensor_id", String.valueOf(alert.getSensorId()),
                        "location", String.valueOf(alert.getLocation()),
                        "alert_reason", String.valueOf(alert.getReason()),
                        "alert_time", String.valueOf(alert.getCreatedAt()),
                        "occurrences", String.valueOf(occurrences),
                        "window_minutes", String.valueOf(windowMinutes),
                        "call_note", callNote
                )
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/call")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            LOG.info("Bolna call placed for sensor {}: {}", alert.getSensorId(), response);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to place Bolna call for sensor {}: {}", alert.getSensorId(), e.getMessage(), e);
            return false;
        }
    }
}
