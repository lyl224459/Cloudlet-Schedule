package CloudletScheduler.MOOptimizer.moppo2;

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
 * 多目标 MO-PPO2 增强调度器
 * 使用 MOPPO2Enhanced 算法
 */
public class MOPPO2EnhancedScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * MainRunner.Config.POPULATION;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private CloudletScheduler.MOOptimizer.moppo2.ParetoArchive paretoArchive; // 保存Pareto存档

    public MOPPO2EnhancedScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Enhanced Multi-Objective Predatory Prey Optimization (MO-PPO2Enhanced) scheduler");
    }
    
    @Override
    public CloudletScheduler.MOOptimizer.ParetoArchive getParetoArchive() {
        // 将moppo2.ParetoArchive转换为MOOptimizer.ParetoArchive
        if (paretoArchive == null) return null;
        CloudletScheduler.MOOptimizer.ParetoArchive result = 
            new CloudletScheduler.MOOptimizer.ParetoArchive(paretoArchive.getMaxSize());
        List<double[]> solutions = paretoArchive.getSolutions();
        List<CloudletScheduler.datacenter.ObjectiveValues> objectives = paretoArchive.getObjectives();
        for (int i = 0; i < solutions.size(); i++) {
            result.add(solutions.get(i), objectives.get(i));
        }
        return result;
    }

    @Override
    public int[] allocate() {

        // 多目标优化函数：复用父类评估方法
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double costEfficiency = estimateCostEfficiencyForMO(assignment); // 成本效率比
            double loadBalanceIndex = estimateLoadBalanceIndexForMO(assignment); // 负载均衡指数
            double resourceWaste = estimateResourceWasteForMO(assignment); // 资源浪费率
            return new ObjectiveValues(makespan, costEfficiency, loadBalanceIndex, resourceWaste);
        };

        // 创建优化器
        MOPPO2Enhanced optimizer = new MOPPO2Enhanced(
                evalFunc,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_FES,
                ARCHIVE_SIZE
        );

        // 执行优化
        CloudletScheduler.MOOptimizer.moppo2.ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存Pareto存档（moppo2.ParetoArchive类型）

        // 回退：存档为空 → 随机分配
        if (archive.isEmpty()) {
            Log.printLine("⚠️ Warning: MO-PPO2Enhanced returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        // Leader selection：选择高质量解
        double[] solution = archive.selectLeader();
        if (solution == null || solution.length != cloudletNum) {
            solution = archive.getSolutions().get(0);
        }

        // double → int, clamp 到合法 VM 范围
        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(solution[i]);
            assignment[i] = Math.max(0, Math.min(vmId, vmNum - 1));
        }

        Log.printLine("✅ MO-PPO2Enhanced found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");

        return assignment;
    }

    /** 生成随机 fallback 分配 */
    private int[] generateRandomAssignment() {
        int[] arr = new int[cloudletNum];
        Random rand = new Random();
        for (int i = 0; i < cloudletNum; i++) {
            arr[i] = rand.nextInt(vmNum);
        }
        return arr;
    }
}
