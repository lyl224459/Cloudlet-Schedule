package CloudletScheduler.Optimizer.hho;

import CloudletScheduler.datacenter.OptFunction;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.Random;

/**
 * Harris Hawks Optimization (HHO) Algorithm in Java
 * 改写自 MATLAB 版本，支持离散整数优化问题
 */
public class HarrisHawksOptimization {

    private final OptFunction optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;
    private double[][] positions;
    private boolean minimize = true; // 默认最小化

    private double[] rabbitLocation;
    private double rabbitEnergy;

    private double[] convergenceCurve;
    private static final Random random = new Random();

    /**
     * 构造函数
     */
    public HarrisHawksOptimization(OptFunction optFunction, int population, double lb, double ub, int dim, int maxIter) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.positions = new double[population][dim];
        this.rabbitLocation = new double[dim];
        this.convergenceCurve = new double[maxIter];

        // 初始化种群
        initPopulation();
        // 初始化兔子能量为正无穷（最小化）
        this.rabbitEnergy = Double.POSITIVE_INFINITY;
    }

    /**
     * 初始化种群位置（均匀随机）
     */
    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPositions(i);
        }
    }

    /**
     * 调整位置到整数并限制在 [lb, ub] 范围内
     */
    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
    }

    /**
     * Levy 飞行生成步长（d 维）
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
     * 计算适应度并更新兔子（最优解）
     */
    private void updateRabbit() {
        for (int i = 0; i < population; i++) {
            adjustPositions(i);
            int[] params = Arrays.stream(positions[i]).mapToInt(x -> (int) x).toArray();
            double fitness = optFunction.calc(params);

            if (fitness < rabbitEnergy) { // 最小化
                rabbitEnergy = fitness;
                System.arraycopy(positions[i], 0, rabbitLocation, 0, dim);
            }
        }
    }

    /**
     * 执行 HHO 算法主循环
     */
    public int[] execute() {
        for (int t = 0; t < maxIter; t++) {
            updateRabbit();
            convergenceCurve[t] = rabbitEnergy;

            double E1 = 2.0 * (1.0 - (double) t / maxIter); // 兔子能量衰减因子

            for (int i = 0; i < population; i++) {
                double E0 = 2.0 * random.nextDouble() - 1.0; // [-1, 1]
                double escapingEnergy = E1 * E0;

                if (Math.abs(escapingEnergy) >= 1) {
                    // ========== Exploration ==========
                    double q = random.nextDouble();
                    int randIdx = random.nextInt(population);
                    double[] X_rand = positions[randIdx];

                    if (q < 0.5) {
                        // 基于其他个体的栖息
                        for (int j = 0; j < dim; j++) {
                            positions[i][j] = X_rand[j] - random.nextDouble() *
                                    Math.abs(X_rand[j] - 2 * random.nextDouble() * positions[i][j]);
                        }
                    } else {
                        // 随机高树栖息
                        double meanX = Arrays.stream(positions).mapToDouble(row ->
                                Arrays.stream(row).sum() / dim
                        ).sum() / population;
                        for (int j = 0; j < dim; j++) {
                            positions[i][j] = (rabbitLocation[j] - meanX) -
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
                            positions[i][j] = rabbitLocation[j] - escapingEnergy *
                                    Math.abs(rabbitLocation[j] - positions[i][j]);
                        }

                    } else if (r >= 0.5 && Math.abs(escapingEnergy) >= 0.5) {
                        // Soft besiege
                        for (int j = 0; j < dim; j++) {
                            positions[i][j] = (rabbitLocation[j] - positions[i][j]) -
                                    escapingEnergy * Math.abs(jumpStrength * rabbitLocation[j] - positions[i][j]);
                        }

                    } else if (r < 0.5 && Math.abs(escapingEnergy) >= 0.5) {
                        // Soft besiege with rapid dives
                        double[] X1 = new double[dim];
                        for (int j = 0; j < dim; j++) {
                            X1[j] = rabbitLocation[j] - escapingEnergy *
                                    Math.abs(jumpStrength * rabbitLocation[j] - positions[i][j]);
                        }
                        boundAndAdjust(X1);
                        if (evaluate(X1) < evaluate(positions[i])) {
                            System.arraycopy(X1, 0, positions[i], 0, dim);
                        } else {
                            double[] levy = levyFlight(dim);
                            double[] X2 = new double[dim];
                            for (int j = 0; j < dim; j++) {
                                X2[j] = rabbitLocation[j] - escapingEnergy *
                                        Math.abs(jumpStrength * rabbitLocation[j] - positions[i][j]) +
                                        random.nextDouble() * levy[j];
                            }
                            boundAndAdjust(X2);
                            if (evaluate(X2) < evaluate(positions[i])) {
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
                            X1[j] = rabbitLocation[j] - escapingEnergy *
                                    Math.abs(jumpStrength * rabbitLocation[j] - meanX);
                        }
                        boundAndAdjust(X1);
                        if (evaluate(X1) < evaluate(positions[i])) {
                            System.arraycopy(X1, 0, positions[i], 0, dim);
                        } else {
                            double[] levy = levyFlight(dim);
                            double[] X2 = new double[dim];
                            for (int j = 0; j < dim; j++) {
                                X2[j] = rabbitLocation[j] - escapingEnergy *
                                        Math.abs(jumpStrength * rabbitLocation[j] - meanX) +
                                        random.nextDouble() * levy[j];
                            }
                            boundAndAdjust(X2);
                            if (evaluate(X2) < evaluate(positions[i])) {
                                System.arraycopy(X2, 0, positions[i], 0, dim);
                            }
                        }
                    }
                }
            }
        }

        // Final evaluation
        updateRabbit();
        return Arrays.stream(rabbitLocation).map(Math::round).mapToInt(x -> (int) x).toArray();
    }

    /**
     * 辅助方法：评估一个解的适应度（自动转为整数）
     */
    private double evaluate(double[] sol) {
        int[] params = Arrays.stream(sol).mapToInt(x -> (int) Math.round(x)).toArray();
        return optFunction.calc(params);
    }

    /**
     * 辅助方法：边界处理 + 离散化（用于临时解如 X1, X2）
     */
    private void boundAndAdjust(double[] sol) {
        for (int j = 0; j < dim; j++) {
            sol[j] = Math.round(sol[j]);
            if (sol[j] < lb) sol[j] = lb;
            if (sol[j] > ub) sol[j] = ub;
        }
    }

    // ================== Getters ==================

    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    public double[] getRabbitLocation() {
        return rabbitLocation;
    }

    public double getRabbitEnergy() {
        return rabbitEnergy;
    }

    public double[][] getPositions() {
        return positions;
    }
}