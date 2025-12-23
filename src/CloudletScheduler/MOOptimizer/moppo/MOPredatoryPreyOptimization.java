package CloudletScheduler.MOOptimizer.moppo;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Multi-Objective Predatory Prey Optimization (MO-PPO)
 * 使用 Pareto 存档维护非支配解集，支持多目标云任务调度优化。
 */
public class MOPredatoryPreyOptimization {

    private final OptFunctionMulti optFunction;
    private double lb, ub;
    private int population;
    private final int dim;
    private final int maxFEs;

    private double[][] positions;
    private double[][] flockMemoryX;
    private ObjectiveValues[] flockMemoryF;

    private ParetoArchive archive;
    private int evaluations;
    private static final Random random = new Random();

    public MOPredatoryPreyOptimization(
            OptFunctionMulti optFunction,
            int population,
            double lb,
            double ub,
            int dim,
            int maxFEs,
            int archiveMaxSize) {

        if (lb >= ub) throw new IllegalArgumentException("Lower bound must be < upper bound.");
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxFEs = maxFEs;
        this.positions = new double[population][dim];
        this.flockMemoryX = new double[population][dim];
        this.flockMemoryF = new ObjectiveValues[population];
        this.archive = new ParetoArchive(archiveMaxSize);
        this.evaluations = 0;

        initializePopulation();
    }

