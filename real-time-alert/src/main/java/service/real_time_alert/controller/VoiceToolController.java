package service.real_time_alert.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.real_time_alert.entity.AlertEntity;
import service.real_time_alert.rag.RagChatController;
import service.real_time_alert.service.AlertService;
import service.real_time_alert.service.LatestReadingsStore;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tools")
public class VoiceToolController {
    public record AlertView(Long id, String sensorId, String location, String reason,
                            String createdAt, boolean acknowledged) {
        static AlertView from(AlertEntity a) {
            return new AlertView(a.getId(), a.getSensorId(), a.getLocation(), a.getReason(),
                    String.valueOf(a.getCreatedAt()), a.isAcknowledged());
        }
    }

    public record SensorCount(String sensorId, long alertCount) {
    }

    public record AlertStatus(long windowMinutes, List<SensorCount> alertCountsInWindow,
                              List<AlertView> recentAlerts) {
    }

    public record ReadingView(String sensorId, String type, double value, String location, String timestamp) {
    }

    private final AlertService alertService;
    private final LatestReadingsStore readings;
    private final RagChatController ragChatController;

    public VoiceToolController(AlertService alertService,
                               LatestReadingsStore readings,
                               RagChatController ragChatController) {
        this.alertService = alertService;
        this.readings = readings;
        this.ragChatController = ragChatController;
    }

    @GetMapping("/alerts/status")
    public AlertStatus alertStatus() {
        return new AlertStatus(
                alertService.windowMinutes(),
                alertService.countsInWindow().stream()
                        .map(c -> new SensorCount(c.getSensorId(), c.getAlertCount())).toList(),
                alertService.recent(5).stream().map(AlertView::from).toList());
    }

    @GetMapping("/readings/latest")
    public List<ReadingView> latestReadings() {
        return readings.all().stream()
                .map(r -> new ReadingView(r.sensorId(), String.valueOf(r.type()), r.value(),
                        r.location(), String.valueOf(r.timestamp())))
                .toList();
    }

    @PostMapping("/alerts/acknowledge")
    public Map<String, Integer> acknowledge() {
        return Map.of("acknowledged", alertService.acknowledgeAll());
    }

    @PostMapping("/sop")
    public Map<String, String> sop(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");
        if (question.isBlank()) {
            return Map.of("answer", "No question was provided.");
        }
        String answer = ragChatController.askQuestion(Map.of("question",
                "Answer in at most three short sentences, for a phone call: " + question)).getBody();
        return Map.of("answer", String.valueOf(answer));
    }
}
