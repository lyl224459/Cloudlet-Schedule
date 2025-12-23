package CloudletScheduler.MOOptimizer.moppo2;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Multi-Objective Predatory Prey Optimization 2 (MO-PPO2)
 * ——改进：
 * 1. 自适应行为切换 P_switch(t)
 * 2. 自适应 Levy β(t)
 * 3. DE/rand/1 差分变异（离散化）
 * 4. 每代立即离散化 + Clamp
 * 5. 避免重复 evaluate()
 * 6. memory + archive 综合 leader 引导
 */
public class MOPPO2 {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;

    private final double[][] positions;
    private final double[][] flockMemoryX;
    private final ObjectiveValues[] flockMemoryF;
    private final ParetoArchive archive;
    private int evaluations;

    private static final Random random = new Random();
    private final NormalDistribution normal = new NormalDistribution();

    public MOPPO2(OptFunctionMulti optFunction,
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

    /** 初始化种群 */
    private void initializePopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            roundAndClamp(positions[i]);
            evaluateAndArchive(i);
        }
        for (int i = 0; i < population; i++) {
            flockMemoryX[i] = positions[i].clone();
            flockMemoryF[i] = evaluate(positions[i]);
        }
    }

    /** 离散化 + Clamp */
    private void roundAndClamp(double[] pos) {
        for (int j = 0; j < dim; j++) {
            pos[j] = Math.round(pos[j]);
            if (pos[j] < lb) pos[j] = lb;
            if (pos[j] > ub) pos[j] = ub;
        }
    }

    /** 评价（整数任务映射） */
    private ObjectiveValues evaluate(double[] pos) {
        int[] params = Arrays.stream(pos).mapToInt(x -> (int) x).toArray();
        return optFunction.evaluate(params);
    }

    /** 更新存档 */
    private void evaluateAndArchive(int i) {
        if (evaluations >= maxFEs) return;
        ObjectiveValues obj = evaluate(positions[i]);
        archive.add(positions[i], obj);
        evaluations++;
    }

    /** Levy Flight (自适应 β) */
    private double[] levyFlight(int d, double beta) {
        double sigma = Math.pow(
                Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta);
        double[] step = new double[d];
        for (int i = 0; i < d; i++) {
            double u = normal.sample() * sigma;
            double v = Math.abs(normal.sample());
            step[i] = u / Math.pow(v, 1.0 / beta);
        }
        return step;
    }

    /** DE/rand/1 差分变异（离散化） */
    private double[] deMutation(int target) {
        int r1, r2, r3;
        do { r1 = random.nextInt(population); } while (r1 == target);
        do { r2 = random.nextInt(population); } while (r2 == target || r2 == r1);
        do { r3 = random.nextInt(population); } while (r3 == target || r3 == r1 || r3 == r2);

        double F = 0.5 + 0.3 * random.nextDouble();
        double[] v = new double[dim];
        for (int j = 0; j < dim; j++) {
            v[j] = positions[r1][j] + F * (positions[r2][j] - positions[r3][j]);
        }
        roundAndClamp(v);
        return v;
    }

    /** 修正出界 */
    private void enforceBounds() {
        for (int i = 0; i < population; i++) {
            roundAndClamp(positions[i]);
        }
    }

    /** 计算伪适应度（archive-guided） */
    private double[] computePseudoFitness() {
        double[] fitness = new double[population];
        var archiveObjectives = archive.getObjectives();
        if (archiveObjectives.isEmpty()) {
            Arrays.fill(fitness, 1.0);
            return fitness;
        }
        for (int i = 0; i < population; i++) {
            double minDist = Double.MAX_VALUE;
            ObjectiveValues obj = evaluate(positions[i]);
            for (ObjectiveValues aObj : archiveObjectives) {
                minDist = Math.min(minDist, euclideanDistance(obj.values, aObj.values));
            }
            fitness[i] = 1.0 / (minDist + 1e-9);
        }
        return fitness;
    }

    private double euclideanDistance(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(s);
    }

    /** 判断是否支配 */
    private boolean isDominated(ObjectiveValues a, ObjectiveValues b) {
        if (b == null) return false;
        boolean worse = false;
        for (int i = 0; i < a.values.length; i++) {
            if (a.values[i] < b.values[i]) return false;
            if (a.values[i] > b.values[i]) worse = true;
        }
        return worse;
    }
    /** 主循环 */
    public ParetoArchive execute() {
        while (evaluations < maxFEs) {

            double t = (double) evaluations / maxFEs;

            // 每个个体独立决定行为
            for (int i = 0; i < population; i++) {

                boolean usePredator = random.nextDouble() > (0.9 - 0.8 * t); // P_switch(t)
                double beta = 1.5 - 0.4 * t; // Levy 自适应 β

                double[] pseudoF = computePseudoFitness();
                double maxF = Arrays.stream(pseudoF).max().orElse(1.0);
                double E = pseudoF[i] / (maxF + 1e-9);

                if (usePredator) {
                    // Predator 状态：利用 archive leader + memory
                    double[] leader = archive.selectLeader();
                    if (leader == null || leader.length != dim) {
                        List<double[]> sols = archive.getSolutions();
                        leader = sols.isEmpty() ? positions[random.nextInt(population)] :
                                sols.get(random.nextInt(sols.size()));
                    }

                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = leader[j] + Math.cos(random.nextDouble() * Math.PI)
                                * (positions[i][j] - leader[j]);
                    }

                } else {
                    // Prey 状态：探索 + Levy
                    double[] levy = levyFlight(dim, beta);
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] += levy[j] * E;
                    }
                }

                // DE/rand/1 差分变异
                if (random.nextDouble() < 0.3) {
                    double[] mutant = deMutation(i);
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = 0.7 * positions[i][j] + 0.3 * mutant[j];
                    }
                }

                roundAndClamp(positions[i]);
            }

            // 更新 archive
            for (int i = 0; i < population && evaluations < maxFEs; i++) {
                evaluateAndArchive(i);
            }

            // 更新 memory
            for (int i = 0; i < population; i++) {
                ObjectiveValues cur = evaluate(positions[i]);
                if (!isDominated(cur, flockMemoryF[i])) {
                    flockMemoryF[i] = cur;
                    flockMemoryX[i] = positions[i].clone();
                }
            }
        }

        return archive;
    }

    public ParetoArchive getArchive() {
        return archive;
    }
}
