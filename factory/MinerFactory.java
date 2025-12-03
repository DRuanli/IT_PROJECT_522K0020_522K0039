package factory;

import core.TUFCIAlgorithm;
import core.interfaces.SupportCalculator;
import database.UncertainDatabase;

/**
 * Factory for creating Closure-Aware Top-K Miner instances
 *
 * @author Dang Nguyen Le
 */
public class MinerFactory {

    /**
     * Create Closure-Aware Top-K Miner with default configuration
     *
     *
     * @param database uncertain database to mine
     * @param minsup minimum support threshold
     * @param tau probability threshold (0 < tau <= 1)
     * @param k number of top itemsets to return
     * @return configured ClosureAwareTopKMiner instance
     */
    public static TUFCIAlgorithm createMiner(
        UncertainDatabase database,
        int minsup,
        double tau,
        int k
    ) {
        return new TUFCIAlgorithm(database, minsup, tau, k);
    }

    /**
     * Create miner with custom support calculator
     *
     * @param database uncertain database to mine
     * @param minsup minimum support threshold
     * @param tau probability threshold
     * @param k number of top itemsets to return
     * @param calculator custom support calculation strategy
     * @return configured ClosureAwareTopKMiner instance
     */
    public static TUFCIAlgorithm createMiner(
        UncertainDatabase database,
        int minsup,
        double tau,
        int k,
        SupportCalculator calculator
    ) {
        return new TUFCIAlgorithm(database, minsup, tau, k, calculator);
    }

}
