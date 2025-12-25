import core.TUFCI;
import database.UncertainDatabase;
import factory.MinerFactory;
import model.Pattern;

import java.io.IOException;
import java.util.List;

/**
 * Simple Test Program for TUFCI Algorithm
 *
 * <p>This program demonstrates how to run the TUFCI (Top-K Uncertain Frequent Closed Itemset)
 * mining algorithm on an uncertain database.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * java Test &lt;database_file&gt; [tau] [k]
 *
 * Arguments:
 *   database_file : Path to the uncertain database file (required)
 *   tau          : Probability threshold (optional, default: 0.7)
 *   k            : Number of top patterns to mine (optional, default: 5)
 * </pre>
 *
 * <p><b>Example:</b></p>
 * <pre>
 * java Test data/database.txt 0.7 10
 * </pre>
 *
 * @author Your Name
 * @version 1.0
 */
public class Test {

    /**
     * Main entry point for the TUFCI test program.
     *
     * <p><b>Execution Steps:</b></p>
     * <ol>
     *   <li>Parse command-line arguments (database file, tau, k)</li>
     *   <li>Load the uncertain database from file</li>
     *   <li>Create TUFCI miner instance</li>
     *   <li>Run the mining algorithm</li>
     *   <li>Display the results</li>
     * </ol>
     *
     * @param args Command-line arguments: [database_file] [tau] [k]
     * @throws IOException If the database file cannot be read
     */
    public static void main(String[] args) throws IOException {
        // ==================== Step 1: Parse Arguments ====================

        // Check if database file is provided
        if (args.length < 1) {
            printUsage();
            return;
        }

        // Get database file path (required)
        String dbFile = args[0];

        // Get tau (probability threshold) - default: 0.7
        double tau = args.length > 1 ? Double.parseDouble(args[1]) : 0.7;

        // Get k (number of top patterns) - default: 5
        int k = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        // Print configuration
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║          TUFCI: Top-K Uncertain Frequent Closed           ║");
        System.out.println("║              Itemset Mining Algorithm                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Database file     : " + dbFile);
        System.out.println("  Tau (threshold)   : " + tau);
        System.out.println("  K (top patterns)  : " + k);
        System.out.println();

        // ==================== Step 2: Load Database ====================

        System.out.println("Loading database...");
        UncertainDatabase database = UncertainDatabase.loadFromFile(dbFile);

        System.out.println("  Transactions : " + database.size());
        System.out.println("  Vocabulary   : " + database.getVocabulary().size() + " unique items");
        System.out.println();

        // ==================== Step 3: Create TUFCI Miner ====================

        System.out.println("Creating TUFCI miner...");
        TUFCI miner = MinerFactory.createMiner(database, tau, k);
        System.out.println("  Algorithm: TUFCI (with all pruning strategies enabled)");
        System.out.println();

        // ==================== Step 4: Run Mining Algorithm ====================

        System.out.println("Starting mining process...");
        System.out.println("─".repeat(65));

        // Record start time to measure performance
        long startTime = System.currentTimeMillis();

        // Execute the mining algorithm
        // This runs the three-phase TUFCI algorithm:
        //   Phase 1: Compute all 1-itemsets
        //   Phase 2: Initialize data structures and check closure
        //   Phase 3: Canonical mining to find top-k closed itemsets
        List<Pattern> results = miner.mine();

        // Record end time
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        System.out.println("─".repeat(65));
        System.out.println("Mining completed!");
        System.out.println("  Execution time: " + executionTime + " ms");
        System.out.println("  Patterns found: " + results.size());
        System.out.println();

        // ==================== Step 5: Display Results ====================

        printResults(results, k);
    }

    /**
     * Prints the mining results in a formatted table.
     *
     * <p>Displays the top patterns with their rank, itemset, support, and probability.
     * The table is limited to showing a reasonable number of patterns for readability.</p>
     *
     * @param results The list of patterns returned by the mining algorithm
     * @param k The number of top patterns requested
     */
    private static void printResults(List<Pattern> results, int k) {
        // Check if any patterns were found
        if (results.isEmpty()) {
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║                    No Patterns Found                      ║");
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("Suggestions:");
            System.out.println("  • Try lowering the tau threshold");
            System.out.println("  • Try increasing k value");
            System.out.println("  • Check if your database has enough transactions");
            return;
        }

        // Determine how many patterns to display (max 50 for readability)
        int displayCount = Math.min(results.size(), 50);

        // Print header
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                     Mining Results                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Top " + displayCount + " Patterns:");
        System.out.println();

        // Print table header
        System.out.printf("%-4s  %-40s  %8s  %12s%n",
            "Rank", "Itemset", "Support", "Probability");
        System.out.println("─".repeat(70));

        // Print each pattern
        for (int i = 0; i < displayCount; i++) {
            Pattern p = results.get(i);

            /**
             * For each pattern, we display:
             * - Rank: Position in the top-k (1-based)
             * - Itemset: The set of items (e.g., {milk, bread})
             * - Support: Expected number of transactions containing this itemset
             * - Probability: Likelihood of appearing in at least one transaction
             */
            System.out.printf("%-4d  %-40s  %8d  %12.4f%n",
                i + 1,                              // Rank (1-based index)
                p.itemset.toStringWithCodec(),      // Itemset as readable string
                p.support,                          // Expected support
                p.probability                       // Probability
            );
        }

        // Show indicator if there are more patterns
        if (results.size() > displayCount) {
            System.out.println("─".repeat(70));
            System.out.println("... and " + (results.size() - displayCount) + " more patterns");
        }

        System.out.println();

        // Print summary statistics
        System.out.println("Summary:");
        System.out.println("  Total patterns found   : " + results.size());
        System.out.println("  Patterns displayed     : " + displayCount);

        if (!results.isEmpty()) {
            Pattern topPattern = results.get(0);
            System.out.println("  Highest support        : " + topPattern.support);
            System.out.println("  Top pattern            : " + topPattern.itemset.toStringWithCodec());
        }

        System.out.println();
    }

    /**
     * Prints usage information for the program.
     *
     * <p>This is displayed when the program is run without required arguments.</p>
     */
    private static void printUsage() {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║          TUFCI: Top-K Uncertain Frequent Closed           ║");
        System.out.println("║              Itemset Mining Algorithm                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java Test <database_file> [tau] [k]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  database_file : Path to the uncertain database file (required)");
        System.out.println("  tau          : Probability threshold (optional, default: 0.7)");
        System.out.println("  k            : Number of top patterns to mine (optional, default: 5)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java Test data/database.txt");
        System.out.println("  java Test data/database.txt 0.7");
        System.out.println("  java Test data/database.txt 0.7 10");
        System.out.println();
    }
}
