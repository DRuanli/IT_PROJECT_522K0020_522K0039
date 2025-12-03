package baseline;

import core.AbstractFrequentItemsetMiner;
import core.interfaces.SupportCalculator;
import database.UncertainDatabase;
import database.Vocabulary;
import model.Itemset;
import model.Pattern;
import model.Tidset;
import strategy.support.GFSupportCalculator;

import java.util.*;

/**
 * Simple baseline Top-K miner without optimizations.
 *
 * Features:
 * - No closure checking
 * - No priority queue optimization
 * - No pruning strategies
 * - Simple breadth-first search
 *
 * Used as baseline to measure effectiveness of optimizations.
 */
public class SimpleTopKMiner extends AbstractFrequentItemsetMiner {

    private final Vocabulary vocab;
    private final SupportCalculator calculator;
    private List<Pattern> allPatterns;

    public SimpleTopKMiner(UncertainDatabase database, int minsup, double tau, int k) {
        super(database, minsup, tau, k);
        this.vocab = database.getVocabulary();
        this.calculator = new GFSupportCalculator(tau);
        this.allPatterns = new ArrayList<>();
    }

    @Override
    protected List<Pattern> computeFrequent1Itemsets() {
        List<Pattern> result = new ArrayList<>();
        int vocabSize = vocab.size();

        for (int item = 0; item < vocabSize; item++) {
            Itemset singleton = new Itemset(vocab);
            singleton.add(item);

            Tidset tidset = database.getTidset(singleton);
            if (tidset.isEmpty()) continue;

            double[] supportResult = calculator.computeSupportAndProbabilitySparse(
                tidset, database.size());
            int support = (int) supportResult[0];
            double probability = supportResult[1];

            if (support >= minsup) {
                Pattern p = new Pattern(singleton, support, probability);
                result.add(p);
                allPatterns.add(p);
            }
        }

        return result;
    }

    @Override
    protected void initializeDataStructures(List<Pattern> frequent1Itemsets) {
        // No special initialization needed for simple approach
    }

    @Override
    protected void performRecursiveMining(List<Pattern> frequent1Itemsets) {
        List<Pattern> currentLevel = new ArrayList<>(frequent1Itemsets);

        while (!currentLevel.isEmpty()) {
            List<Pattern> nextLevel = new ArrayList<>();

            // Generate candidates by joining
            for (int i = 0; i < currentLevel.size(); i++) {
                for (int j = i + 1; j < currentLevel.size(); j++) {
                    Pattern p1 = currentLevel.get(i);
                    Pattern p2 = currentLevel.get(j);

                    Itemset candidate = p1.itemset.union(p2.itemset);

                    // Skip if not a valid join
                    if (candidate.size() != p1.itemset.size() + 1) {
                        continue;
                    }

                    // Compute support
                    Tidset tidset = database.getTidset(candidate);
                    if (tidset.isEmpty()) continue;

                    double[] result = calculator.computeSupportAndProbabilitySparse(
                        tidset, database.size());
                    int support = (int) result[0];
                    double probability = result[1];

                    if (support >= minsup) {
                        Pattern p = new Pattern(candidate, support, probability);
                        nextLevel.add(p);
                        allPatterns.add(p);
                        notifyPatternFound(p);
                    }
                }
            }

            currentLevel = nextLevel;
        }
    }

    @Override
    protected List<Pattern> getTopKResults() {
        allPatterns.sort((a, b) -> {
            int cmp = Integer.compare(b.support, a.support);
            if (cmp != 0) return cmp;
            return Double.compare(b.probability, a.probability);
        });

        int returnSize = Math.min(k, allPatterns.size());
        return allPatterns.subList(0, returnSize);
    }
}
