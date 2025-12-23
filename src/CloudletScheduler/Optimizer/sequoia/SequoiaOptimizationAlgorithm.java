package CloudletScheduler.Optimizer.sequoia;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * Sequoia 优化算法（Sequoia Optimization Algorithm, SequoiaOA）
 * 模拟红杉树群落生长、火灾适应、繁殖与精英保留机制的元启发式算法
 */
public class SequoiaOptimizationAlgorithm {
    private final OptFunction optFunction; // 目标函数
    private final int popSize;             // 种群大小
    private final int maxIter;             // 最大迭代次数
    private final double lb;               // 搜索下界
    private final double ub;               // 搜索上界
    private final int dim;                 // 问题维度

    private double[][] population;         // 当前种群
    private double[] fitness;              // 个体适应度
    private double bestFitness;            // 全局最优适应度
    private double[] bestSolution;         // 全局最优解
    private double[] convergenceCurve;     // 收敛曲线

    private static final Random random = new Random();

    /**
     * 构造函数
     *
     * @param optFunction 目标函数接口
     * @param popSize     种群大小
     * @param maxIter     最大迭代次数
     * @param lb          搜索下界
     * @param ub          搜索上界
     * @param dim         问题维度
     */
    public SequoiaOptimizationAlgorithm(OptFunction optFunction, int popSize, int maxIter, double lb, double ub, int dim) {
        this.optFunction = optFunction;
        this.popSize = popSize;
        this.maxIter = maxIter;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;

        this.population = new double[popSize][dim];
        this.fitness = new double[popSize];
        this.bestSolution = new double[dim];
        this.convergenceCurve = new double[maxIter];

        initializePopulation();
    }

    /**
     * 初始化种群：在 [lb, ub] 范围内随机生成
     */
    private void initializePopulation() {
        for (int i = 0; i < popSize; i++) {
            for (int j = 0; j < dim; j++) {
                population[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            fitness[i] = evaluate(population[i]);
        }

        // 初始化全局最优
        int bestIdx = getMinIndex(fitness);
        bestFitness = fitness[bestIdx];
        System.arraycopy(population[bestIdx], 0, bestSolution, 0, dim);
    }

    /**
     * 计算适应度值（调用用户定义的目标函数）
     */
    private double evaluate(double[] solution) {
        int[] params = Arrays.stream(solution).mapToInt(x -> (int) Math.round(x)).toArray();
        return optFunction.calc(params);
    }

    /**
     * 获取数组中最小值的索引
     */
    private int getMinIndex(double[] arr) {
        int minIdx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[minIdx]) {
                minIdx = i;
            }
        }
        return minIdx;
    }

    /**
     * 边界处理：将解限制在 [lb, ub] 范围内
     */
    private void enforceBounds(double[] sol) {
        for (int j = 0; j < dim; j++) {
            if (sol[j] < lb) sol[j] = lb;
            if (sol[j] > ub) sol[j] = ub;
        }
    }

