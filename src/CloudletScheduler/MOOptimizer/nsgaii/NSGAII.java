package CloudletScheduler.MOOptimizer.nsgaii;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;

import java.util.*;
import java.util.Arrays;

/**
 * Non-dominated Sorting Genetic Algorithm II (NSGA-II)
 * 
 * 经典的多目标优化算法，主要特点：
 * 1. 非支配排序（Non-dominated Sorting）
 * 2. 拥挤距离（Crowding Distance）
 * 3. 精英保留（Elitism）
 * 4. 二进制锦标赛选择（Binary Tournament Selection）
 * 5. 模拟二进制交叉（SBX）和多项式变异
 */
public class NSGAII {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;
    private final int archiveMaxSize;

    private double[][] positions;
    private ObjectiveValues[] objectives;
    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive;

    private static final Random random = new Random();
    private static final double CROSSOVER_PROB = 0.9; // 交叉概率
    private final double MUTATION_PROB; // 变异概率（自适应，基于维度）
    private static final double ETA_C = 20.0; // SBX分布指数
    private static final double ETA_M = 20.0; // 多项式变异分布指数

    public NSGAII(
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
        this.MUTATION_PROB = 1.0 / dim; // 初始化变异概率

        initializePopulation();
    }

    /**
     * 初始化种群
     */
    private void initializePopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPosition(i);
            int[] assignment = Arrays.stream(positions[i]).mapToInt(x -> (int) x).toArray();
            objectives[i] = optFunction.evaluate(assignment);
            archive.add(positions[i], objectives[i]);
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
     * 非支配排序
     * 返回：每个个体所属的前沿层级（0为最优前沿）
     */
    private int[] nonDominatedSort() {
        int[] rank = new int[population];
        int[] dominatedCount = new int[population];
        List<List<Integer>> dominatedSets = new ArrayList<>();
        
        for (int i = 0; i < population; i++) {
            dominatedSets.add(new ArrayList<>());
        }

        // 计算支配关系
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < population; j++) {
                if (i == j) continue;
                if (dominates(objectives[i], objectives[j])) {
                    dominatedSets.get(i).add(j);
                } else if (dominates(objectives[j], objectives[i])) {
                    dominatedCount[i]++;
                }
            }
        }

        // 分层：找到所有rank=0的个体，然后更新rank=1的个体，以此类推
        int currentRank = 0;
        boolean[] assigned = new boolean[population];
        int assignedCount = 0;

        while (assignedCount < population) {
            List<Integer> currentFront = new ArrayList<>();
            for (int i = 0; i < population; i++) {
                if (!assigned[i] && dominatedCount[i] == 0) {
                    rank[i] = currentRank;
                    currentFront.add(i);
                    assigned[i] = true;
                    assignedCount++;
                }
            }

            // 更新下一层的dominatedCount
            for (int idx : currentFront) {
                for (int dominatedIdx : dominatedSets.get(idx)) {
                    if (!assigned[dominatedIdx]) {
                        dominatedCount[dominatedIdx]--;
                    }
                }
            }
            currentRank++;
        }

        return rank;
    }

    /**
     * 判断obj1是否支配obj2
     */
    private boolean dominates(ObjectiveValues obj1, ObjectiveValues obj2) {
        boolean atLeastOneBetter = false;
        for (int i = 0; i < obj1.values.length; i++) {
            if (obj1.values[i] > obj2.values[i]) {
                return false; // obj1在某个目标上更差，不支配
            }
            if (obj1.values[i] < obj2.values[i]) {
                atLeastOneBetter = true; // obj1在某个目标上更好
            }
        }
        return atLeastOneBetter;
    }

    /**
     * 计算拥挤距离
     * 
     * @param frontIndices 前沿中个体的索引数组
     * @param objArray 目标值数组（可以是objectives或combinedObj）
     */
    private double[] computeCrowdingDistance(int[] frontIndices, ObjectiveValues[] objArray) {
        int frontSize = frontIndices.length;
        double[] distance = new double[frontSize];
        
        if (frontSize <= 2) {
            Arrays.fill(distance, Double.POSITIVE_INFINITY);
            return distance;
        }

        int numObjectives = objArray[frontIndices[0]].values.length;

        for (int objIdx = 0; objIdx < numObjectives; objIdx++) {
            // 按当前目标值排序
            Integer[] sortedIndices = new Integer[frontSize];
            for (int i = 0; i < frontSize; i++) {
                sortedIndices[i] = i;
            }
            final int finalObjIdx = objIdx;
            Arrays.sort(sortedIndices, Comparator.comparingDouble(
                i -> objArray[frontIndices[i]].values[finalObjIdx]));

            // 边界解距离设为无穷大
            distance[sortedIndices[0]] = Double.POSITIVE_INFINITY;
            distance[sortedIndices[frontSize - 1]] = Double.POSITIVE_INFINITY;

            // 计算目标值的范围
            double minObj = objArray[frontIndices[sortedIndices[0]]].values[objIdx];
            double maxObj = objArray[frontIndices[sortedIndices[frontSize - 1]]].values[objIdx];
            double range = maxObj - minObj;
            if (range < 1e-10) range = 1.0;

            // 计算中间解的拥挤距离
            for (int i = 1; i < frontSize - 1; i++) {
                int idx = sortedIndices[i];
                double prevObj = objArray[frontIndices[sortedIndices[i - 1]]].values[objIdx];
                double nextObj = objArray[frontIndices[sortedIndices[i + 1]]].values[objIdx];
                distance[idx] += (nextObj - prevObj) / range;
            }
        }

        return distance;
    }
    
    /**
     * 计算拥挤距离（使用类成员objectives数组，用于当前种群）
     */
    private double[] computeCrowdingDistance(int[] frontIndices) {
        return computeCrowdingDistance(frontIndices, objectives);
    }

    /**
     * 二进制锦标赛选择
     */
    private int binaryTournamentSelection(int[] rank, double[] crowdingDistance) {
        int idx1 = random.nextInt(population);
        int idx2 = random.nextInt(population);
        
        // 优先选择rank更小的（前沿层级更优）
        if (rank[idx1] < rank[idx2]) return idx1;
        if (rank[idx2] < rank[idx1]) return idx2;
        
        // rank相同，选择拥挤距离更大的（多样性更好）
        if (crowdingDistance[idx1] > crowdingDistance[idx2]) return idx1;
        if (crowdingDistance[idx2] > crowdingDistance[idx1]) return idx2;
        
        // 都相同，随机选择
        return random.nextBoolean() ? idx1 : idx2;
    }

    /**
     * 模拟二进制交叉（SBX）
     */
    private void simulatedBinaryCrossover(double[] parent1, double[] parent2, 
                                          double[] offspring1, double[] offspring2) {
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() <= CROSSOVER_PROB) {
                double u = random.nextDouble();
                double beta;
                if (u <= 0.5) {
                    beta = Math.pow(2 * u, 1.0 / (ETA_C + 1));
                } else {
                    beta = Math.pow(1.0 / (2 * (1 - u)), 1.0 / (ETA_C + 1));
                }
                
                offspring1[j] = 0.5 * ((1 + beta) * parent1[j] + (1 - beta) * parent2[j]);
                offspring2[j] = 0.5 * ((1 - beta) * parent1[j] + (1 + beta) * parent2[j]);
            } else {
                offspring1[j] = parent1[j];
                offspring2[j] = parent2[j];
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
        int evaluations = population; // 初始评估次数

        while (evaluations < maxFEs) {
            // 非支配排序
            int[] rank = nonDominatedSort();

            // 计算拥挤距离
            double[] crowdingDistance = new double[population];
            Map<Integer, List<Integer>> frontMap = new HashMap<>();
            for (int i = 0; i < population; i++) {
                frontMap.computeIfAbsent(rank[i], k -> new ArrayList<>()).add(i);
            }
            for (List<Integer> front : frontMap.values()) {
                int[] frontArray = front.stream().mapToInt(i -> i).toArray();
                double[] frontDistances = computeCrowdingDistance(frontArray);
                for (int i = 0; i < front.size(); i++) {
                    crowdingDistance[front.get(i)] = frontDistances[i];
                }
            }

            // 生成子代
            double[][] offspring = new double[population][dim];
            ObjectiveValues[] offspringObj = new ObjectiveValues[population];

            for (int i = 0; i < population; i += 2) {
                // 选择父代
                int parent1Idx = binaryTournamentSelection(rank, crowdingDistance);
                int parent2Idx = binaryTournamentSelection(rank, crowdingDistance);

                // 交叉
                simulatedBinaryCrossover(positions[parent1Idx], positions[parent2Idx],
                        offspring[i], i + 1 < population ? offspring[i + 1] : new double[dim]);

                // 变异
                polynomialMutation(offspring[i]);
                if (i + 1 < population) {
                    polynomialMutation(offspring[i + 1]);
                }

                // 边界处理
                adjustPositionArray(offspring[i]);
                if (i + 1 < population) {
                    adjustPositionArray(offspring[i + 1]);
                }

                // 评估
                if (evaluations < maxFEs) {
                    int[] assignment1 = Arrays.stream(offspring[i]).mapToInt(x -> (int) x).toArray();
                    offspringObj[i] = optFunction.evaluate(assignment1);
                    archive.add(offspring[i], offspringObj[i]);
                    evaluations++;
                }

                if (i + 1 < population && evaluations < maxFEs) {
                    int[] assignment2 = Arrays.stream(offspring[i + 1]).mapToInt(x -> (int) x).toArray();
                    offspringObj[i + 1] = optFunction.evaluate(assignment2);
                    archive.add(offspring[i + 1], offspringObj[i + 1]);
                    evaluations++;
                }
            }

            // 合并父代和子代，选择下一代
            double[][] combined = new double[2 * population][dim];
            ObjectiveValues[] combinedObj = new ObjectiveValues[2 * population];
            System.arraycopy(positions, 0, combined, 0, population);
            System.arraycopy(offspring, 0, combined, population, population);
            System.arraycopy(objectives, 0, combinedObj, 0, population);
            System.arraycopy(offspringObj, 0, combinedObj, population, population);

            // 对合并后的种群进行非支配排序
            int[] combinedRank = new int[2 * population];
            int[] combinedDominatedCount = new int[2 * population];
            List<List<Integer>> combinedDominatedSets = new ArrayList<>();
            for (int i = 0; i < 2 * population; i++) {
                combinedDominatedSets.add(new ArrayList<>());
            }

            for (int i = 0; i < 2 * population; i++) {
                for (int j = 0; j < 2 * population; j++) {
                    if (i == j) continue;
                    if (dominates(combinedObj[i], combinedObj[j])) {
                        combinedDominatedSets.get(i).add(j);
                    } else if (dominates(combinedObj[j], combinedObj[i])) {
                        combinedDominatedCount[i]++;
                    }
                }
            }

            int currentRank = 0;
            boolean[] assigned = new boolean[2 * population];
            int assignedCount = 0;
            Map<Integer, List<Integer>> combinedFrontMap = new HashMap<>();

            while (assignedCount < 2 * population) {
                List<Integer> currentFront = new ArrayList<>();
                for (int i = 0; i < 2 * population; i++) {
                    if (!assigned[i] && combinedDominatedCount[i] == 0) {
                        combinedRank[i] = currentRank;
                        currentFront.add(i);
                        assigned[i] = true;
                        assignedCount++;
                    }
                }
                combinedFrontMap.put(currentRank, currentFront);
                for (int idx : currentFront) {
                    for (int dominatedIdx : combinedDominatedSets.get(idx)) {
                        if (!assigned[dominatedIdx]) {
                            combinedDominatedCount[dominatedIdx]--;
                        }
                    }
                }
                currentRank++;
            }

            // 计算合并后种群的拥挤距离（使用combinedObj数组）
            double[] combinedCrowdingDistance = new double[2 * population];
            for (List<Integer> front : combinedFrontMap.values()) {
                int[] frontArray = front.stream().mapToInt(i -> i).toArray();
                double[] frontDistances = computeCrowdingDistance(frontArray, combinedObj);
                for (int i = 0; i < front.size(); i++) {
                    combinedCrowdingDistance[front.get(i)] = frontDistances[i];
                }
            }

            // 选择下一代：按rank排序，rank相同时按拥挤距离排序
            Integer[] indices = new Integer[2 * population];
            for (int i = 0; i < 2 * population; i++) {
                indices[i] = i;
            }
            Arrays.sort(indices, (i1, i2) -> {
                if (combinedRank[i1] != combinedRank[i2]) {
                    return Integer.compare(combinedRank[i1], combinedRank[i2]);
                }
                return Double.compare(combinedCrowdingDistance[i2], combinedCrowdingDistance[i1]);
            });

            // 选择前population个个体
            for (int i = 0; i < population; i++) {
                positions[i] = combined[indices[i]].clone();
                objectives[i] = combinedObj[indices[i]];
            }
        }

        return archive;
    }

    private void adjustPositionArray(double[] pos) {
        for (int j = 0; j < dim; j++) {
            pos[j] = Math.round(pos[j]);
            if (pos[j] < lb) pos[j] = lb;
            if (pos[j] > ub) pos[j] = ub;
        }
    }
}
