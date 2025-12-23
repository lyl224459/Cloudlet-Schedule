package CloudletScheduler.MOOptimizer.moppo3;

import CloudletScheduler.MOOptimizer.moppo2.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.Random;

/**
 * Multi-Objective Predatory Prey Optimization 3 (MO-PPO3)
 *
 * 相比MOPPO2Enhanced的改进：
 * 1. 混沌映射初始化 (Tent Map) - 提高初始种群多样性
 * 2. 成功率记忆自适应 - 根据历史成功率动态调整参数
 * 3. DE/current-to-best/1 变异 - 更强的精英引导
 * 4. 高斯局部搜索 - 对优秀解进行精细化搜索
 * 5. 多样性监控与重启机制 - 防止早熟收敛
 * 6. 多策略协同 - 探索与利用的动态平衡
 */
public class MOPPO3 {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;

    private final double[][] positions;
    private final double[][] flockMemoryX;
    private final ObjectiveValues[] flockMemoryF;
    private final ParetoArchive archive;
    private int evaluations;

    // 成功率记忆
    private double successRatePredator = 0.5;
    private double successRatePrey = 0.5;
    private double successRateDE = 0.5;
    private int successCountPredator = 0, totalCountPredator = 0;
    private int successCountPrey = 0, totalCountPrey = 0;
    private int successCountDE = 0, totalCountDE = 0;

    // 多样性监控
    private double lastDiversity = Double.MAX_VALUE;
    private int stagnationCount = 0;
    private static final int STAGNATION_THRESHOLD = 10;
    private static final double DIVERSITY_THRESHOLD = 0.1;

    private static final Random random = new Random();
    private final NormalDistribution normal = new NormalDistribution();

    public MOPPO3(OptFunctionMulti optFunction,
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

        initializeWithChaos();
    }

