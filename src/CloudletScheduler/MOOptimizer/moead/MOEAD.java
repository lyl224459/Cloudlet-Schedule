package CloudletScheduler.MOOptimizer.moead;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;

import java.util.*;

/**
 * Multi-Objective Evolutionary Algorithm based on Decomposition (MOEA/D)
 * 
 * 基于分解的多目标进化算法，主要特点：
 * 1. 将多目标问题分解为多个单目标子问题
 * 2. 使用权重向量（Weight Vectors）定义子问题
 * 3. 邻域更新机制
 * 4. 切比雪夫分解方法（Tchebycheff Decomposition）
 */
public class MOEAD {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;
    private final int archiveMaxSize;

    private double[][] positions;
    private ObjectiveValues[] objectives;
    private double[][] weightVectors; // 权重向量
    private int[][] neighbors; // 每个子问题的邻域
    private double[] referencePoint; // 参考点（理想点）
    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive;

    private static final Random random = new Random();
    private static final double CROSSOVER_PROB = 0.9;
    private static final double MUTATION_PROB = 1.0 / 100; // 自适应变异概率
    private static final double ETA_C = 20.0;
    private static final double ETA_M = 20.0;
    private static final int NEIGHBORHOOD_SIZE = 20; // 邻域大小

    public MOEAD(
            OptFunctionMulti optFunction,
            int population,
            double lb,
            double ub,
            int dim,
            int maxFEs,
            int archiveMaxSize) {

        if (lb >= ub) throw new IllegalArgumentException("Lower bound must be < upper bound.");
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxFEs = maxFEs;
        this.archiveMaxSize = archiveMaxSize;

        this.positions = new double[population][dim];
        this.objectives = new ObjectiveValues[population];
        this.archive = new ParetoArchive(archiveMaxSize);
        this.weightVectors = new double[population][4]; // 4个目标
        this.neighbors = new int[population][NEIGHBORHOOD_SIZE];
        this.referencePoint = new double[4];

        initializeWeightVectors();
        initializePopulation();
    }

    /**
     * 初始化权重向量（使用均匀分布）
     */
    private void initializeWeightVectors() {
        // 简化方法：为4个目标生成均匀分布的权重向量
        int m = 4; // 目标数量
        int H = population - 1; // 用于生成权重向量的参数
        
        int idx = 0;
        for (int i = 0; i <= H && idx < population; i++) {
            for (int j = 0; j <= H - i && idx < population; j++) {
                for (int k = 0; k <= H - i - j && idx < population; k++) {
                    int l = H - i - j - k;
                    if (l >= 0) {
                        weightVectors[idx][0] = (double) i / H;
                        weightVectors[idx][1] = (double) j / H;
                        weightVectors[idx][2] = (double) k / H;
                        weightVectors[idx][3] = (double) l / H;
                        
                        // 归一化
                        double sum = weightVectors[idx][0] + weightVectors[idx][1] + 
                                    weightVectors[idx][2] + weightVectors[idx][3];
                        if (sum > 0) {
                            for (int obj = 0; obj < m; obj++) {
                                weightVectors[idx][obj] /= sum;
                            }
                        }
                        idx++;
                    }
                }
            }
        }
        
        // 如果权重向量数量不足，随机生成剩余的
        while (idx < population) {
            double sum = 0;
            for (int obj = 0; obj < m; obj++) {
                weightVectors[idx][obj] = random.nextDouble();
                sum += weightVectors[idx][obj];
            }
            for (int obj = 0; obj < m; obj++) {
                weightVectors[idx][obj] /= sum;
            }
            idx++;
        }

        // 初始化邻域（基于权重向量的欧氏距离）
        for (int i = 0; i < population; i++) {
            List<Pair> distances = new ArrayList<>();
            for (int j = 0; j < population; j++) {
                if (i != j) {
                    double dist = euclideanDistance(weightVectors[i], weightVectors[j]);
                    distances.add(new Pair(j, dist));
                }
            }
            distances.sort(Comparator.comparingDouble(p -> p.distance));
            for (int k = 0; k < Math.min(NEIGHBORHOOD_SIZE, distances.size()); k++) {
                neighbors[i][k] = distances.get(k).index;
            }
        }
    }

