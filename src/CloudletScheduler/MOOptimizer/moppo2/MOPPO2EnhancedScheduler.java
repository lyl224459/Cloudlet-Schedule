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

    public MOPPO2EnhancedScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        Log.printLine("Using Enhanced Multi-Objective Predatory Prey Optimization (MO-PPO2Enhanced) scheduler");
    }

    @Override
    public int[] allocate() {

        // 多目标优化函数：复用父类评估方法
        OptFunctionMulti evalFunc = (int[] assignment) -> {
            double makespan = estimateMakespan(assignment);
            double totalTime = estimateTotalTime(assignment);
            double cost = estimateCost(assignment);
            double lb = estimateLB(assignment);

            return new ObjectiveValues(
                    makespan,
//                    totalTime,
                    cost,
                    lb
            );
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
        ParetoArchive archive = optimizer.execute();

        // 回退：存档为空 → 随机分配
        if (archive.isEmpty()) {
            Log.printLine("⚠️ MO-PPO2Enhanced produced empty archive. Using random assignment.");
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

        Log.printLine("✅ MO-PPO2Enhanced returned " + archive.size() + " Pareto solutions. Leader chosen.");

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
