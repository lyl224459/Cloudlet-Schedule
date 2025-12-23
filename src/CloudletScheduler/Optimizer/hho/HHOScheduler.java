package CloudletScheduler.Optimizer.hho;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * @author : LA4AM12
 * @create : 2025-04-XX XX:XX:XX
 * @description : HHO (Harris Hawks Optimization) based Scheduler for Cloudlet-VM allocation
 */
public class HHOScheduler extends Scheduler {

    private HarrisHawksOptimization hho;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    /**
     * 构造函数：初始化 HHO 调度器
     *
     * @param cloudletList 待调度的任务列表
     * @param vmList       可用的虚拟机列表
     */
    public HHOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        // 注意：搜索空间下界为 0，上界为 vmNum - 1（VM 索引从 0 开始）
        this.hho = new HarrisHawksOptimization(
                this::estimateFitness,   // 适应度函数
                POPULATION,              // 种群大小
                0,                       // 下界（最小 VM 索引）
                vmNum - 1,               // 上界（最大 VM 索引）
                cloudletNum,             // 维度 = 任务数量（每个维度表示一个 Cloudlet 分配给哪个 VM）
                MAX_ITER                 // 最大迭代次数
        );
        Log.printLine("Using HHO scheduler");
    }

    /**
     * 执行调度：调用 HHO 算法生成最优分配方案
     *
     * @return int[] 每个 Cloudlet 对应的 VM 索引数组
     */
    @Override
    public int[] allocate() {
        return hho.execute();
    }
}