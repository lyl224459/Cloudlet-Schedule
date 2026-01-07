package CloudletScheduler.MOOptimizer.moppo3;

import CloudletScheduler.MOOptimizer.moppo2.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Gamma;

import java.util.*;

/**
 * Multi-Objective Predatory Prey Optimization 3 v2 (MO-PPO3v2)
 * 
 * 针对多目标均衡优化的改进版本：
 * 1. 反向学习初始化 (Opposition-based Learning) - 提高初始覆盖
 * 2. 目标分解引导 (Decomposition-based Guidance) - 多权重向量引导
 * 3. 精英池机制 (Elite Pool) - 保留各目标维度最优解
 * 4. 自适应交叉操作 - 解之间的信息交换
 * 5. 弱势目标增强 - 针对表现差的目标进行局部优化
 */
public class MOPPO3v2 {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;
    private final int numObjectives;

    private final double[][] positions;
    private final double[][] flockMemoryX;
    private final ObjectiveValues[] flockMemoryF;
    private final ParetoArchive archive;
    private int evaluations;

    // 精英池 - 保存各目标维度上的最优解
    private double[][] elitePool;
    private ObjectiveValues[] elitePoolF;
    private static final int ELITE_POOL_SIZE = 5;

    // 权重向量集合 (目标分解)
    private double[][] weightVectors;
    private static final int NUM_WEIGHT_VECTORS = 10;

    // 目标归一化参数
    private double[] objMin;
    private double[] objMax;

    private static final Random random = new Random();
    private final NormalDistribution normal = new NormalDistribution();

    public MOPPO3v2(OptFunctionMulti optFunction,
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
        this.numObjectives = 4; // makespan, cost, lb, resourceUtilization

        this.positions = new double[population][dim];
        this.flockMemoryX = new double[population][dim];
        this.flockMemoryF = new ObjectiveValues[population];
        this.archive = new ParetoArchive(archiveMaxSize);
        this.evaluations = 0;

        // 初始化精英池
        this.elitePool = new double[numObjectives * ELITE_POOL_SIZE][dim];
        this.elitePoolF = new ObjectiveValues[numObjectives * ELITE_POOL_SIZE];

        // 初始化目标边界
        this.objMin = new double[numObjectives];
        this.objMax = new double[numObjectives];
        Arrays.fill(objMin, Double.MAX_VALUE);
        Arrays.fill(objMax, Double.MIN_VALUE);

        initializeWeightVectors();
        initializeWithOpposition();
    }

    /**
     * 初始化权重向量 - 用于目标分解引导
     */
    private void initializeWeightVectors() {
        weightVectors = new double[NUM_WEIGHT_VECTORS][numObjectives];
        
        // 均匀分布的权重向量
        for (int i = 0; i < NUM_WEIGHT_VECTORS; i++) {
            double sum = 0;
            for (int j = 0; j < numObjectives; j++) {
                weightVectors[i][j] = random.nextDouble() + 0.1;
                sum += weightVectors[i][j];
            }
            // 归一化
            for (int j = 0; j < numObjectives; j++) {
                weightVectors[i][j] /= sum;
            }
        }
        
        // 添加几个极端权重向量（各目标优先）
        for (int j = 0; j < Math.min(numObjectives, NUM_WEIGHT_VECTORS); j++) {
            Arrays.fill(weightVectors[j], 0.1 / (numObjectives - 1));
            weightVectors[j][j] = 0.9;
        }
    }