    private static class Pair {
        int index;
        double distance;
        Pair(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }
    }

    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return Math.sqrt(sum);
    }

    /**
     * 初始化种群
     */
    private void initializePopulation() {
        Arrays.fill(referencePoint, Double.MAX_VALUE);
        
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPosition(i);
            int[] assignment = Arrays.stream(positions[i]).mapToInt(x -> (int) x).toArray();
            objectives[i] = optFunction.evaluate(assignment);
            archive.add(positions[i], objectives[i]);
            
            // 更新参考点
            for (int obj = 0; obj < objectives[i].values.length; obj++) {
                referencePoint[obj] = Math.min(referencePoint[obj], objectives[i].values[obj]);
            }
        }
        
        // 保存第一代Pareto存档快照
        firstGenerationArchive = archive.deepCopy();
    }

    /**
     * 获取第一代Pareto存档
     */
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }

    /**
     * 调整位置
     */
    private void adjustPosition(int idx) {
        for (int j = 0; j < dim; j++) {
            positions[idx][j] = Math.round(positions[idx][j]);
            if (positions[idx][j] < lb) positions[idx][j] = lb;
            if (positions[idx][j] > ub) positions[idx][j] = ub;
        }
    }

    /**
     * 切比雪夫分解函数
     */
    private double tchebycheffFunction(double[] weight, ObjectiveValues obj) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < weight.length && i < obj.values.length; i++) {
            double value = weight[i] * Math.abs(obj.values[i] - referencePoint[i]);
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    /**
     * 模拟二进制交叉
     */
    private void simulatedBinaryCrossover(double[] parent1, double[] parent2, 
                                          double[] offspring) {
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() <= CROSSOVER_PROB) {
                double u = random.nextDouble();
                double beta;
                if (u <= 0.5) {
                    beta = Math.pow(2 * u, 1.0 / (ETA_C + 1));
                } else {
                    beta = Math.pow(1.0 / (2 * (1 - u)), 1.0 / (ETA_C + 1));
                }
                offspring[j] = 0.5 * ((1 + beta) * parent1[j] + (1 - beta) * parent2[j]);
            } else {
                offspring[j] = parent1[j];
            }
        }
    }

    /**
     * 多项式变异
     */
    private void polynomialMutation(double[] individual) {
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() <= MUTATION_PROB) {
                double u = random.nextDouble();
                double delta;
                if (u < 0.5) {
                    delta = Math.pow(2 * u + (1 - 2 * u) * Math.pow(1 - (individual[j] - lb) / (ub - lb), ETA_M + 1),
                            1.0 / (ETA_M + 1)) - 1;
                } else {
                    delta = 1 - Math.pow(2 * (1 - u) + 2 * (u - 0.5) * Math.pow(1 - (ub - individual[j]) / (ub - lb), ETA_M + 1),
                            1.0 / (ETA_M + 1));
                }
                individual[j] += delta * (ub - lb);
            }
        }
    }

    /**
     * 主执行循环
     */
    public ParetoArchive execute() {
        int evaluations = population;

        while (evaluations < maxFEs) {
            // 随机排列种群索引
            int[] permutation = new int[population];
            for (int i = 0; i < population; i++) {
                permutation[i] = i;
            }
            for (int i = population - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temp = permutation[i];
                permutation[i] = permutation[j];
                permutation[j] = temp;
            }

            for (int i = 0; i < population && evaluations < maxFEs; i++) {
                int idx = permutation[i];
                
                // 从邻域中选择两个父代
                int neighborSize = Math.min(NEIGHBORHOOD_SIZE, neighbors[idx].length);
                int p1Idx = neighbors[idx][random.nextInt(neighborSize)];
                int p2Idx = neighbors[idx][random.nextInt(neighborSize)];
                
                // 生成子代
                double[] offspring = new double[dim];
                simulatedBinaryCrossover(positions[p1Idx], positions[p2Idx], offspring);
                polynomialMutation(offspring);
                
                // 边界处理
                for (int j = 0; j < dim; j++) {
                    offspring[j] = Math.round(offspring[j]);
                    if (offspring[j] < lb) offspring[j] = lb;
                    if (offspring[j] > ub) offspring[j] = ub;
                }
                
                // 评估子代
                int[] assignment = Arrays.stream(offspring).mapToInt(x -> (int) x).toArray();
                ObjectiveValues offspringObj = optFunction.evaluate(assignment);
                archive.add(offspring, offspringObj);
                evaluations++;
                
                // 更新参考点
                for (int obj = 0; obj < offspringObj.values.length; obj++) {
                    referencePoint[obj] = Math.min(referencePoint[obj], offspringObj.values[obj]);
                }
                
                // 更新邻域解
                double offspringFitness = tchebycheffFunction(weightVectors[idx], offspringObj);
                for (int k = 0; k < neighborSize; k++) {
                    int neighborIdx = neighbors[idx][k];
                    double neighborFitness = tchebycheffFunction(weightVectors[neighborIdx], objectives[neighborIdx]);
                    if (offspringFitness < neighborFitness) {
                        positions[neighborIdx] = offspring.clone();
                        objectives[neighborIdx] = offspringObj;
                        break; // 只更新第一个满足条件的邻域解
                    }
                }
            }
        }

        return archive;
    }
}
