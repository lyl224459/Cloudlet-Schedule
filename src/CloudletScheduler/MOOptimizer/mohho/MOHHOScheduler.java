package CloudletScheduler.MOOptimizer.mohho;

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
 * Multi-Objective Harris Hawks Optimization Scheduler (MO-HHO)
 * 基于 Pareto 存档的多目标任务调度器。
 */
public class MOHHOScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;

    public MOHHOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Multi-Objective Harris Hawks Optimization (MO-HHO) scheduler");
    }

    @Override
    public int[] allocate() {
        // 定义多目标评估函数
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
//            double totalTime = estimateTotalTime(assignment);
            double cost = estimateCost(assignment);
            double lb = estimateLB(assignment);
            return new ObjectiveValues(makespan,
//                    totalTime,
                    cost, lb);
        };

        MOHarrisHawksOptimization optimizer = new MOHarrisHawksOptimization(
                evalFunc,
                POPULATION,
                0,                      // VM 索引下界
                vmNum - 1,              // VM 索引上界
                cloudletNum,            // 维度 = 任务数量
                MAX_ITER,
                ARCHIVE_SIZE
        );

        ParetoArchive archive = optimizer.execute();

        if (archive.size() == 0) {
            Log.printLine("⚠️ Warning: MO-HHO returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        // 从存档中选择一个高质量解作为最终调度方案
        double[] selectedSolution = archive.selectLeader();
        if (selectedSolution == null || selectedSolution.length != cloudletNum) {
            // fallback：使用第一个非支配解
            selectedSolution = archive.getSolutions().get(0);
        }

        // 转换为合法整数 VM 分配
        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(selectedSolution[i]);
            vmId = Math.max(0, Math.min(vmId, vmNum - 1)); // clamp to [0, vmNum-1]
            assignment[i] = vmId;
        }

        Log.printLine("✅ MO-HHO found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
        return assignment;
    }

    /**
     * 生成随机合法任务分配（fallback）
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