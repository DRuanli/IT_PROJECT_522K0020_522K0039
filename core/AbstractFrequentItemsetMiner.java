package core;

import util.MiningObserver;
import database.UncertainDatabase;
import model.Pattern;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AbstractFrequentItemsetMiner - Template Method Pattern for mining algorithms.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * TEMPLATE METHOD PATTERN
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * The Template Method Pattern defines the skeleton of an algorithm in a base class,
 * letting subclasses override specific steps without changing structure.
 *
 * Structure:
 *   AbstractFrequentItemsetMiner (this class)
 *       │
 *       ├── mine()                    ← Template Method (final, defines skeleton)
 *       │     ├── computeFrequent1Itemsets()    ← Abstract (subclass implements)
 *       │     ├── initializeDataStructures()    ← Abstract (subclass implements)
 *       │     ├── performRecursiveMining()      ← Abstract (subclass implements)
 *       │     └── getTopKResults()              ← Abstract (subclass implements)
 *       │
 *       └── ClosureAwareTopKMiner (subclass)
 *             └── Implements all abstract methods
 *
 * Benefits:
 *   1. Code reuse: Common logic (timing, observers) in base class
 *   2. Inversion of control: Base class calls subclass methods
 *   3. Easy extension: Add new mining algorithms by subclassing
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * THREE-PHASE MINING ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Phase 1: COMPUTE FREQUENT 1-ITEMSETS
 *   - Scan database to find all single items with support ≥ minsup
 *   - Typically runs in parallel for performance
 *   - Output: List of frequent 1-itemset patterns
 *
 * Phase 2: INITIALIZE DATA STRUCTURES
 *   - Build priority queue, caches, and other structures
 *   - Prepare for main mining loop
 *   - May pre-compute 2-itemsets or other optimizations
 *
 * Phase 3: RECURSIVE MINING
 *   - Main mining loop using priority queue
 *   - Generate candidates, check closure, update Top-K heap
 *   - Continue until early termination or exhaustion
 *
 * @author Dang Nguyen Le
 */
public abstract class AbstractFrequentItemsetMiner {

    /**
     * The uncertain database to mine.
     * Contains transactions with probabilistic item occurrences.
     */
    protected UncertainDatabase database;

    /**
     * Minimum support threshold.
     * Patterns must have probabilistic support ≥ minsup to be considered.
     */
    protected int minsup;

    /**
     * Probability threshold τ (tau).
     * Pattern is frequent if P(support ≥ s) ≥ τ.
     */
    protected double tau;

    /**
     * Number of top patterns to return.
     * Mining finds the K patterns with highest support.
     */
    protected int k;

    /**
     * List of observers monitoring mining progress.
     * Uses CopyOnWriteArrayList for thread safety during parallel Phase 1.
     */
    protected List<MiningObserver> observers;

    /**
     * Constructor with parameter validation.
     *
     * @param database uncertain database to mine
     * @param minsup minimum support threshold (≥ 1)
     * @param tau probability threshold (0 < τ ≤ 1)
     * @param k number of top patterns to find (≥ 1)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public AbstractFrequentItemsetMiner(UncertainDatabase database, int minsup, double tau, int k) {
        // Validate all parameters before storing
        validateParameters(database, minsup, tau, k);

        this.database = database;
        this.minsup = minsup;
        this.tau = tau;
        this.k = k;

        // CopyOnWriteArrayList: thread-safe for concurrent reads during parallel mining
        // Writes (adding observers) are rare; reads (notifications) are frequent
        this.observers = new CopyOnWriteArrayList<>();
    }

    /**
     * Add an observer to monitor mining progress.
     *
     * Observers receive notifications for:
     *   - Phase start/complete events
     *   - Pattern found events
     *   - Candidate pruned events
     *
     * @param observer observer instance to add
     */
    public void addObserver(MiningObserver observer) {
        observers.add(observer);
    }

    /**
     * Notify all observers that a phase has started.
     *
     * Thread-safe: synchronized to prevent interleaved notifications.
     *
     * @param phase phase number (1, 2, or 3)
     * @param description human-readable description
     */
    protected synchronized void notifyPhaseStart(int phase, String description) {
        for (MiningObserver obs : observers) {
            obs.onPhaseStart(phase, description);
        }
    }

    /**
     * Notify all observers that a phase has completed.
     *
     * @param phase phase number (1, 2, or 3)
     * @param duration phase duration in milliseconds
     */
    protected synchronized void notifyPhaseComplete(int phase, long duration) {
        for (MiningObserver obs : observers) {
            obs.onPhaseComplete(phase, duration);
        }
    }

    /**
     * Notify all observers that a pattern was found.
     *
     * Called from parallel Phase 1, so synchronization is critical.
     *
     * @param pattern the discovered pattern
     */
    protected synchronized void notifyPatternFound(Pattern pattern) {
        for (MiningObserver obs : observers) {
            obs.onPatternFound(pattern);
        }
    }