    private void initializePopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPosition(i);
            evaluateAndArchive(i);
        }
        // 初始化记忆
        for (int i = 0; i < population; i++) {
            flockMemoryX[i] = positions[i].clone();
            flockMemoryF[i] = evaluate(positions[i]);
        }
    }

    private void adjustPosition(int idx) {
        for (int j = 0; j < dim; j++) {
            positions[idx][j] = Math.round(positions[idx][j]);
            if (positions[idx][j] < lb) positions[idx][j] = lb;
            if (positions[idx][j] > ub) positions[idx][j] = ub;
        }
    }

    private ObjectiveValues evaluate(double[] pos) {
        int[] params = Arrays.stream(pos).mapToInt(x -> (int) x).toArray();
        return optFunction.evaluate(params);
    }

    private void evaluateAndArchive(int i) {
        if (evaluations >= maxFEs) return;
        ObjectiveValues obj = evaluate(positions[i]);
        archive.add(positions[i], obj);
        evaluations++;
    }

    private double[] levyFlight(int d) {
        double beta = 1.5;
        double sigma = Math.pow(
                gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta
        );

        double[] u = new double[d];
        double[] v = new double[d];
        for (int j = 0; j < d; j++) {
            u[j] = random.nextGaussian() * sigma;
            v[j] = random.nextGaussian();
        }

        double[] step = new double[d];
        for (int j = 0; j < d; j++) {
            step[j] = u[j] / Math.pow(Math.abs(v[j]), 1.0 / beta);
        }
        return step;
    }

    private double gamma(double x) {
        if (x <= 0) return Double.NaN;
        return Math.exp(lanczosGammaLog(x));
    }

    private double lanczosGammaLog(double z) {
        double[] p = {
                0.99999999999980993,
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };

        if (z < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * z)) - lanczosGammaLog(1 - z);
        }

        z -= 1;
        double x = p[0];
        for (int i = 1; i < p.length; i++) {
            x += p[i] / (z + i);
        }
        double t = z + 7 + 0.5;
        return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(x);
    }

    private void enforceBounds() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                if (positions[i][j] < lb) positions[i][j] = lb;
                if (positions[i][j] > ub) positions[i][j] = ub;
            }
        }
    }

    private double[] computePseudoFitness() {
        double[] fitness = new double[population];
        var archiveObjectives = archive.getObjectives(); // ✅ 关键修正：使用 getObjectives()

        if (archiveObjectives.isEmpty()) {
            Arrays.fill(fitness, 1.0);
            return fitness;
        }

        for (int i = 0; i < population; i++) {
            double minDist = Double.MAX_VALUE;
            ObjectiveValues currentObj = evaluate(positions[i]);
            for (ObjectiveValues solObj : archiveObjectives) {
                double dist = euclideanDistance(currentObj.values, solObj.values);
                if (dist < minDist) minDist = dist;
            }
            fitness[i] = 1.0 / (minDist + 1e-6);
        }
        return fitness;
    }

    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return Math.sqrt(sum);
    }

    public ParetoArchive execute() {
        while (evaluations < maxFEs) {
            int[] indices = getRandomPermutation(population);
            double[][] Y = new double[population][dim];
            for (int i = 0; i < population; i++) {
                System.arraycopy(flockMemoryX[indices[i]], 0, Y[i], 0, dim);
            }

            double[] pseudoF = computePseudoFitness();
            double maxF = Arrays.stream(pseudoF).max().orElse(1.0);
            double[] E = new double[population];
            for (int i = 0; i < population; i++) {
                E[i] = pseudoF[i] / (maxF + 1e-10);
            }

            double totalDist = 0.0;
            for (int i = 0; i < population; i++) {
                double dist = 0.0;
                for (int j = 0; j < dim; j++) {
                    dist += Math.pow(positions[i][j] - Y[i][j], 2);
                }
                totalDist += Math.sqrt(dist) / dim;
            }
            double omega = totalDist / population;

            double[] D = new double[population];
            for (int i = 0; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    double v = E[i] * Math.abs(positions[i][j] - Y[i][j]);
                    positions[i][j] = Y[i][j] + Math.cos(random.nextDouble() * Math.PI) * v;
                }
                double dist = 0.0;
                for (int j = 0; j < dim; j++) {
                    dist += Math.pow(positions[i][j] - Y[i][j], 2);
                }
                D[i] = Math.sqrt(dist);
            }

            double avgD = Arrays.stream(D).average().orElse(0.0);
            double sigma = avgD * ((1.0 - (double) evaluations / maxFEs) + 0.5);

            for (int i = 0; i < population; i++) {
                if (D[i] < sigma) {
                    for (int j = 0; j < dim; j++) {
                        Y[i][j] = Y[i][j] + random.nextDouble() * E[i] * (positions[i][j] - Y[i][j]);
                    }
                    double[] levy = levyFlight(dim);
                    double decay = Math.exp(1.0 - (double) evaluations / maxFEs);
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = Y[i][j] + levy[j] * omega * decay;
                    }
                } else {
                    double[] leader = archive.selectLeader();
                    if (leader == null || leader.length != dim) {
                        List<double[]> sols = archive.getSolutions();
                        if (sols.isEmpty()) {
                            leader = positions[random.nextInt(population)];
                        } else {
                            leader = sols.get(random.nextInt(sols.size()));
                        }
                    }
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = leader[j] + Math.cos(random.nextDouble() * Math.PI) * (positions[i][j] - leader[j]);
                    }
                }
            }

            enforceBounds();

            for (int i = 0; i < population && evaluations < maxFEs; i++) {
                evaluateAndArchive(i);
            }

            for (int i = 0; i < population; i++) {
                ObjectiveValues currentObj = evaluate(positions[i]);
                if (!isDominated(currentObj, flockMemoryF[i])) {
                    flockMemoryF[i] = currentObj;
                    flockMemoryX[i] = positions[i].clone();
                }
            }
        }

        return archive;
    }

    private boolean isDominated(ObjectiveValues obj1, ObjectiveValues obj2) {
        if (obj2 == null) return false;
        boolean atLeastOneWorse = false;
        for (int i = 0; i < obj1.values.length; i++) {
            if (obj1.values[i] < obj2.values[i]) {
                return false;
            } else if (obj1.values[i] > obj2.values[i]) {
                atLeastOneWorse = true;
            }
        }
        return atLeastOneWorse;
    }

    private int[] getRandomPermutation(int n) {
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
        return arr;
    }

    public ParetoArchive getArchive() {
        return archive;
    }
}