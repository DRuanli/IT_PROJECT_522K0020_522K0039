package experiment;

import baseline.SimpleTopKMiner;
import config.MiningConfiguration;
import core.AbstractFrequentItemsetMiner;
import core.TUFCIAlgorithm;
import database.UncertainDatabase;
import factory.MinerFactory;
import model.Pattern;

import java.util.*;

/**
 * Orchestrates mining experiments with different configurations.
 * Handles timing, memory measurement, and result collection.
 */
public class ExperimentRunner {

    private final ExperimentConfiguration config;
    private final List<ExperimentResult> results;

    public ExperimentRunner(ExperimentConfiguration config) {
        this.config = config;
        this.results = new ArrayList<>();
    }

    /**
     * Run all experiments defined in configuration.
     */
    public List<ExperimentResult> runAll() {
        List<MiningConfiguration> miningConfigs = config.generateConfigurations();

        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  EXPERIMENT RUNNER");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Total configurations: " + miningConfigs.size());
        System.out.println("Runs per configuration: " + config.getNumRuns());
        System.out.println("Total runs: " + (miningConfigs.size() * config.getNumRuns()));
        System.out.println();

        int runNumber = 0;
        for (MiningConfiguration miningConfig : miningConfigs) {
            System.out.println("─────────────────────────────────────────────────");
            System.out.println("Configuration " + (++runNumber) + "/" + miningConfigs.size());
            System.out.println(miningConfig);
            System.out.println("─────────────────────────────────────────────────");

            // Run multiple times and take average
            List<ExperimentResult> runResults = new ArrayList<>();
            for (int i = 0; i < config.getNumRuns(); i++) {
                System.out.println("  Run " + (i + 1) + "/" + config.getNumRuns() + "...");
                ExperimentResult result = runSingle(miningConfig);
                runResults.add(result);
                results.add(result);

                if (result.isSuccess()) {
                    System.out.println("  ✓ Completed in " + result.getMiningTime() + "ms");
                } else {
                    System.out.println("  ✗ Failed: " + result.getErrorMessage());
                }
            }

            // Print average statistics
            printAverageStats(runResults);
            System.out.println();
        }

        return results;
    }

    /**
     * Run a single mining experiment.
     */
    private ExperimentResult runSingle(MiningConfiguration config) {
        ExperimentResult.Builder resultBuilder = new ExperimentResult.Builder()
            .configuration(config);

        try {
            // Force garbage collection before measurement
            System.gc();
            long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Load database
            long startLoad = System.currentTimeMillis();
            UncertainDatabase database = UncertainDatabase.loadFromFile(config.getDatabasePath());
            long loadTime = System.currentTimeMillis() - startLoad;

            // Create miner based on algorithm name
            AbstractFrequentItemsetMiner miner = createMiner(config, database);

            // Run mining
            long startMine = System.currentTimeMillis();
            List<Pattern> patterns = miner.mine();
            long miningTime = System.currentTimeMillis() - startMine;

            // Measure memory
            long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = memAfter - memBefore;

            // Build result
            resultBuilder
                .loadTime(loadTime)
                .miningTime(miningTime)
                .totalTime(loadTime + miningTime)
                .memoryUsed(memoryUsed)
                .patternsFound(patterns.size())
                .topPatterns(patterns)
                .success(true);

        } catch (Exception e) {
            resultBuilder
                .success(false)
                .errorMessage(e.getMessage());
        }

        return resultBuilder.build();
    }

    /**
     * Create miner instance based on algorithm name.
     */
    private AbstractFrequentItemsetMiner createMiner(MiningConfiguration config,
                                                     UncertainDatabase database) {
        String algo = config.getAlgorithmName().toLowerCase();

        switch (algo) {
            case "tufci":
                return new TUFCIAlgorithm(database, config.getMinsup(),
                                         config.getTau(), config.getK());

            case "simple":
            case "baseline":
                return new SimpleTopKMiner(database, config.getMinsup(),
                                          config.getTau(), config.getK());

            default:
                // Default to TUFCI
                return MinerFactory.createMiner(database, config.getMinsup(),
                                               config.getTau(), config.getK());
        }
    }

    /**
     * Print average statistics across multiple runs.
     */
    private void printAverageStats(List<ExperimentResult> runs) {
        List<ExperimentResult> successful = runs.stream()
            .filter(ExperimentResult::isSuccess)
            .toList();

        if (successful.isEmpty()) {
            System.out.println("  No successful runs");
            return;
        }

        double avgMiningTime = successful.stream()
            .mapToLong(ExperimentResult::getMiningTime)
            .average()
            .orElse(0.0);

        double avgMemory = successful.stream()
            .mapToLong(ExperimentResult::getMemoryUsed)
            .average()
            .orElse(0.0);

        System.out.println("  Average mining time: " + String.format("%.2f", avgMiningTime) + "ms");
        System.out.println("  Average memory: " + String.format("%.2f", avgMemory / (1024 * 1024)) + "MB");
        System.out.println("  Patterns found: " + successful.get(0).getPatternsFound());
    }

    /**
     * Get all experiment results.
     */
    public List<ExperimentResult> getResults() {
        return Collections.unmodifiableList(results);
    }
}
