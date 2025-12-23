package CloudletScheduler.Optimizer.hso;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * 混合群智能优化算法（Hybrid Swarm Optimization, HSO）
 * 结合群体协作更新、模拟退火接受准则与自适应高斯变异
 */
public class HybridSwarmOptimization {
    private final OptFunction optFunction; // 目标函数
    private final int nPop;                // 种群大小
    private final int maxIt;               // 最大迭代次数
    private final double varMin;           // 变量下界
    private final double varMax;           // 变量上界
    private final int nVar;                // 决策变量维度

    private double[][] positions;          // 当前种群位置
    private double[][] positionsNew;       // 新位置（用于更新）
    private double[] fitness;              // 当前适应度
    private double[] fitnessNew;           // 新适应度

    private double bestCost;               // 全局最优代价
    private double[] bestPosition;         // 全局最优解
    private double[] bestCosts;            // 收敛曲线

    // 算法参数
    private final double alpha = 3.0;      // 位置更新缩放因子
    private final double initialTemp = 10000.0;
    private final double coolingRate = 0.995;
    private final double initialMutationRate = 0.5;
    private final double finalMutationRate = 0.1;
    private final double initialMutationStep = 0.3;
    private final double finalMutationStep = 0.1;

    private static final Random random = new Random();

    /**
     * 构造函数
     */
    public HybridSwarmOptimization(OptFunction optFunction, int nPop, int maxIt, double varMin, double varMax, int nVar) {
        this.optFunction = optFunction;
        this.nPop = nPop;
        this.maxIt = maxIt;
        this.varMin = varMin;
        this.varMax = varMax;
        this.nVar = nVar;

        this.positions = new double[nPop][nVar];
        this.positionsNew = new double[nPop][nVar];
        this.fitness = new double[nPop];
        this.fitnessNew = new double[nPop];
        this.bestPosition = new double[nVar];
        this.bestCosts = new double[maxIt];

        this.bestCost = Double.POSITIVE_INFINITY;

        initializePopulation();
    }

    /**
     * 初始化种群：在 [varMin, varMax] 内随机生成
     */
    private void initializePopulation() {
        for (int i = 0; i < nPop; i++) {
            for (int j = 0; j < nVar; j++) {
                positions[i][j] = varMin + (varMax - varMin) * random.nextDouble();
            }
            fitness[i] = evaluate(positions[i]);
        }

        // 初始化全局最优
        updateGlobalBest();
    }

    /**
     * 评估个体适应度（调用目标函数）
     */
    private double evaluate(double[] pos) {
        // 若用于任务调度，可转为整数；否则保留实数
        int[] params = Arrays.stream(pos).mapToInt(x -> (int) Math.round(x)).toArray();
        return optFunction.calc(params);
    }

    /**
     * 更新全局最优解
     */
    private void updateGlobalBest() {
        int bestIdx = 0;
        for (int i = 1; i < nPop; i++) {
            if (fitness[i] < fitness[bestIdx]) {
                bestIdx = i;
            }
        }
        if (fitness[bestIdx] < bestCost) {
            bestCost = fitness[bestIdx];
            System.arraycopy(positions[bestIdx], 0, bestPosition, 0, nVar);
        }
    }

    /**
     * 边界处理：将解限制在 [varMin, varMax]
     */
    private void enforceBounds(double[][] pop) {
        for (int i = 0; i < nPop; i++) {
            for (int j = 0; j < nVar; j++) {
                if (pop[i][j] < varMin) pop[i][j] = varMin;
                if (pop[i][j] > varMax) pop[i][j] = varMax;
            }
        }
    }

    /**
     * 计算均方根（RMS）
     */
    private double rms(double[] arr) {
        double sumSq = 0.0;
        for (double v : arr) {
            sumSq += v * v;
        }
        return Math.sqrt(sumSq / arr.length);
    }