    /**
     * 混沌映射初始化 (Tent Map)
     * 比纯随机初始化有更好的遍历性
     */
    private void initializeWithChaos() {
        double[] chaos = new double[dim];
        // 初始混沌值
        for (int j = 0; j < dim; j++) {
            chaos[j] = random.nextDouble();
        }

        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                // Tent Map: x_{n+1} = 2*x_n if x_n < 0.5, else 2*(1-x_n)
                chaos[j] = tentMap(chaos[j]);
                // 映射到解空间
                positions[i][j] = lb + chaos[j] * (ub - lb);
            }
            roundAndClamp(positions[i]);
            evaluateAndArchive(i);
        }

        // 初始化记忆
        for (int i = 0; i < population; i++) {
            flockMemoryX[i] = positions[i].clone();
            flockMemoryF[i] = evaluate(positions[i]);
        }
    }

    /**
     * Tent Map 混沌映射 - 确保输出在[0,1]范围
     */
    private double tentMap(double x) {
        // 确保输入在有效范围
        x = Math.max(0.001, Math.min(0.999, x));
        double mu = 0.499;
        double result;
        if (x < mu) {
            result = x / mu;
        } else {
            result = (1.0 - x) / (1.0 - mu);
        }
        // 确保输出在[0,1]
        return Math.max(0.0, Math.min(1.0, result));
    }

    /**
     * 离散化 + 边界处理
     */
    private void roundAndClamp(double[] pos) {
        for (int j = 0; j < dim; j++) {
            pos[j] = Math.round(pos[j]);
            if (pos[j] < lb) pos[j] = lb;
            if (pos[j] > ub) pos[j] = ub;
        }
    }

    /**
     * 评价函数 - 确保索引在有效范围内
     */
    private ObjectiveValues evaluate(double[] pos) {
        int[] params = new int[pos.length];
        for (int j = 0; j < pos.length; j++) {
            int val = (int) Math.round(pos[j]);
            // 确保索引在有效范围 [lb, ub]
            if (val < (int) lb) val = (int) lb;
            if (val > (int) ub) val = (int) ub;
            params[j] = val;
        }
        return optFunction.evaluate(params);
    }

    /**
     * 评价并更新存档
     */
    private void evaluateAndArchive(int i) {
        if (evaluations >= maxFEs) return;
        ObjectiveValues obj = evaluate(positions[i]);
        archive.add(positions[i], obj);
        evaluations++;
    }

    /**
     * 自适应Lévy Flight - 限制步长防止越界
     * β根据成功率和进度动态调整
     */
    private double[] levyFlight(int d, double beta) {
        double sigma = Math.pow(
                Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta);
        double[] step = new double[d];
        double maxStep = (ub - lb) * 0.5; // 限制最大步长为搜索空间的50%
        for (int i = 0; i < d; i++) {
            double u = normal.sample() * sigma;
            double v = Math.abs(normal.sample()) + 1e-10; // 避免除零
            step[i] = u / Math.pow(v, 1.0 / beta);
            // 限制步长范围
            step[i] = Math.max(-maxStep, Math.min(maxStep, step[i]));
        }
        return step;
    }

    /**
     * DE/current-to-best/1 变异
     * 比DE/rand/1更强的精英引导
     */
    private double[] deCurrentToBest(int target, double[] best, double adaptiveF) {
        int r1, r2;
        do { r1 = random.nextInt(population); } while (r1 == target);
        do { r2 = random.nextInt(population); } while (r2 == target || r2 == r1);

        double[] v = new double[dim];
        for (int j = 0; j < dim; j++) {
            // current-to-best/1: v = x_i + F*(best - x_i) + F*(x_r1 - x_r2)
            v[j] = positions[target][j]
                    + adaptiveF * (best[j] - positions[target][j])
                    + adaptiveF * (positions[r1][j] - positions[r2][j]);
        }
        roundAndClamp(v);
        return v;
    }

    /**
     * 高斯局部搜索
     * 对优秀个体进行精细化搜索
     */
    private double[] gaussianLocalSearch(double[] pos, double sigma) {
        double[] newPos = new double[dim];
        for (int j = 0; j < dim; j++) {
            newPos[j] = pos[j] + normal.sample() * sigma;
        }
        roundAndClamp(newPos);
        return newPos;
    }

    /**
     * 计算种群多样性 (平均距离)
     */
    private double computeDiversity() {
        double totalDist = 0.0;
        int count = 0;
        double[] centroid = new double[dim];

        // 计算质心
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                centroid[j] += positions[i][j];
            }
        }
        for (int j = 0; j < dim; j++) {
            centroid[j] /= population;
        }

        // 计算到质心的平均距离
        for (int i = 0; i < population; i++) {
            double dist = 0;
            for (int j = 0; j < dim; j++) {
                dist += Math.pow(positions[i][j] - centroid[j], 2);
            }
            totalDist += Math.sqrt(dist);
        }

        return totalDist / population / (ub - lb); // 归一化
    }

    /**
     * 部分重启机制
     * 当多样性过低时，重新初始化部分个体
     */
    private void partialRestart(double ratio) {
        int restartCount = (int) (population * ratio);
        double[] chaos = new double[dim];
        for (int j = 0; j < dim; j++) {
            chaos[j] = random.nextDouble();
        }

        // 随机选择个体重启
        for (int k = 0; k < restartCount; k++) {
            int i = random.nextInt(population);
            for (int j = 0; j < dim; j++) {
                chaos[j] = tentMap(chaos[j]);
                positions[i][j] = lb + chaos[j] * (ub - lb);
            }
            roundAndClamp(positions[i]);
        }
    }

    /**
     * 计算伪适应度
     */
    private double[] computePseudoFitness() {
        double[] fitness = new double[population];
        var archiveObjs = archive.getObjectives();
        if (archiveObjs.isEmpty()) {
            Arrays.fill(fitness, 1.0);
            return fitness;
        }
        for (int i = 0; i < population; i++) {
            double minDist = Double.MAX_VALUE;
            ObjectiveValues obj = evaluate(positions[i]);
            for (ObjectiveValues aObj : archiveObjs) {
                minDist = Math.min(minDist, euclideanDistance(obj.values, aObj.values));
            }
            fitness[i] = 1.0 / (minDist + 1e-9);
        }
        return fitness;
    }

    private double euclideanDistance(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(s);
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
     * 更新成功率记忆
     */
    private void updateSuccessRate(String strategy, boolean success) {
        switch (strategy) {
            case "predator":
                totalCountPredator++;
                if (success) successCountPredator++;
                if (totalCountPredator >= 20) {
                    successRatePredator = 0.9 * successRatePredator + 0.1 * ((double) successCountPredator / totalCountPredator);
                    successCountPredator = totalCountPredator = 0;
                }
                break;
            case "prey":
                totalCountPrey++;
                if (success) successCountPrey++;
                if (totalCountPrey >= 20) {
                    successRatePrey = 0.9 * successRatePrey + 0.1 * ((double) successCountPrey / totalCountPrey);
                    successCountPrey = totalCountPrey = 0;
                }
                break;
            case "de":
                totalCountDE++;
                if (success) successCountDE++;
                if (totalCountDE >= 20) {
                    successRateDE = 0.9 * successRateDE + 0.1 * ((double) successCountDE / totalCountDE);
                    successCountDE = totalCountDE = 0;
                }
                break;
        }
    }

    /**
     * 主循环
     */
    public ParetoArchive execute() {
        while (evaluations < maxFEs) {
            double t = (double) evaluations / maxFEs;
            double[] pseudoF = computePseudoFitness();
            double maxF = Arrays.stream(pseudoF).max().orElse(1.0);

            // 多样性监控
            double currentDiversity = computeDiversity();
            if (Math.abs(currentDiversity - lastDiversity) < DIVERSITY_THRESHOLD * lastDiversity) {
                stagnationCount++;
            } else {
                stagnationCount = 0;
            }
            lastDiversity = currentDiversity;

            // 触发部分重启
            if (stagnationCount >= STAGNATION_THRESHOLD) {
                partialRestart(0.3); // 重启30%个体
                stagnationCount = 0;
            }

            // 获取当前最优解用于引导
            double[] globalBest = archive.getBestBySum();
            if (globalBest == null) {
                globalBest = positions[0];
            }

            for (int i = 0; i < population; i++) {
                double fitness = pseudoF[i] / (maxF + 1e-9);
                double[] oldPos = positions[i].clone();
                ObjectiveValues oldObj = flockMemoryF[i];

                // 自适应策略选择 - 基于成功率
                double totalRate = successRatePredator + successRatePrey + 0.01;
                double predatorProb = successRatePredator / totalRate;

                // 时间因子调制
                double adjustedPredatorProb = predatorProb * (0.3 + 0.7 * t);
                boolean usePredator = random.nextDouble() < adjustedPredatorProb;

                // 自适应β: 成功率低时增加探索
                double beta = 1.5 - 0.5 * t * successRatePrey;

                String usedStrategy;
                if (usePredator) {
                    usedStrategy = "predator";
                    // Predator状态: 精英引导
                    double[] leader = archive.selectLeader();
                    if (leader == null || leader.length != dim) {
                        var sols = archive.getSolutions();
                        leader = sols.isEmpty() ? positions[random.nextInt(population)]
                                : sols.get(random.nextInt(sols.size()));
                    }

                    // 自适应步长
                    double stepFactor = 0.5 + 0.5 * (1 - t) * (1 - fitness);
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = leader[j] + stepFactor * Math.cos(random.nextDouble() * Math.PI)
                                * (positions[i][j] - leader[j]);
                    }
                } else {
                    usedStrategy = "prey";
                    // Prey状态: Lévy飞行探索
                    double[] levy = levyFlight(dim, beta);
                    double stepScale = (1 - t) * (1 + fitness);
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] += levy[j] * stepScale;
                    }
                }

                // 自适应DE变异
                double deProb = 0.2 + 0.3 * successRateDE;
                if (random.nextDouble() < deProb) {
                    double adaptiveF = 0.4 + 0.4 * random.nextDouble() * (1 + successRateDE);
                    double[] mutant = deCurrentToBest(i, globalBest, adaptiveF);
                    double mixRatio = 0.3 + 0.2 * successRateDE;
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = (1 - mixRatio) * positions[i][j] + mixRatio * mutant[j];
                    }
                    usedStrategy = "de";
                }

                // 高斯局部搜索 (对高适应度个体)
                if (fitness > 0.7 && random.nextDouble() < 0.2) {
                    double sigma = (ub - lb) * 0.05 * (1 - t);
                    double[] localPos = gaussianLocalSearch(positions[i], sigma);
                    ObjectiveValues localObj = evaluate(localPos);
                    ObjectiveValues currentObj = evaluate(positions[i]);
                    if (!isDominated(localObj, currentObj)) {
                        positions[i] = localPos;
                    }
                }

                roundAndClamp(positions[i]);

                // 更新成功率
                ObjectiveValues newObj = evaluate(positions[i]);
                boolean success = !isDominated(newObj, oldObj);
                updateSuccessRate(usedStrategy, success);
            }

            // 更新存档
            for (int i = 0; i < population && evaluations < maxFEs; i++) {
                evaluateAndArchive(i);
            }

            // 更新记忆
            for (int i = 0; i < population; i++) {
                ObjectiveValues cur = evaluate(positions[i]);
                if (!isDominated(cur, flockMemoryF[i])) {
                    flockMemoryF[i] = cur;
                    flockMemoryX[i] = positions[i].clone();
                }
            }
        }

        return archive;
    }

    public ParetoArchive getArchive() {
        return archive;
    }
}
