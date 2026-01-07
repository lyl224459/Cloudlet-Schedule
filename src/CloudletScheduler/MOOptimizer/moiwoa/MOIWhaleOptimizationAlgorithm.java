package CloudletScheduler.MOOptimizer.moiwoa;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * MOI-WOA with Greedy Initialization and Constraint Handling
 */
public class MOIWhaleOptimizationAlgorithm {
    private final OptFunctionMulti optFunction;
    private final int population;
    private final int dim;          // number of tasks
    private final int maxIter;
    private final double lb, ub;    // VM ID range: [lb, ub] → e.g., [0, numVMs - 1]

    // --- 新增：约束参数 ---
    private final int[] vmCapacities;      // vmCapacities[i] = max tasks on VM i
    private final int[] taskDemands;       // taskDemands[j] = resource demand of task j (optional)

    private double[][] positions;
    private ObjectiveValues[] objectives;
    private ParetoArchive archive;

    private static final Random random = new Random();
    private static final int ELITE_COUNT = 2;

    /**
     * 构造函数（新增 vmCapacities）
     */
    public MOIWhaleOptimizationAlgorithm(
            OptFunctionMulti optFunction,
            int population,
            double lb,
            double ub,
            int dim,
            int maxIter,
            int archiveMaxSize,
            int[] vmCapacities,
            int[] taskDemands) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.vmCapacities = vmCapacities != null ? vmCapacities.clone() : null;
        this.taskDemands = taskDemands != null ? taskDemands.clone() : null;
        this.archive = new ParetoArchive(archiveMaxSize);

        this.positions = new double[population][dim];
        this.objectives = new ObjectiveValues[population];

