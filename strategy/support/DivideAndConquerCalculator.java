package strategy.support;

import model.Tidset;
import util.Constants;

/**
 * DivideAndConquerCalculator - Hierarchical probabilistic support calculator.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * ALGORITHM OVERVIEW
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Unlike GF (sequential processing) or FFT (transform-based), this uses a
 * divide-and-conquer strategy:
 *
 * 1. DIVIDE: Split transactions into two halves
 * 2. CONQUER: Recursively compute distribution for each half
 * 3. COMBINE: Merge the two distributions via convolution
 *
 * The merge step uses:
 *   - Naive convolution: O(n²) per merge
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * COMPLEXITY ANALYSIS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Tree structure with log(n) levels:
 *   Level 0: 1 merge of size n
 *   Level 1: 2 merges of size n/2
 *   Level 2: 4 merges of size n/4
 *   ...
 *   Level log(n): n merges of size 1
 *
 * With Naive Convolution (only method used):
 *   Work per level = O(n²)
 *   Total levels = O(log n)
 *   Total complexity = O(n² log n)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * ADVANTAGES OVER SEQUENTIAL METHODS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. PARALLELIZABLE: At each level, all subtrees are independent
 *    - Can process left and right halves concurrently
 *    - Natural fit for multi-core systems
 *
 * 2. DIFFERENT PARADIGM: Hierarchical vs sequential
 *    - Shows alternative algorithmic structure
 *    - Educational value for research comparison
 *
 * 3. SIMPLE CONVOLUTION: Uses naive convolution only
 *    - No FFT overhead or complexity
 *    - Easier to understand and maintain
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * COMPARISON WITH OTHER METHODS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * GF (Sequential DP):
 *   - O(n²) time
 *   - Simple, fast for small n
 *   - Not parallelizable
 *
 * FFT (Transform-based):
 *   - O(n log² n) time
 *   - Fast for large n
 *   - Complex implementation
 *
 * Divide & Conquer (Naive):
 *   - O(n² log n) time
 *   - Parallelizable structure
 *   - Simple convolution, demonstrates different approach
 *
 * @author Dang Nguyen Le
 */
public class DivideAndConquerCalculator extends AbstractSupportCalculator {

    /**
     * Constructor.
     *
     * @param tau probability threshold (0 < τ ≤ 1)
     */
    public DivideAndConquerCalculator(double tau) {
        super(tau);
    }

    /**
     * Compute probabilistic support from dense probability array.
     *
     * @param transactionProbs probability array for all transactions
     * @param minRequiredSupport hint for early termination (unused in this implementation)
     * @return probabilistic support value
     */
    @Override
    public int computeProbabilisticSupport(double[] transactionProbs, int minRequiredSupport) {
        // Step 1: Compute distribution via divide and conquer
        double[] distribution = divideAndConquer(transactionProbs, 0, transactionProbs.length);

        // Step 2: Convert to frequentness (cumulative probabilities)
        double[] frequentness = computeFrequentness(distribution);

        // Step 3: Binary search for max support where freq[s] ≥ τ
        return findProbabilisticSupport(frequentness);
    }

    /**
     * Compute probability P(support ≥ s) for given support value.
     *
     * @param transactionProbs probability array
     * @param support target support value
     * @return P(support ≥ s)
     */
    @Override
    public double computeProbability(double[] transactionProbs, int support) {
        // Compute full distribution
        double[] distribution = divideAndConquer(transactionProbs, 0, transactionProbs.length);
        double[] frequentness = computeFrequentness(distribution);

        // Return frequentness at requested support level
        if (support >= 0 && support < frequentness.length) {
            return frequentness[support];
        }
        return 0.0;
    }

    /**
     * Compute both support and probability in single call (optimized).
     *
     * @param transactionProbs probability array
     * @return [support, probability]
     */
    @Override
    public double[] computeSupportAndProbability(double[] transactionProbs) {
        // Single distribution computation
        double[] distribution = divideAndConquer(transactionProbs, 0, transactionProbs.length);
        double[] frequentness = computeFrequentness(distribution);

        // Extract both values
        int support = findProbabilisticSupport(frequentness);
        double probability = (support >= 0 && support < frequentness.length)
                           ? frequentness[support] : 0.0;

        return new double[]{support, probability};
    }

    /**
     * Compute support and probability from sparse tidset.
     *
     * @param tidset sparse transaction ID set with probabilities
     * @param totalTransactions total transactions (unused, for interface compatibility)
     * @return [support, probability]
     */
    @Override
    public double[] computeSupportAndProbabilitySparse(Tidset tidset, int totalTransactions) {
        // Handle empty tidset
        if (tidset.isEmpty()) {
            return new double[]{0, 0.0};
        }

        // Extract probabilities from tidset
        int m = tidset.size();
        double[] probs = new double[m];
        int idx = 0;
        for (Tidset.TIDProb entry : tidset.getEntries()) {
            probs[idx++] = entry.prob;
        }

        // Compute using divide and conquer
        return computeSupportAndProbability(probs);
    }

