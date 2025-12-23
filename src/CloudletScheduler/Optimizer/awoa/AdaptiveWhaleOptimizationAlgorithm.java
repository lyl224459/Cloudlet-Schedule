package CloudletScheduler.Optimizer.awoa;

import CloudletScheduler.datacenter.OptFunction;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.distribution.LevyDistribution;

import java.util.Arrays;
import java.util.Random;

/**
 * 自适应鲸鱼优化算法 (AWOA - Adaptive Whale Optimization Algorithm)
 *
 * 改进策略（与IWOA完全不同）：
 * 1. 非线性自适应收敛因子 - 使用指数递减函数替代线性递减
 * 2. Levy飞行机制 - 增强全局搜索能力，帮助跳出局部最优
 * 3. 反向学习(OBL)初始化 - 同时生成正向和反向解，增加种群多样性
 * 4. 自适应探索/开发权重 - 基于适应度动态调整搜索策略
 * 5. DE/best/1差分进化变异 - 融合差分进化策略增强搜索能力
 */
public class AdaptiveWhaleOptimizationAlgorithm {
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
    private double[] fitnessValues;

    private static final Random random = new Random();

    // Levy飞行参数
    private static final double LEVY_BETA = 1.5;
    // 预计算Levy飞行的sigma值（使用math3的Gamma函数）
    private final double levySigma;
    // Levy分布（使用math3）
    private final LevyDistribution levyDistribution;

    // 差分进化参数
    private static final double DE_F = 0.5;  // 缩放因子
    private static final double DE_CR = 0.3; // 交叉概率

    /**
     * 构造函数
     */
    public AdaptiveWhaleOptimizationAlgorithm(OptFunction optFunction, int population,
                                              double lb, double ub, int dim, int maxIter, boolean minimize) {
        if (lb >= ub) throw new IllegalArgumentException("lb must be less than ub");
        if (dim <= 0 || population <= 0 || maxIter <= 0)
            throw new IllegalArgumentException("Invalid parameters: dim, population, and maxIter must be positive.");

        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.minimize = minimize;
        this.optimalScore = minimize ? Double.MAX_VALUE : -Double.MAX_VALUE;
        this.optimalPos = new double[dim];
        this.positions = new double[population][dim];
        this.convergenceCurve = new double[maxIter];
        this.fitnessValues = new double[population];

        // 使用Apache Commons Math3计算Levy飞行参数
        this.levySigma = Math.pow(
                Gamma.gamma(1 + LEVY_BETA) * Math.sin(Math.PI * LEVY_BETA / 2) /
                        (Gamma.gamma((1 + LEVY_BETA) / 2) * LEVY_BETA * Math.pow(2, (LEVY_BETA - 1) / 2)),
                1.0 / LEVY_BETA
        );
        this.levyDistribution = new LevyDistribution(0, 1);

        initPopulationWithOBL();
    }

