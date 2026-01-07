package CloudletScheduler.MOOptimizer.mogwo;

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
 * Multi-Objective Grey Wolf Optimizer Scheduler (MO-GWO)
 * Integrates MO-GWO into CloudSim task scheduling.
 */
public class MOGWOScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive; // 保存最终Pareto存档
    private ParetoArchive firstGenerationArchive; // 保存第一代Pareto存档

    public MOGWOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Multi-Objective Grey Wolf Optimizer (MO-GWO) scheduler");
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
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
//            double totalTime = estimateTotalTime(assignment);
            double cost = estimateCost(assignment);
            double lb = estimateLBForMO(assignment); // 多目标优化使用变异系数
            double resourceUtilization = estimateResourceUtilization(assignment);
            double ruMinimized = 1.0 - resourceUtilization;
            return new ObjectiveValues(makespan, cost, lb, ruMinimized);
        };

        MOGreyWolfOptimizer optimizer = new MOGreyWolfOptimizer(
                evalFunc,
                POPULATION,
                0,                      // lower bound: VM index starts at 0
                vmNum - 1,              // upper bound: max VM index
                cloudletNum,            // dimension = number of cloudlets
                MAX_ITER,
                ARCHIVE_SIZE
        );

        ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存最终Pareto存档
        this.firstGenerationArchive = optimizer.getFirstGenerationArchive(); // 保存第一代Pareto存档

        if (archive.size() == 0) {
            Log.printLine("⚠️ Warning: MO-GWO returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        double[] selectedSolution = archive.selectLeader();
        if (selectedSolution == null || selectedSolution.length != cloudletNum) {
            selectedSolution = archive.getSolutions().get(0);
        }

        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(selectedSolution[i]);
            vmId = Math.max(0, Math.min(vmId, vmNum - 1)); // clamp to [0, vmNum-1]
            assignment[i] = vmId;
        }

        Log.printLine("✅ MO-GWO found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
        return assignment;
    }

    private int[] generateRandomAssignment() {
        int[] assignment = new int[cloudletNum];
        Random rand = new Random();
        for (int i = 0; i < cloudletNum; i++) {
            assignment[i] = rand.nextInt(vmNum);
        }
        return assignment;
    }
}