package service.real_time_alert.service;

import Pratham.model.SensorData;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LatestReadingsStore {
    private final ConcurrentHashMap<String, SensorData> latest = new ConcurrentHashMap<>();

    public void update(SensorData data) {
        latest.put(data.sensorId() + ":" + data.type(), data);
    }

    public Collection<SensorData> all() {
        return latest.values();
    }
}
