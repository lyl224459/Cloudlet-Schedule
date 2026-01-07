package CloudletScheduler.MOOptimizer.moiwoa;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * 多目标改进型鲸鱼优化算法调度器（MO-IWOA）
 * 集成：
 *   - 贪心初始化（负载均衡导向）
 *   - VM 任务容量约束处理
 *   - 可行性保障
 */
public class MOIWOAScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive; // 保存Pareto存档

    // --- 约束参数 ---
    private final int maxTasksPerVm; // 每个 VM 最大任务数（可根据 VM 能力动态计算）

    public MOIWOAScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        // 示例：每个 VM 最多运行 ceil(cloudletNum / vmNum * 2) 个任务，避免过载
        this.maxTasksPerVm = (int) Math.ceil((double) cloudletNum / vmNum * 1.5);
        Log.printLine("Using MO-IWOA with Greedy Initialization and Constraint Handling");
        Log.printLine("→ Max tasks per VM: " + maxTasksPerVm);
    }
    
    @Override
    public ParetoArchive getParetoArchive() {
        return paretoArchive;
    }

    @Override
    public int[] allocate() {
        // 构建 VM 容量数组（所有 VM 同构，若异构可按 VM MIPS 动态分配）
        int[] vmCapacities = new int[vmNum];
        for (int i = 0; i < vmNum; i++) {
            vmCapacities[i] = maxTasksPerVm;
        }

        // 多目标评估函数
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double cost = estimateCost(assignment);
            double lb = estimateLBForMO(assignment); // 多目标优化使用变异系数
            double resourceUtilization = estimateResourceUtilization(assignment);
            double ruMinimized = 1.0 - resourceUtilization;
            return new ObjectiveValues(makespan, cost, lb, ruMinimized);
        };

        // 创建优化器（传入约束）
        MOIWhaleOptimizationAlgorithm optimizer = new MOIWhaleOptimizationAlgorithm(
                evalFunc,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_ITER,
                ARCHIVE_SIZE,
                vmCapacities,   // ← 关键：传入约束
                null            // taskDemands（暂不使用）
        );

        ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存Pareto存档

        if (archive.size() == 0) {
            Log.printLine("⚠️ Warning: MO-IWOA returned empty Pareto archive. Using greedy fallback.");
            return generateGreedyAssignment();
        }

        // 选择解：优先选 makespan 最小的（可替换为 archive.selectLeader()）
        double[] bestSolution = selectBestByMakespan(archive);

        // 转换并修复（双重保险）
        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(bestSolution[i]);
            vmId = Math.max(0, Math.min(vmId, vmNum - 1));
            assignment[i] = vmId;
        }

        // 最终修复（确保满足容量）
        assignment = repairAssignment(assignment, vmCapacities);

        Log.printLine("✅ MO-IWOA found " + archive.size() + " non-dominated solutions. Selected best by makespan.");
        return assignment;
    }

    /**
     * 从 Pareto 存档中选择 makespan 最小的解（更实用）
     */
    private double[] selectBestByMakespan(ParetoArchive archive) {
        List<double[]> solutions = archive.getSolutions();
        List<ObjectiveValues> objectives = archive.getObjectives();

        int bestIdx = 0;
        double minMakespan = Double.MAX_VALUE;
        for (int i = 0; i < objectives.size(); i++) {
            double makespan = objectives.get(i).getValues()[0]; // 假设第0维是 makespan
            if (makespan < minMakespan) {
                minMakespan = makespan;
                bestIdx = i;
            }
        }
        return solutions.get(bestIdx).clone();
    }

    /**
     * 贪心回退方案（负载均衡）
     */
    private int[] generateGreedyAssignment() {
        int[] assignment = new int[cloudletNum];
        int[] load = new int[vmNum];

        for (int i = 0; i < cloudletNum; i++) {
            // 找当前负载最低且未超容的 VM
            int bestVm = 0;
            int minLoad = Integer.MAX_VALUE;
            for (int vm = 0; vm < vmNum; vm++) {
                if (load[vm] < minLoad && load[vm] < maxTasksPerVm) {
                    minLoad = load[vm];
                    bestVm = vm;
                }
            }
            assignment[i] = bestVm;
            load[bestVm]++;
        }
        return assignment;
    }

    /**
     * 修复分配方案以满足 VM 容量约束
     */
    private int[] repairAssignment(int[] assignment, int[] vmCapacities) {
        int[] count = new int[vmNum];
        for (int vmId : assignment) {
            if (vmId >= 0 && vmId < vmNum) {
                count[vmId]++;
            }
        }

        // 复制一份用于修改
        int[] fixed = assignment.clone();

        // 处理超载 VM
        for (int i = 0; i < fixed.length; i++) {
            int vmId = fixed[i];
            if (vmId < 0 || vmId >= vmNum) continue;
            if (count[vmId] > vmCapacities[vmId]) {
                // 迁移到负载最低的合法 VM
                int target = findLeastLoadedUnderCapacity(count, vmCapacities);
                if (target != -1) {
                    fixed[i] = target;
                    count[vmId]--;
                    count[target]++;
                }
            }
        }
        return fixed;
    }

    private int findLeastLoadedUnderCapacity(int[] load, int[] capacities) {
        int best = -1;
        int minLoad = Integer.MAX_VALUE;
        for (int i = 0; i < load.length; i++) {
            if (load[i] < capacities[i] && load[i] < minLoad) {
                minLoad = load[i];
                best = i;
            }
        }
        return best;
    }
}