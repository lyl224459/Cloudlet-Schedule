package CloudletScheduler.MOOptimizer.mosequoia;

import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import CloudletScheduler.MOOptimizer.ParetoArchive;

import java.util.Arrays;
import java.util.Random;

/**
 * 多目标红杉优化算法（Multi-Objective Sequoia Optimization Algorithm, MO-SequoiaOA）
 */
public class MOSequoiaOptimizationAlgorithm {
    private final OptFunctionMulti optFunction; // 多目标函数
    private final int popSize;                  // 种群大小
    private final int maxIter;                  // 最大迭代次数
    private final double lb;                    // 下界
    private final double ub;                    // 上界
    private final int dim;                      // 维度

    private double[][] population;              // 当前种群（实数编码）
    private ObjectiveValues[] objectives;       // 每个个体的多目标值
    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive; // 第一代Pareto存档快照              // Pareto 存档

    private static final Random random = new Random();

    public MOSequoiaOptimizationAlgorithm(
            OptFunctionMulti optFunction,
            int popSize,
            int maxIter,
            double lb,
            double ub,
            int dim,
            int archiveMaxSize) {
        this.optFunction = optFunction;
        this.popSize = popSize;
        this.maxIter = maxIter;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.archive = new ParetoArchive(archiveMaxSize);
        this.population = new double[popSize][dim];
        this.objectives = new ObjectiveValues[popSize];

        initializePopulation();
    }

    /**
     * 初始化种群并评估，加入存档
     */
    private void initializePopulation() {
        for (int i = 0; i < popSize; i++) {
            for (int j = 0; j < dim; j++) {
                population[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            objectives[i] = evaluate(population[i]);
            archive.add(population[i].clone(), objectives[i]);
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
     * 评估个体（转为整数后调用多目标函数）
     */
    private ObjectiveValues evaluate(double[] sol) {
        int[] assignment = Arrays.stream(sol)
                .mapToInt(x -> (int) Math.round(x))
                .toArray();
        return optFunction.evaluate(assignment);
    }

    /**
     * 边界处理
     */
    private void enforceBounds(double[] sol) {
        for (int j = 0; j < dim; j++) {
            if (sol[j] < lb) sol[j] = lb;
            if (sol[j] > ub) sol[j] = ub;
        }
    }

    /**
     * 执行 MO-SequoiaOA 主循环
     *
     * @return Pareto 存档（包含非支配解集）
     */
    public ParetoArchive execute() {
        final int eliteSize = 2;

        for (int iter = 0; iter < maxIter; iter++) {
            double fireProbability = Math.max(0.3 - 0.15 * ((double) iter / maxIter), 0.1);
            double mutationRate = Math.max(0.2 - 0.1 * ((double) iter / maxIter), 0.02);

            // 从存档中选择 leader 作为当前最优引导（用于生长和局部搜索）
            double[] leader = archive.selectLeader();
            if (leader == null || leader.length != dim) {
                // fallback: 使用第一个非支配解
                leader = archive.getSolutions().isEmpty() ? new double[dim] : archive.getSolutions().get(0).clone();
            }

            // 集体生长：以 leader 和 top half 为参考（简化：仅用 leader）
            double[] meanRef = leader.clone(); // 可扩展为 top-k 平均

            // 更新整个种群
            for (int i = 0; i < popSize; i++) {
                for (int j = 0; j < dim; j++) {
                    population[i][j] += random.nextGaussian() * (meanRef[j] - population[i][j]);
                }
            }

            // 火灾扰动
            if (random.nextDouble() < fireProbability) {
                for (int i = 0; i < popSize; i++) {
                    for (int j = 0; j < dim; j++) {
                        population[i][j] += random.nextGaussian() * 0.5;
                    }
                }
            }

            // 繁殖与交叉变异
            for (int i = 0; i < popSize - 1; i += 2) {
                double alpha = random.nextDouble();
                double[] offspring1 = new double[dim];
                double[] offspring2 = new double[dim];

                for (int j = 0; j < dim; j++) {
                    offspring1[j] = alpha * population[i][j] + (1 - alpha) * population[i + 1][j];
                    offspring2[j] = alpha * population[i + 1][j] + (1 - alpha) * population[i][j];
                }

                if (random.nextDouble() < mutationRate) {
                    for (int j = 0; j < dim; j++) {
                        offspring1[j] += random.nextGaussian() * 0.3;
                        offspring2[j] += random.nextGaussian() * 0.3;
                    }
                }

                enforceBounds(offspring1);
                enforceBounds(offspring2);

                System.arraycopy(offspring1, 0, population[i], 0, dim);
                System.arraycopy(offspring2, 0, population[i + 1], 0, dim);
            }

            // 局部搜索：在 leader 附近扰动
            double[] localSearch = new double[dim];
            for (int j = 0; j < dim; j++) {
                localSearch[j] = leader[j] + 0.1 * random.nextGaussian();
            }
            enforceBounds(localSearch);
            ObjectiveValues localObj = evaluate(localSearch);
            archive.add(localSearch.clone(), localObj);

            // 重新评估整个种群并更新存档
            for (int i = 0; i < popSize; i++) {
                enforceBounds(population[i]); // 再次确保边界
                objectives[i] = evaluate(population[i]);
                archive.add(population[i].clone(), objectives[i]);
            }

            // 精英保留：从存档中选 eliteSize 个高质量解替换最差个体
            // 简化策略：随机选或按拥挤距离选；此处我们用 leader 替换部分个体
            if (!archive.isEmpty()) {
                double[] elite = archive.selectLeader();
                if (elite != null && elite.length == dim) {
                    // 替换最后几个个体（最差区域）
                    for (int i = popSize - eliteSize; i < popSize; i++) {
                        System.arraycopy(elite, 0, population[i], 0, dim);
                    }
                }
            }
        }

        System.out.printf("MO-SequoiaOA completed. Archive size = %d%n", archive.size());
        return archive;
    }
}