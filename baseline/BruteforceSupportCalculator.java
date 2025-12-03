package baseline;

import model.Tidset;
import strategy.support.AbstractSupportCalculator;

/**
 * Brute-force support calculator for baseline comparison.
 *
 * Uses exhaustive enumeration of all possible support values.
 * Simple but inefficient - O(n³) complexity.
 *
 * Used as baseline to measure speedup of optimized algorithms.
 */
public class BruteforceSupportCalculator extends AbstractSupportCalculator {

    public BruteforceSupportCalculator(double tau) {
        super(tau);
    }

    @Override
    public int computeProbabilisticSupport(double[] transactionProbs, int minRequiredSupport) {
        int n = transactionProbs.length;

        // Try each possible support value from n down to 0
        for (int s = n; s >= 0; s--) {
            double prob = computeProbabilityForSupport(transactionProbs, s);
            if (prob >= tau) {
                return s;
            }
        }
        return 0;
    }

    @Override
    public double computeProbability(double[] transactionProbs, int support) {
        return computeProbabilityForSupport(transactionProbs, support);
    }

    @Override
    public String getStrategyName() {
        return "Brute-force Enumeration";
    }

    /**
     * Compute P(support >= s) by exhaustive enumeration.
     *
     * Enumerate all 2^n subsets and sum probabilities where |subset| >= s.
     *
     * Time Complexity: O(2^n * n) - exponential!
     */
    private double computeProbabilityForSupport(double[] probs, int targetSupport) {
        int n = probs.length;
        double totalProb = 0.0;

        // Enumerate all 2^n possible subsets
        for (int mask = 0; mask < (1 << n); mask++) {
            int count = Integer.bitCount(mask);

            // Only consider subsets with size >= targetSupport
            if (count >= targetSupport) {
                double prob = 1.0;

                // Compute probability of this specific subset
                for (int i = 0; i < n; i++) {
                    if ((mask & (1 << i)) != 0) {
                        prob *= probs[i];  // Item included
                    } else {
                        prob *= (1.0 - probs[i]);  // Item not included
                    }
                }

                totalProb += prob;
            }
        }

        return totalProb;
    }

    @Override
    public double[] computeSupportAndProbability(double[] transactionProbs) {
        int support = computeProbabilisticSupport(transactionProbs, 0);
        double probability = computeProbability(transactionProbs, support);
        return new double[]{support, probability};
    }

    @Override
    public double[] computeSupportAndProbabilitySparse(Tidset tidset, int totalTransactions) {
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

        // Find support
        int support = computeProbabilisticSupport(probs, 0);
        double probability = computeProbability(probs, support);

        return new double[]{support, probability};
    }
}
