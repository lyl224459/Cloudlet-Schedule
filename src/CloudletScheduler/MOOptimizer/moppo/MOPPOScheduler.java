package CloudletScheduler.MOOptimizer.moppo;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;
import java.util.Random;

/**
 * 多目标掠食者-猎物优化算法调度器（MO-PPO）
 * 基于 Pareto 存档维护非支配解集，支持多目标云任务调度。
 * 直接复用父类 Scheduler 中的 estimateXXX 方法进行多目标评估。
 */
public class MOPPOScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * MainRunner.Config.POPULATION; // 转换为函数评估次数
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive; // 保存最终Pareto存档
    private ParetoArchive firstGenerationArchive; // 保存第一代Pareto存档

    public MOPPOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Multi-Objective Predatory Prey Optimization (MO-PPO) scheduler");
    }
    
    @Override
    public ParetoArchive getParetoArchive() {
        return paretoArchive;
    }
    
    /**
     * 获取第一代Pareto存档
     * @return 第一代Pareto存档
     */
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }

    @Override
    public int[] allocate() {
        // 定义多目标评估函数：复用父类的 estimateXXX 方法
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);      // 最大完成时间
            double cost = estimateCost(assignment);              // 总成本
            double lb = estimateLBForMO(assignment);                  // 负载均衡度（值越小越均衡，使用变异系数）
            double resourceUtilization = estimateResourceUtilization(assignment); // 资源利用率
            // 转换为最小化：1 - utilization
            double ruMinimized = 1.0 - resourceUtilization;
            return new ObjectiveValues(makespan, cost, lb, ruMinimized);
        };

        // 创建 MO-PPO 优化器
        MOPredatoryPreyOptimization optimizer = new MOPredatoryPreyOptimization(
                evalFunc,
                POPULATION,
                0,                      // 决策变量下界（VM 索引从 0 开始）
                vmNum - 1,              // 决策变量上界（最大 VM ID）
                cloudletNum,            // 解的维度 = 任务数量
                MAX_FES,                // 最大函数评估次数（FEs）
                ARCHIVE_SIZE            // Pareto 存档最大容量
        );

        // 执行优化
        ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存最终Pareto存档
        this.firstGenerationArchive = optimizer.getFirstGenerationArchive(); // 保存第一代Pareto存档

        // 若存档为空，回退到随机分配
        if (archive.isEmpty()) {
            Log.printLine("⚠️ Warning: MO-PPO returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        // 使用 ParetoArchive 的 leader selection 策略选择高质量解
        double[] selectedSolution = archive.selectLeader();
        if (selectedSolution == null || selectedSolution.length != cloudletNum) {
            // fallback：取第一个非支配解
            selectedSolution = archive.getSolutions().get(0);
        }

        // 转换为整数型 VM 分配方案，并确保在合法范围内 [0, vmNum - 1]
        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(selectedSolution[i]);
            vmId = Math.max(0, Math.min(vmId, vmNum - 1)); // clamp
            assignment[i] = vmId;
        }

        Log.printLine("✅ MO-PPO found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
        return assignment;
    }

    /**
     * 生成随机合法分配（fallback 方案）
     */
    private int[] generateRandomAssignment() {
        int[] assignment = new int[cloudletNum];
        Random rand = new Random();
        for (int i = 0; i < cloudletNum; i++) {
            assignment[i] = rand.nextInt(vmNum);
        }
        return assignment;
    }
}