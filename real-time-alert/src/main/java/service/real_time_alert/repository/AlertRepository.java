package service.real_time_alert.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import service.real_time_alert.entity.AlertEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRepository extends JpaRepository<AlertEntity, Long> {

    List<AlertEntity> findByAcknowledgedFalseAndEscalatedAtIsNullAndCallPlacedAtBetween(LocalDateTime from, LocalDateTime to);

    long countBySensorIdAndCreatedAtAfter(String sensorId, LocalDateTime since);

    boolean existsByCallPlacedAtAfter(LocalDateTime since);
    long countByCallPlacedAtAfter(LocalDateTime since);

    List<AlertEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select a.sensorId as sensorId, count(a) as alertCount " +
            "from AlertEntity a where a.createdAt > :since group by a.sensorId")
    List<SensorAlertCount> countPerSensorSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("update AlertEntity a set a.acknowledged = true where a.acknowledged = false")
    int acknowledgeAllOpen();

    interface SensorAlertCount {
        String getSensorId();
        long getAlertCount();
    }
}
