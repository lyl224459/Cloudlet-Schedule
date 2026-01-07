package CloudletScheduler.MOOptimizer.nsgaii;

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
 * NSGA-II调度器
 * 使用经典的非支配排序遗传算法进行多目标优化
 */
public class NSGAIIScheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * MainRunner.Config.POPULATION;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private ParetoArchive paretoArchive;
    private ParetoArchive firstGenerationArchive;

    public NSGAIIScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Non-dominated Sorting Genetic Algorithm II (NSGA-II) scheduler");
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
            double costEfficiency = estimateCostEfficiencyForMO(assignment);
            double loadBalanceIndex = estimateLoadBalanceIndexForMO(assignment);
            double resourceWaste = estimateResourceWasteForMO(assignment);
            return new ObjectiveValues(makespan, costEfficiency, loadBalanceIndex, resourceWaste);
        };

        NSGAII optimizer = new NSGAII(
                evalFunc,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_FES,
                ARCHIVE_SIZE
        );

        ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive;
        this.firstGenerationArchive = optimizer.getFirstGenerationArchive();

        if (archive.isEmpty()) {
            Log.printLine("⚠️ Warning: NSGA-II returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        double[] selectedSolution = archive.selectLeader();
        if (selectedSolution == null || selectedSolution.length != cloudletNum) {
            selectedSolution = archive.getSolutions().get(0);
        }

        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(selectedSolution[i]);
            vmId = Math.max(0, Math.min(vmId, vmNum - 1));
            assignment[i] = vmId;
        }

        Log.printLine("✅ NSGA-II found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
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
