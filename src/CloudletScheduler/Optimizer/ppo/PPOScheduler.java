package CloudletScheduler.Optimizer.ppo;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * @create : 2025-11-20 13:00:00
 * @description : 使用掠食-猎物优化算法（PPO）进行云任务调度
 */
public class PPOScheduler extends Scheduler {
    private PredatoryPreyOptimization ppo;

    // 从配置中读取参数
    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER;

    /**
     * 构造函数：初始化 PPO 调度器
     *
     * @param cloudletList 云任务列表
     * @param vmList       虚拟机列表
     */
    public PPOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        // 初始化 PPO 算法：
        // - 目标函数：this::estimateFitness
        // - 种群大小：POPULATION
        // - 搜索范围：[0, vmNum - 1]（每个维度对应一个 VM 索引）
        // - 维度：cloudletNum（每个云任务分配一个 VM）
        // - 最大函数评估次数：MAX_FES
        this.ppo = new PredatoryPreyOptimization(
                this::estimateFitness,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_FES
        );
        Log.printLine("Using PPO scheduler");
    }

    /**
     * 执行调度：运行 PPO 算法并返回最优分配方案
     *
     * @return 云任务到虚拟机的映射数组，索引为 Cloudlet ID，值为 VM ID
     */
    @Override
    public int[] allocate() {
        return ppo.execute();
    }
}