package Pratham.model;

public class RedisStreams {

    public static final String WATER_QUALITY_STREAM = "water_quality_stream";
    public static final String ANOMALIES_STREAM = "anomalies_stream";

    // Keys for data entries in Redis Streams
    public static final String SENSOR_DATA_KEY = "sensorData";
    public static final String ANOMALY_DATA_KEY = "anomalyData";
}
