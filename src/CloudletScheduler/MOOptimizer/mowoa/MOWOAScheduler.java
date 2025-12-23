package CloudletScheduler.MOOptimizer.mowoa;

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

public class MOWOAScheduler extends Scheduler {
    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;

    public MOWOAScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Multi-Objective Whale Optimization Algorithm (MO-WOA) scheduler");
    }

    @Override
    public int[] allocate() {
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
//            double totalTime = estimateTotalTime(assignment);
            double cost = estimateCost(assignment);
            double lb = estimateLB(assignment);
            return new ObjectiveValues(makespan,
//                    totalTime,
                    cost, lb);
        };

        MOWhaleOptimizationAlgorithm optimizer = new MOWhaleOptimizationAlgorithm(
                evalFunc,
                POPULATION,
                0,               // VM 下界
                vmNum - 1,       // VM 上界
                cloudletNum,     // 维度 = 任务数
                MAX_ITER,
                ARCHIVE_SIZE
        );

        ParetoArchive archive = optimizer.execute();

        if (archive.isEmpty()) {
            Log.printLine("⚠️ MO-WOA returned empty archive. Using random assignment.");
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

        Log.printLine("✅ MO-WOA found " + archive.size() + " non-dominated solutions.");
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