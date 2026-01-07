package CloudletScheduler.MOOptimizer.spea2;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;

import java.util.*;

/**
 * Strength Pareto Evolutionary Algorithm 2 (SPEA2)
 * 
 * 强度Pareto进化算法2，主要特点：
 * 1. 强度值（Strength）计算
 * 2. 原始适应度（Raw Fitness）计算
 * 3. 密度估计（K近邻距离）
 * 4. 环境选择（Environmental Selection）
 * 5. 档案截断（Archive Truncation）
 */
public class SPEA2 {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;
    private final int archiveSize; // 存档大小（通常等于population）

    private double[][] positions;
    private ObjectiveValues[] objectives;
    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive;

    private static final Random random = new Random();
    private static final double CROSSOVER_PROB = 0.9;
    private static final double MUTATION_PROB = 1.0 / 100;
    private static final double ETA_C = 20.0;
    private static final double ETA_M = 20.0;
    private final int K; // K近邻参数（基于种群大小）

    public SPEA2(
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
        this.archiveSize = Math.min(population, archiveMaxSize);

        this.positions = new double[population][dim];
        this.objectives = new ObjectiveValues[population];
        this.archive = new ParetoArchive(archiveMaxSize);
        this.K = (int) Math.sqrt(population); // 初始化K值

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
     * 判断obj1是否支配obj2
     */
    private boolean dominates(ObjectiveValues obj1, ObjectiveValues obj2) {
        boolean atLeastOneBetter = false;
        for (int i = 0; i < obj1.values.length; i++) {
            if (obj1.values[i] > obj2.values[i]) {
                return false;
            }
            if (obj1.values[i] < obj2.values[i]) {
                atLeastOneBetter = true;
            }
        }
        return atLeastOneBetter;
    }

    /**
     * 计算强度值（Strength）：支配多少个解
     */
    private int[] computeStrength(ObjectiveValues[] allObjectives, int totalSize) {
        int[] strength = new int[totalSize];
        for (int i = 0; i < totalSize; i++) {
            for (int j = 0; j < totalSize; j++) {
                if (i != j && dominates(allObjectives[i], allObjectives[j])) {
                    strength[i]++;
                }
            }
        }
        return strength;
    }

    /**
     * 计算原始适应度（Raw Fitness）：被多少个解支配
     */
    private double[] computeRawFitness(int[] strength, ObjectiveValues[] allObjectives, int totalSize) {
        double[] rawFitness = new double[totalSize];
        for (int i = 0; i < totalSize; i++) {
            for (int j = 0; j < totalSize; j++) {
                if (i != j && dominates(allObjectives[j], allObjectives[i])) {
                    rawFitness[i] += strength[j];
                }
            }
        }
        return rawFitness;
    }

    /**
     * 计算K近邻距离（用于密度估计）
     */
    private double[] computeKthNearestDistance(ObjectiveValues[] allObjectives, int totalSize) {
        double[] distances = new double[totalSize];
        
        for (int i = 0; i < totalSize; i++) {
            List<Double> distList = new ArrayList<>();
            for (int j = 0; j < totalSize; j++) {
                if (i != j) {
                    double dist = euclideanDistance(allObjectives[i].values, allObjectives[j].values);
                    distList.add(dist);
                }
            }
            Collections.sort(distList);
            int k = Math.min(K, distList.size());
            distances[i] = k > 0 ? distList.get(k - 1) : Double.MAX_VALUE;
        }
        
        return distances;
    }

    /**
     * 欧氏距离
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算适应度（Raw Fitness + Density）
     */
    private double[] computeFitness(double[] rawFitness, double[] density) {
        double[] fitness = new double[rawFitness.length];
        for (int i = 0; i < rawFitness.length; i++) {
            fitness[i] = rawFitness[i] + 1.0 / (density[i] + 2.0); // +2避免除零
        }
        return fitness;
    }

    /**
     * 环境选择：选择非支配解和部分支配解进入存档
     */
    private void environmentalSelection(double[][] allPositions, ObjectiveValues[] allObjectives, 
                                       double[] fitness, int totalSize) {
        // 找出所有非支配解
        List<Integer> nonDominated = new ArrayList<>();
        for (int i = 0; i < totalSize; i++) {
            boolean isDominated = false;
            for (int j = 0; j < totalSize; j++) {
                if (i != j && dominates(allObjectives[j], allObjectives[i])) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                nonDominated.add(i);
            }
        }

        archive = new ParetoArchive(archiveSize);
        
        if (nonDominated.size() <= archiveSize) {
            // 非支配解数量不超过存档大小，全部加入
            for (int idx : nonDominated) {
                archive.add(allPositions[idx], allObjectives[idx]);
            }
            
            // 如果还有空间，加入适应度最好的支配解
            if (nonDominated.size() < archiveSize) {
                List<Integer> dominated = new ArrayList<>();
                for (int i = 0; i < totalSize; i++) {
                    if (!nonDominated.contains(i)) {
                        dominated.add(i);
                    }
                }
                dominated.sort(Comparator.comparingDouble(i -> fitness[i]));
                int remaining = archiveSize - nonDominated.size();
                for (int i = 0; i < Math.min(remaining, dominated.size()); i++) {
                    int idx = dominated.get(i);
                    archive.add(allPositions[idx], allObjectives[idx]);
                }
            }
        } else {
            // 非支配解数量超过存档大小，需要截断
            archiveTruncation(allPositions, allObjectives, nonDominated);
        }
    }

    /**
     * 存档截断：当非支配解数量超过存档大小时，移除距离最近的解
     */
    private void archiveTruncation(double[][] allPositions, ObjectiveValues[] allObjectives, 
                                   List<Integer> nonDominated) {
        List<Integer> archiveIndices = new ArrayList<>(nonDominated);
        
        while (archiveIndices.size() > archiveSize) {
            // 计算每对解之间的距离
            double minDist = Double.MAX_VALUE;
            int removeIdx = -1;
            
            for (int i = 0; i < archiveIndices.size(); i++) {
                double minDistToOthers = Double.MAX_VALUE;
                for (int j = 0; j < archiveIndices.size(); j++) {
                    if (i != j) {
                        double dist = euclideanDistance(
                            allObjectives[archiveIndices.get(i)].values,
                            allObjectives[archiveIndices.get(j)].values);
                        if (dist < minDistToOthers) {
                            minDistToOthers = dist;
                        }
                    }
                }
                if (minDistToOthers < minDist) {
                    minDist = minDistToOthers;
                    removeIdx = i;
                }
            }
            
            if (removeIdx >= 0) {
                archiveIndices.remove(removeIdx);
            } else {
                break;
            }
        }
        
        // 将选中的解加入存档
        for (int idx : archiveIndices) {
            archive.add(allPositions[idx], allObjectives[idx]);
        }
    }

    /**
     * 二进制锦标赛选择
     */
    private int binaryTournamentSelection(double[] fitness) {
        int idx1 = random.nextInt(population);
        int idx2 = random.nextInt(population);
        return fitness[idx1] < fitness[idx2] ? idx1 : idx2;
    }

    /**
     * 模拟二进制交叉
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
        int evaluations = population;

        while (evaluations < maxFEs) {
            // 合并种群和存档
            List<double[]> allPositions = new ArrayList<>();
            List<ObjectiveValues> allObjectives = new ArrayList<>();
            
            for (int i = 0; i < population; i++) {
                allPositions.add(positions[i]);
                allObjectives.add(objectives[i]);
            }
            
            var archiveSolutions = archive.getSolutions();
            var archiveObjs = archive.getObjectives();
            for (int i = 0; i < archiveSolutions.size(); i++) {
                allPositions.add(archiveSolutions.get(i));
                allObjectives.add(archiveObjs.get(i));
            }
            
            int totalSize = allPositions.size();
            double[][] allPosArray = allPositions.toArray(new double[totalSize][]);
            ObjectiveValues[] allObjArray = allObjectives.toArray(new ObjectiveValues[totalSize]);
            
            // 计算强度值
            int[] strength = computeStrength(allObjArray, totalSize);
            
            // 计算原始适应度
            double[] rawFitness = computeRawFitness(strength, allObjArray, totalSize);
            
            // 计算K近邻距离（密度）
            double[] density = computeKthNearestDistance(allObjArray, totalSize);
            
            // 计算适应度
            double[] fitness = computeFitness(rawFitness, density);
            
            // 环境选择：更新存档
            environmentalSelection(allPosArray, allObjArray, fitness, totalSize);
            
            // 生成子代
            double[][] offspring = new double[population][dim];
            ObjectiveValues[] offspringObj = new ObjectiveValues[population];
            
            // 使用前population个解的适应度进行选择
            double[] selectionFitness = new double[population];
            System.arraycopy(fitness, 0, selectionFitness, 0, population);
            
            for (int i = 0; i < population; i += 2) {
                int parent1Idx = binaryTournamentSelection(selectionFitness);
                int parent2Idx = binaryTournamentSelection(selectionFitness);
                
                simulatedBinaryCrossover(positions[parent1Idx], positions[parent2Idx],
                        offspring[i], i + 1 < population ? offspring[i + 1] : new double[dim]);
                
                polynomialMutation(offspring[i]);
                if (i + 1 < population) {
                    polynomialMutation(offspring[i + 1]);
                }
                
                adjustPositionArray(offspring[i]);
                if (i + 1 < population) {
                    adjustPositionArray(offspring[i + 1]);
                }
                
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
            
            // 更新种群
            positions = offspring;
            objectives = offspringObj;
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
