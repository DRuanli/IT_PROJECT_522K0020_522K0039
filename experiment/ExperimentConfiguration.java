package experiment;

import config.MiningConfiguration;
import java.util.*;

/**
 * Configuration for running experiments.
 * Supports multiple datasets, algorithms, and parameter combinations.
 */
public class ExperimentConfiguration {
    private final List<String> datasets;
    private final List<String> algorithms;
    private final List<Integer> minsupValues;
    private final List<Double> tauValues;
    private final List<Integer> kValues;
    private final int numRuns;
    private final boolean warmup;
    private final String outputDirectory;

    private ExperimentConfiguration(Builder builder) {
        this.datasets = Collections.unmodifiableList(new ArrayList<>(builder.datasets));
        this.algorithms = Collections.unmodifiableList(new ArrayList<>(builder.algorithms));
        this.minsupValues = Collections.unmodifiableList(new ArrayList<>(builder.minsupValues));
        this.tauValues = Collections.unmodifiableList(new ArrayList<>(builder.tauValues));
        this.kValues = Collections.unmodifiableList(new ArrayList<>(builder.kValues));
        this.numRuns = builder.numRuns;
        this.warmup = builder.warmup;
        this.outputDirectory = builder.outputDirectory;
    }

    public List<String> getDatasets() { return datasets; }
    public List<String> getAlgorithms() { return algorithms; }
    public List<Integer> getMinsupValues() { return minsupValues; }
    public List<Double> getTauValues() { return tauValues; }
    public List<Integer> getKValues() { return kValues; }
    public int getNumRuns() { return numRuns; }
    public boolean isWarmup() { return warmup; }
    public String getOutputDirectory() { return outputDirectory; }

    /**
     * Generate all mining configurations from this experiment configuration.
     */
    public List<MiningConfiguration> generateConfigurations() {
        List<MiningConfiguration> configs = new ArrayList<>();

        for (String dataset : datasets) {
            for (String algorithm : algorithms) {
                for (int minsup : minsupValues) {
                    for (double tau : tauValues) {
                        for (int k : kValues) {
                            configs.add(new MiningConfiguration.Builder()
                                .databasePath(dataset)
                                .algorithmName(algorithm)
                                .minsup(minsup)
                                .tau(tau)
                                .k(k)
                                .build());
                        }
                    }
                }
            }
        }

        return configs;
    }

    /**
     * Builder for ExperimentConfiguration
     */
    public static class Builder {
        private List<String> datasets = new ArrayList<>();
        private List<String> algorithms = new ArrayList<>();
        private List<Integer> minsupValues = new ArrayList<>();
        private List<Double> tauValues = new ArrayList<>();
        private List<Integer> kValues = new ArrayList<>();
        private int numRuns = 3;
        private boolean warmup = true;
        private String outputDirectory = "results";

        public Builder addDataset(String path) {
            this.datasets.add(path);
            return this;
        }

        public Builder addAlgorithm(String name) {
            this.algorithms.add(name);
            return this;
        }

        public Builder addMinsup(int minsup) {
            this.minsupValues.add(minsup);
            return this;
        }

        public Builder addTau(double tau) {
            this.tauValues.add(tau);
            return this;
        }

        public Builder addK(int k) {
            this.kValues.add(k);
            return this;
        }

        public Builder numRuns(int runs) {
            this.numRuns = runs;
            return this;
        }

        public Builder warmup(boolean warmup) {
            this.warmup = warmup;
            return this;
        }

        public Builder outputDirectory(String dir) {
            this.outputDirectory = dir;
            return this;
        }

        public ExperimentConfiguration build() {
            if (datasets.isEmpty()) {
                throw new IllegalArgumentException("At least one dataset required");
            }
            if (algorithms.isEmpty()) {
                throw new IllegalArgumentException("At least one algorithm required");
            }
            return new ExperimentConfiguration(this);
        }
    }
}
