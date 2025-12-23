package CloudletScheduler.Optimizer.awoa;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * AWOA调度器 - 基于自适应鲸鱼优化算法的云任务调度器
 * 
 * 改进特性：
 * 1. 非线性自适应收敛因子
 * 2. Levy飞行增强全局搜索
 * 3. 反向学习(OBL)初始化
 * 4. 自适应探索/开发权重
 * 5. DE/best/1差分进化变异
 */
public class AWOAScheduler extends Scheduler {
    private AdaptiveWhaleOptimizationAlgorithm awoa;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    public AWOAScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.awoa = new AdaptiveWhaleOptimizationAlgorithm(
                this::estimateFitness, POPULATION, 0, vmNum - 1, cloudletNum, MAX_ITER, true);
        Log.printLine("Using AWOA (Adaptive WOA) scheduler");
    }

    @Override
    public int[] allocate() {
        return awoa.execute();
    }
}
