package benchmark;

import experiment.ExperimentConfiguration;
import experiment.ExperimentResult;
import experiment.ExperimentRunner;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Runs comprehensive benchmarks and generates comparison reports.
 */
public class BenchmarkRunner {

    private final ExperimentConfiguration config;
    private final String outputDir;

    public BenchmarkRunner(ExperimentConfiguration config) {
        this.config = config;
        this.outputDir = config.getOutputDirectory();
    }

    /**
     * Run complete benchmark suite.
     */
    public BenchmarkReport runBenchmark() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  BENCHMARK SUITE");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println();

        // Run experiments
        ExperimentRunner runner = new ExperimentRunner(config);
        List<ExperimentResult> results = runner.runAll();

        // Analyze results
        AlgorithmComparator comparator = new AlgorithmComparator(results);

        AlgorithmComparator.ComparisonReport timeReport = comparator.compareByMiningTime();
        AlgorithmComparator.ComparisonReport memoryReport = comparator.compareByMemoryUsage();
        AlgorithmComparator.ComparisonReport patternsReport = comparator.compareByPatternsFound();

        // Create benchmark report
        BenchmarkReport report = new BenchmarkReport(results, timeReport, memoryReport, patternsReport);

        // Print to console
        System.out.println("\n" + report);

        // Save to file
        try {
            saveReport(report);
        } catch (IOException e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }

        return report;
    }

    /**
     * Save benchmark report to file.
     */
    private void saveReport(BenchmarkReport report) throws IOException {
        // Create output directory
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        // Generate filename with timestamp
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "benchmark_" + timestamp + ".txt";

        Path reportPath = outputPath.resolve(filename);

        // Write report
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportPath))) {
            writer.println(report);
        }

        System.out.println("\nReport saved to: " + reportPath);
    }

    /**
     * Complete benchmark report.
     */
    public static class BenchmarkReport {
        private final List<ExperimentResult> results;
        private final AlgorithmComparator.ComparisonReport timeReport;
        private final AlgorithmComparator.ComparisonReport memoryReport;
        private final AlgorithmComparator.ComparisonReport patternsReport;

        public BenchmarkReport(List<ExperimentResult> results,
                              AlgorithmComparator.ComparisonReport timeReport,
                              AlgorithmComparator.ComparisonReport memoryReport,
                              AlgorithmComparator.ComparisonReport patternsReport) {
            this.results = results;
            this.timeReport = timeReport;
            this.memoryReport = memoryReport;
            this.patternsReport = patternsReport;
        }

        public List<ExperimentResult> getResults() {
            return Collections.unmodifiableList(results);
        }

        public AlgorithmComparator.ComparisonReport getTimeReport() {
            return timeReport;
        }

        public AlgorithmComparator.ComparisonReport getMemoryReport() {
            return memoryReport;
        }

        public AlgorithmComparator.ComparisonReport getPatternsReport() {
            return patternsReport;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("═══════════════════════════════════════════════════\n");
            sb.append("  BENCHMARK REPORT\n");
            sb.append("═══════════════════════════════════════════════════\n\n");

            sb.append("Generated: ").append(LocalDateTime.now()).append("\n");
            sb.append("Total experiments: ").append(results.size()).append("\n");

            long successful = results.stream().filter(ExperimentResult::isSuccess).count();
            sb.append("Successful: ").append(successful).append("\n");
            sb.append("Failed: ").append(results.size() - successful).append("\n\n");

            sb.append(timeReport).append("\n");
            sb.append(memoryReport).append("\n");
            sb.append(patternsReport).append("\n");

            // Speedup analysis
            sb.append("═══════════════════════════════════════════════════\n");
            sb.append("  SPEEDUP ANALYSIS\n");
            sb.append("═══════════════════════════════════════════════════\n");

            Map<String, AlgorithmComparator.Statistics> timeStats = timeReport.getStatsByAlgorithm();
            if (timeStats.containsKey("baseline") && timeStats.containsKey("tufci")) {
                double baselineTime = timeStats.get("baseline").mean;
                double tufciTime = timeStats.get("tufci").mean;
                double speedup = baselineTime / tufciTime;
                sb.append(String.format("TUFCI vs Baseline: %.2fx speedup\n", speedup));
            }

            return sb.toString();
        }
    }
}
