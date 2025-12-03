package config;

/**
 * Configuration for mining parameters.
 * Immutable configuration object following Builder pattern.
 */
public class MiningConfiguration {
    private final String databasePath;
    private final int minsup;
    private final double tau;
    private final int k;
    private final String algorithmName;
    private final boolean verbose;

    private MiningConfiguration(Builder builder) {
        this.databasePath = builder.databasePath;
        this.minsup = builder.minsup;
        this.tau = builder.tau;
        this.k = builder.k;
        this.algorithmName = builder.algorithmName;
        this.verbose = builder.verbose;
    }

    // Getters
    public String getDatabasePath() { return databasePath; }
    public int getMinsup() { return minsup; }
    public double getTau() { return tau; }
    public int getK() { return k; }
    public String getAlgorithmName() { return algorithmName; }
    public boolean isVerbose() { return verbose; }

    @Override
    public String toString() {
        return String.format("MiningConfig[db=%s, minsup=%d, tau=%.2f, k=%d, algo=%s]",
                           databasePath, minsup, tau, k, algorithmName);
    }

    /**
     * Builder for MiningConfiguration
     */
    public static class Builder {
        private String databasePath;
        private int minsup = 2;
        private double tau = 0.7;
        private int k = 10;
        private String algorithmName = "TUFCI";
        private boolean verbose = false;

        public Builder databasePath(String path) {
            this.databasePath = path;
            return this;
        }

        public Builder minsup(int minsup) {
            this.minsup = minsup;
            return this;
        }

        public Builder tau(double tau) {
            this.tau = tau;
            return this;
        }

        public Builder k(int k) {
            this.k = k;
            return this;
        }

        public Builder algorithmName(String name) {
            this.algorithmName = name;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public MiningConfiguration build() {
            if (databasePath == null || databasePath.isEmpty()) {
                throw new IllegalArgumentException("Database path is required");
            }
            if (minsup < 0) {
                throw new IllegalArgumentException("Minsup must be non-negative");
            }
            if (tau <= 0 || tau > 1) {
                throw new IllegalArgumentException("Tau must be in (0, 1]");
            }
            if (k <= 0) {
                throw new IllegalArgumentException("K must be positive");
            }
            return new MiningConfiguration(this);
        }
    }
}
