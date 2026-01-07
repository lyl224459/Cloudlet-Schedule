package CloudletScheduler.MOOptimizer.mowoa;

import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import CloudletScheduler.MOOptimizer.ParetoArchive;

import java.util.Arrays;
import java.util.Random;

/**
 * 多目标鲸鱼优化算法（MO-WOA）
 * 模拟鲸鱼狩猎行为，结合 Pareto 存档处理多个优化目标
 */
public class MOWhaleOptimizationAlgorithm {
    private final OptFunctionMulti optFunction;
    private final int population;
    private final int dim;
    private final int maxIter;
    private final double lb, ub;

    private double[][] positions;
    private ObjectiveValues[] objectives;
    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive; // 第一代Pareto存档快照

    private static final Random random = new Random();
    private static final double F = 0.8; // 差分变异因子（可选）
    private static final int DEGREES_OF_FREEDOM = 5;

    public MOWhaleOptimizationAlgorithm(
            OptFunctionMulti optFunction,
            int population,
            double lb,
            double ub,
            int dim,
            int maxIter,
            int archiveMaxSize) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.archive = new ParetoArchive(archiveMaxSize);

        this.positions = new double[population][dim];
        this.objectives = new ObjectiveValues[population];

        initPopulation();
    }

    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
    }

    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPositions(i);
            objectives[i] = evaluate(positions[i]);
            archive.add(positions[i].clone(), objectives[i]);
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

    private ObjectiveValues evaluate(double[] sol) {
        int[] assignment = Arrays.stream(sol)
                .mapToInt(x -> (int) Math.round(x))
                .toArray();
        return optFunction.evaluate(assignment);
    }

    /**
     * 更新所有鲸鱼的位置，使用存档中的 leader 作为引导者
     */
    private void updatePosition(double a, double a2, double[] leader) {
        for (int i = 0; i < population; i++) {
            double r1 = random.nextDouble();
            double r2 = random.nextDouble();
            double A = 2.0 * a * r1 - a;
            double C = 2.0 * r2;
            double b = 1.0;
            double l = (a2 - 1.0) * random.nextDouble() + 1.0;
            double p = random.nextDouble();

            for (int j = 0; j < dim; j++) {
                if (p < 0.5) {
                    if (Math.abs(A) < 1 && leader != null) {
                        double D_Leader = Math.abs(C * leader[j] - positions[i][j]);
                        positions[i][j] = leader[j] - A * D_Leader;
                    } else {
                        // 随机选择另一个个体（非支配解或种群内）
                        int randIdx = random.nextInt(population);
                        double[] randomPos = positions[randIdx];
                        double D_X_rand = Math.abs(C * randomPos[j] - positions[i][j]);
                        positions[i][j] = randomPos[j] - A * D_X_rand;
                    }
                } else {
                    if (leader != null) {
                        double distance2Leader = Math.abs(leader[j] - positions[i][j]);
                        positions[i][j] = distance2Leader * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + leader[j];
                    } else {
                        // fallback: 不更新或轻微扰动
                        positions[i][j] += random.nextGaussian() * 0.1;
                    }
                }
            }
        }
    }

    public ParetoArchive execute() {
        for (int iter = 0; iter < maxIter; iter++) {
            // 从存档中选择 leader（用于引导搜索）
            double[] leader = archive.selectLeader();

            // 计算当前种群的目标值并更新存档
            for (int i = 0; i < population; i++) {
                adjustPositions(i);
                objectives[i] = evaluate(positions[i]);
                archive.add(positions[i].clone(), objectives[i]);
            }

            // 动态参数 a 和 a2
            double a = 2.0 - (double) iter * (2.0 / maxIter);
            double a2 = -1.0 + (double) iter * (-1.0 / maxIter);

            // 更新位置
            updatePosition(a, a2, leader);

            // 可选：局部搜索或差分变异增强多样性
            // （此处省略以保持简洁，可根据需要添加）
        }

        // 最终评估
        for (int i = 0; i < population; i++) {
            adjustPositions(i);
            objectives[i] = evaluate(positions[i]);
            archive.add(positions[i].clone(), objectives[i]);
        }

        System.out.printf("MO-WOA completed. Final archive size = %d%n", archive.size());
        return archive;
    }

    // Getters
    public double[][] getPositions() { return positions; }
    public ParetoArchive getArchive() { return archive; }
}