    /**
     * 反向学习初始化 - 提高初始种群多样性和覆盖度
     */
    private void initializeWithOpposition() {
        int halfPop = population / 2;
        
        // 前半部分：随机初始化
        for (int i = 0; i < halfPop; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            roundAndClamp(positions[i]);
            evaluateAndArchive(i);
        }
        
        // 后半部分：反向学习
        for (int i = halfPop; i < population; i++) {
            int mirrorIdx = i - halfPop;
            for (int j = 0; j < dim; j++) {
                // 反向点: x' = lb + ub - x
                positions[i][j] = lb + ub - positions[mirrorIdx][j];
                // 添加小扰动
                positions[i][j] += (random.nextDouble() - 0.5) * (ub - lb) * 0.1;
            }
            roundAndClamp(positions[i]);
            evaluateAndArchive(i);
        }

        // 初始化记忆
        for (int i = 0; i < population; i++) {
            flockMemoryX[i] = positions[i].clone();
            flockMemoryF[i] = evaluate(positions[i]);
            updateElitePool(positions[i], flockMemoryF[i]);
        }
    }

    /**
     * 边界处理
     */
    private void roundAndClamp(double[] pos) {
        for (int j = 0; j < dim; j++) {
            pos[j] = Math.round(pos[j]);
            if (pos[j] < lb) pos[j] = lb;
            if (pos[j] > ub) pos[j] = ub;
        }
    }

    /**
     * 评价函数 - 安全边界检查
     */
    private ObjectiveValues evaluate(double[] pos) {
        int[] params = new int[pos.length];
        for (int j = 0; j < pos.length; j++) {
            int val = (int) Math.round(pos[j]);
            if (val < (int) lb) val = (int) lb;
            if (val > (int) ub) val = (int) ub;
            params[j] = val;
        }
        ObjectiveValues obj = optFunction.evaluate(params);
        updateObjectiveBounds(obj);
        return obj;
    }

    /**
     * 更新目标边界（用于归一化）
     */
    private void updateObjectiveBounds(ObjectiveValues obj) {
        for (int j = 0; j < obj.values.length; j++) {
            objMin[j] = Math.min(objMin[j], obj.values[j]);
            objMax[j] = Math.max(objMax[j], obj.values[j]);
        }
    }

    /**
     * 更新精英池
     */
    private void updateElitePool(double[] pos, ObjectiveValues obj) {
        for (int objIdx = 0; objIdx < numObjectives; objIdx++) {
            int startIdx = objIdx * ELITE_POOL_SIZE;
            int worstIdx = startIdx;
            double worstVal = Double.MIN_VALUE;
            
            // 找到该目标维度上最差的精英
            for (int k = startIdx; k < startIdx + ELITE_POOL_SIZE; k++) {
                if (elitePoolF[k] == null) {
                    worstIdx = k;
                    break;
                }
                if (elitePoolF[k].values[objIdx] > worstVal) {
                    worstVal = elitePoolF[k].values[objIdx];
                    worstIdx = k;
                }
            }
            
            // 如果当前解在该目标上更好，替换
            if (elitePoolF[worstIdx] == null || obj.values[objIdx] < worstVal) {
                elitePool[worstIdx] = pos.clone();
                elitePoolF[worstIdx] = obj.clone();
            }
        }
    }

    /**
     * 获取指定目标的精英解
     */
    private double[] getEliteForObjective(int objIdx) {
        int startIdx = objIdx * ELITE_POOL_SIZE;
        int bestIdx = startIdx;
        double bestVal = Double.MAX_VALUE;
        
        for (int k = startIdx; k < startIdx + ELITE_POOL_SIZE; k++) {
            if (elitePoolF[k] != null && elitePoolF[k].values[objIdx] < bestVal) {
                bestVal = elitePoolF[k].values[objIdx];
                bestIdx = k;
            }
        }
        
        return elitePoolF[bestIdx] != null ? elitePool[bestIdx].clone() : null;
    }

    private void evaluateAndArchive(int i) {
        if (evaluations >= maxFEs) return;
        ObjectiveValues obj = evaluate(positions[i]);
        archive.add(positions[i], obj);
        evaluations++;
    }

