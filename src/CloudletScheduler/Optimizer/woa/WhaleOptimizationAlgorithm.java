package CloudletScheduler.Optimizer.woa;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * @author : LA4AM12
 * @create : 2023-02-10 16:16:20
 * @description : class implements the whale optimization algorithm
 */

/**
 * 鲸鱼优化算法类
 * 该算法模拟鲸鱼的狩猎行为来进行优化操作，主要用于解决函数优化问题
 */
public class WhaleOptimizationAlgorithm {
    // 优化函数接口，用于计算适应度值
    private final OptFunction optFunction;
    // 搜索空间的下界和上界
    private double lb, ub;
    // 种群大小，即鲸鱼的数量
    private int population;
    // 问题的维度
    private final int dim;
    // 最大迭代次数
    private final int maxIter;
    // 鲸鱼的位置数组
    private double[][] positions;
    // 是否进行最小化优化
    private boolean minimize;
    // 收敛曲线，记录每次迭代的最佳适应度值
    private double[] convergenceCurve;
    // 最优位置，即最优解的位置
    private double[] optimalPos;
    // 最优解的适应度值
    private double optimalScore;
    // 变异概率，用于增加种群多样性
    double mutationRate = 0.005;
    private static final Random random = new Random();
    // t分布的自由度
    private static final int DEGREES_OF_FREEDOM = 5;
    // 差分权重因子
    private static final double F = 0.8; // 你可以根据需要调整 F 值