    /**
     * 边界约束处理
     */
    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
    }

    /**
     * 反向学习(Opposition-Based Learning)初始化
     * 同时生成正向解和反向解，选择较优的一半作为初始种群
     */
    private void initPopulationWithOBL() {
        double[][] candidates = new double[population * 2][dim];
        double[] candidateFitness = new double[population * 2];

        // 生成正向解和反向解
        for (int i = 0; i < population; i++) {
            // 正向解 - 随机初始化
            for (int j = 0; j < dim; j++) {
                candidates[i][j] = lb + (ub - lb) * random.nextDouble();
            }

            // 反向解 - OBL策略
            for (int j = 0; j < dim; j++) {
                candidates[i + population][j] = lb + ub - candidates[i][j];
            }
        }

        // 计算所有候选解的适应度
        for (int i = 0; i < population * 2; i++) {
            // 边界约束
            for (int j = 0; j < dim; j++) {
                candidates[i][j] = Math.round(candidates[i][j]);
                if (candidates[i][j] < lb) candidates[i][j] = lb;
                if (candidates[i][j] > ub) candidates[i][j] = ub;
            }
            int[] params = Arrays.stream(candidates[i]).mapToInt(x -> (int) Math.round(x)).toArray();
            candidateFitness[i] = optFunction.calc(params);
        }

        // 创建索引数组并按适应度排序
        Integer[] indices = new Integer[population * 2];
        for (int i = 0; i < indices.length; i++) indices[i] = i;

        // 根据最小化/最大化目标排序
        if (minimize) {
            Arrays.sort(indices, (a, b) -> Double.compare(candidateFitness[a], candidateFitness[b]));
        } else {
            Arrays.sort(indices, (a, b) -> Double.compare(candidateFitness[b], candidateFitness[a]));
        }

        // 选择最优的population个解作为初始种群
        for (int i = 0; i < population; i++) {
            int idx = indices[i];
            System.arraycopy(candidates[idx], 0, positions[i], 0, dim);
            fitnessValues[i] = candidateFitness[idx];

            // 更新全局最优
            if (i == 0) {
                optimalScore = candidateFitness[idx];
                System.arraycopy(candidates[idx], 0, optimalPos, 0, dim);
            }
        }
    }

    /**
     * 计算适应度并更新最优解
     */
    private void calcFitness() {
        for (int i = 0; i < population; i++) {
            adjustPositions(i);
            int[] params = Arrays.stream(positions[i]).mapToInt(x -> (int) Math.round(x)).toArray();
            double fitness = optFunction.calc(params);
            fitnessValues[i] = fitness;

            if (minimize && fitness < optimalScore || !minimize && fitness > optimalScore) {
                optimalScore = fitness;
                System.arraycopy(positions[i], 0, optimalPos, 0, dim);
            }
        }
    }

    /**
     * 非线性自适应收敛因子
     * 使用指数递减函数，前期保持较大值增强全局搜索，后期快速收敛
     */
    private double getAdaptiveA(int iter) {
        // a = 2 * exp(-2 * (iter/maxIter)^2) - 从2指数递减到约0
        double ratio = (double) iter / maxIter;
        return 2.0 * Math.exp(-2.0 * ratio * ratio);
    }

    /**
     * 自适应螺旋参数
     */
    private double getAdaptiveA2(int iter) {
        // 非线性从-1到-2
        double ratio = (double) iter / maxIter;
        return -1.0 - Math.pow(ratio, 0.5);
    }

    /**
     * 计算自适应探索权重
     * 基于当前个体适应度与全局最优的差距动态调整
     */
    private double getAdaptiveWeight(int agentIndex) {
        double avgFitness = 0;
        for (int i = 0; i < population; i++) {
            avgFitness += fitnessValues[i];
        }
        avgFitness /= population;

        double diff = Math.abs(fitnessValues[agentIndex] - optimalScore);
        double avgDiff = Math.abs(avgFitness - optimalScore);

        if (avgDiff < 1e-10) return 0.5;

        // 适应度差距越大，权重越大（更倾向于探索）
        return Math.min(1.0, 0.5 + 0.5 * (diff / (avgDiff + 1e-10)));
    }

    /**
     * Levy飞行 - 产生Levy分布的随机步长
     * 使用Apache Commons Math3的Gamma函数和Mantegna算法
     * 增强全局搜索能力，帮助跳出局部最优
     */
    private double levyFlight() {
        // 使用预计算的sigma值（基于math3的Gamma函数）
        double u = random.nextGaussian() * levySigma;
        double v = Math.abs(random.nextGaussian());

        return u / Math.pow(v, 1.0 / LEVY_BETA);
    }

    /**
     * 使用Apache Commons Math3的LevyDistribution生成Levy步长（备选方法）
     */
    private double levyFlightMath3() {
        return levyDistribution.sample();
    }

    /**
     * DE/best/1 差分进化变异
     * 使用最优个体作为基向量，增强开发能力
     */
    private void applyDEMutation(int agentIndex) {
        if (random.nextDouble() > DE_CR) return;

        // 随机选择两个不同的个体
        int r1, r2;
        do { r1 = random.nextInt(population); } while (r1 == agentIndex);
        do { r2 = random.nextInt(population); } while (r2 == agentIndex || r2 == r1);

        // DE/best/1变异
        double[] mutant = new double[dim];
        for (int j = 0; j < dim; j++) {
            mutant[j] = optimalPos[j] + DE_F * (positions[r1][j] - positions[r2][j]);
            // 边界处理
            mutant[j] = Math.max(lb, Math.min(ub, Math.round(mutant[j])));
        }

        // 计算变异个体适应度
        int[] params = Arrays.stream(mutant).mapToInt(x -> (int) Math.round(x)).toArray();
        double mutantFitness = optFunction.calc(params);

        // 贪婪选择
        boolean isBetter = minimize ? (mutantFitness < fitnessValues[agentIndex])
                : (mutantFitness > fitnessValues[agentIndex]);
        if (isBetter) {
            System.arraycopy(mutant, 0, positions[agentIndex], 0, dim);
            fitnessValues[agentIndex] = mutantFitness;
        }
    }

    /**
     * 更新位置 - 核心搜索机制
     */
    private void updatePosition(double a, double a2, int iter) {
        for (int i = 1; i < population; i++) {
            double r1 = random.nextDouble();
            double r2 = random.nextDouble();
            double A = 2.0 * a * r1 - a;
            double C = 2.0 * r2;
            double b = 1.0;
            double l = (a2 - 1.0) * random.nextDouble() + 1.0;
            double p = random.nextDouble();

            // 获取自适应权重
            double w = getAdaptiveWeight(i);

            for (int j = 0; j < dim; j++) {
                double newPos;

                if (p < 0.5) {
                    if (Math.abs(A) < 1) {
                        // 收缩包围机制
                        double D_Leader = Math.abs(C * optimalPos[j] - positions[i][j]);
                        newPos = optimalPos[j] - A * D_Leader;
                    } else {
                        // 随机搜索机制
                        int randWhaleIdx = random.nextInt(population);
                        double[] randomPos = positions[randWhaleIdx];
                        double D_X_rand = Math.abs(C * randomPos[j] - positions[i][j]);
                        newPos = randomPos[j] - A * D_X_rand;
                    }
                } else {
                    // 螺旋更新机制
                    double distance2Leader = Math.abs(optimalPos[j] - positions[i][j]);
                    newPos = distance2Leader * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + optimalPos[j];
                }

                // 应用Levy飞行扰动（概率触发，避免过度干扰）
                if (random.nextDouble() < 0.1 * w) {
                    double levy = levyFlight();
                    newPos = newPos + 0.01 * levy * (ub - lb);
                }

                positions[i][j] = newPos;
            }

            adjustPositions(i);

            // 应用DE变异（后期增强开发能力）
            double progress = (double) iter / maxIter;
            if (progress > 0.5 && random.nextDouble() < 0.3) {
                applyDEMutation(i);
            }
        }
    }

    /**
     * 执行优化
     */
    public int[] execute() {
        for (int iter = 0; iter < maxIter; iter++) {
            calcFitness();
            convergenceCurve[iter] = optimalScore;

            // 非线性自适应收敛因子
            double a = getAdaptiveA(iter);
            double a2 = getAdaptiveA2(iter);

            updatePosition(a, a2, iter);
        }

        calcFitness();
        return Arrays.stream(optimalPos).map(Math::round).mapToInt(x -> (int) x).toArray();
    }

    // Getter方法
    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    public double[] getLeaderPos() {
        return optimalPos;
    }

    public double getOptimalScore() {
        return optimalScore;
    }

    public double[][] getPositions() {
        return positions;
    }
}
