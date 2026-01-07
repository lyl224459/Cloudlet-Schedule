package CloudletScheduler.MOOptimizer.moppo2;

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
 * 多目标 MO-PPO2 调度器
 * 使用改进后的 MOPPO2 算法
 */
public class MOPPO2Scheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * MainRunner.Config.POPULATION;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive; // 保存Pareto存档

    public MOPPO2Scheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Improved Multi-Objective Predatory Prey Optimization (MO-PPO2) scheduler");
    }
    
    @Override
    public ParetoArchive getParetoArchive() {
        return paretoArchive;
    }

    @Override
    public int[] allocate() {

        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double costEfficiency = estimateCostEfficiencyForMO(assignment); // 成本效率比
            double loadBalanceIndex = estimateLoadBalanceIndexForMO(assignment); // 负载均衡指数
            double resourceWaste = estimateResourceWasteForMO(assignment); // 资源浪费率
            return new ObjectiveValues(makespan, costEfficiency, loadBalanceIndex, resourceWaste);
        };

        MOPPO2 optimizer = new MOPPO2(
                evalFunc,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_FES,
                ARCHIVE_SIZE
        );

        ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存Pareto存档

        if (archive.isEmpty()) {
            Log.printLine("⚠️ Warning: MO-PPO2 returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        double[] solution = archive.selectLeader();
        if (solution == null || solution.length != cloudletNum) {
            solution = archive.getSolutions().get(0);
        }

        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(solution[i]);
            assignment[i] = Math.max(0, Math.min(vmId, vmNum - 1));
        }

        Log.printLine("✅ MO-PPO2 found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
        return assignment;
    }

    private int[] generateRandomAssignment() {
        int[] arr = new int[cloudletNum];
        Random rand = new Random();
        for (int i = 0; i < cloudletNum; i++) {
            arr[i] = rand.nextInt(vmNum);
        }
        return arr;
    }
}
