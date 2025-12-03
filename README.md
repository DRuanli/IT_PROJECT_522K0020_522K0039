# TUFCI - Top-K Uncertain Frequent Closed Itemset Miner

A high-performance algorithm for mining top-K closed frequent itemsets from uncertain databases with guaranteed closure properties and no threshold inflation.

## Features

- **Closure-Aware Mining**: Immediate closure checking during mining (not post-processing)
- **Priority Queue Optimization**: Processes candidates by support (highest first)
- **Multiple Pruning Strategies**: Early termination, upper bounds, tidset size checks
- **Three Execution Modes**: Single mining, experiments, and benchmarks
- **Baseline Comparison**: Built-in simple algorithms to measure speedup
- **Extensible Architecture**: Easy to add new algorithms and strategies

## Quick Start

### Compile

```bash
javac -d bin -sourcepath . **/*.java
```

### Run

**Single Mining:**
```bash
java -cp bin Main mine data/chess_uncertain.txt 10 0.7 20
```
- `10` = minimum support
- `0.7` = probability threshold (tau)
- `20` = number of top patterns (K)

**Experiment Mode** (test multiple configurations):
```bash
java -cp bin Main experiment
```

**Benchmark Mode** (compare algorithms):
```bash
java -cp bin Main benchmark
```

## Usage

### Basic Commands

```bash
# Single mining (with defaults)
java -cp bin Main mine <database_file>

# Single mining (with parameters)
java -cp bin Main mine <database_file> [minsup] [tau] [k]

# Run experiments
java -cp bin Main experiment

# Run benchmarks
java -cp bin Main benchmark

# Show help
java -cp bin Main help
```

### Example

```bash
# Mine top 20 patterns with minsup=10 and tau=0.7
java -cp bin Main mine data/chess_uncertain.txt 10 0.7 20
```

**Output:**
```
═══════════════════════════════════════════════════════
  TUFCI - Closure-Aware Top-K Frequent Itemset Miner
═══════════════════════════════════════════════════════

Parameters:
  Database: data/chess_uncertain.txt
  Min Support: 10
  Tau: 0.7
  K: 20

Loading database...
Database loaded in 45ms:
  Transactions: 3196
  Vocabulary: 75

Mining started...
─────────────────────────────────────────────────────
Found 20 closed patterns

Top 20 patterns:
Rank Itemset                            Support  Probability
──────────────────────────────────────────────────────────────
1    {item1, item5, item12}               156      0.8523
2    {item3, item7}                       142      0.8102
...
```

## Architecture

```
Main.java (3 modes)
    ├─ mine       → Single mining run
    ├─ experiment → Parameter sweeps with multiple runs
    └─ benchmark  → Statistical algorithm comparison

├── config/          Configuration layer
├── baseline/        Simple implementations (for comparison)
├── experiment/      Experiment framework
├── benchmark/       Benchmarking framework
├── core/            Mining algorithms
├── strategy/        Support calculators (GF, FFT)
├── factory/         Object creation
├── model/           Data structures
├── database/        Database management
└── util/            Utilities
```

## Algorithm Overview

**TUFCI Algorithm:**
- Priority queue processing (highest support first)
- Immediate closure checking (prevents threshold inflation)
- Canonical ordering (duplicate-free enumeration)
- Multiple pruning optimizations
- Cache reuse for efficiency

**Baseline Algorithm:**
- Simple breadth-first mining
- No optimizations (for comparison)
- Typically 10-100x slower than TUFCI

## Support Calculators

| Calculator | Complexity | Best For |
|------------|------------|----------|
| Generating Functions (GF) | O(n²) | General purpose (default) |
| Fast Fourier Transform (FFT) | O(n log n) | Large datasets (n > 10,000) |
| Brute-force | O(2^n) | Validation only (n ≤ 20) |

## Customizing Experiments

Edit `Main.java` to customize experiment configurations:

```java
private static void runExperiment(String[] args) {
    ExperimentConfiguration config = new ExperimentConfiguration.Builder()
        .addDataset("data/chess_uncertain.txt")
        .addDataset("data/mushroom_uncertain.txt")  // Add more datasets
        .addAlgorithm("TUFCI")
        .addAlgorithm("baseline")
        .addMinsup(5)
        .addMinsup(10)
        .addMinsup(15)        // Test multiple values
        .addTau(0.6)
        .addTau(0.7)
        .addTau(0.8)          // Test multiple values
        .addK(10)
        .addK(20)
        .numRuns(3)           // Runs per configuration
        .outputDirectory("results")
        .build();

    ExperimentRunner runner = new ExperimentRunner(config);
    runner.runAll();
}
```


## Design Patterns

- **Strategy Pattern**: Pluggable support calculators
- **Factory Pattern**: Miner creation
- **Builder Pattern**: Configuration objects
- **Template Method**: Mining algorithm skeleton
- **Observer Pattern**: Progress monitoring

## Project Structure

```
TUFCI/
├── Main.java                    # Entry point (3 modes)
├── config/                      # Configuration layer
├── baseline/                    # Simple algorithms
├── experiment/                  # Experiment framework
├── benchmark/                   # Benchmarking framework
├── core/                        # Core algorithms
├── strategy/                    # Calculation strategies
├── factory/                     # Object factories
├── model/                       # Data models
├── database/                    # Database management
├── util/                        # Utilities
└── data/                        # Datasets (create this)
```

## Requirements

- Java 8 or higher
- No external dependencies

## Documentation

- **README.md** (this file) - Quick start and overview
- **README_ARCHITECTURE.md** - Detailed architecture documentation
- **QUICKSTART.md** - Step-by-step quick start guide
- **SUMMARY.md** - Implementation summary
- **STRUCTURE_OVERVIEW.txt** - Visual structure overview


## Parameters

| Parameter | Description | Default | Range |
|-----------|-------------|---------|-------|
| **minsup** | Minimum support threshold | 2 | > 0 |
| **tau** | Probability threshold | 0.7 | (0, 1] |
| **k** | Number of top patterns | 5 | > 0 |

## Input Format

Uncertain database format (space-separated):
```
item1:prob1 item2:prob2 item3:prob3 ...
item5:prob5 item6:prob6 ...
...
```

Example:
```
1:0.8 5:0.9 12:0.7
3:0.6 7:0.85
1:0.75 3:0.9 8:0.8
```

## Output

Mining results include:
- Rank (1 to K)
- Itemset (closed frequent itemset)
- Support (probabilistic support)
- Probability (P(support ≥ threshold))


## Author

Dang Nguyen Le

# IT_PROJECT_522K0020_522K0039
