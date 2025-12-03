package core;

import core.interfaces.SupportCalculator;
import database.UncertainDatabase;
import database.Vocabulary;
import model.Itemset;
import model.Pattern;
import model.Tidset;
import strategy.support.GFSupportCalculator;
import util.TopKHeap;

import java.util.*;

/**
 * ClosureAwareTopKMiner - Priority Queue based Top-K Closed Frequent Itemset Miner.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * ALGORITHM OVERVIEW
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * This is the CORE mining algorithm implementing:
 *   1. Priority Queue processing (highest support first)
 *   2. Immediate closure verification
 *   3. Canonical ordering (duplicate-free enumeration)
 *   4. Multiple pruning optimizations
 *
 * Key Innovation: Check closure IMMEDIATELY when processing each pattern,
 * not in a separate post-processing phase. Only CLOSED patterns enter Top-K heap,
 * preventing threshold inflation.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * PRUNING STRATEGIES
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. EARLY TERMINATION: Stop when support < threshold (PQ is max-heap by support)
 * 2. FREQUENT-ONLY: Only check items with support ≥ minsup
 * 3. UPPER BOUND: Skip if min(sup(X), sup({e})) < threshold
 * 4. TIGHTER BOUND: Also check cached 2-itemset subsets
 * 5. TIDSET SIZE: Skip GF computation if tidset.size() < threshold
 * 6. CACHE REUSE: Store computed supports and tidsets
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * THREE-PHASE ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Phase 1: Compute frequent 1-itemsets (parallel)
 * Phase 2: Initialize PQ, caches, and helper structures
 * Phase 3: Main mining loop with closure checking
 *
 * @author Dang Nguyen Le
 */
public class TUFCIAlgorithm extends AbstractFrequentItemsetMiner {

    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE VARIABLES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Vocabulary for item name encoding/decoding.
     */
    private final Vocabulary vocab;

    /**
     * Top-K heap storing ONLY closed patterns.
     * Min-heap by support: root = minimum support in Top-K.
     * Used to provide dynamic pruning threshold.
     */
    private TopKHeap closedTopK;

    /**
     * Priority queue for candidate patterns.
     * MAX-heap by support: highest support processed first.
     * This enables early termination when support drops below threshold.
     */
    private PriorityQueue<CandidatePattern> pq;

    /**
     * Cache for computed pattern information.
     * Maps: Itemset → (support, probability, tidset)
     * Enables efficient tidset intersection reuse.
     */
    private Map<Itemset, PatternInfo> cache;

    /**
     * Support calculator (strategy pattern).
     * Default: GFSupportCalculator (Dynamic Programming).
     * Can use FFT or Hybrid for large datasets.
     */
    private SupportCalculator calculator;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS (for performance analysis)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Total candidates extracted from PQ and processed */
    private int totalCandidatesProcessed = 0;

    /** Closed patterns found and added to Top-K heap */
    private int closedPatternsFound = 0;

    /** Extensions skipped due to various pruning strategies */
    private int extensionsSkipped = 0;

    /** Cache hits (avoided recomputation) */
    private int cacheHits = 0;

    /** Extensions pruned by tighter upper bound (2-itemset check) */
    private int tighterBoundPruning = 0;

    /** GF computations skipped via tidset size check */
    private int tidsetSizePruning = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // OPTIMIZATION STRUCTURES
    // ═══════════════════════════════════════════════════════════════════════════

    /** Verbosity control: false = quiet, true = detailed output */
    private boolean verbose = false;

    /**
     * Singleton itemset cache.
     * singletonCache[i] = Itemset containing only item i.
     * Created once in Phase 2, reused in Phase 3.
     * Avoids creating new Itemset objects repeatedly.
     */
    private Itemset[] singletonCache;

    /**
     * Array of FREQUENT item indices only.
     * Sorted by support DESC for early violation detection.
     * Only these items are checked during closure verification.
     */
    private int[] frequentItems;

