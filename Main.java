import benchmark.BenchmarkRunner;
import config.MiningConfiguration;
import experiment.ExperimentConfiguration;
import experiment.ExperimentRunner;
import factory.MinerFactory;
import core.TUFCIAlgorithm;
import database.UncertainDatabase;
import model.Pattern;
import util.Constants;
import util.MiningObserver;

import java.util.List;

/**
 * Main entry point for TUFCI Closure-Aware Top-K Miner
 *
 * Supports three execution modes:
 * 1. Single mining run: java Main mine <database_file> [minsup] [tau] [k]
 * 2. Experiment mode: java Main experiment
 * 3. Benchmark mode: java Main benchmark
 *
 * Usage: java Main <mode> [args...]
 * 
 * @author Dang Nguyen Le
 */
public class Main {

    public static void main(String[] args) {
        // Print banner
        printBanner();

        // Parse mode
        if (args.length < 1) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();

        try {
            switch (mode) {
                case "mine":
                case "run":
                    runSingleMining(args);
                    break;

                case "experiment":
                case "exp":
                    runExperiment(args);
                    break;

                case "benchmark":
                case "bench":
                    runBenchmark(args);
                    break;

                case "help":
                case "-h":
                case "--help":
                    printUsage();
                    break;

                default:
                    // Backward compatibility: assume single mining with database file
                    runLegacyMode(args);
                    break;
            }
        } catch (Exception e) {
            System.err.println("\n Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Run single mining operation.
     */
    private static void runSingleMining(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Error: Database file required");
            printUsage();
            return;
        }

        String dbFile = args[1];
        int minsup = args.length > 2 ? Integer.parseInt(args[2]) : Constants.DEFAULT_MINSUP;
        double tau = args.length > 3 ? Double.parseDouble(args[3]) : Constants.DEFAULT_TAU;
        int k = args.length > 4 ? Integer.parseInt(args[4]) : Constants.DEFAULT_K;

        runMining(dbFile, minsup, tau, k);
    }

    /**
     * Legacy mode for backward compatibility.
     */
    private static void runLegacyMode(String[] args) throws Exception {
        String dbFile = args[0];
        int minsup = args.length > 1 ? Integer.parseInt(args[1]) : Constants.DEFAULT_MINSUP;
        double tau = args.length > 2 ? Double.parseDouble(args[2]) : Constants.DEFAULT_TAU;
        int k = args.length > 3 ? Integer.parseInt(args[3]) : Constants.DEFAULT_K;

        runMining(dbFile, minsup, tau, k);
    }

    /**
     * Run experiment suite.
     */
    private static void runExperiment(String[] args) {
        // Example experiment configuration
        ExperimentConfiguration config = new ExperimentConfiguration.Builder()
            .addDataset("data/chess_uncertain.txt")
            .addAlgorithm("TUFCI")
            .addAlgorithm("baseline")
            .addMinsup(5)
            .addMinsup(10)
            .addTau(0.7)
            .addTau(0.8)
            .addK(10)
            .numRuns(3)
            .outputDirectory("results")
            .build();

        ExperimentRunner runner = new ExperimentRunner(config);
        runner.runAll();
    }

    /**
     * Run benchmark suite.
     */
    private static void runBenchmark(String[] args) {
        // Example benchmark configuration
        ExperimentConfiguration config = new ExperimentConfiguration.Builder()
            .addDataset("data/chess_uncertain.txt")
            .addAlgorithm("TUFCI")
            .addAlgorithm("baseline")
            .addMinsup(5)
            .addTau(0.7)
            .addK(10)
            .numRuns(5)
            .outputDirectory("benchmark_results")
            .build();

        BenchmarkRunner benchmark = new BenchmarkRunner(config);
        benchmark.runBenchmark();
    }

    private static void runMining(String dbFile, int minsup, double tau, int k) throws Exception {
        System.out.println("Parameters:");
        System.out.println("  Database: " + dbFile);
        System.out.println("  Min Support: " + minsup);
        System.out.println("  Tau: " + tau);
        System.out.println("  K: " + k);
        System.out.println();

        // Load database
        System.out.println("Loading database...");
        long startLoad = System.currentTimeMillis();
        UncertainDatabase database = UncertainDatabase.loadFromFile(dbFile);
        long loadTime = System.currentTimeMillis() - startLoad;

        System.out.println("Database loaded in " + loadTime + "ms:");
        System.out.println("  Transactions: " + database.size());
        System.out.println("  Vocabulary: " + database.getVocabulary().size());
        System.out.println();

        // Create miner
        System.out.println("Creating Closure-Aware Miner...");
        TUFCIAlgorithm miner = MinerFactory.createMiner(database, minsup, tau, k);

        // Add timing observer
        PhaseTimingObserver timingObserver = new PhaseTimingObserver();
        miner.addObserver(timingObserver);

        // Mine
        System.out.println("Mining started...");
        System.out.println("─────────────────────────────────────────────────────");
        long startMine = System.currentTimeMillis();
        List<Pattern> results = miner.mine();
        long mineTime = System.currentTimeMillis() - startMine;
        System.out.println("─────────────────────────────────────────────────────");

        // Print results
        printResults(results, mineTime, timingObserver);
    }

    private static void printResults(List<Pattern> results, long mineTime, PhaseTimingObserver observer) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  RESULTS");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Total mining time: " + mineTime + "ms");
        System.out.println();
        System.out.println("Phase breakdown:");
        System.out.println("  Phase 1 (Compute 1-itemsets): " + observer.getPhaseTime(1) + "ms");
        System.out.println("  Phase 2 (Initialize): " + observer.getPhaseTime(2) + "ms");
        System.out.println("  Phase 3 (Mining): " + observer.getPhaseTime(3) + "ms");
        System.out.println();
        System.out.println("Found " + results.size() + " closed patterns");
        System.out.println();

        if (results.isEmpty()) {
            System.out.println("No patterns found. Try lowering minsup or tau.");
            return;
        }

        // Print top patterns
        int displayCount = Math.min(results.size(), 50);
        System.out.println("Top " + displayCount + " patterns:");
        System.out.println();
        System.out.printf("%-4s %-40s %8s %12s%n", "Rank", "Itemset", "Support", "Probability");
        System.out.println("─".repeat(70));

        for (int i = 0; i < displayCount; i++) {
            Pattern p = results.get(i);
            System.out.printf("%-4d %-40s %8d %12.4f%n",
                i + 1,
                truncate(p.itemset.toStringWithCodec(), 40),
                p.support,
                p.probability
            );
        }

        if (results.size() > displayCount) {
            System.out.println("... and " + (results.size() - displayCount) + " more patterns");
        }

    }

