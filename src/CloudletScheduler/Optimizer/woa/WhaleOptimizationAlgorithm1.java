package CloudletScheduler.Optimizer.woa;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * 鲸鱼优化算法类（增强版：含高斯+差分混合精英变异）
 */
public class WhaleOptimizationAlgorithm1 {
    private final OptFunction optFunction;
    private double lb, ub;
    private int population;
    private final int dim;
    private final int maxIter;
    private double[][] positions;
    private boolean minimize;
    private double[] convergenceCurve;
    private double[] optimalPos;
    private double optimalScore;
    private double mutationRate = 0.05; // 建议提高到 0.05~0.1 以获得更好效果
    private static final Random random = new Random();
    private static final double F = 0.8; // 差分缩放因子

    // 构造函数（保持不变，但建议将 lb/ub 改为 double 类型）
    public WhaleOptimizationAlgorithm1(OptFunction optFunction, int population, double lb, double ub, int dim, int maxIter, boolean minimize) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.positions = new double[population][dim];
        this.convergenceCurve = new double[maxIter];
        this.minimize = minimize;
        this.optimalScore = minimize ? Double.MAX_VALUE : -Double.MAX_VALUE;
        this.optimalPos = new double[dim];
        initPopulation();
    }

    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
    }

    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPositions(i);
        }
    }

    private void calcFitness() {
        for (int i = 0; i < population; i++) {
            adjustPositions(i);
            int[] params = Arrays.stream(positions[i]).mapToInt(x -> (int) x).toArray();
            double fitness = optFunction.calc(params);

            if (minimize && fitness < optimalScore || !minimize && fitness > optimalScore) {
                optimalScore = fitness;
                System.arraycopy(positions[i], 0, optimalPos, 0, dim);
            }
        }
    }

    // 新增：高斯 + 差分混合精英变异
    private void applyEliteMutation(int i) {
        if (random.nextDouble() >= mutationRate) return;

        // 随机选择两个不同于 i 的个体
        int r1, r2;
        do {
            r1 = random.nextInt(population);
        } while (r1 == i);
        do {
            r2 = random.nextInt(population);
        } while (r2 == i || r2 == r1);

        for (int j = 0; j < dim; j++) {
            // 差分扰动：F * (X_r1 - X_r2)
            double diff = F * (positions[r1][j] - positions[r2][j]);
            // 高斯噪声：标准差设为搜索空间的 10%
            double noise = random.nextGaussian() * 0.1 * (ub - lb);
            // 以当前最优解为中心进行扰动（也可用 positions[i][j]）
            positions[i][j] = optimalPos[j] + diff + noise;
        }

        // 确保变异后的位置合法（整数 + 边界）
        adjustPositions(i);
    }

    private void updatePosition(double a, double a2) {
        for (int i = 0; i < population; i++) { // 注意：原代码从 i=1 开始，应包含 i=0（所有个体都更新）
            double r1 = random.nextDouble();
            double r2 = random.nextDouble();
            double A = 2.0 * a * r1 - a;
            double C = 2.0 * r2;
            double b = 1.0;
            double l = (a2 - 1.0) * random.nextDouble() + 1.0;
            double p = random.nextDouble();

            for (int j = 0; j < dim; j++) {
                if (p < 0.5) {
                    if (Math.abs(A) < 1) {
                        double D_Leader = Math.abs(C * optimalPos[j] - positions[i][j]);
                        positions[i][j] = optimalPos[j] - A * D_Leader;
                    } else {
                        int randWhaleIdx = random.nextInt(population);
                        double D_X_rand = Math.abs(C * positions[randWhaleIdx][j] - positions[i][j]);
                        positions[i][j] = positions[randWhaleIdx][j] - A * D_X_rand;
                    }
                } else {
                    double distance2Leader = Math.abs(optimalPos[j] - positions[i][j]);
                    positions[i][j] = distance2Leader * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + optimalPos[j];
                }
            }

            // ✅ 应用精英变异（关键改进）
            applyEliteMutation(i);
        }
    }

    public int[] execute() {
        for (int iter = 0; iter < maxIter; iter++) {
            calcFitness();
            convergenceCurve[iter] = optimalScore;

            double a = 2.0 - (double) iter * (2.0 / maxIter);
            double a2 = -1.0 + (double) iter * (-1.0 / maxIter);

            updatePosition(a, a2);
        }
        calcFitness(); // Final evaluation
        return Arrays.stream(optimalPos).map(Math::round).mapToInt(x -> (int) x).toArray();
    }

    // Getters（保持不变）
    public double[] getConvergenceCurve() { return convergenceCurve; }
    public double[] getLeaderPos() { return optimalPos; }
    public double getOptimalScore() { return optimalScore; }
    public double[][] getPositions() { return positions; }
}