    /**
     * Get strategy name for logging.
     *
     * @return strategy name
     */
    @Override
    public String getStrategyName() {
        return "Divide & Conquer (Hierarchical with Naive Convolution)";
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * CORE ALGORITHM: Divide and Conquer Distribution Computation
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Recursively splits transactions and merges distributions.
     *
     * Algorithm:
     *   1. Base case: Single transaction → return [1-p, p]
     *   2. Divide: Split transactions in half
     *   3. Conquer: Recursively compute distribution for each half
     *   4. Combine: Convolve the two distributions
     *
     * Example with 4 transactions [0.6, 0.8, 0.5, 0.7]:
     *
     *                    [0.6, 0.8, 0.5, 0.7]
     *                     /                \
     *              [0.6, 0.8]            [0.5, 0.7]
     *               /      \              /      \
     *            [0.6]    [0.8]        [0.5]    [0.7]
     *             ↓         ↓            ↓        ↓
     *         [0.4,0.6] [0.2,0.8]   [0.5,0.5] [0.3,0.7]
     *              \      /              \      /
     *           convolve              convolve
     *               ↓                     ↓
     *        [0.08,0.44,0.48]      [0.15,0.50,0.35]
     *                 \                 /
     *                    convolve
     *                       ↓
     *            [0.012, 0.106, 0.320, 0.394, 0.168]
     *
     * @param probs probability array
     * @param start start index (inclusive)
     * @param end end index (exclusive)
     * @return probability distribution where result[s] = P(support = s)
     */
    private double[] divideAndConquer(double[] probs, int start, int end) {
        int length = end - start;

        // ─────────────────────────────────────────────────────────────────
        // BASE CASE: Single transaction
        // ─────────────────────────────────────────────────────────────────
        // Transaction with probability p contributes:
        //   P(support = 0) = 1 - p  (not in transaction)
        //   P(support = 1) = p      (in transaction)
        // ─────────────────────────────────────────────────────────────────
        if (length == 1) {
            double p = probs[start];

            // Skip near-zero probabilities (numerical stability)
            if (p < Constants.MIN_PROB) {
                return new double[]{1.0, 0.0};
            }

            return new double[]{1.0 - p, p};
        }

        // ─────────────────────────────────────────────────────────────────
        // DIVIDE: Split into two halves
        // ─────────────────────────────────────────────────────────────────
        int mid = start + length / 2;

        // ─────────────────────────────────────────────────────────────────
        // CONQUER: Recursively compute distributions
        // ─────────────────────────────────────────────────────────────────
        // NOTE: This is where parallelization can happen!
        // In parallel implementation:
        //   Future<double[]> leftFuture = executor.submit(() -> divideAndConquer(probs, start, mid));
        //   Future<double[]> rightFuture = executor.submit(() -> divideAndConquer(probs, mid, end));
        //   double[] leftDist = leftFuture.get();
        //   double[] rightDist = rightFuture.get();
        // ─────────────────────────────────────────────────────────────────
        double[] leftDist = divideAndConquer(probs, start, mid);
        double[] rightDist = divideAndConquer(probs, mid, end);

        // ─────────────────────────────────────────────────────────────────
        // COMBINE: Merge distributions via convolution
        // ─────────────────────────────────────────────────────────────────
        return convolve(leftDist, rightDist);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * CONVOLUTION: Merge two probability distributions
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Given two independent random variables with distributions:
     *   a[i] = P(left = i)
     *   b[j] = P(right = j)
     *
     * Their sum has distribution:
     *   result[s] = P(left + right = s) = Σ_i a[i] × b[s-i]
     *
     * This is the DEFINITION of discrete convolution.
     *
     * Example: a = [0.4, 0.6], b = [0.2, 0.8]
     *   result[0] = 0.4 × 0.2 = 0.08
     *   result[1] = 0.4 × 0.8 + 0.6 × 0.2 = 0.44
     *   result[2] = 0.6 × 0.8 = 0.48
     *   Result: [0.08, 0.44, 0.48]
     *
     * @param a first distribution
     * @param b second distribution
     * @return convolution of a and b
     */
    private double[] convolve(double[] a, double[] b) {
        return naiveConvolve(a, b);
    }

    /**
     * Naive convolution: Direct computation of convolution sum.
     *
     * Algorithm:
     *   result[s] = Σ_{i=0}^{s} a[i] × b[s-i]
     *
     * Time Complexity: O(|a| × |b|)
     * Space Complexity: O(|a| + |b|)
     *
     * Advantages:
     *   - Simple, no overhead
     *   - Easy to understand and maintain
     *   - Exact (no numerical errors from FFT)
     *
     * @param a first distribution
     * @param b second distribution
     * @return convolution result
     */
    private double[] naiveConvolve(double[] a, double[] b) {
        int lenA = a.length;
        int lenB = b.length;
        double[] result = new double[lenA + lenB - 1];

        // Compute each coefficient by summing products
        for (int i = 0; i < lenA; i++) {
            for (int j = 0; j < lenB; j++) {
                result[i + j] += a[i] * b[j];
            }
        }

        return result;
    }
}
