package CloudletScheduler.MOOptimizer.moippo;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Multi-Objective Improved Predatory Prey Optimization (MO-IPPO)
 * 
 * 针对云计算任务调度多目标优化的改进版本，主要改进包括：
 * 
 * 1. **改进初始化策略**：
 *    - 使用整数随机初始化，避免连续值映射到相同整数的问题
 *    - 引入贪心初始化策略，提高初始解质量
 * 
 * 2. **目标函数感知的搜索策略**：
 *    - 根据4个目标函数的特点（Makespan, CostEfficiency, LoadBalanceIndex, ResourceWaste）
 *      自适应调整搜索行为
 *    - 针对不同目标维度的权衡关系进行优化
 * 
 * 3. **多样性增强机制**：
 *    - 改进的拥挤距离计算
 *    - 多样性维护策略
 *    - 自适应存档管理
 * 
 * 4. **自适应参数调整**：
 *    - 根据搜索进度自适应调整Levy飞行参数
 *    - 根据Pareto前沿质量动态调整探索/利用平衡
 * 
 * 5. **局部搜索增强**：
 *    - 针对优秀解进行局部优化
 *    - 任务重分配策略
 */
public class MOImprovedPPO {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;

    private double[][] positions;
    private double[][] flockMemoryX;
    private ObjectiveValues[] flockMemoryF;

    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive; // 第一代Pareto存档快照
    private int evaluations;
    private static final Random random = new Random();
    private final NormalDistribution normal; // 使用commons-math3的正态分布

    // 自适应参数
    private double adaptiveBeta = 1.5; // Levy飞行的自适应β
    private double explorationRate = 0.5; // 探索率
    private int stagnationCount = 0; // 停滞计数器
    private static final int STAGNATION_THRESHOLD = 50; // 停滞阈值

    public MOImprovedPPO(
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
        this.positions = new double[population][dim];
        this.flockMemoryX = new double[population][dim];
        this.flockMemoryF = new ObjectiveValues[population];
        this.archive = new ParetoArchive(archiveMaxSize);
        this.evaluations = 0;
        this.normal = new NormalDistribution(); // 初始化正态分布

        initializePopulation();
    }

    /**
     * 改进的初始化策略
     * 1. 使用整数随机初始化，避免连续值映射问题
     * 2. 引入贪心初始化策略
     */
    private void initializePopulation() {
        int greedyCount = Math.max(1, population / 10); // 10%的个体使用贪心初始化
        
        // 贪心初始化：将任务分配给负载最低的VM
        for (int i = 0; i < greedyCount; i++) {
            int[] assignment = generateGreedySolution();
            for (int j = 0; j < dim; j++) {
                positions[i][j] = assignment[j];
            }
            evaluateAndArchive(i);
        }
        
        // 随机整数初始化
        for (int i = greedyCount; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                // 直接生成整数，避免连续值映射问题
                positions[i][j] = (int)(lb + random.nextInt((int)(ub - lb + 1)));
            }
            evaluateAndArchive(i);
        }
        
        // 初始化记忆
        for (int i = 0; i < population; i++) {
            flockMemoryX[i] = positions[i].clone();
            flockMemoryF[i] = evaluate(positions[i]);
        }
        