    /**
     * Notify all observers that a candidate was pruned.
     *
     * @param reason description of pruning reason
     */
    protected synchronized void notifyCandidatePruned(String reason) {
        for (MiningObserver obs : observers) {
            obs.onCandidatePruned(reason);
        }
    }

    /**
     * Validate mining parameters.
     *
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private void validateParameters(UncertainDatabase database, int minsup, double tau, int k) {
        // Database must exist
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }

        // Minimum support must be at least 1 (at least one transaction)
        if (minsup < 1) {
            throw new IllegalArgumentException("Minimum support must be at least 1");
        }

        // Tau must be in (0, 1] - probability threshold
        // tau = 0 would accept any support (meaningless)
        // tau > 1 is impossible (probability cannot exceed 1)
        if (tau <= 0 || tau > 1) {
            throw new IllegalArgumentException("Tau must be in (0, 1]");
        }

        // K must be at least 1 (find at least one pattern)
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * TEMPLATE METHOD: Defines the mining algorithm skeleton
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * This method is FINAL - subclasses cannot override it.
     * The algorithm structure is fixed; only specific steps vary.
     *
     * Algorithm:
     *   1. Phase 1: Compute frequent 1-itemsets
     *   2. Phase 2: Initialize data structures
     *   3. Phase 3: Perform recursive mining
     *   4. Return top-K results
     *
     * Each phase is timed and observers are notified.
     *
     * @return list of top-K frequent closed itemsets
     */
    public final List<Pattern> mine() {
        // ═══════════════════════════════════════════════════════════════
        // PHASE 1: Compute frequent 1-itemsets
        // ═══════════════════════════════════════════════════════════════
        notifyPhaseStart(1, "Computing frequent 1-itemsets");
        long start1 = System.nanoTime();

        // Subclass implements this: scans database for frequent single items
        List<Pattern> frequent1Itemsets = computeFrequent1Itemsets();

        long phase1Time = (System.nanoTime() - start1) / 1_000_000;  // Convert to ms
        notifyPhaseComplete(1, phase1Time);

        // ═══════════════════════════════════════════════════════════════
        // PHASE 2: Initialize data structures
        // ═══════════════════════════════════════════════════════════════
        notifyPhaseStart(2, "Initializing data structures");
        long start2 = System.nanoTime();

        // Subclass implements this: builds PQ, caches, etc.
        initializeDataStructures(frequent1Itemsets);

        long phase2Time = (System.nanoTime() - start2) / 1_000_000;
        notifyPhaseComplete(2, phase2Time);

        // ═══════════════════════════════════════════════════════════════
        // PHASE 3: Recursive mining (main loop)
        // ═══════════════════════════════════════════════════════════════
        notifyPhaseStart(3, "Recursive mining");
        long start3 = System.nanoTime();

        // Subclass implements this: priority queue processing, closure checking
        performRecursiveMining(frequent1Itemsets);

        long phase3Time = (System.nanoTime() - start3) / 1_000_000;
        notifyPhaseComplete(3, phase3Time);

        // ═══════════════════════════════════════════════════════════════
        // RETURN: Get final top-K results
        // ═══════════════════════════════════════════════════════════════
        return getTopKResults();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ABSTRACT METHODS - Subclasses must implement these
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Phase 1: Compute frequent 1-itemsets.
     *
     * Subclass responsibility:
     *   - Scan all items in vocabulary
     *   - Compute probabilistic support for each
     *   - Return items with support ≥ minsup
     *
     * Typically runs in parallel for performance.
     *
     * @return list of frequent 1-itemset patterns
     */
    protected abstract List<Pattern> computeFrequent1Itemsets();

    /**
     * Phase 2: Initialize data structures for mining.
     *
     * Subclass responsibility:
     *   - Create priority queue with 1-itemsets
     *   - Initialize caches and helper structures
     *   - Optionally pre-compute 2-itemsets
     *
     * @param frequent1Itemsets frequent 1-itemsets from Phase 1
     */
    protected abstract void initializeDataStructures(List<Pattern> frequent1Itemsets);

    /**
     * Phase 3: Perform recursive mining to find larger itemsets.
     *
     * Subclass responsibility:
     *   - Process priority queue
     *   - Check closure for each candidate
     *   - Generate extensions and add to queue
     *   - Update top-K heap with closed patterns
     *
     * @param frequent1Itemsets frequent 1-itemsets from Phase 1
     */
    protected abstract void performRecursiveMining(List<Pattern> frequent1Itemsets);

    /**
     * Get final top-K results after mining.
     *
     * Subclass responsibility:
     *   - Extract patterns from top-K heap
     *   - Sort by support (descending)
     *   - Return final result list
     *
     * @return list of top-K closed frequent patterns
     */
    protected abstract List<Pattern> getTopKResults();
}