    /**
     * 构造函数，初始化鲸鱼优化算法的参数
     *
     * @param optFunction 优化函数对象，用于计算适应度值
     * @param population  种群大小，即鲸鱼的数量
     * @param lb          搜索空间的下界
     * @param ub          搜索空间的上界
     * @param dim         问题的维度
     * @param maxIter     最大迭代次数
     * @param minimize    是否进行最小化优化
     */
    public WhaleOptimizationAlgorithm(OptFunction optFunction, int population, int lb, int ub, int dim, int maxIter, boolean minimize) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        // 初始化种群位置数组，用于存储每个鲸鱼的位置
        this.positions = new double[population][dim];
        // 初始化收敛曲线数组，用于记录每次迭代的最优适应度值
        this.convergenceCurve = new double[maxIter];
        this.minimize = minimize;
        // 根据是否最小化，初始化最优解的适应度值
        this.optimalScore = minimize ? Double.MAX_VALUE : -Double.MAX_VALUE;
        // 初始化最优解的位置数组
        this.optimalPos = new double[dim];
        // 初始化种群位置
        initPopulation();
    }

    /**
     * 调整指定代理人的位置，确保其在边界内且为离散值
     * 此方法旨在将代理人的位置调整到最近的离散值，并确保位置值不会超出预定义的上下界
     *
     * @param agentIndex 代理人的索引，用于在位置数组中标识特定代理人的位置数据
     */
    private void adjustPositions(int agentIndex) {
        // 遍历每个维度，调整位置
        for (int j = 0; j < dim; j++) {
            // 将位置调整为最近的离散值
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            // 如果调整后的维度位置低于下界，则设置为下界
            if (positions[agentIndex][j] < lb) {
                positions[agentIndex][j] = lb;
            }
            // 如果调整后的维度位置高于上界，则设置为上界
            if (positions[agentIndex][j] > ub) {
                positions[agentIndex][j] = ub;
            }
        }
    }

    /**
     * 初始化种群位置
     * 该方法用于在搜索空间内随机初始化种群的位置
     * 每个个体的位置是一个dim维的向量，整个种群由population个个体组成
     * 位置的每个维度都在[lb, ub]范围内，以确保初始位置的多样性
     */
    private void initPopulation() {

        // 初始化种群位置数组，行数为种群大小，列数为问题维度
        this.positions = new double[population][dim];

        // 遍历种群中的每个个体
        for (int i = 0; i < population; i++) {
            // 生成初始混沌值
            double x = random.nextDouble();
            // 遍历每个维度
            for (int j = 0; j < dim; j++) {
                // 在[lb, ub]范围内随机生成每个维度的位置值
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPositions(i);
        }
    }
    /**
     * 计算种群中每个搜索代理的适应度值
     * 该方法遍历种群中的每个搜索代理，检查并调整搜索代理的位置，
     * 根据目标函数计算搜索代理的适应度值，并在找到更好的解时更新最优解。
     */
    private void calcFitness() {
        // 遍历种群中的每个搜索代理
        for (int i = 0; i < population; i++) {
            // 将超出搜索空间边界的搜索代理位置调整回边界内
            adjustPositions(i);

            // 计算每个搜索代理的目标函数值
            int[] params = Arrays.stream(positions[i]).mapToInt((x) -> (int) x).toArray();
            double fitness = optFunction.calc(params);

            // 更新最优解
            if (minimize && fitness < optimalScore || !minimize && fitness > optimalScore) {
                optimalScore = fitness;
                System.arraycopy(positions[i], 0, optimalPos, 0, dim);
            }
        }
    }

    /**
     * 更新每个个体的位置
     * 该方法根据灰狼优化算法中的公式，更新种群中每个个体的位置
     * 参数 a 和 a2 是控制算法收敛速度的重要参数
     *
     * @param a  控制参数，影响算法的收敛速度
     * @param a2 控制参数，与参数 a 一起影响算法行为
     */
    private void updatePosition(double a, double a2) {
        Random rand = new Random();
        for (int i = 1; i < population; i++) {
            double r1 = rand.nextDouble();
            double r2 = rand.nextDouble();
            double A = 2.0 * a * r1 - a;
            double C = 2.0 * r2;
            double b = 1.0;
            double l = (a2 - 1.0) * rand.nextDouble() + 1.0;
            double p = rand.nextDouble();

            for (int j = 0; j < dim; j++) {
                if (p < 0.5) {
                    if (Math.abs(A) < 1) {
                        double D_Leader = Math.abs(C * optimalPos[j] - positions[i][j]);
                        positions[i][j] = optimalPos[j] - A * D_Leader;
                    } else {
                        int randWhaleIdx = rand.nextInt(population);
                        double[] randomPos = positions[randWhaleIdx];
                        double D_X_rand = Math.abs(C * randomPos[j] - positions[i][j]);
                        positions[i][j] = randomPos[j] - A * D_X_rand;
                    }
                } else {
                    double distance2Leader = Math.abs(optimalPos[j] - positions[i][j]);
                    positions[i][j] = distance2Leader * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + optimalPos[j];
                }
            }
        }

    }
    /**
     * 执行优化算法的主要循环。
     * <p>
     * 该方法迭代执行优化过程，包括适应度计算、更新收敛曲线和位置更新，直到满足终止条件。
     *
     * @return 返回完成优化过程后的最优解位置整数数组。
     */
    public int[] execute() {
        // 迭代执行优化过程，直到达到最大迭代次数。
        for (int iter = 0; iter < maxIter; iter++) {
            // 计算并更新当前种群的适应度值。
            calcFitness();
            // 记录当前迭代的最优适应度值，用于绘制收敛曲线。
            convergenceCurve[iter] = optimalScore;

            // a 按照公式 (2.3) 从 2 线性减少到 0
            double a = 2.0 - (double) iter * (2.0 / maxIter);

            // a2 按照公式 (3.12) 从 -1 线性减少到 -2
            double a2 = -1.0 + (double) iter * (-1.0 / maxIter);

            // 根据当前的 a 和 a2 值更新每个解的位置。
            updatePosition(a, a2);
        }
        // 在循环结束后重新计算适应度，确保最终解的适应度值是最新的。
        calcFitness();
        // 将最优解位置转换为整数数组并返回。
        return Arrays.stream(optimalPos).map(Math::round).mapToInt((x) -> (int) x).toArray();
    }


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