    /**
     * 执行 SequoiaOA 主循环
     *
     * @return 全局最优解（整数形式）
     */
    public int[] execute() {
        // 精英数量
        final int eliteSize = 2;

        for (int iter = 0; iter < maxIter; iter++) {
            // 动态调整参数
            double fireProbability = Math.max(0.3 - 0.15 * ((double) iter / maxIter), 0.1);
            double mutationRate = Math.max(0.2 - 0.1 * ((double) iter / maxIter), 0.02);

            // 按适应度排序种群（升序：越小越好）
            Integer[] indices = new Integer[popSize];
            for (int i = 0; i < popSize; i++) indices[i] = i;
            Arrays.sort(indices, (i, j) -> Double.compare(fitness[i], fitness[j]));

            double[][] sortedPop = new double[popSize][dim];
            double[] sortedFit = new double[popSize];
            for (int i = 0; i < popSize; i++) {
                System.arraycopy(population[indices[i]], 0, sortedPop[i], 0, dim);
                sortedFit[i] = fitness[indices[i]];
            }
            population = sortedPop;
            fitness = sortedFit;

            // 保存精英解（前 eliteSize 个）
            double[][] eliteSolutions = new double[eliteSize][dim];
            for (int i = 0; i < eliteSize; i++) {
                System.arraycopy(population[i], 0, eliteSolutions[i], 0, dim);
            }

            // 集体生长：资源共享与网络协作
            int halfPop = (int) Math.round(popSize / 2.0);
            double[] meanTop = new double[dim];
            for (int j = 0; j < dim; j++) {
                for (int i = 0; i < halfPop; i++) {
                    meanTop[j] += population[i][j];
                }
                meanTop[j] /= halfPop;
            }

            // 更新整个种群：X = X + N(0,1) * (meanTop - X)
            for (int i = 0; i < popSize; i++) {
                for (int j = 0; j < dim; j++) {
                    population[i][j] += random.nextGaussian() * (meanTop[j] - population[i][j]);
                }
            }

            // 适应性与韧性机制：模拟火灾扰动
            if (random.nextDouble() < fireProbability) {
                for (int i = 0; i < popSize; i++) {
                    for (int j = 0; j < dim; j++) {
                        population[i][j] += random.nextGaussian() * 0.5;
                    }
                }
            }

            // 繁殖与多样性维护：成对交叉与变异
            for (int i = 0; i < popSize - 1; i += 2) {
                double alpha = random.nextDouble();
                double[] offspring1 = new double[dim];
                double[] offspring2 = new double[dim];

                // 交叉
                for (int j = 0; j < dim; j++) {
                    offspring1[j] = alpha * population[i][j] + (1 - alpha) * population[i + 1][j];
                    offspring2[j] = alpha * population[i + 1][j] + (1 - alpha) * population[i][j];
                }

                // 变异
                if (random.nextDouble() < mutationRate) {
                    for (int j = 0; j < dim; j++) {
                        offspring1[j] += random.nextGaussian() * 0.3;
                        offspring2[j] += random.nextGaussian() * 0.3;
                    }
                }

                // 边界处理
                enforceBounds(offspring1);
                enforceBounds(offspring2);

                // 替换父代
                System.arraycopy(offspring1, 0, population[i], 0, dim);
                System.arraycopy(offspring2, 0, population[i + 1], 0, dim);
            }

            // 局部搜索机制：在当前最优解附近扰动
            double[] localBest = new double[dim];
            for (int j = 0; j < dim; j++) {
                localBest[j] = bestSolution[j] + 0.1 * random.nextGaussian();
            }
            enforceBounds(localBest);
            double localFitness = evaluate(localBest);
            if (localFitness < bestFitness) {
                bestFitness = localFitness;
                System.arraycopy(localBest, 0, bestSolution, 0, dim);
            }

            // 精英保留机制：用精英替换最差个体
            for (int i = 0; i < eliteSize; i++) {
                int worstIdx = popSize - eliteSize + i;
                System.arraycopy(eliteSolutions[i], 0, population[worstIdx], 0, dim);
            }

            // 重新评估整个种群
            for (int i = 0; i < popSize; i++) {
                fitness[i] = evaluate(population[i]);
            }

            // 更新全局最优
            int currentBestIdx = getMinIndex(fitness);
            if (fitness[currentBestIdx] < bestFitness) {
                bestFitness = fitness[currentBestIdx];
                System.arraycopy(population[currentBestIdx], 0, bestSolution, 0, dim);
            }

            // 记录收敛曲线
            convergenceCurve[iter] = bestFitness;

        }

        // 返回整数解
        return Arrays.stream(bestSolution)
                .mapToLong(Math::round)
                .mapToInt(l -> (int) l)
                .toArray();
    }

    // Getter 方法
    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    public double[] getBestSolution() {
        return bestSolution;
    }

    public double getBestFitness() {
        return bestFitness;
    }
}