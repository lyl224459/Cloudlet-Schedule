package CloudletScheduler.MOOptimizer.mosfoa;

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

public class MOSFOAScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.POPULATION * MainRunner.Config.MAX_ITER;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive; // 保存最终Pareto存档
    private ParetoArchive firstGenerationArchive; // 保存第一代Pareto存档

    public MOSFOAScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Multi-Objective Sunflower Optimization Algorithm (MO-SFOA) scheduler");
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

        MOSunflowerOptimization optimizer = new MOSunflowerOptimization(
                evalFunc,
                POPULATION,
                MAX_FES,
                0,
                vmNum - 1,
                cloudletNum,
                ARCHIVE_SIZE
        );

        ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存最终Pareto存档
        this.firstGenerationArchive = optimizer.getFirstGenerationArchive(); // 保存第一代Pareto存档

        if (archive.isEmpty()) {
            Log.printLine("⚠️ Warning: MO-SFOA returned empty archive. Using random assignment.");
            return generateRandomAssignment();
        }

        double[] selected = archive.selectLeader();
        if (selected == null || selected.length != cloudletNum) {
            selected = archive.getSolutions().get(0);
        }

        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            assignment[i] = Math.max(0, Math.min(vmNum - 1, (int) Math.round(selected[i])));
        }

        Log.printLine("✅ MO-SFOA found " + archive.size() + " non-dominated solutions.");
        return assignment;
    }

    private int[] generateRandomAssignment() {
        int[] assign = new int[cloudletNum];
        Random r = new Random();
        for (int i = 0; i < cloudletNum; i++) {
            assign[i] = r.nextInt(vmNum);
        }
        return assign;
    }
}