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
    
    private CloudletScheduler.MOOptimizer.moppo2.ParetoArchive paretoArchive; // 保存Pareto存档

    public MOPPO3Scheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Advanced Multi-Objective Predatory Prey Optimization (MO-PPO3) scheduler");
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
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double costEfficiency = estimateCostEfficiencyForMO(assignment); // 成本效率比
            double loadBalanceIndex = estimateLoadBalanceIndexForMO(assignment); // 负载均衡指数
            double resourceWaste = estimateResourceWasteForMO(assignment); // 资源浪费率
            return new ObjectiveValues(makespan, costEfficiency, loadBalanceIndex, resourceWaste);
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

        CloudletScheduler.MOOptimizer.moppo2.ParetoArchive archive = optimizer.execute();
        this.paretoArchive = archive; // 保存Pareto存档

        if (archive.isEmpty()) {
            Log.printLine("⚠️ Warning: MO-PPO3 returned empty Pareto archive. Using random assignment.");
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

        Log.printLine("✅ MO-PPO3 found " + archive.size() + " non-dominated solutions. Best solution chosen.");
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
        double[] weights = {0.4, 0.25, 0.2, 0.15}; // makespan, costEfficiency, loadBalanceIndex, resourceWaste
        int bestIdx = 0;
        double bestScore = Double.MAX_VALUE;
        
        for (int i = 0; i < objectives.size(); i++) {
            double score = 0;
            for (int j = 0; j < Math.min(numObj, weights.length); j++) {
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
