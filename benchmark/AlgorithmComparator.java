package benchmark;

import experiment.ExperimentResult;
import java.util.*;

/**
 * Compares performance of different algorithms.
 * Generates statistical analysis and comparison reports.
 */
public class AlgorithmComparator {

    private final List<ExperimentResult> results;

    public AlgorithmComparator(List<ExperimentResult> results) {
        this.results = new ArrayList<>(results);
    }

    /**
     * Compare algorithms by mining time.
     */
    public ComparisonReport compareByMiningTime() {
        Map<String, List<Long>> timesByAlgo = groupByAlgorithm(
            result -> result.getMiningTime()
        );

        return createReport("Mining Time (ms)", timesByAlgo);
    }

    /**
     * Compare algorithms by memory usage.
     */
    public ComparisonReport compareByMemoryUsage() {
        Map<String, List<Long>> memoryByAlgo = groupByAlgorithm(
            result -> result.getMemoryUsed()
        );

        return createReport("Memory Usage (bytes)", memoryByAlgo);
    }

    /**
     * Compare algorithms by number of patterns found.
     */
    public ComparisonReport compareByPatternsFound() {
        Map<String, List<Long>> patternsByAlgo = groupByAlgorithm(
            result -> (long) result.getPatternsFound()
        );

        return createReport("Patterns Found", patternsByAlgo);
    }

    /**
     * Group results by algorithm and extract metric.
     */
    private Map<String, List<Long>> groupByAlgorithm(
            java.util.function.Function<ExperimentResult, Long> metricExtractor) {

        Map<String, List<Long>> grouped = new HashMap<>();

        for (ExperimentResult result : results) {
            if (!result.isSuccess()) continue;

            String algo = result.getConfiguration().getAlgorithmName();
            grouped.computeIfAbsent(algo, k -> new ArrayList<>())
                   .add(metricExtractor.apply(result));
        }

        return grouped;
    }

    /**
     * Create comparison report with statistics.
     */
    private ComparisonReport createReport(String metricName,
                                         Map<String, List<Long>> dataByAlgo) {
        ComparisonReport report = new ComparisonReport(metricName);

        for (Map.Entry<String, List<Long>> entry : dataByAlgo.entrySet()) {
            String algo = entry.getKey();
            List<Long> values = entry.getValue();

            Statistics stats = computeStatistics(values);
            report.addAlgorithmStats(algo, stats);
        }

        return report;
    }

    /**
     * Compute statistics for a list of values.
     */
    private Statistics computeStatistics(List<Long> values) {
        if (values.isEmpty()) {
            return new Statistics(0, 0, 0, 0, 0);
        }

        // Sort for median and percentiles
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        long median = sorted.get(sorted.size() / 2);

        // Standard deviation
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        return new Statistics(mean, median, min, max, stdDev);
    }

    /**
     * Statistical measures.
     */
    public static class Statistics {
        public final double mean;
        public final long median;
        public final long min;
        public final long max;
        public final double stdDev;

        public Statistics(double mean, long median, long min, long max, double stdDev) {
            this.mean = mean;
            this.median = median;
            this.min = min;
            this.max = max;
            this.stdDev = stdDev;
        }

        @Override
        public String toString() {
            return String.format("mean=%.2f, median=%d, min=%d, max=%d, stdDev=%.2f",
                               mean, median, min, max, stdDev);
        }
    }

    /**
     * Comparison report for a specific metric.
     */
    public static class ComparisonReport {
        private final String metricName;
        private final Map<String, Statistics> statsByAlgorithm;

        public ComparisonReport(String metricName) {
            this.metricName = metricName;
            this.statsByAlgorithm = new HashMap<>();
        }

        public void addAlgorithmStats(String algorithm, Statistics stats) {
            statsByAlgorithm.put(algorithm, stats);
        }

        public String getMetricName() {
            return metricName;
        }

        public Map<String, Statistics> getStatsByAlgorithm() {
            return Collections.unmodifiableMap(statsByAlgorithm);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══════════════════════════════════════════════════\n");
            sb.append("  ").append(metricName).append("\n");
            sb.append("═══════════════════════════════════════════════════\n");

            for (Map.Entry<String, Statistics> entry : statsByAlgorithm.entrySet()) {
                sb.append(String.format("%-15s: %s\n", entry.getKey(), entry.getValue()));
            }

            return sb.toString();
        }
    }
}
