package CloudletScheduler.Optimizer.sfoa;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * 使用向日葵优化算法（SFOA）进行云任务调度
 */
public class SFOAScheduler extends Scheduler {
    private final SunflowerOptimization sfoa;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_FES = MainRunner.Config.MAX_ITER * POPULATION; // 或直接设为 MAX_ITER

    public SFOAScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.sfoa = new SunflowerOptimization(
                this::estimateFitness,
                POPULATION,
                MAX_FES,
                0,                 // lb: VM 索引从 0 开始
                vmNum - 1,         // ub: 最大 VM 索引
                cloudletNum        // 每个 Cloudlet 分配一个 VM
        );
        Log.printLine("Using SFOA scheduler");
    }

    @Override
    public int[] allocate() {
        return sfoa.execute();
    }
}