        initPopulationWithGreedy();
    }

    // ========================
    // 贪心初始化
    // ========================

    private void initPopulationWithGreedy() {
        int numGreedy = Math.min(population / 2, 3); // 至少1个，最多一半

        // 1. 生成贪心解（Min-Min 风格：任务分配给预计完成时间最短的 VM）
        for (int i = 0; i < numGreedy; i++) {
            double[] greedySol = generateGreedySolution();
            greedySol = repairSolution(greedySol); // 确保可行
            positions[i] = greedySol;
            objectives[i] = evaluate(positions[i]);
            archive.add(positions[i].clone(), objectives[i]);
        }

        // 2. 其余个体随机初始化
        for (int i = numGreedy; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            positions[i] = repairSolution(positions[i]);
            adjustPositions(i);
            objectives[i] = evaluate(positions[i]);
            archive.add(positions[i].clone(), objectives[i]);
        }
    }

    /**
     * 贪心策略：将每个任务分配给当前负载最低的 VM（简单负载均衡）
     */
    private double[] generateGreedySolution() {
        double[] assignment = new double[dim];
        int numVMs = (int) (ub - lb + 1);
        int[] currentLoad = new int[numVMs]; // 当前每个 VM 的任务数

        // 可选：按任务长度排序（长任务优先分配）
        Integer[] taskIndices = IntStream.range(0, dim).boxed().toArray(Integer[]::new);
        // Arrays.sort(taskIndices, (i, j) -> Double.compare(taskLengths[j], taskLengths[i]));

        for (int t : taskIndices) {
            // 找负载最低且未超容的 VM
            int bestVM = 0;
            int minLoad = Integer.MAX_VALUE;
            for (int vm = 0; vm < numVMs; vm++) {
                if (currentLoad[vm] < minLoad &&
                        (vmCapacities == null || currentLoad[vm] < vmCapacities[vm])) {
                    minLoad = currentLoad[vm];
                    bestVM = vm;
                }
            }
            assignment[t] = lb + bestVM; // 转换为全局 VM ID
            currentLoad[bestVM]++;
        }
        return assignment;
    }

    // ========================
    // 约束处理：修复不可行解
    // ========================

    /**
     * 修复解：确保每个 VM 的任务数不超过容量
     */
    private double[] repairSolution(double[] sol) {
        if (vmCapacities == null) return sol;

        double[] fixed = sol.clone();
        int numVMs = vmCapacities.length;
        int[] count = new int[numVMs];

        // 统计当前分配
        for (int j = 0; j < dim; j++) {
            int vmID = (int) Math.round(fixed[j]);
            vmID = Math.max((int) lb, Math.min((int) ub, vmID));
            int localVM = vmID - (int) lb;
            if (localVM >= 0 && localVM < numVMs) {
                count[localVM]++;
            }
        }

        // 修复超载 VM：将多余任务迁移到负载最低的合法 VM
        for (int j = 0; j < dim; j++) {
            int vmID = (int) Math.round(fixed[j]);
            vmID = Math.max((int) lb, Math.min((int) ub, vmID));
            int localVM = vmID - (int) lb;

            if (localVM >= 0 && localVM < numVMs && count[localVM] > vmCapacities[localVM]) {
                // 找一个未满的 VM
                int targetVM = findLeastLoadedUnderCapacity(count);
                if (targetVM != -1) {
                    fixed[j] = lb + targetVM;
                    count[localVM]--;
                    count[targetVM]++;
                }
            }
        }
        return fixed;
    }

    private int findLeastLoadedUnderCapacity(int[] load) {
        int best = -1;
        int minLoad = Integer.MAX_VALUE;
        for (int i = 0; i < load.length; i++) {
            if (load[i] < vmCapacities[i] && load[i] < minLoad) {
                minLoad = load[i];
                best = i;
            }
        }
        return best;
    }

    // ========================
    // 其他方法（保持不变，略作适配）
    // ========================

    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
        // 再次修复（防止 WOA 更新后越界）
        positions[agentIndex] = repairSolution(positions[agentIndex]);
    }

    private ObjectiveValues evaluate(double[] sol) {
        // 先修复（双重保险）
        sol = repairSolution(sol);
        int[] assignment = Arrays.stream(sol)
                .mapToInt(x -> (int) Math.round(x))
                .toArray();


        if (!isFeasible(assignment)) {
            // 先评估一个合法解以获取目标维度（或预设）
            // 更安全的方式：使用已知目标数量（例如 3）
            // 或者：通过临时评估一个合法解获取长度
            int numObjectives = 4; // 👈 根据你的实际目标数设定（makespan, cost, lb, resourceUtilization）

            double[] penalties = new double[numObjectives];
            Arrays.fill(penalties, Double.MAX_VALUE / 10.0);
            return new ObjectiveValues(penalties[0], penalties[1], penalties[2], penalties[3]);
        }

        return optFunction.evaluate(assignment);
    }

    private boolean isFeasible(int[] assignment) {
        if (vmCapacities == null) return true;
        int numVMs = vmCapacities.length;
        int[] count = new int[numVMs];
        for (int vmID : assignment) {
            int localVM = vmID - (int) lb;
            if (localVM < 0 || localVM >= numVMs) return false;
            count[localVM]++;
            if (count[localVM] > vmCapacities[localVM]) return false;
        }
        return true;
    }

    // ========================
    // 以下方法与之前相同（updatePosition, performElitism, execute 等）
    // 为简洁起见，此处省略，实际代码中需保留
    // ========================

    private boolean dominates(ObjectiveValues a, ObjectiveValues b) {
        double[] va = a.getValues();
        double[] vb = b.getValues();
        boolean betterInAtLeastOne = false;
        for (int i = 0; i < va.length; i++) {
            if (va[i] > vb[i]) return false;
            if (va[i] < vb[i]) betterInAtLeastOne = true;
        }
        return betterInAtLeastOne;
    }

    private int[] computeDominationRanks(ObjectiveValues[] objs) {
        int n = objs.length;
        int[] rank = new int[n];
        Arrays.fill(rank, 0);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && dominates(objs[j], objs[i])) {
                    rank[i]++;
                }
            }
        }
        return rank;
    }

    private void performElitism() {
        if (archive.size() == 0) return;
        int eliteCount = Math.min(ELITE_COUNT, archive.size());
        List<double[]> elites = new ArrayList<>();
        for (int i = 0; i < eliteCount; i++) {
            elites.add(archive.getSolution(i).clone());
        }
        int[] ranks = computeDominationRanks(objectives);
        Integer[] indices = IntStream.range(0, population)
                .boxed()
                .sorted((i1, i2) -> Integer.compare(ranks[i1], ranks[i2]))
                .toArray(Integer[]::new);
        for (int i = 0; i < eliteCount && i < elites.size(); i++) {
            int worstIdx = indices[population - 1 - i];
            positions[worstIdx] = elites.get(i);
        }
    }

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
                        positions[i][j] += random.nextGaussian() * 0.1;
                    }
                }
            }
        }
    }

    public ParetoArchive execute() {
        for (int iter = 0; iter < maxIter; iter++) {
            double a = 2.0 - (double) iter * (2.0 / maxIter);
            double a2 = -1.0 + (double) iter * (-1.0 / maxIter);

            double[] leader = archive.selectLeader();
            if (leader == null && !archive.isEmpty()) {
                leader = archive.getSolution(0);
            }

            updatePosition(a, a2, leader);

            for (int i = 0; i < population; i++) {
                adjustPositions(i);
                objectives[i] = evaluate(positions[i]);
                archive.add(positions[i].clone(), objectives[i]);
            }

            performElitism();
        }

        for (int i = 0; i < population; i++) {
            adjustPositions(i);
            objectives[i] = evaluate(positions[i]);
            archive.add(positions[i].clone(), objectives[i]);
        }

        System.out.printf("MOI-WOA (Greedy + Constraint) completed. Final archive size = %d%n", archive.size());
        return archive;
    }

    // Getters
    public double[][] getPositions() { return positions; }
    public ParetoArchive getArchive() { return archive; }
}