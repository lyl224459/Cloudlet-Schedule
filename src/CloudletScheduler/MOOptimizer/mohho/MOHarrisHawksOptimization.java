package CloudletScheduler.MOOptimizer.mohho;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.Random;

/**
 * Multi-Objective Harris Hawks Optimization (MO-HHO)
 * 支持 Pareto 优化，使用外部存档保存非支配解。
 */
public class MOHarrisHawksOptimization {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;
    private double[][] positions;
    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive; // 第一代Pareto存档快照

    private static final Random random = new Random();

    public MOHarrisHawksOptimization(
            OptFunctionMulti optFunction,
            int population,
            double lb,
            double ub,
            int dim,
            int maxIter,
            int archiveMaxSize) {

        if (lb >= ub) throw new IllegalArgumentException("lb must be less than ub");
        if (dim <= 0 || population <= 0 || maxIter <= 0)
            throw new IllegalArgumentException("Invalid parameters");

        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.positions = new double[population][dim];
        this.archive = new ParetoArchive(archiveMaxSize);

        initPopulation();
    }

    /**
     * 初始化种群并立即评估，填充 Pareto 存档
     */
    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPositions(i);
            int[] assignment = Arrays.stream(positions[i]).mapToInt(v -> (int) v).toArray();
            ObjectiveValues obj = optFunction.evaluate(assignment);
            archive.add(positions[i], obj);
        }
        
        // 保存第一代Pareto存档快照（初始化完成后）
        firstGenerationArchive = archive.deepCopy();
    }
    
    /**
     * 获取第一代Pareto存档快照
     * @return 第一代Pareto存档
     */
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }

    /**
     * 调整位置到合法整数 VM 索引
     */
    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
    }

    /**
     * Levy 飞行（d 维）
     */
    private double[] levyFlight(int d) {
        double beta = 1.5;
        double sigma = Math.pow(
                (Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2)) /
                        (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta
        );
        double[] u = new double[d];
        double[] v = new double[d];
        for (int i = 0; i < d; i++) {
            u[i] = random.nextGaussian() * sigma;
            v[i] = random.nextGaussian();
        }
        double[] step = new double[d];
        for (int i = 0; i < d; i++) {
            step[i] = u[i] / Math.pow(Math.abs(v[i]), 1.0 / beta);
        }
        return step;
    }

    /**
     * 边界处理 + 整数化（用于临时解）
     */
    private void boundAndAdjust(double[] sol) {
        for (int j = 0; j < dim; j++) {
            sol[j] = Math.round(sol[j]);
            if (sol[j] < lb) sol[j] = lb;
            if (sol[j] > ub) sol[j] = ub;
        }
    }

    /**
     * 评估一个解并返回其 ObjectiveValues
     */
    private ObjectiveValues evaluate(double[] sol) {
        int[] assignment = Arrays.stream(sol).mapToInt(x -> (int) Math.round(x)).toArray();
        return optFunction.evaluate(assignment);
    }

    /**
     * 执行 MO-HHO 主循环
     */
    public ParetoArchive execute() {
        for (int t = 0; t < maxIter; t++) {
            double E1 = 2.0 * (1.0 - (double) t / maxIter); // 兔子能量衰减因子

            // 从存档中选择引导解（leader）
            double[] rabbit = archive.selectLeader();
            if (rabbit == null || rabbit.length != dim) {
                if (!archive.getSolutions().isEmpty()) {
                    rabbit = archive.getSolutions().get(0);
                } else {
                    rabbit = positions[random.nextInt(population)].clone();
                }
            }

            for (int i = 0; i < population; i++) {
                double E0 = 2.0 * random.nextDouble() - 1.0; // [-1, 1]
                double escapingEnergy = E1 * E0;

                if (Math.abs(escapingEnergy) >= 1) {
                    // ========== Exploration ==========
                    double q = random.nextDouble();
                    int randIdx = random.nextInt(population);
                    double[] X_rand = positions[randIdx];

                    if (q < 0.5) {
                        for (int j = 0; j < dim; j++) {
                            positions[i][j] = X_rand[j] - random.nextDouble() *
                                    Math.abs(X_rand[j] - 2 * random.nextDouble() * positions[i][j]);
                        }
                    } else {
                        double meanX = Arrays.stream(positions).mapToDouble(row ->
                                Arrays.stream(row).sum() / dim
                        ).sum() / population;
                        for (int j = 0; j < dim; j++) {
                            positions[i][j] = (rabbit[j] - meanX) -
                                    random.nextDouble() * ((ub - lb) * random.nextDouble() + lb);
                        }
                    }

                } else {
                    // ========== Exploitation ==========
                    double r = random.nextDouble();
                    double jumpStrength = 2.0 * (1.0 - random.nextDouble());

                    if (r >= 0.5 && Math.abs(escapingEnergy) < 0.5) {
                        // Hard besiege
                        for (int j = 0; j < dim; j++) {
                            positions[i][j] = rabbit[j] - escapingEnergy *
                                    Math.abs(rabbit[j] - positions[i][j]);
                        }

                    } else if (r >= 0.5 && Math.abs(escapingEnergy) >= 0.5) {
                        // Soft besiege
                        for (int j = 0; j < dim; j++) {
                            positions[i][j] = (rabbit[j] - positions[i][j]) -
                                    escapingEnergy * Math.abs(jumpStrength * rabbit[j] - positions[i][j]);
                        }

                    } else if (r < 0.5 && Math.abs(escapingEnergy) >= 0.5) {
                        // Soft besiege with rapid dives
                        double[] X1 = new double[dim];
                        for (int j = 0; j < dim; j++) {
                            X1[j] = rabbit[j] - escapingEnergy *
                                    Math.abs(jumpStrength * rabbit[j] - positions[i][j]);
                        }
                        boundAndAdjust(X1);
                        if (dominates(evaluate(X1), evaluate(positions[i]))) {
                            System.arraycopy(X1, 0, positions[i], 0, dim);
                        } else {
                            double[] levy = levyFlight(dim);
                            double[] X2 = new double[dim];
                            for (int j = 0; j < dim; j++) {
                                X2[j] = rabbit[j] - escapingEnergy *
                                        Math.abs(jumpStrength * rabbit[j] - positions[i][j]) +
                                        random.nextDouble() * levy[j];
                            }
                            boundAndAdjust(X2);
                            if (dominates(evaluate(X2), evaluate(positions[i]))) {
                                System.arraycopy(X2, 0, positions[i], 0, dim);
                            }
                        }

                    } else if (r < 0.5 && Math.abs(escapingEnergy) < 0.5) {
                        // Hard besiege with rapid dives
                        double meanX = Arrays.stream(positions).mapToDouble(row ->
                                Arrays.stream(row).sum() / dim
                        ).sum() / population;

                        double[] X1 = new double[dim];
                        for (int j = 0; j < dim; j++) {
                            X1[j] = rabbit[j] - escapingEnergy *
                                    Math.abs(jumpStrength * rabbit[j] - meanX);
                        }
                        boundAndAdjust(X1);
                        if (dominates(evaluate(X1), evaluate(positions[i]))) {
                            System.arraycopy(X1, 0, positions[i], 0, dim);
                        } else {
                            double[] levy = levyFlight(dim);
                            double[] X2 = new double[dim];
                            for (int j = 0; j < dim; j++) {
                                X2[j] = rabbit[j] - escapingEnergy *
                                        Math.abs(jumpStrength * rabbit[j] - meanX) +
                                        random.nextDouble() * levy[j];
                            }
                            boundAndAdjust(X2);
                            if (dominates(evaluate(X2), evaluate(positions[i]))) {
                                System.arraycopy(X2, 0, positions[i], 0, dim);
                            }
                        }
                    }
                }
            }

            // 评估更新后的种群并更新存档
            for (int i = 0; i < population; i++) {
                adjustPositions(i);
                int[] assignment = Arrays.stream(positions[i]).mapToInt(v -> (int) v).toArray();
                ObjectiveValues obj = optFunction.evaluate(assignment);
                archive.add(positions[i], obj);
            }
        }

        return archive;
    }

    /**
     * 判断 obj1 是否支配 obj2（最小化问题）
     */
    private boolean dominates(ObjectiveValues obj1, ObjectiveValues obj2) {
        boolean atLeastOneBetter = false;
        for (int i = 0; i < obj1.values.length; i++) {
            if (obj1.values[i] > obj2.values[i]) {
                return false; // obj1 在某个目标上更差
            }
            if (obj1.values[i] < obj2.values[i]) {
                atLeastOneBetter = true;
            }
        }
        return atLeastOneBetter;
    }

    // ================== Getter ==================

    public ParetoArchive getArchive() {
        return archive;
    }
}