    private static String truncate(String str, int maxLen) {
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  TUFCI - Closure-Aware Top-K Frequent Itemset Miner");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Algorithm: Priority Queue with Immediate Closure Checking");
        System.out.println("Guarantees: No threshold inflation, all results closed");
        System.out.println();
    }

    private static void printUsage() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  USAGE");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Three execution modes available:");
        System.out.println();
        System.out.println("1. SINGLE MINING:");
        System.out.println("   java Main mine <database_file> [minsup] [tau] [k]");
        System.out.println();
        System.out.println("   Arguments:");
        System.out.println("     database_file  Path to uncertain database file (required)");
        System.out.println("     minsup         Minimum support threshold (default: " +
                           Constants.DEFAULT_MINSUP + ")");
        System.out.println("     tau            Probability threshold 0<tau<=1 (default: " +
                           Constants.DEFAULT_TAU + ")");
        System.out.println("     k              Number of top patterns (default: " +
                           Constants.DEFAULT_K + ")");
        System.out.println();
        System.out.println("   Examples:");
        System.out.println("     java Main mine data/chess_uncertain.txt");
        System.out.println("     java Main mine data/chess_uncertain.txt 10 0.7 20");
        System.out.println();
        System.out.println("2. EXPERIMENT MODE:");
        System.out.println("   java Main experiment");
        System.out.println();
        System.out.println("   Run predefined experiment suite with multiple configurations.");
        System.out.println("   Results saved to 'results/' directory.");
        System.out.println();
        System.out.println("3. BENCHMARK MODE:");
        System.out.println("   java Main benchmark");
        System.out.println();
        System.out.println("   Run benchmark suite comparing different algorithms.");
        System.out.println("   Results saved to 'benchmark_results/' directory.");
        System.out.println();
        System.out.println("BACKWARD COMPATIBILITY:");
        System.out.println("   java Main <database_file> [minsup] [tau] [k]");
        System.out.println("   (Equivalent to 'mine' mode)");
        System.out.println();
    }

    /**
     * Observer to track phase timing
     */
    private static class PhaseTimingObserver implements MiningObserver {
        private final long[] phaseTimes = new long[4]; // Index 0 unused, 1-3 for phases
        private final String[] phaseDescriptions = new String[4];

        @Override
        public void onPhaseStart(int phase, String description) {
            if (phase >= 1 && phase <= 3) {
                phaseDescriptions[phase] = description;
            }
        }

        @Override
        public void onPhaseComplete(int phase, long durationMs) {
            if (phase >= 1 && phase <= 3) {
                phaseTimes[phase] = durationMs;
            }
        }

        @Override
        public void onPatternFound(Pattern pattern) {
            // Not needed for timing
        }

        @Override
        public void onCandidatePruned(String reason) {
            // Not needed for timing
        }

        public long getPhaseTime(int phase) {
            if (phase >= 1 && phase <= 3) {
                return phaseTimes[phase];
            }
            return 0;
        }

        public String getPhaseDescription(int phase) {
            if (phase >= 1 && phase <= 3) {
                return phaseDescriptions[phase];
            }
            return "";
        }
    }
}
