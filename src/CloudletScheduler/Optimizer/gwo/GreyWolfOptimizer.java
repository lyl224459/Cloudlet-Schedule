package CloudletScheduler.Optimizer.gwo;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * Grey Wolf Optimizer (GWO) in Java
 * 改写自 MATLAB 版本，适用于最小化问题（如 makespan）
 */
public class GreyWolfOptimizer {

    private final OptFunction optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;

    private double[][] positions; // 种群位置

    // Alpha, Beta, Delta
    private double[] alphaPos;
    private double alphaScore = Double.POSITIVE_INFINITY;

    private double[] betaPos;
    private double betaScore = Double.POSITIVE_INFINITY;

    private double[] deltaPos;
    private double deltaScore = Double.POSITIVE_INFINITY;

    private double[] convergenceCurve;

    private static final Random random = new Random();

    /**
     * 构造函数
     */
    public GreyWolfOptimizer(OptFunction optFunction, int population, double lb, double ub, int dim, int maxIter) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;

        this.positions = new double[population][dim];
        this.alphaPos = new double[dim];
        this.betaPos = new double[dim];
        this.deltaPos = new double[dim];
        this.convergenceCurve = new double[maxIter];

        initializePositions();
    }

    /**
     * 初始化种群位置
     */
    private void initializePositions() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustAndEvaluate(i);
        }
    }

    /**
     * 边界处理 + 离散化 + 评估适应度
     */
    private void adjustAndEvaluate(int agentIndex) {
        // 离散化为整数（适用于 VM 索引）
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }

        // 评估
        int[] params = Arrays.stream(positions[agentIndex]).mapToInt(v -> (int) v).toArray();
        double fitness = optFunction.calc(params);

        // 更新 Alpha, Beta, Delta
        if (fitness < alphaScore) {
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, dim);

            betaScore = alphaScore;
            System.arraycopy(alphaPos, 0, betaPos, 0, dim);

            alphaScore = fitness;
            System.arraycopy(positions[agentIndex], 0, alphaPos, 0, dim);
        } else if (fitness < betaScore) {
            deltaScore = betaScore;
            System.arraycopy(betaPos, 0, deltaPos, 0, dim);

            betaScore = fitness;
            System.arraycopy(positions[agentIndex], 0, betaPos, 0, dim);
        } else if (fitness < deltaScore) {
            deltaScore = fitness;
            System.arraycopy(positions[agentIndex], 0, deltaPos, 0, dim);
        }
    }

    /**
     * 执行 GWO 主循环
     */
    public int[] execute() {
        for (int l = 0; l < maxIter; l++) {
            double a = 2.0 - (double) l * (2.0 / maxIter); // a 从 2 线性减到 0

            // 更新每个个体位置
            for (int i = 0; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    // 向 Alpha 靠拢
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    double A1 = 2 * a * r1 - a;
                    double C1 = 2 * r2;
                    double D_alpha = Math.abs(C1 * alphaPos[j] - positions[i][j]);
                    double X1 = alphaPos[j] - A1 * D_alpha;

                    // 向 Beta 靠拢
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A2 = 2 * a * r1 - a;
                    double C2 = 2 * r2;
                    double D_beta = Math.abs(C2 * betaPos[j] - positions[i][j]);
                    double X2 = betaPos[j] - A2 * D_beta;

                    // 向 Delta 靠拢
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A3 = 2 * a * r1 - a;
                    double C3 = 2 * r2;
                    double D_delta = Math.abs(C3 * deltaPos[j] - positions[i][j]);
                    double X3 = deltaPos[j] - A3 * D_delta;

                    // 新位置 = 三者平均
                    positions[i][j] = (X1 + X2 + X3) / 3.0;
                }

                // 边界处理 & 评估
                for (int j = 0; j < dim; j++) {
                    if (positions[i][j] < lb) positions[i][j] = lb;
                    if (positions[i][j] > ub) positions[i][j] = ub;
                }
                adjustAndEvaluate(i);
            }

            convergenceCurve[l] = alphaScore;
        }

        return Arrays.stream(alphaPos).map(Math::round).mapToInt(v -> (int) v).toArray();
    }

    // ================== Getters ==================

    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    public double[] getAlphaPos() {
        return alphaPos;
    }

    public double getAlphaScore() {
        return alphaScore;
    }
}