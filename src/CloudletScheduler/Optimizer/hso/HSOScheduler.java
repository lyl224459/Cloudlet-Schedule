package CloudletScheduler.Optimizer.hso;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * 使用混合群智能优化算法（HSO）进行云任务调度
 */
public class HSOScheduler extends Scheduler {
    private final HybridSwarmOptimization hso;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    public HSOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.hso = new HybridSwarmOptimization(
                this::estimateFitness,
                POPULATION,
                MAX_ITER,
                0,                 // 下界：VM 索引从 0 开始
                vmNum - 1,         // 上界：最大 VM 索引
                cloudletNum        // 每个 Cloudlet 分配一个 VM
        );
        Log.printLine("Using HSO scheduler");
    }

    @Override
    public int[] allocate() {
        return hso.execute();
    }
}