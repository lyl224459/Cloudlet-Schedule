package CloudletScheduler.MOOptimizer.modbo;

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
 * 多目标蜣螂优化算法调度器（MO-DBO）
 * 基于 Pareto 支配与拥挤距离维护多样非支配解集。
 * 直接复用父类 Scheduler 中的 estimateXXX 方法进行多目标评估。
 */
public class MODBOScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive; // 保存最终Pareto存档
    private ParetoArchive firstGenerationArchive; // 保存第一代Pareto存档

    public MODBOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Multi-Objective Dung Beetle Optimizer (MO-DBO) scheduler");
    }
    
    @Override
    public ParetoArchive getParetoArchive() {
        return paretoArchive;
    }
    
    @Override
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }

    @Override
    public int[] allocate() {
        // 构建多目标评估函数（使用新的独立目标函数设计）
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double costEfficiency = estimateCostEfficiencyForMO(assignment); // 成本效率比
            double loadBalanceIndex = estimateLoadBalanceIndexForMO(assignment); // 负载均衡指数
            double resourceWaste = estimateResourceWasteForMO(assignment); // 资源浪费率
            return new ObjectiveValues(makespan, costEfficiency, loadBalanceIndex, resourceWaste);
        };

        // 初始化 MO-DBO 优化器
        MODungBeetleOptimizer optimizer = new MODungBeetleOptimizer(
                evalFunc,
                POPULATION,
                0,                      // VM 索引下界
                vmNum - 1,              // VM 索引上界
                cloudletNum,            // 决策变量维度 = 任务数
                MAX_ITER,
                ARCHIVE_SIZE
        );

        // 执行优化
        ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存最终Pareto存档
        this.firstGenerationArchive = optimizer.getFirstGenerationArchive(); // 保存第一代Pareto存档

        // 若存档为空，回退到随机分配
        if (archive.size() == 0) {
            Log.printLine("⚠️ Warning: MO-DBO returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        // 从存档中选择一个高质量且多样化的解（基于拥挤距离或随机 leader）
        double[] selectedSolution = archive.selectLeader();
        if (selectedSolution == null) {
            // 极端情况：selectLeader 返回 null，则取第一个
            selectedSolution = archive.getSolutions().get(0);
        }

        // 转换为整数 VM 分配方案
        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(selectedSolution[i]);
            vmId = Math.max(0, Math.min(vmId, vmNum - 1)); // 确保在 [0, vmNum-1] 范围内
            assignment[i] = vmId;
        }

        Log.printLine("✅ MO-DBO found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
        return assignment;
    }

    /**
     * 生成随机合法的任务-VM分配方案（fallback 策略）
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