    /**
     * Lévy Flight - 步长受控
     */
    private double[] levyFlight(int d, double beta) {
        double sigma = Math.pow(
                Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta);
        double[] step = new double[d];
        double maxStep = (ub - lb) * 0.3;
        for (int i = 0; i < d; i++) {
            double u = normal.sample() * sigma;
            double v = Math.abs(normal.sample()) + 1e-10;
            step[i] = u / Math.pow(v, 1.0 / beta);
            step[i] = Math.max(-maxStep, Math.min(maxStep, step[i]));
        }
        return step;
    }

    /**
     * 自适应交叉操作 - 解之间信息交换
     */
    private double[] adaptiveCrossover(double[] parent1, double[] parent2, double cr) {
        double[] child = new double[dim];
        int jRand = random.nextInt(dim);
        
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() < cr || j == jRand) {
                // 混合交叉
                double alpha = random.nextDouble();
                child[j] = alpha * parent1[j] + (1 - alpha) * parent2[j];
            } else {
                child[j] = parent1[j];
            }
        }
        roundAndClamp(child);
        return child;
    }

    /**
     * 计算归一化加权适应度
     */
    private double computeWeightedFitness(ObjectiveValues obj, double[] weights) {
        double fitness = 0;
        for (int j = 0; j < numObjectives; j++) {
            double range = objMax[j] - objMin[j];
            if (range > 1e-10) {
                double normalized = (obj.values[j] - objMin[j]) / range;
                fitness += weights[j] * normalized;
            }
        }
        return fitness;
    }

    /**
     * 识别弱势目标
     */
    private int identifyWeakObjective(ObjectiveValues obj) {
        int weakest = 0;
        double worstNormalized = 0;
        
        for (int j = 0; j < numObjectives; j++) {
            double range = objMax[j] - objMin[j];
            if (range > 1e-10) {
                double normalized = (obj.values[j] - objMin[j]) / range;
                if (normalized > worstNormalized) {
                    worstNormalized = normalized;
                    weakest = j;
                }
            }
        }
        return weakest;
    }

    /**
     * 弱势目标增强搜索
     */
    private double[] weakObjectiveEnhancement(double[] pos, int weakObjIdx) {
        // 获取该目标的精英解
        double[] elite = getEliteForObjective(weakObjIdx);
        if (elite == null) return pos.clone();
        
        double[] newPos = new double[dim];
        double learningRate = 0.3 + 0.4 * random.nextDouble();
        
        for (int j = 0; j < dim; j++) {
            // 向该目标的精英学习
            newPos[j] = pos[j] + learningRate * (elite[j] - pos[j]);
            // 添加小扰动保持多样性
            newPos[j] += (random.nextDouble() - 0.5) * (ub - lb) * 0.05;
        }
        roundAndClamp(newPos);
        return newPos;
    }

    /**
     * 判断支配关系
     */
    private boolean isDominated(ObjectiveValues a, ObjectiveValues b) {
        if (b == null) return false;
        boolean worse = false;
        for (int i = 0; i < a.values.length; i++) {
            if (a.values[i] < b.values[i]) return false;
            if (a.values[i] > b.values[i]) worse = true;
        }
        return worse;
    }

    /**
     * 主循环
     */
    public ParetoArchive execute() {
        while (evaluations < maxFEs) {
            double t = (double) evaluations / maxFEs;

            // 为每个个体分配一个权重向量
            for (int i = 0; i < population; i++) {
                int weightIdx = i % NUM_WEIGHT_VECTORS;
                double[] weights = weightVectors[weightIdx];
                
                ObjectiveValues currentObj = flockMemoryF[i];
                double[] oldPos = positions[i].clone();

                // 阶段1：根据进度选择策略
                if (t < 0.5) {
                    // 早期：探索为主
                    explorePhase(i, weights, t);
                } else {
                    // 后期：利用为主
                    exploitPhase(i, weights, t);
                }

                roundAndClamp(positions[i]);

                // 阶段2：弱势目标增强 (30%概率)
                if (random.nextDouble() < 0.3) {
                    int weakObj = identifyWeakObjective(currentObj);
                    double[] enhanced = weakObjectiveEnhancement(positions[i], weakObj);
                    ObjectiveValues enhancedObj = evaluate(enhanced);
                    ObjectiveValues currentPosObj = evaluate(positions[i]);
                    
                    // 如果增强后更好，采用
                    if (!isDominated(enhancedObj, currentPosObj)) {
                        positions[i] = enhanced;
                    }
                }

                // 阶段3：自适应交叉 (40%概率)
                if (random.nextDouble() < 0.4) {
                    // 从存档中选择一个解进行交叉
                    double[] archiveSol = archive.selectLeader();
                    if (archiveSol != null && archiveSol.length == dim) {
                        double cr = 0.3 + 0.4 * (1 - t); // 交叉率随时间降低
                        double[] crossed = adaptiveCrossover(positions[i], archiveSol, cr);
                        ObjectiveValues crossedObj = evaluate(crossed);
                        ObjectiveValues currentPosObj = evaluate(positions[i]);
                        
                        if (!isDominated(crossedObj, currentPosObj)) {
                            positions[i] = crossed;
                        }
                    }
                }

                roundAndClamp(positions[i]);
            }

            // 更新存档
            for (int i = 0; i < population && evaluations < maxFEs; i++) {
                evaluateAndArchive(i);
            }

            // 更新记忆和精英池
            for (int i = 0; i < population; i++) {
                ObjectiveValues cur = evaluate(positions[i]);
                if (!isDominated(cur, flockMemoryF[i])) {
                    flockMemoryF[i] = cur;
                    flockMemoryX[i] = positions[i].clone();
                }
                updateElitePool(positions[i], cur);
            }
        }

        return archive;
    }

    /**
     * 探索阶段 - 强调多样性
     */
    private void explorePhase(int i, double[] weights, double t) {
        double beta = 1.5 - 0.3 * t;
        double[] levy = levyFlight(dim, beta);
        
        // Lévy飞行探索
        double stepScale = 1.0 - 0.5 * t;
        for (int j = 0; j < dim; j++) {
            positions[i][j] += levy[j] * stepScale;
        }
        
        // 20%概率进行反向学习跳跃
        if (random.nextDouble() < 0.2) {
            for (int j = 0; j < dim; j++) {
                if (random.nextDouble() < 0.3) {
                    positions[i][j] = lb + ub - positions[i][j];
                }
            }
        }
    }

    /**
     * 利用阶段 - 强调收敛
     */
    private void exploitPhase(int i, double[] weights, double t) {
        // 选择引导者：根据权重向量选择最匹配的存档解
        double[] leader = selectLeaderByWeight(weights);
        if (leader == null) {
            leader = archive.selectLeader();
        }
        if (leader == null || leader.length != dim) {
            leader = flockMemoryX[random.nextInt(population)];
        }

        // 向引导者学习
        double stepFactor = 0.3 + 0.5 * (t - 0.5);
        for (int j = 0; j < dim; j++) {
            double direction = leader[j] - positions[i][j];
            positions[i][j] += stepFactor * direction;
            // 添加小扰动
            positions[i][j] += (random.nextDouble() - 0.5) * (ub - lb) * 0.05 * (1 - t);
        }
    }

    /**
     * 根据权重向量选择最匹配的引导者
     */
    private double[] selectLeaderByWeight(double[] weights) {
        List<double[]> solutions = archive.getSolutions();
        List<ObjectiveValues> objectives = archive.getObjectives();
        
        if (solutions.isEmpty()) return null;
        
        int bestIdx = 0;
        double bestFitness = Double.MAX_VALUE;
        
        for (int i = 0; i < objectives.size(); i++) {
            double fitness = computeWeightedFitness(objectives.get(i), weights);
            if (fitness < bestFitness) {
                bestFitness = fitness;
                bestIdx = i;
            }
        }
        
        return solutions.get(bestIdx).clone();
    }

    public ParetoArchive getArchive() {
        return archive;
    }
}
