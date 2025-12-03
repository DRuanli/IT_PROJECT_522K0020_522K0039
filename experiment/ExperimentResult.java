package experiment;

import config.MiningConfiguration;
import model.Pattern;
import java.util.*;

/**
 * Result of a single mining experiment run.
 * Captures all relevant metrics and outcomes.
 */
public class ExperimentResult {
    private final MiningConfiguration configuration;
    private final long loadTime;
    private final long miningTime;
    private final long totalTime;
    private final long memoryUsed;
    private final int patternsFound;
    private final List<Pattern> topPatterns;
    private final Map<String, Long> phaseTimings;
    private final Map<String, Object> additionalMetrics;
    private final boolean success;
    private final String errorMessage;

    private ExperimentResult(Builder builder) {
        this.configuration = builder.configuration;
        this.loadTime = builder.loadTime;
        this.miningTime = builder.miningTime;
        this.totalTime = builder.totalTime;
        this.memoryUsed = builder.memoryUsed;
        this.patternsFound = builder.patternsFound;
        this.topPatterns = Collections.unmodifiableList(new ArrayList<>(builder.topPatterns));
        this.phaseTimings = Collections.unmodifiableMap(new HashMap<>(builder.phaseTimings));
        this.additionalMetrics = Collections.unmodifiableMap(new HashMap<>(builder.additionalMetrics));
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    // Getters
    public MiningConfiguration getConfiguration() { return configuration; }
    public long getLoadTime() { return loadTime; }
    public long getMiningTime() { return miningTime; }
    public long getTotalTime() { return totalTime; }
    public long getMemoryUsed() { return memoryUsed; }
    public int getPatternsFound() { return patternsFound; }
    public List<Pattern> getTopPatterns() { return topPatterns; }
    public Map<String, Long> getPhaseTimings() { return phaseTimings; }
    public Map<String, Object> getAdditionalMetrics() { return additionalMetrics; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        if (!success) {
            return String.format("ExperimentResult[FAILED: %s]", errorMessage);
        }
        return String.format("ExperimentResult[algo=%s, time=%dms, patterns=%d]",
                           configuration.getAlgorithmName(), miningTime, patternsFound);
    }

    /**
     * Builder for ExperimentResult
     */
    public static class Builder {
        private MiningConfiguration configuration;
        private long loadTime;
        private long miningTime;
        private long totalTime;
        private long memoryUsed;
        private int patternsFound;
        private List<Pattern> topPatterns = new ArrayList<>();
        private Map<String, Long> phaseTimings = new HashMap<>();
        private Map<String, Object> additionalMetrics = new HashMap<>();
        private boolean success = true;
        private String errorMessage = null;

        public Builder configuration(MiningConfiguration config) {
            this.configuration = config;
            return this;
        }

        public Builder loadTime(long ms) {
            this.loadTime = ms;
            return this;
        }

        public Builder miningTime(long ms) {
            this.miningTime = ms;
            return this;
        }

        public Builder totalTime(long ms) {
            this.totalTime = ms;
            return this;
        }

        public Builder memoryUsed(long bytes) {
            this.memoryUsed = bytes;
            return this;
        }

        public Builder patternsFound(int count) {
            this.patternsFound = count;
            return this;
        }

        public Builder topPatterns(List<Pattern> patterns) {
            this.topPatterns = patterns;
            return this;
        }

        public Builder addPhaseTiming(String phase, long ms) {
            this.phaseTimings.put(phase, ms);
            return this;
        }

        public Builder addMetric(String name, Object value) {
            this.additionalMetrics.put(name, value);
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String message) {
            this.errorMessage = message;
            this.success = false;
            return this;
        }

        public ExperimentResult build() {
            if (configuration == null) {
                throw new IllegalArgumentException("Configuration is required");
            }
            return new ExperimentResult(this);
        }
    }
}