    /** Number of frequent items (frequentItems.length) */
    private int frequentItemCount;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Constructor with default GF support calculator.
     *
     * @param database uncertain database to mine
     * @param minsup minimum support threshold
     * @param tau probability threshold (0 < τ ≤ 1)
     * @param k number of top patterns to find
     */
    public TUFCIAlgorithm(UncertainDatabase database, int minsup, double tau, int k) {
        // Call parent constructor (validates parameters)
        super(database, minsup, tau, k);

        // Store vocabulary reference
        this.vocab = database.getVocabulary();

        // Default support calculator: GF (Dynamic Programming)
        this.calculator = new GFSupportCalculator(tau);

        // Initialize cache early for parallel Phase 1
        this.cache = new HashMap<>();
    }

    /**
     * Constructor with custom support calculator.
     *
     * @param database uncertain database to mine
     * @param minsup minimum support threshold
     * @param tau probability threshold
     * @param k number of top patterns
     * @param calculator custom support calculation strategy
     */
    public TUFCIAlgorithm(UncertainDatabase database, int minsup, double tau, int k,
                                  SupportCalculator calculator) {
        super(database, minsup, tau, k);
        this.vocab = database.getVocabulary();
        this.calculator = calculator;
        this.cache = new HashMap<>();
    }

    /**
     * Set verbosity level for debugging output.
     *
     * @param verbose true for detailed output, false for quiet mode
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 1: Compute Frequent 1-Itemsets (Parallel)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Phase 1: Find all frequent single-item patterns.
     *
     * Algorithm:
     *   1. For each item in vocabulary (parallel)
     *   2. Get tidset from vertical database
     *   3. Compute probabilistic support
     *   4. If support ≥ minsup, add to result and cache
     *
     * Parallelization: Items are independent, so parallel stream is safe.
     * Synchronization: Required for adding to result list and cache.
     *
     * @return list of frequent 1-itemset patterns
     */
    @Override
    protected List<Pattern> computeFrequent1Itemsets() {
        // Result list (thread-safe access via synchronized blocks)
        List<Pattern> result = new ArrayList<>();
        int vocabSize = vocab.size();

        // ─────────────────────────────────────────────────────────────────────
        // PARALLEL COMPUTATION
        // Each item is processed independently → safe to parallelize
        // ─────────────────────────────────────────────────────────────────────
        java.util.stream.IntStream.range(0, vocabSize).parallel().forEach(item -> {
            // Create singleton itemset for this item
            Itemset singleton = createSingletonItemset(item);

            // Get tidset from vertical database
            Tidset tidset = database.getTidset(singleton);

            // Skip if no transactions contain this item
            if (tidset.isEmpty()) return;

            // Compute probabilistic support using sparse optimization
            double[] supportResult = calculator.computeSupportAndProbabilitySparse(
                tidset, database.size());
            int support = (int) supportResult[0];
            double probability = supportResult[1];

            // Check if frequent (support ≥ minsup)
            if (support >= minsup) {
                // Create pattern
                Pattern p = new Pattern(singleton, support, probability);

                // Add to result list (synchronized for thread safety)
                synchronized (result) {
                    result.add(p);
                }

                // Cache the 1-itemset with its tidset for Phase 3 reuse
                synchronized (cache) {
                    cache.put(singleton, new PatternInfo(support, probability, tidset));
                }

                // Notify observers
                notifyPatternFound(p);
            }
        });

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 2: Initialize Data Structures
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Phase 2: Initialize priority queue, caches, and helper structures.
     *
     * Steps:
     *   1. Create Top-K heap and priority queue
     *   2. Re-cache 1-itemsets (ensure consistency after parallel Phase 1)
     *   3. Build singleton cache (optimization)
     *   4. Build frequent-only item list (optimization)
     *   5. Add 1-itemsets to priority queue
     *
     * @param frequent1Itemsets frequent 1-itemsets from Phase 1
     */
    @Override
    protected void initializeDataStructures(List<Pattern> frequent1Itemsets) {
        // ─────────────────────────────────────────────────────────────────────
        // STEP 1: Initialize core data structures
        // ─────────────────────────────────────────────────────────────────────

        // Top-K heap: stores ONLY closed patterns
        this.closedTopK = new TopKHeap(k);

        // Priority queue: MAX-heap by support (highest first)
        // Comparator: primary = support DESC, secondary = probability DESC,
        //             tertiary = size ASC (prefer simpler patterns)
        this.pq = new PriorityQueue<>((a, b) -> {
            // Primary: higher support first
            int cmp = Integer.compare(b.support, a.support);
            if (cmp != 0) return cmp;

            // Secondary: higher probability first
            cmp = Double.compare(b.probability, a.probability);
            if (cmp != 0) return cmp;

            // Tertiary: smaller itemset first (simpler patterns)
            return Integer.compare(a.itemset.size(), b.itemset.size());
        });

        // ─────────────────────────────────────────────────────────────────────
        // STEP 2: Re-cache 1-itemsets with tidsets
        // Ensures cache is consistent after parallel Phase 1
        // ─────────────────────────────────────────────────────────────────────
        for (Pattern p : frequent1Itemsets) {
            Tidset tidset = database.getTidset(p.itemset);
            cache.put(p.itemset, new PatternInfo(p.support, p.probability, tidset));
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 3: Build singleton cache (OPTIMIZATION)
        // Pre-create all singleton itemsets to avoid repeated object creation
        // ─────────────────────────────────────────────────────────────────────
        int vocabSize = vocab.size();
        this.singletonCache = new Itemset[vocabSize];

        for (int i = 0; i < vocabSize; i++) {
            Itemset singleton = new Itemset(vocab);
            singleton.add(i);
            singletonCache[i] = singleton;
        }

        if (verbose) {
            System.out.println("  Created singleton cache for " + vocabSize + " items");
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 4: Build FREQUENT-ONLY item list (OPTIMIZATION)
        // ─────────────────────────────────────────────────────────────────────
        // WHY: Non-frequent items (support < minsup) can NEVER:
        //   - Produce valid extensions (antimonotonicity)
        //   - Violate closure (their support is always lower)
        //
        // BENEFIT: If vocab=1000 but only 100 frequent items,
        //          iteration count reduced by 90%!
        // ─────────────────────────────────────────────────────────────────────

        // Sort by support DESC for early violation detection
        frequent1Itemsets.sort((a, b) -> {
            int cmp = Integer.compare(b.support, a.support);
            if (cmp != 0) return cmp;
            return Double.compare(b.probability, a.probability);
        });

        // Extract frequent item indices
        this.frequentItemCount = frequent1Itemsets.size();
        this.frequentItems = new int[frequentItemCount];

        for (int i = 0; i < frequentItemCount; i++) {
            // Get the single item index from each 1-itemset
            frequentItems[i] = frequent1Itemsets.get(i).itemset.getItems().get(0);
        }

        if (verbose) {
            System.out.println("  Extension candidates: " + frequentItemCount +
                              " frequent items (vocabulary: " + vocabSize + " total)");
            if (frequentItemCount < vocabSize) {
                int skipped = vocabSize - frequentItemCount;
                double skipRate = 100.0 * skipped / vocabSize;
                System.out.printf("  Skipping %d non-frequent items (%.1f%% reduction)%n",
                                 skipped, skipRate);
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 5: Add 1-itemsets to priority queue
        // Already sorted by support DESC, so PQ ordering matches
        // ─────────────────────────────────────────────────────────────────────
        for (Pattern p : frequent1Itemsets) {
            pq.add(new CandidatePattern(p.itemset, p.support, p.probability));
        }

        if (verbose) {
            System.out.println("  Initialized PQ with " + frequent1Itemsets.size() +
                               " frequent 1-itemsets");
            System.out.println("  Using lazy computation (no 2-itemset pre-computation)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 3: Main Mining Loop - Priority Queue Processing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Phase 3: Process priority queue, check closure, generate extensions.
     *
     * Main loop algorithm:
     *   1. Extract highest-support candidate from PQ
     *   2. Check early termination condition
     *   3. Check closure + generate extensions
     *   4. If closed, add to Top-K heap
     *   5. Add promising extensions to PQ
     *   6. Repeat until PQ empty or early termination
     *
     * @param frequent1Itemsets frequent 1-itemsets (not used, kept for interface)
     */
    @Override
    protected void performRecursiveMining(List<Pattern> frequent1Itemsets) {
        if (verbose) {
            System.out.println("\n=== Priority Queue Mining (Closure-Aware) ===");
        }

        // ─────────────────────────────────────────────────────────────────────
        // MAIN MINING LOOP
        // Process candidates in support-descending order
        // ─────────────────────────────────────────────────────────────────────
        while (!pq.isEmpty()) {
            // Extract candidate with highest support
            CandidatePattern candidate = pq.poll();
            totalCandidatesProcessed++;

            // ═══════════════════════════════════════════════════════════════
            // EARLY TERMINATION CHECK
            // ═══════════════════════════════════════════════════════════════
            // If candidate.support < threshold AND heap is full:
            //   - All remaining candidates have support ≤ current (max-heap)
            //   - None can enter Top-K → stop immediately
            // ═══════════════════════════════════════════════════════════════
            int threshold = getThreshold();
            if (candidate.support < threshold && closedTopK.isFull()) {
                if (verbose) {
                    System.out.println("Early termination at support " + candidate.support +
                                       " (threshold=" + threshold + ")");
                }
                break;
            }

            // ═══════════════════════════════════════════════════════════════
            // CORE: Check closure + Generate extensions
            // This is where the magic happens!
            // ═══════════════════════════════════════════════════════════════
            ClosureCheckResult result = checkClosureAndGenerateExtensions(candidate);

            // ═══════════════════════════════════════════════════════════════
            // ADD TO HEAP ONLY IF CLOSED
            // This prevents threshold inflation!
            // ═══════════════════════════════════════════════════════════════
            if (result.isClosed) {
                closedTopK.insert(candidate.itemset, candidate.support, candidate.probability);
                closedPatternsFound++;

                // Notify observers
                notifyPatternFound(new Pattern(candidate.itemset, candidate.support,
                                                candidate.probability));

                // Progress output (first 10 + every 100th)
                if (verbose && (closedPatternsFound <= 10 || closedPatternsFound % 100 == 0)) {
                    System.out.println("  Closed #" + closedPatternsFound + ": " +
                                       candidate.itemset.toStringWithCodec() +
                                       " sup=" + candidate.support +
                                       " threshold=" + getThreshold());
                }
            } else {
                // Pattern not closed - skip it (no threshold inflation!)
                notifyCandidatePruned("Not closed");
            }

            // ═══════════════════════════════════════════════════════════════
            // ADD PROMISING EXTENSIONS TO PQ
            // ═══════════════════════════════════════════════════════════════
            // Canonical ordering guarantees no duplicates:
            //   - Each extension has current candidate as unique parent
            //   - Current candidate processed exactly once (now)
            //   - Therefore each extension added exactly once
            // ═══════════════════════════════════════════════════════════════
            int newThreshold = getThreshold();  // May have changed after insertion
            for (CandidatePattern ext : result.extensions) {
                // Only add if could potentially enter Top-K
                if (ext.support >= newThreshold || !closedTopK.isFull()) {
                    pq.add(ext);
                }
            }
        }

        // Print statistics
        if (verbose) {
            System.out.println("\n=== Mining Statistics ===");
            System.out.println("  Candidates processed: " + totalCandidatesProcessed);
            System.out.println("  Closed patterns found: " + closedPatternsFound);
            System.out.println("  Extensions skipped: " + extensionsSkipped);
            System.out.println("  Cache hits: " + cacheHits);
            System.out.println("  Tighter bound pruning: " + tighterBoundPruning);
            System.out.println("  Tidset size pruning: " + tidsetSizePruning);
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * CORE METHOD: Check Closure and Generate Extensions
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * For candidate X with support supX:
     *   1. For each frequent item e not in X:
     *      a. Check if e can violate closure (supX ∪ {e} = supX?)
     *      b. Check if extension X ∪ {e} should be generated (canonical order)
     *   2. Return (isClosed, list of extensions)
     *
     * Optimizations applied:
     *   - Only check FREQUENT items (support ≥ minsup)
     *   - Early exit when no more items can violate closure (sorted by support)
     *   - Upper bound pruning (min(supX, sup{e}))
     *   - Tighter bound using cached 2-itemsets
     *   - Tidset size pruning (skip GF if tidset too small)
     *   - Cache reuse for computed supports
     *
     * @param candidate the candidate pattern to check
     * @return ClosureCheckResult with isClosed flag and list of extensions
     */
    private ClosureCheckResult checkClosureAndGenerateExtensions(CandidatePattern candidate) {
        Itemset X = candidate.itemset;
        int supX = candidate.support;

        // Get current threshold (may change during mining)
        int threshold = getThreshold();

        // Assume closed until proven otherwise
        boolean isClosed = true;

        // Collect valid extensions
        List<CandidatePattern> extensions = new ArrayList<>();

        // Get maximum item in X for canonical ordering
        int maxItemInX = getMaxItemIndex(X);

        // Track when closure checking can stop (optimization)
        boolean closureCheckingDone = false;

        // ─────────────────────────────────────────────────────────────────────
        // ITERATE OVER FREQUENT ITEMS ONLY
        // frequentItems is sorted by support DESC
        // ─────────────────────────────────────────────────────────────────────
        for (int idx = 0; idx < frequentItemCount; idx++) {
            int item = frequentItems[idx];

            // Skip if item already in X
            if (X.contains(item)) continue;

            // Get item's support (guaranteed ≥ minsup since it's in frequentItems)
            int itemSupport = getItemSupport(item);

            // ═══════════════════════════════════════════════════════════════
            // EARLY BREAK OPTIMIZATION
            // ═══════════════════════════════════════════════════════════════
            // frequentItems sorted by support DESC
            // When itemSupport < supX, ALL remaining items have support < supX
            // → Cannot violate closure (requires sup(X∪{e}) = supX ≤ sup({e}))
            // ═══════════════════════════════════════════════════════════════
            if (!closureCheckingDone && itemSupport < supX) {
                closureCheckingDone = true;
            }

            // Determine what we need to compute
            boolean needClosureCheck = !closureCheckingDone && isClosed;
            boolean needExtension = (item > maxItemInX);  // Canonical ordering

            // ═══════════════════════════════════════════════════════════════
            // UPPER BOUND PRUNING
            // sup(X ∪ {e}) ≤ min(sup(X), sup({e})) by antimonotonicity
            // ═══════════════════════════════════════════════════════════════
            int upperBound = Math.min(supX, itemSupport);
            int standardUpperBound = upperBound;

            // ═══════════════════════════════════════════════════════════════
            // TIGHTER BOUND USING CACHED 2-ITEMSETS
            // For |X| ≥ 3: also check sup({existing_item, new_item})
            // ═══════════════════════════════════════════════════════════════
            if (X.size() >= 3 && closedTopK.isFull() && needExtension) {
                for (int existingItem : X.getItems()) {
                    // Create 2-itemset {existingItem, item}
                    Itemset twoItemset = new Itemset(vocab);
                    twoItemset.add(Math.min(existingItem, item));
                    twoItemset.add(Math.max(existingItem, item));

                    // Check cache
                    PatternInfo cachedSubset = cache.get(twoItemset);
                    if (cachedSubset != null) {
                        upperBound = Math.min(upperBound, cachedSubset.support);

                        // Early exit if bound already too low
                        if (upperBound < threshold) break;
                    }
                }

                // Track effectiveness
                if (standardUpperBound >= threshold && upperBound < threshold) {
                    tighterBoundPruning++;
                }
            }

            // Check if extension could enter Top-K
            boolean canEnterTopK = (upperBound >= threshold) || !closedTopK.isFull();
            boolean shouldGenerateExtension = needExtension && canEnterTopK;

            // ═══════════════════════════════════════════════════════════════
            // SKIP if nothing to do
            // ═══════════════════════════════════════════════════════════════
            if (!needClosureCheck && !shouldGenerateExtension) {
                extensionsSkipped++;
                continue;
            }

            // ═══════════════════════════════════════════════════════════════
            // COMPUTE OR RETRIEVE SUPPORT OF X ∪ {item}
            // ═══════════════════════════════════════════════════════════════
            Itemset itemItemset = singletonCache[item];
            Itemset Xe = X.union(itemItemset);
            int supXe;
            double probXe;
            Tidset tidsetXe;

            // Check cache first
            PatternInfo cached = cache.get(Xe);
            if (cached != null) {
                supXe = cached.support;
                probXe = cached.probability;
                tidsetXe = cached.tidset;
                cacheHits++;
            } else {
                // Must compute - get tidsets from cache
                PatternInfo xInfo = cache.get(X);
                PatternInfo itemInfo = cache.get(itemItemset);

                if (xInfo == null || itemInfo == null) {
                    // Fallback: compute from database
                    Tidset tidsetX = database.getTidset(X);
                    Tidset tidsetItem = database.getTidset(itemItemset);
                    tidsetXe = tidsetX.intersect(tidsetItem);
                } else {
                    // Intersect cached tidsets (efficient!)
                    tidsetXe = xInfo.tidset.intersect(itemInfo.tidset);
                }

                int tidsetSize = tidsetXe.size();

                // ═══════════════════════════════════════════════════════════
                // TIDSET SIZE PRUNING
                // sup(Xe) ≤ tidset.size() (upper bound)
                // If tidset.size() < threshold → cannot enter Top-K
                // ═══════════════════════════════════════════════════════════
                if (tidsetSize < threshold && !needClosureCheck && closedTopK.isFull()) {
                    supXe = 0;
                    probXe = 0.0;
                    tidsetSizePruning++;
                    cache.put(Xe, new PatternInfo(0, 0.0, tidsetXe));
                    extensionsSkipped++;
                    continue;
                }

                // Check if closure check can be skipped due to tidset size
                if (needClosureCheck && tidsetSize < supX) {
                    // sup(Xe) ≤ tidsetSize < supX → cannot violate closure
                    if (!shouldGenerateExtension) {
                        supXe = 0;
                        probXe = 0.0;
                        cache.put(Xe, new PatternInfo(0, 0.0, tidsetXe));
                        extensionsSkipped++;
                        continue;
                    }
                    needClosureCheck = false;
                }

                // Compute probabilistic support
                double[] result = calculator.computeSupportAndProbabilitySparse(
                    tidsetXe, database.size());
                supXe = (int) result[0];
                probXe = result[1];

                // Cache for future use
                cache.put(Xe, new PatternInfo(supXe, probXe, tidsetXe));
            }

            // ═══════════════════════════════════════════════════════════════
            // CLOSURE CHECK
            // If sup(X ∪ {e}) = sup(X), then X is NOT closed
            // ═══════════════════════════════════════════════════════════════
            if (needClosureCheck && supXe == supX) {
                isClosed = false;
                // Don't break - still need to generate extensions for mining
            }

            // ═══════════════════════════════════════════════════════════════
            // COLLECT EXTENSION (canonical ordering)
            // Only if item > max(X) and support ≥ minsup
            // ═══════════════════════════════════════════════════════════════
            if (shouldGenerateExtension && supXe >= minsup) {
                extensions.add(new CandidatePattern(Xe, supXe, probXe));
            }
        }

        return new ClosureCheckResult(isClosed, extensions);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET FINAL RESULTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get final top-K closed patterns after mining.
     *
     * All patterns in closedTopK are GUARANTEED closed because
     * closure was verified during mining (Phase 3).
     *
     * @return list of top-K closed patterns, sorted by support DESC
     */
    @Override
    protected List<Pattern> getTopKResults() {
        // Get all patterns from heap
        List<Pattern> results = closedTopK.getAll();

        // Sort by support DESC, then probability DESC
        results.sort((a, b) -> {
            int cmp = Integer.compare(b.support, a.support);
            if (cmp != 0) return cmp;
            return Double.compare(b.probability, a.probability);
        });

        if (verbose) {
            System.out.println("\n=== Final Results ===");
            System.out.println("  Returned " + results.size() + " closed patterns");
            System.out.println("  All patterns GUARANTEED closed (verified during mining)");
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get current pruning threshold.
     *
     * If Top-K heap is not full: threshold = minsup
     * If Top-K heap is full: threshold = max(minsup, heap.minSupport)
     *
     * @return current threshold for pruning
     */
    private int getThreshold() {
        if (!closedTopK.isFull()) {
            return minsup;
        }
        return Math.max(minsup, closedTopK.getMinSupport());
    }

    /**
     * Get support of a single item from cache.
     *
     * @param item item index
     * @return support of item, or 0 if not cached
     */
    private int getItemSupport(int item) {
        if (item < 0 || item >= singletonCache.length) return 0;
        Itemset singleton = singletonCache[item];
        PatternInfo info = cache.get(singleton);
        return (info != null) ? info.support : 0;
    }

    /**
     * Create singleton itemset containing one item.
     *
     * @param item item index
     * @return new Itemset containing only this item
     */
    private Itemset createSingletonItemset(int item) {
        Itemset itemset = new Itemset(vocab);
        itemset.add(item);
        return itemset;
    }

    /**
     * Get maximum item index in itemset.
     * Used for canonical ordering check.
     *
     * @param itemset the itemset
     * @return maximum item index, or -1 if empty
     */
    private int getMaxItemIndex(Itemset itemset) {
        List<Integer> items = itemset.getItems();
        if (items.isEmpty()) return -1;
        // Items are sorted in ascending order (BitSet iteration)
        return items.get(items.size() - 1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Candidate pattern in priority queue.
     * Lightweight wrapper for itemset + support + probability.
     */
    private static class CandidatePattern {
        final Itemset itemset;
        final int support;
        final double probability;

        CandidatePattern(Itemset itemset, int support, double probability) {
            this.itemset = itemset;
            this.support = support;
            this.probability = probability;
        }
    }

    /**
     * Cached pattern information.
     * Stores support, probability, AND tidset for efficient reuse.
     */
    private static class PatternInfo {
        final int support;
        final double probability;
        final Tidset tidset;

        PatternInfo(int support, double probability, Tidset tidset) {
            this.support = support;
            this.probability = probability;
            this.tidset = tidset;
        }
    }

    /**
     * Result of closure checking.
     * Contains isClosed flag and list of valid extensions.
     */
    private static class ClosureCheckResult {
        final boolean isClosed;
        final List<CandidatePattern> extensions;

        ClosureCheckResult(boolean isClosed, List<CandidatePattern> extensions) {
            this.isClosed = isClosed;
            this.extensions = extensions;
        }
    }
}