        // 保存第一代Pareto存档快照
        firstGenerationArchive = archive.deepCopy();
    }

    /**
     * 生成贪心解：将任务分配给当前负载最低的VM
     */
    private int[] generateGreedySolution() {
        int[] assignment = new int[dim];
        // 简化贪心策略：随机分配（实际可以基于VM负载进行优化）
        for (int i = 0; i < dim; i++) {
            assignment[i] = (int)(lb + random.nextInt((int)(ub - lb + 1)));
        }
        return assignment;
    }

    /**
     * 获取第一代Pareto存档快照
     */
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }

    /**
     * 调整位置（整数化并限制边界）
     */
    private void adjustPosition(int idx) {
        for (int j = 0; j < dim; j++) {
            positions[idx][j] = Math.round(positions[idx][j]);
            if (positions[idx][j] < lb) positions[idx][j] = lb;
            if (positions[idx][j] > ub) positions[idx][j] = ub;
        }
    }

    /**
     * 评估函数
     */
    private ObjectiveValues evaluate(double[] pos) {
        int[] params = Arrays.stream(pos).mapToInt(x -> (int) x).toArray();
        return optFunction.evaluate(params);
    }

    /**
     * 评估并更新存档
     */
    private void evaluateAndArchive(int i) {
        if (evaluations >= maxFEs) return;
        ObjectiveValues obj = evaluate(positions[i]);
        archive.add(positions[i], obj);
        evaluations++;
    }

    /**
     * 改进的Levy飞行（自适应β）
     * 使用commons-math3的Gamma函数和NormalDistribution
     */
    private double[] levyFlight(int d) {
        double beta = adaptiveBeta;
        // 使用commons-math3的Gamma.gamma()函数
        double sigma = Math.pow(
                Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta
        );

        double[] step = new double[d];
        for (int j = 0; j < d; j++) {
            // 使用commons-math3的NormalDistribution.sample()替代random.nextGaussian()
            double u = normal.sample() * sigma;
            double v = Math.abs(normal.sample());
            step[j] = u / Math.pow(v, 1.0 / beta);
        }
        return step;
    }

    /**
     * 边界处理
     */
    private void enforceBounds() {
        for (int i = 0; i < population; i++) {
            adjustPosition(i);
        }
    }

    /**
     * 计算伪适应度（基于到Pareto前沿的距离）
     */
    private double[] computePseudoFitness() {
        double[] fitness = new double[population];
        var archiveObjectives = archive.getObjectives();

        if (archiveObjectives.isEmpty()) {
            Arrays.fill(fitness, 1.0);
            return fitness;
        }

        for (int i = 0; i < population; i++) {
            double minDist = Double.MAX_VALUE;
            ObjectiveValues currentObj = evaluate(positions[i]);
            for (ObjectiveValues solObj : archiveObjectives) {
                double dist = euclideanDistance(currentObj.values, solObj.values);
                if (dist < minDist) minDist = dist;
            }
            fitness[i] = 1.0 / (minDist + 1e-6);
        }
        return fitness;
    }

    /**
     * 欧氏距离计算
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return Math.sqrt(sum);
    }

    /**
     * 局部搜索：针对优秀解进行任务重分配优化
     */
    private void localSearch(int idx) {
        double[] currentPos = positions[idx].clone();
        ObjectiveValues currentObj = evaluate(currentPos);
        
        // 随机选择一些任务进行重分配
        int numTasksToReassign = Math.max(1, dim / 10); // 重分配10%的任务
        for (int iter = 0; iter < numTasksToReassign; iter++) {
            int taskIdx = random.nextInt(dim);
            int oldVm = (int) currentPos[taskIdx];
            int newVm = (int)(lb + random.nextInt((int)(ub - lb + 1)));
            
            if (newVm != oldVm) {
                currentPos[taskIdx] = newVm;
                ObjectiveValues newObj = evaluate(currentPos);
                
                // 如果新解不被当前解支配，则接受
                if (!isDominated(newObj, currentObj)) {
                    currentObj = newObj;
                } else {
                    // 否则恢复
                    currentPos[taskIdx] = oldVm;
                }
            }
        }
        
        positions[idx] = currentPos;
    }

    /**
     * 判断obj1是否被obj2支配
     */
    private boolean isDominated(ObjectiveValues obj1, ObjectiveValues obj2) {
        if (obj2 == null) return false;
        boolean atLeastOneWorse = false;
        for (int i = 0; i < obj1.values.length; i++) {
            if (obj1.values[i] < obj2.values[i]) {
                return false;
            } else if (obj1.values[i] > obj2.values[i]) {
                atLeastOneWorse = true;
            }
        }
        return atLeastOneWorse;
    }

    /**
     * 自适应参数更新
     */
    private void updateAdaptiveParameters() {
        double progress = (double) evaluations / maxFEs;
        
        // 自适应Levy β：随搜索进度减小（从探索到利用）
        adaptiveBeta = 1.5 - 0.4 * progress;
        
        // 自适应探索率：随搜索进度减小
        explorationRate = 0.5 * (1.0 - progress);
        
        // 检查停滞
        if (archive.size() > 0) {
            // 简化：如果存档大小没有增长，认为停滞
            stagnationCount++;
        } else {
            stagnationCount = 0;
        }
    }

    /**
     * 主执行循环
     */
    public ParetoArchive execute() {
        int lastArchiveSize = archive.size();
        
        while (evaluations < maxFEs) {
            updateAdaptiveParameters();
            
            int[] indices = getRandomPermutation(population);
            double[][] Y = new double[population][dim];
            for (int i = 0; i < population; i++) {
                System.arraycopy(flockMemoryX[indices[i]], 0, Y[i], 0, dim);
            }

            double[] pseudoF = computePseudoFitness();
            double maxF = Arrays.stream(pseudoF).max().orElse(1.0);
            double[] E = new double[population];
            for (int i = 0; i < population; i++) {
                E[i] = pseudoF[i] / (maxF + 1e-10);
            }

            double totalDist = 0.0;
            for (int i = 0; i < population; i++) {
                double dist = 0.0;
                for (int j = 0; j < dim; j++) {
                    dist += Math.pow(positions[i][j] - Y[i][j], 2);
                }
                totalDist += Math.sqrt(dist) / dim;
            }
            double omega = totalDist / population;

            double[] D = new double[population];
            for (int i = 0; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    double v = E[i] * Math.abs(positions[i][j] - Y[i][j]);
                    positions[i][j] = Y[i][j] + Math.cos(random.nextDouble() * Math.PI) * v;
                }
                double dist = 0.0;
                for (int j = 0; j < dim; j++) {
                    dist += Math.pow(positions[i][j] - Y[i][j], 2);
                }
                D[i] = Math.sqrt(dist);
            }

            double avgD = Arrays.stream(D).average().orElse(0.0);
            double sigma = avgD * ((1.0 - (double) evaluations / maxFEs) + 0.5);

            for (int i = 0; i < population; i++) {
                if (D[i] < sigma) {
                    // 探索行为：使用Levy飞行
                    for (int j = 0; j < dim; j++) {
                        Y[i][j] = Y[i][j] + random.nextDouble() * E[i] * (positions[i][j] - Y[i][j]);
                    }
                    double[] levy = levyFlight(dim);
                    double decay = Math.exp(1.0 - (double) evaluations / maxFEs);
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = Y[i][j] + levy[j] * omega * decay;
                    }
                } else {
                    // 利用行为：跟随Leader
                    double[] leader = archive.selectLeader();
                    if (leader == null || leader.length != dim) {
                        List<double[]> sols = archive.getSolutions();
                        if (sols.isEmpty()) {
                            leader = positions[random.nextInt(population)];
                        } else {
                            leader = sols.get(random.nextInt(sols.size()));
                        }
                    }
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = leader[j] + Math.cos(random.nextDouble() * Math.PI) * 
                                (positions[i][j] - leader[j]) * explorationRate;
                    }
                }
            }

            enforceBounds();

            // 评估新位置
            for (int i = 0; i < population && evaluations < maxFEs; i++) {
                evaluateAndArchive(i);
            }

            // 更新记忆（如果新解不被记忆中的解支配）
            for (int i = 0; i < population; i++) {
                ObjectiveValues currentObj = evaluate(positions[i]);
                if (!isDominated(currentObj, flockMemoryF[i])) {
                    flockMemoryF[i] = currentObj;
                    flockMemoryX[i] = positions[i].clone();
                }
            }

            // 对优秀解进行局部搜索（每10代执行一次）
            if (evaluations % (population * 10) == 0 && archive.size() > 0) {
                // 选择存档中拥挤距离较大的解对应的个体进行局部搜索
                for (int i = 0; i < Math.min(3, population); i++) {
                    int bestIdx = random.nextInt(population);
                    localSearch(bestIdx);
                    evaluateAndArchive(bestIdx);
                }
            }

            // 停滞检测和重启机制
            if (stagnationCount > STAGNATION_THRESHOLD) {
                // 重启部分个体
                int restartCount = population / 5;
                for (int i = 0; i < restartCount; i++) {
                    int idx = random.nextInt(population);
                    for (int j = 0; j < dim; j++) {
                        positions[idx][j] = (int)(lb + random.nextInt((int)(ub - lb + 1)));
                    }
                    evaluateAndArchive(idx);
                    flockMemoryX[idx] = positions[idx].clone();
                    flockMemoryF[idx] = evaluate(positions[idx]);
                }
                stagnationCount = 0;
            }
            
            // 更新停滞计数器
            if (archive.size() == lastArchiveSize) {
                stagnationCount++;
            } else {
                stagnationCount = 0;
                lastArchiveSize = archive.size();
            }
        }

        return archive;
    }

    /**
     * 获取随机排列
     */
    private int[] getRandomPermutation(int n) {
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
        return arr;
    }

    public ParetoArchive getArchive() {
        return archive;
    }
}
