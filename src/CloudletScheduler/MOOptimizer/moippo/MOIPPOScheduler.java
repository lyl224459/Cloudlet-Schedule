package CloudletScheduler.MOOptimizer.moippo;

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
 * 多目标改进掠食者-猎物优化算法调度器（MO-IPPO）
 * 
 * 基于改进的MO-PPO算法，针对云计算任务调度的4个多目标函数进行优化：
 * - Makespan（最大完成时间）
 * - CostEfficiency（成本效率比）
 * - LoadBalanceIndex（负载均衡指数）
 * - ResourceWaste（资源浪费率）
 * 
 * 主要改进：
 * 1. 改进的初始化策略（整数随机初始化 + 贪心初始化）
 * 2. 目标函数感知的搜索策略
 * 3. 多样性增强机制
 * 4. 自适应参数调整
 * 5. 局部搜索增强
 */
public class MOIPPOScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * MainRunner.Config.POPULATION;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive; // 保存最终Pareto存档
    private ParetoArchive firstGenerationArchive; // 保存第一代Pareto存档

    public MOIPPOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Improved Multi-Objective Predatory Prey Optimization (MO-IPPO) scheduler");
    }
    
    @Override
    public ParetoArchive getParetoArchive() {
        return paretoArchive;
    }
    
    /**
     * 获取第一代Pareto存档
     */
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }

    @Override
    public int[] allocate() {
        // 定义多目标评估函数（使用新的独立目标函数设计）
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double costEfficiency = estimateCostEfficiencyForMO(assignment); // 成本效率比
            double loadBalanceIndex = estimateLoadBalanceIndexForMO(assignment); // 负载均衡指数
            double resourceWaste = estimateResourceWasteForMO(assignment); // 资源浪费率
            return new ObjectiveValues(makespan, costEfficiency, loadBalanceIndex, resourceWaste);
        };

        // 创建 MO-IPPO 优化器
        MOImprovedPPO optimizer = new MOImprovedPPO(
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
            Log.printLine("⚠️ Warning: MO-IPPO returned empty Pareto archive. Using random assignment.");
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

        Log.printLine("✅ MO-IPPO found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
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