    /**
     * 执行 HSO 主循环
     *
     * @return 最优解（整数形式）
     */
    public int[] execute() {
        for (int iter = 0; iter < maxIt; iter++) {
            // 当前温度（模拟退火）
            double temp = initialTemp * Math.pow(coolingRate, iter);

            // 自适应变异参数
            double mutationRate = initialMutationRate - iter * ((initialMutationRate - finalMutationRate) / maxIt);
            double mutationStep = initialMutationStep - iter * ((initialMutationStep - finalMutationStep) / maxIt);

            // 边界处理新位置（安全起见）
            enforceBounds(positionsNew);

            // 评估新位置并执行选择（含 SA 接受准则）
            for (int i = 0; i < nPop; i++) {
                fitnessNew[i] = evaluate(positionsNew[i]);

                if (fitnessNew[i] < fitness[i]) {
                    // 贪婪接受更优解
                    System.arraycopy(positionsNew[i], 0, positions[i], 0, nVar);
                    fitness[i] = fitnessNew[i];
                } else {
                    // 模拟退火：以概率接受劣解
                    double delta = fitnessNew[i] - fitness[i];
                    if (Math.exp(-delta / temp) > random.nextDouble()) {
                        System.arraycopy(positionsNew[i], 0, positions[i], 0, nVar);
                        fitness[i] = fitnessNew[i];
                    }
                }
            }

            // 按适应度排序（升序）
            Integer[] indices = new Integer[nPop];
            for (int i = 0; i < nPop; i++) indices[i] = i;
            Arrays.sort(indices, (i, j) -> Double.compare(fitness[i], fitness[j]));

            double[][] sortedPos = new double[nPop][nVar];
            double[] sortedFit = new double[nPop];
            for (int i = 0; i < nPop; i++) {
                System.arraycopy(positions[indices[i]], 0, sortedPos[i], 0, nVar);
                sortedFit[i] = fitness[indices[i]];
            }
            positions = sortedPos;
            fitness = sortedFit;

            // 更新全局最优
            updateGlobalBest();

            // ========== 位置更新阶段 ==========

            // Step 1: 计算更新系数 updateCoef
            double[] fitnessVec = Arrays.copyOf(fitness, nPop);
            double rmsVal = rms(fitnessVec);
            double[] differences = new double[nPop];
            double sumAbsDiff = 0.0;
            for (int i = 0; i < nPop; i++) {
                differences[i] = rmsVal - fitnessVec[i];
                sumAbsDiff += Math.abs(differences[i]);
            }

            // 避免除零
            if (Math.abs(sumAbsDiff) < 1e-12) {
                sumAbsDiff = 1e-12;
            }

            double[] updateCoef = new double[nPop];
            for (int i = 0; i < nPop; i++) {
                updateCoef[i] = differences[i] / sumAbsDiff;
            }

            // Step 2: 更新每个个体的新位置
            for (int i = 0; i < nPop; i++) {
                for (int j = 0; j < nVar; j++) {
                    double displacement = 0.0;
                    for (int k = 0; k < nPop; k++) {
                        double randWeight = random.nextDouble();
                        displacement += randWeight * updateCoef[k] * (positions[k][j] - positions[i][j]);
                    }
                    positionsNew[i][j] = positions[i][j] + alpha * displacement;
                }
            }

            // Step 3: 自适应高斯变异
            for (int i = 0; i < nPop; i++) {
                if (random.nextDouble() < mutationRate) {
                    for (int j = 0; j < nVar; j++) {
                        positionsNew[i][j] += mutationStep * random.nextGaussian();
                    }
                }
            }

            // 记录收敛曲线
            bestCosts[iter] = bestCost;
        }

        // 返回整数解（适用于调度）
        return Arrays.stream(bestPosition)
                .mapToLong(Math::round)
                .mapToInt(l -> (int) l)
                .toArray();
    }

    // Getter 方法
    public double[] getConvergenceCurve() {
        return bestCosts;
    }

    public double[] getBestPosition() {
        return bestPosition;
    }

    public double getBestCost() {
        return bestCost;
    }
}