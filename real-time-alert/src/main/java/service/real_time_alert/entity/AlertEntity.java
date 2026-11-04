package service.real_time_alert.entity;

import Pratham.model.Anomaly;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sensorId;
    private String location;

    @Column(length = 500)
    private String reason;

    private LocalDateTime createdAt;
    private boolean acknowledged;
    private LocalDateTime callPlacedAt;
    private LocalDateTime escalatedAt;

    protected AlertEntity() {
    }

    public AlertEntity(Anomaly anomaly) {
        this.sensorId = anomaly.sensorId();
        this.location = anomaly.location();
        this.reason = anomaly.reason();
        this.createdAt = anomaly.createdAt() != null ? anomaly.createdAt() : LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getSensorId() { return sensorId; }
    public String getLocation() { return location; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isAcknowledged() { return acknowledged; }
    public LocalDateTime getCallPlacedAt() { return callPlacedAt; }
    public void setCallPlacedAt(LocalDateTime callPlacedAt) { this.callPlacedAt = callPlacedAt; }

    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(LocalDateTime escalatedAt) { this.escalatedAt = escalatedAt; }
}
