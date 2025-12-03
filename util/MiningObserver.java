package util;

import model.Pattern;

/**
 * MiningObserver - Observer Pattern interface for monitoring mining progress.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * OBSERVER PATTERN
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * The Observer Pattern defines a one-to-many dependency between objects:
 *   - Subject (AbstractFrequentItemsetMiner): the object being observed
 *   - Observer (this interface): objects that want to be notified of changes
 *
 * Benefits:
 *   1. Loose coupling: Miner doesn't need to know observer implementations
 *   2. Open/Closed: Add new observers without modifying miner code
 *   3. Single Responsibility: Mining logic separate from reporting logic
 *
 * Usage Example:
 *   miner.addObserver(new MiningObserver() {
 *       @Override
 *       public void onPatternFound(Pattern p) {
 *           System.out.println("Found: " + p.itemset);
 *       }
 *       // ... other methods
 *   });
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * EVENT TYPES
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. Phase Events: Track mining progress through phases
 *    - onPhaseStart(): Phase begins
 *    - onPhaseComplete(): Phase ends with duration
 *
 * 2. Pattern Events: Track discovered patterns
 *    - onPatternFound(): New frequent pattern discovered
 *
 * 3. Pruning Events: Track optimization effectiveness
 *    - onCandidatePruned(): Candidate rejected (for statistics)
 *
 * @author Dang Nguyen Le
 */
public interface MiningObserver {

    /**
     * Called when a mining phase starts.
     *
     * TUFCI has 3 phases:
     *   Phase 1: Compute frequent 1-itemsets (parallel)
     *   Phase 2: Initialize data structures (PQ, cache)
     *   Phase 3: Main mining loop (closure checking)
     *
     * @param phase phase number (1, 2, or 3)
     * @param description human-readable phase description
     */
    void onPhaseStart(int phase, String description);

    /**
     * Called when a mining phase completes.
     *
     * Use for:
     *   - Performance profiling
     *   - Progress reporting
     *   - Logging
     *
     * @param phase phase number (1, 2, or 3)
     * @param durationMs phase duration in milliseconds
     */
    void onPhaseComplete(int phase, long durationMs);

    /**
     * Called when a new frequent pattern is discovered.
     *
     * Note: This is called for ALL discovered patterns, not just
     * those that make it to final Top-K. Pattern may be:
     *   - Added to heap (if competitive)
     *   - Rejected (if not competitive enough)
     *
     * Use for:
     *   - Real-time pattern streaming
     *   - Progress indication
     *   - Debugging
     *
     * @param pattern the discovered pattern with support and probability
     */
    void onPatternFound(Pattern pattern);

    /**
     * Called when a candidate is pruned (not explored).
     *
     * Pruning reasons include:
     *   - "Not closed": Pattern has superset with same support
     *   - "Below threshold": Support too low for Top-K
     *   - "Duplicate": Already processed
     *
     * Use for:
     *   - Pruning effectiveness statistics
     *   - Algorithm debugging
     *   - Performance analysis
     *
     * @param reason description of why candidate was pruned
     */
    void onCandidatePruned(String reason);
}