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
 * 多目标 MO-PPO3v2 调度器
 * 
 * 使用改进的MOPPO3v2算法，针对多目标均衡优化：
 * - 反向学习初始化
 * - 目标分解引导
 * - 精英池机制
 * - 自适应交叉操作
 * - 弱势目标增强
 */
public class MOPPO3v2Scheduler extends Scheduler {

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * MainRunner.Config.POPULATION;
    private static final int ARCHIVE_SIZE = MainRunner.Config.ARCHIVE_SIZE;
    
    private CloudletScheduler.MOOptimizer.moppo2.ParetoArchive paretoArchive; // 保存Pareto存档

    public MOPPO3v2Scheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Balanced Multi-Objective PPO (MO-PPO3v2) scheduler");
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

        MOPPO3v2 optimizer = new MOPPO3v2(
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
            Log.printLine("⚠️ Warning: MO-PPO3v2 returned empty Pareto archive. Using random assignment.");
            return generateRandomAssignment();
        }

        // 使用均衡选择策略
        double[] solution = selectBalancedSolution(archive);
        if (solution == null || solution.length != cloudletNum) {
            solution = archive.getSolutions().get(0);
        }

        int[] assignment = new int[cloudletNum];
        for (int i = 0; i < cloudletNum; i++) {
            int vmId = (int) Math.round(solution[i]);
            assignment[i] = Math.max(0, Math.min(vmId, vmNum - 1));
        }

        Log.printLine("✅ MO-PPO3v2 found " + archive.size() + " non-dominated solutions. Selected one via leader selection.");
        return assignment;
    }

    /**
     * 均衡选择策略：综合考虑各目标的表现
     * 使用归一化后的最小距离法 (距离理想点最近)
     */
    private double[] selectBalancedSolution(ParetoArchive archive) {
        List<double[]> solutions = archive.getSolutions();
        List<ObjectiveValues> objectives = archive.getObjectives();
        
        if (solutions.isEmpty()) return null;
        if (solutions.size() == 1) return solutions.get(0);

        int numObj = objectives.get(0).values.length;
        
        // 计算各目标的最大最小值
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

        // 理想点：各目标的最小值
        // 计算每个解到理想点的归一化欧氏距离
        // 权重：makespan=0.3, costEfficiency=0.3, loadBalanceIndex=0.25, resourceWaste=0.15 (更均衡的权重)
        double[] weights = {0.3, 0.3, 0.25, 0.15};
        
        int bestIdx = 0;
        double bestDist = Double.MAX_VALUE;
        
        for (int i = 0; i < objectives.size(); i++) {
            double dist = 0;
            for (int j = 0; j < Math.min(numObj, weights.length); j++) {
                double range = maxVals[j] - minVals[j];
                if (range > 1e-10) {
                    double normalized = (objectives.get(i).values[j] - minVals[j]) / range;
                    dist += weights[j] * normalized * normalized;
                }
            }
            dist = Math.sqrt(dist);
            
            if (dist < bestDist) {
                bestDist = dist;
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
