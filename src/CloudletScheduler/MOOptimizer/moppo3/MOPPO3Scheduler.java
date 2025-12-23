package CloudletScheduler.MOOptimizer.moppo3;

import CloudletScheduler.MOOptimizer.moppo2.ParetoArchive;
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
 * 多目标 MO-PPO3 调度器
 * 
 * 使用改进的MOPPO3算法，包含：
 * - 混沌映射初始化
 * - 成功率记忆自适应
 * - DE/current-to-best变异
 * - 高斯局部搜索
 * - 多样性监控与重启机制
 */
public class MOPPO3Scheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * MainRunner.Config.POPULATION;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;

    public MOPPO3Scheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Advanced Multi-Objective Predatory Prey Optimization (MO-PPO3) scheduler");
    }

    @Override
    public int[] allocate() {
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double cost = estimateCost(assignment);
            double lb = estimateLB(assignment);
            return new ObjectiveValues(
                    makespan,
                    cost,
                    lb
            );
        };

        MOPPO3 optimizer = new MOPPO3(
                evalFunc,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_FES,
                ARCHIVE_SIZE
        );

        ParetoArchive archive = optimizer.execute();

        if (archive.isEmpty()) {
            Log.printLine("⚠️ MO-PPO3 produced empty archive. Using random assignment.");
            return generateRandomAssignment();
        }

        // 优先选择makespan最优的解
        double[] solution = selectBestSolution(archive);
        if (solution == null || solution.length != cloudletNum) {
            solution = archive.getSolutions().get(0);
        }

        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(solution[i]);
            assignment[i] = Math.max(0, Math.min(vmId, vmNum - 1));
        }

        Log.printLine("✅ MO-PPO3 returned " + archive.size() + " Pareto solutions. Best solution chosen.");
        return assignment;
    }

    /**
     * 选择最佳解：综合考虑各目标
     * 使用归一化加权和方法
     */
    private double[] selectBestSolution(ParetoArchive archive) {
        List<double[]> solutions = archive.getSolutions();
        List<ObjectiveValues> objectives = archive.getObjectives();
        
        if (solutions.isEmpty()) return null;
        if (solutions.size() == 1) return solutions.get(0);

        // 计算各目标的最大最小值用于归一化
        int numObj = objectives.get(0).values.length;
        double[] minVals = new double[numObj];
        double[] maxVals = new double[numObj];
        for (int j = 0; j < numObj; j++) {
            minVals[j] = Double.MAX_VALUE;
            maxVals[j] = Double.MIN_VALUE;
        }
        
        for (ObjectiveValues obj : objectives) {
            for (int j = 0; j < numObj; j++) {
                minVals[j] = Math.min(minVals[j], obj.values[j]);
                maxVals[j] = Math.max(maxVals[j], obj.values[j]);
            }
        }

        // 归一化并计算加权和 (makespan权重更高)
        double[] weights = {0.5, 0.3, 0.2}; // makespan, cost, lb
        int bestIdx = 0;
        double bestScore = Double.MAX_VALUE;
        
        for (int i = 0; i < objectives.size(); i++) {
            double score = 0;
            for (int j = 0; j < numObj; j++) {
                double range = maxVals[j] - minVals[j];
                double normalized = range > 0 ? (objectives.get(i).values[j] - minVals[j]) / range : 0;
                score += weights[j] * normalized;
            }
            if (score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        
        return solutions.get(bestIdx);
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
