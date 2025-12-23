package CloudletScheduler.Optimizer.pso;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * Particle Swarm Optimization (PSO) Algorithm in Java
 * 改写自 MATLAB 版本，支持离散整数优化（如 Cloudlet-VM 分配）
 */
public class ParticleSwarmOptimization {

    private final OptFunction optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;

    private double[][] positions;      // pos
    private double[][] velocities;     // vel
    private double[][] pBest;          // personal best positions
    private double[] gBest;            // global best position
    private double[] pBestScore;       // personal best scores
    private double gBestScore;         // global best score

    private double[] convergenceCurve;

    private static final Random random = new Random();

    // PSO 参数
    private static final double W_MAX = 0.9;
    private static final double W_MIN = 0.2;
    private static final double C1 = 2.0;
    private static final double C2 = 2.0;

    /**
     * 构造函数
     */
    public ParticleSwarmOptimization(OptFunction optFunction, int population, double lb, double ub, int dim, int maxIter) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;

        // 初始化数组
        this.positions = new double[population][dim];
        this.velocities = new double[population][dim];
        this.pBest = new double[population][dim];
        this.pBestScore = new double[population];
        this.gBest = new double[dim];
        this.convergenceCurve = new double[maxIter];

        // 初始化种群和速度
        initPopulation();
        Arrays.fill(pBestScore, Double.POSITIVE_INFINITY);
        this.gBestScore = Double.POSITIVE_INFINITY;
    }

    /**
     * 初始化位置和速度
     */
    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
                velocities[i][j] = random.nextDouble(); // 初始速度 [0,1)
            }
            adjustPositions(i);
        }
    }

    /**
     * 调整位置为整数并限制在 [lb, ub] 范围内
     */
    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
    }

    /**
     * 辅助方法：评估一个解的适应度（自动转为整数）
     */
    private double evaluate(double[] sol) {
        int[] params = Arrays.stream(sol).mapToInt(x -> (int) Math.round(x)).toArray();
        return optFunction.calc(params);
    }

    /**
     * 执行 PSO 主循环
     */
    public int[] execute() {
        for (int t = 0; t < maxIter; t++) {
            double w = W_MAX - (double) t * (W_MAX - W_MIN) / maxIter;
            double[] vMax = new double[dim];
            for (int j = 0; j < dim; j++) {
                vMax[j] = (ub - lb) * 0.2; // vMax = (ub - lb) * 0.2
            }

            // 更新每个粒子
            for (int i = 0; i < population; i++) {
                // 边界处理
                for (int j = 0; j < dim; j++) {
                    if (positions[i][j] > ub) positions[i][j] = ub;
                    if (positions[i][j] < lb) positions[i][j] = lb;
                }

                // 计算适应度
                double fitness = evaluate(positions[i]);

                // 更新个体最优
                if (fitness < pBestScore[i]) {
                    pBestScore[i] = fitness;
                    System.arraycopy(positions[i], 0, pBest[i], 0, dim);
                }

                // 更新全局最优
                if (fitness < gBestScore) {
                    gBestScore = fitness;
                    System.arraycopy(positions[i], 0, gBest, 0, dim);
                }
            }

            // 更新速度和位置
            for (int i = 0; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    velocities[i][j] = w * velocities[i][j]
                            + C1 * r1 * (pBest[i][j] - positions[i][j])
                            + C2 * r2 * (gBest[j] - positions[i][j]);

                    // 速度钳制
                    if (velocities[i][j] > vMax[j]) velocities[i][j] = vMax[j];
                    if (velocities[i][j] < -vMax[j]) velocities[i][j] = -vMax[j];

                    // 更新位置
                    positions[i][j] += velocities[i][j];
                }
                // 离散化处理（确保 VM 索引合法）
                adjustPositions(i);
            }

            convergenceCurve[t] = gBestScore;
        }

        // 返回最终最优解（整数）
        return Arrays.stream(gBest).map(Math::round).mapToInt(x -> (int) x).toArray();
    }

    // ================== Getters ==================

    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    public double[] getGBest() {
        return gBest;
    }

    public double getGBestScore() {
        return gBestScore;
    }

    public double[][] getPositions() {
        return positions;
    }
}
