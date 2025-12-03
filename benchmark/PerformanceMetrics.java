package benchmark;

import java.util.*;

/**
 * Collects and tracks performance metrics for algorithm execution.
 */
public class PerformanceMetrics {
    private final Map<String, Long> timings;
    private final Map<String, Long> counters;
    private final Map<String, Double> measurements;
    private long startTime;
    private long endTime;

    public PerformanceMetrics() {
        this.timings = new HashMap<>();
        this.counters = new HashMap<>();
        this.measurements = new HashMap<>();
    }

    /**
     * Start overall timing.
     */
    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * End overall timing.
     */
    public void end() {
        this.endTime = System.currentTimeMillis();
    }

    /**
     * Get total elapsed time.
     */
    public long getElapsedTime() {
        return endTime - startTime;
    }

    /**
     * Record a timing measurement.
     */
    public void recordTiming(String name, long milliseconds) {
        timings.put(name, milliseconds);
    }

    /**
     * Increment a counter.
     */
    public void incrementCounter(String name) {
        counters.put(name, counters.getOrDefault(name, 0L) + 1);
    }

    /**
     * Set a counter value.
     */
    public void setCounter(String name, long value) {
        counters.put(name, value);
    }

    /**
     * Record a measurement.
     */
    public void recordMeasurement(String name, double value) {
        measurements.put(name, value);
    }

    /**
     * Get a timing value.
     */
    public long getTiming(String name) {
        return timings.getOrDefault(name, 0L);
    }

    /**
     * Get a counter value.
     */
    public long getCounter(String name) {
        return counters.getOrDefault(name, 0L);
    }

    /**
     * Get a measurement value.
     */
    public double getMeasurement(String name) {
        return measurements.getOrDefault(name, 0.0);
    }

    /**
     * Get all timings.
     */
    public Map<String, Long> getAllTimings() {
        return Collections.unmodifiableMap(timings);
    }

    /**
     * Get all counters.
     */
    public Map<String, Long> getAllCounters() {
        return Collections.unmodifiableMap(counters);
    }

    /**
     * Get all measurements.
     */
    public Map<String, Double> getAllMeasurements() {
        return Collections.unmodifiableMap(measurements);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PerformanceMetrics[\n");
        sb.append("  Total Time: ").append(getElapsedTime()).append("ms\n");

        if (!timings.isEmpty()) {
            sb.append("  Timings:\n");
            timings.forEach((name, value) ->
                sb.append("    ").append(name).append(": ").append(value).append("ms\n"));
        }

        if (!counters.isEmpty()) {
            sb.append("  Counters:\n");
            counters.forEach((name, value) ->
                sb.append("    ").append(name).append(": ").append(value).append("\n"));
        }

        if (!measurements.isEmpty()) {
            sb.append("  Measurements:\n");
            measurements.forEach((name, value) ->
                sb.append("    ").append(name).append(": ").append(String.format("%.4f", value)).append("\n"));
        }

        sb.append("]");
        return sb.toString();
    }
}
